package com.tuapp.tabatatrainer.sensor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeCadencePcc
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceType
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Un cadenciómetro visto por un protocolo concreto (el mismo aparato puede aparecer por ANT+ y por BLE) */
data class CadDevice(
    val id: String,              // "ANT:12345" o "BLE:AA:BB:..."
    val name: String,
    val protocol: String,        // "ANT+" o "BLE"
    val rpm: Int = 0,
    val lastUpdateMs: Long = 0L, // último dato recibido (aunque sea 0 rpm)
    val lastRevMs: Long = 0L,    // último dato con biela girando (rpm > 0)
    val connected: Boolean = false,
    val battery: SensorBattery? = null
) {
    /** 0 rpm (parado) también es un dato: fresca = conectada y con dato reciente */
    fun isFresh(now: Long = System.currentTimeMillis()) =
        connected && lastUpdateMs > 0 && now - lastUpdateMs < CadenceHub.STALE_MS
}

/**
 * Gestor único de cadenciómetros ANT+ y BLE (spec 007, T-03). Espejo de [HeartRateHub]:
 *
 * - ANT+: búsqueda compartida con el pulso ([AntRadio]) y un canal `BIKE_CADENCE` por número de dispositivo.
 * - BLE: un único escaneo continuo del servicio CSC (0x1816) y un GATT por dirección; rpm con [CscParser].
 * - Un aparato de doble señal se agrupa ([PairingPolicy], vínculo guardado solo si es seguro) y ocupa un solo
 *   hueco (C1/C2) que reparte [DualSlotAllocator]: ANT+ preferente, BLE de respaldo sin cambiar de hueco.
 * - 0 rpm (parado) es un dato válido: si pasan [CscParser.ZERO_AFTER_MS] sin vuelta de biela el valor pasa a 0
 *   sin desconectar el aparato ni soltar su hueco.
 * - Sin observadores: radios fuera a los 5 s y huecos olvidados 55 s después (R9).
 */
@Singleton
class CadenceHub @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: SensorDeviceRegistry,
    private val antRadio: AntRadio
) : AntRadioClient {
    companion object {
        const val STALE_MS = 5_000L
        private const val BLE_CONNECT_TIMEOUT_MS = 20_000L
        private const val SLOTS = 2
        private const val HISTORY = 20             // muestras (1 Hz) por dispositivo (≥ PairingThresholds.CADENCE.minSamples)
        private val KIND = SensorKind.CADENCE
        private const val HANDOFF_MAX_DIFF = 15    // relevo sin solapamiento: diferencia máxima con el último rpm del hueco
        private const val RADIO_STOP_DELAY_MS = 5_000L
        private const val SLOT_MEMORY_MS = 55_000L
        private val CSC_SERVICE = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
        private val CSC_MEASUREMENT = UUID.fromString("00002A5B-0000-1000-8000-00805f9b34fb")
        private val CCC_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val BATTERY_SERVICE = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _devices = MutableStateFlow<Map<String, CadDevice>>(emptyMap())
    val devices: StateFlow<Map<String, CadDevice>> = _devices.asStateFlow()

    private val pairing = PairingPolicy(KIND, PairingThresholds.CADENCE)
    private val allocator = DualSlotAllocator(KIND, registry, "C", HANDOFF_MAX_DIFF, SLOTS) { Timber.d("🚴 $it") }
    private val history = HashMap<String, ArrayDeque<Int>>()

    private val slotFlows = List(SLOTS) {
        MutableSharedFlow<CadenceReading>(replay = 1, extraBufferCapacity = 16, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    }

    @Volatile private var running = false
    private var tickJob: Job? = null

    fun slotFlow(slot: Int): Flow<CadenceReading> = slotFlows[slot]

    // Batería del aparato que alimenta cada hueco ahora (spec 005)
    private val slotBatteries = List(SLOTS) { MutableStateFlow<SensorBattery?>(null) }

    fun slotBattery(slot: Int): StateFlow<SensorBattery?> = slotBatteries[slot].asStateFlow()

    // Estado completo de cada hueco (la UI lo usa desde T-04)
    private val slotStatuses = List(SLOTS) { MutableStateFlow(SlotStatus(it)) }

    fun slotStatus(slot: Int): StateFlow<SlotStatus> = slotStatuses[slot].asStateFlow()

    init {
        scope.launch {
            combine(slotFlows.map { it.subscriptionCount }) { counts -> counts.sum() }
                .map { it > 0 }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (active) start() else {
                        delay(RADIO_STOP_DELAY_MS); stopRadios()
                        delay(SLOT_MEMORY_MS); forgetSlots()
                    }
                }
        }
    }

    @Synchronized
    fun start() {
        if (running) return
        running = true
        Timber.d("🚴 CadenceHub: inicio")
        slotFlows.forEach { it.tryEmit(CadenceReading.Scanning) }
        startAnt()
        startBle()
        tickJob = scope.launch {
            var n = 0
            while (isActive) {
                tick()
                if (++n % 5 == 0) { ensureBle(); antRadio.ensure() }
                delay(1_000)
            }
        }
    }

    /** Parada completa: radios y huecos */
    @Synchronized
    fun stop() {
        stopRadios()
        forgetSlots()
    }

    /** Para las radios y la sesión de señales, pero recuerda qué aparato va en cada hueco */
    @Synchronized
    private fun stopRadios() {
        if (!running) return
        running = false
        Timber.d("🚴 CadenceHub: parada (huecos recordados)")
        tickJob?.cancel()
        stopAnt()
        stopBle()
        _devices.value = emptyMap()
        history.clear(); pairing.reset(); allocator.pause()
        slotBatteries.forEach { it.value = null }
        slotStatuses.forEachIndexed { i, s -> s.value = SlotStatus(i, deviceKey = allocator.groupIn(i)) }
        slotFlows.forEach { it.resetReplayCache() }
    }

    /** Olvida el reparto de huecos y los vínculos de sesión (solo con las radios paradas) */
    @Synchronized
    private fun forgetSlots() {
        if (running) return
        allocator.clear()
        slotStatuses.forEachIndexed { i, s -> s.value = SlotStatus(i) }
        Timber.d("🚴 CadenceHub: huecos olvidados")
    }

    /** Fuerza una reconexión completa (botón "reconectar") */
    fun restart() {
        stop()
        if (slotFlows.any { it.subscriptionCount.value > 0 }) start()
    }

    // ------------------------------------------------------------------------
    // Estado común
    // ------------------------------------------------------------------------

    private fun upsert(id: String, f: (CadDevice?) -> CadDevice) {
        _devices.update { it + (id to f(it[id])) }
    }

    /** Solo actualiza un aparato ya conocido (los datos nunca crean ni conectan nada) */
    private fun update(id: String, f: (CadDevice) -> CadDevice) {
        _devices.update { m -> m[id]?.let { m + (id to f(it)) } ?: m }
    }

    private fun onBattery(id: String, battery: SensorBattery?) {
        if (!running || battery == null) return
        update(id) { it.copy(battery = battery) }
        Timber.d("🔋 $id: ${battery.percent?.let { "$it %" } ?: battery.level}")
    }

    /** Dato de cadencia (0 = parado, válido) */
    private fun onRpm(id: String, rpm: Int) {
        if (!running || rpm !in 0..CscParser.MAX_RPM) return
        val now = System.currentTimeMillis()
        update(id) { it.copy(rpm = rpm, lastUpdateMs = now, lastRevMs = if (rpm > 0) now else it.lastRevMs, connected = true) }
        publish(id, rpm)
    }

    /** Paquete sin rpm nuevo (sin vuelta aún): el aparato sigue vivo */
    private fun onAlive(id: String) {
        if (!running) return
        update(id) { it.copy(lastUpdateMs = System.currentTimeMillis(), connected = true) }
    }

    private fun publish(id: String, rpm: Int) {
        for (i in 0 until SLOTS) if (allocator.sourceOf(i) == id) slotFlows[i].tryEmit(CadenceReading.Value(rpm))
    }

    /** Cada segundo: pone a 0 lo parado, empareja señales, reparte huecos y publica la fuente de cada hueco */
    @Synchronized
    private fun tick() {
        val now = System.currentTimeMillis()
        zeroStopped(now)
        val devs = _devices.value
        updateHistory(devs, now)
        detectTwins(devs, now)

        val signals = devs.values.map {
            SignalState(it.id, it.name, it.protocol == "ANT+", it.connected, it.isFresh(now), it.rpm, it.battery)
        }
        for (st in allocator.tick(signals, now)) {
            val i = st.slot
            val changed = st.sourceId != slotStatuses[i].value.sourceId
            slotStatuses[i].value = st
            slotBatteries[i].value = st.battery
            val out = slotFlows[i]
            if (st.sourceId == null) {
                if (changed) out.tryEmit(if (st.deviceKey == null) CadenceReading.Scanning else CadenceReading.Disconnected)
            } else {
                // Connected en cada tick para que un suscriptor tardío vea el protocolo (replay = 1)
                out.tryEmit(CadenceReading.Connected(st.sourceName ?: st.sourceId, st.protocol ?: "BLE"))
                if (changed) out.tryEmit(CadenceReading.Value(st.value))   // 0 rpm también
            }
        }
    }

    /** Sin vuelta de biela durante [CscParser.ZERO_AFTER_MS] (o sin datos): 0 rpm, sin desconectar */
    private fun zeroStopped(now: Long) {
        val stopped = _devices.value.values.filter {
            it.connected && it.rpm > 0 && now - it.lastRevMs >= CscParser.ZERO_AFTER_MS
        }
        for (d in stopped) {
            update(d.id) { it.copy(rpm = 0) }
            publish(d.id, 0)
            Timber.d("🚴 ${d.name} (${d.protocol}): parado, 0 rpm")
        }
    }

    /** Serie de rpm a 1 Hz por aparato fresco (los ceros cuentan; se reinicia al perder datos) */
    private fun updateHistory(devs: Map<String, CadDevice>, now: Long) {
        history.keys.retainAll(devs.keys)
        for (d in devs.values) {
            if (d.isFresh(now)) {
                val h = history.getOrPut(d.id) { ArrayDeque() }
                h.addLast(d.rpm)
                if (h.size > HISTORY) h.removeFirst()
            } else history.remove(d.id)
        }
    }

    /** Emparejado ANT+ ↔ BLE según [PairingPolicy] (spec 007, R3/R5): persistente solo si es seguro */
    private fun detectTwins(devs: Map<String, CadDevice>, now: Long) {
        val signals = devs.values.filter { it.connected }.map {
            SignalSnapshot(it.id, it.name, it.isFresh(now), history[it.id]?.toList().orEmpty())
        }
        for (p in pairing.evaluate(signals, registry)) {
            val a = devs[p.antId] ?: continue
            val b = devs[p.bleId] ?: continue
            if (p.persistent) {
                if (registry.link(KIND, a.id, b.id, a.name, manual = false)) {
                    allocator.leaveSession(a.id, b.id)
                    Timber.d("🚴 ${b.name} (BLE) es el mismo aparato que ${a.name} (ANT+) — guardado (${p.reason})")
                }
            } else if (allocator.groupOf(a.id) != allocator.groupOf(b.id)) {
                val (joiner, target) = if (allocator.hasSlot(allocator.groupOf(a.id))) b to a else a to b
                allocator.joinSession(joiner.id, allocator.groupOf(target.id))
                Timber.d("🚴 ${b.name} (BLE) y ${a.name} (ANT+) agrupados en la sesión (${p.reason})")
            }
        }
    }

    // ------------------------------------------------------------------------
    // ANT+ (búsqueda compartida en AntRadio; aquí solo los canales de cadencia)
    // ------------------------------------------------------------------------

    private val antHandles = ConcurrentHashMap<Int, PccReleaseHandle<AntPlusBikeCadencePcc>>()
    private val antConnecting = ConcurrentHashMap.newKeySet<Int>()

    // AntRadio llama a estos métodos con su propio cerrojo: no deben tomar el del hub (orden hub → radio)
    override val antType: DeviceType = DeviceType.BIKE_CADENCE
    override val antTag = "🚴"
    override fun wantsAntSearch() = running && antHandles.size < SLOTS
    override fun isAntKnown(number: Int) = antHandles.containsKey(number) || antConnecting.contains(number)
    override fun onAntFound(number: Int, displayName: String?) = connectAnt(number, displayName)

    private fun startAnt() {
        antRadio.register(this)
        antRadio.request()
    }

    private fun connectAnt(number: Int, displayName: String?) {
        if (!running || antHandles.containsKey(number) || !antConnecting.add(number)) return
        if (!antRadio.beginConnect(this, number)) { antConnecting.remove(number); return }
        val id = "ANT:$number"
        val resultReceiver = AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeCadencePcc> { pcc, code, _ ->
            antConnecting.remove(number)
            if (code == RequestAccessResult.SUCCESS && pcc != null && running) {
                val name = pcc.deviceName?.takeIf { it.isNotBlank() } ?: displayName ?: "ANT+ $number"
                upsert(id) { CadDevice(id, name, "ANT+", connected = true) }
                Timber.d("🚴 ANT+ $name: conectado")
                try {
                    pcc.subscribeCalculatedCadenceEvent { _, _, calculatedCadence ->
                        calculatedCadence?.toInt()?.let { onRpm(id, it) }
                    }
                    // Datos en bruto: llegan aunque la biela esté parada → el aparato sigue fresco (0 rpm)
                    pcc.subscribeRawCadenceDataEvent { _, _, _, _ -> onAlive(id) }
                    pcc.subscribeBatteryStatusEvent { _, _, voltage, status ->
                        onBattery(id, SensorBattery.fromAntStatus(status?.name, voltage?.toFloat()))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "🚴 ANT+ $name: error al suscribirse")
                }
            } else {
                Timber.w("🚴 ANT+ $number: acceso $code")
                antHandles.remove(number)?.close()
            }
            antRadio.endConnect(this@CadenceHub, number)
        }
        val stateReceiver = AntPluginPcc.IDeviceStateChangeReceiver { state ->
            if (state == DeviceState.DEAD) {
                Timber.w("🚴 ANT+ $number: perdido, se volverá a buscar")
                upsert(id) { (it ?: CadDevice(id, displayName ?: id, "ANT+")).copy(connected = false, rpm = 0) }
                antHandles.remove(number)?.close()
                if (running) antRadio.request(AntRadio.RETRY_MS)
            }
        }
        scope.launch(Dispatchers.Main) {
            try {
                // false = solo cadencia (no velocidad+cadencia combinado), como hasta ahora
                val handle = AntPlusBikeCadencePcc.requestAccess(context, number, 0, false, resultReceiver, stateReceiver)
                if (handle != null) antHandles[number] = handle
                else { antConnecting.remove(number); antRadio.endConnect(this@CadenceHub, number) }
            } catch (e: Exception) {
                antConnecting.remove(number)
                Timber.e(e, "🚴 ANT+ $number: error al conectar")
                antRadio.endConnect(this@CadenceHub, number, AntRadio.SEARCH_PAUSE_MS)
            }
        }
    }

    private fun stopAnt() {
        antRadio.release(this)
        antHandles.values.forEach { try { it.close() } catch (_: Exception) {} }
        antHandles.clear()
        antConnecting.clear()
    }

    // ------------------------------------------------------------------------
    // BLE
    // ------------------------------------------------------------------------

    private val bluetoothAdapter get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val gatts = ConcurrentHashMap<String, BluetoothGatt>()
    private val parsers = ConcurrentHashMap<String, CscParser>()
    private var scanCallback: ScanCallback? = null
    private var bleUnavailableLogged = false

    /** Arranca el escaneo si el Bluetooth se enciende con el hub ya en marcha */
    private fun ensureBle() {
        if (!running) return
        val enabled = bluetoothAdapter?.isEnabled == true
        if (scanCallback != null && !enabled) { scanCallback = null; Timber.w("🚴 BLE: Bluetooth apagado") }
        if (scanCallback == null && enabled) startBle()
    }

    /** Un único escaneo continuo (no se para y arranca en bucle) */
    @SuppressLint("MissingPermission")
    private fun startBle() {
        val scanner = bluetoothAdapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: run {
            if (!bleUnavailableLogged) Timber.w("🚴 BLE: no disponible")
            bleUnavailableLogged = true
            return
        }
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                if (!gatts.containsKey(device.address)) {
                    val name = result.scanRecord?.deviceName ?: safeName(device) ?: "BLE ${device.address.takeLast(5)}"
                    connectBle(device, name)
                }
            }
            override fun onScanFailed(errorCode: Int) {
                Timber.e("🚴 BLE: escaneo falló ($errorCode)")
                scanCallback = null
                if (running) scope.launch { delay(5_000); if (running && scanCallback == null) startBle() }
            }
        }
        try {
            val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(CSC_SERVICE)).build()
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
            scanner.startScan(listOf(filter), settings, cb)
            scanCallback = cb
            bleUnavailableLogged = false
            Timber.d("🚴 BLE: escaneo iniciado")
        } catch (e: SecurityException) {
            Timber.e(e, "🚴 BLE: sin permiso de escaneo")
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeName(d: BluetoothDevice): String? = try { d.name } catch (_: SecurityException) { null }

    @SuppressLint("MissingPermission")
    private fun connectBle(device: BluetoothDevice, name: String) {
        if (!running) return
        val id = "BLE:${device.address}"
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Timber.w("🚴 BLE $name: desconectado ($status)")
                    upsert(id) { (it ?: CadDevice(id, name, "BLE")).copy(connected = false, rpm = 0) }
                    parsers.remove(id)
                    g.close()
                    gatts.remove(device.address, g)
                    if (running) scope.launch { delay(3_000); if (running && !gatts.containsKey(device.address)) connectBle(device, name) }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val ch = g.getService(CSC_SERVICE)?.getCharacteristic(CSC_MEASUREMENT) ?: run {
                    Timber.w("🚴 BLE $name: sin característica CSC"); return
                }
                parsers[id] = CscParser()
                g.setCharacteristicNotification(ch, true)
                val desc = ch.getDescriptor(CCC_DESCRIPTOR) ?: run {
                    Timber.w("🚴 BLE $name: sin descriptor de notificaciones"); return
                }
                writeNotify(g, desc)
                upsert(id) { CadDevice(id, name, "BLE", connected = true) }
                Timber.d("🚴 BLE $name: conectado")
            }

            // Cola GATT: una operación cada vez. Tras activar las notificaciones CSC se lee la batería;
            // tras leerla se activan sus notificaciones (si el aparato las admite).
            override fun onDescriptorWrite(g: BluetoothGatt, desc: BluetoothGattDescriptor, status: Int) {
                if (desc.characteristic.uuid != CSC_MEASUREMENT) return
                val bat = g.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL) ?: run {
                    Timber.d("🔋 BLE $name: sin servicio de batería"); return
                }
                try {
                    if (!g.readCharacteristic(bat)) Timber.w("🔋 BLE $name: lectura de batería rechazada")
                } catch (e: SecurityException) {
                    Timber.w(e, "🔋 BLE $name: sin permiso para leer batería")
                }
            }

            override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                onBatteryRead(g, ch, value, status)
            }

            @Deprecated("API < 33")
            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onBatteryRead(g, ch, ch.value, status)
            }

            private fun onBatteryRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray?, status: Int) {
                if (ch.uuid != BATTERY_LEVEL) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Timber.w("🔋 BLE $name: lectura de batería falló ($status)"); return
                }
                onBattery(id, SensorBattery.parseBleBatteryLevel(value))
                if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                    try {
                        g.setCharacteristicNotification(ch, true)
                        ch.getDescriptor(CCC_DESCRIPTOR)?.let { writeNotify(g, it) }
                    } catch (e: SecurityException) {
                        Timber.w(e, "🔋 notificaciones de batería sin permiso")
                    }
                }
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
                onNotification(ch, value)
            }

            @Deprecated("API < 33")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onNotification(ch, ch.value ?: return)
            }

            private fun onNotification(ch: BluetoothGattCharacteristic, value: ByteArray) {
                when (ch.uuid) {
                    BATTERY_LEVEL -> onBattery(id, SensorBattery.parseBleBatteryLevel(value))
                    CSC_MEASUREMENT -> {
                        val parser = parsers[id] ?: return   // ya desconectado: no revivir el aparato
                        val rpm = parser.onMeasurement(value, System.currentTimeMillis())
                        if (rpm != null) onRpm(id, rpm) else onAlive(id)
                    }
                }
            }
        }
        try {
            val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (g != null) {
                gatts[device.address] = g
                // Vigilante: una conexión que no llega a suscribirse en 20 s se cierra para que el escaneo la
                // vuelva a enganchar en cuanto emita.
                scope.launch {
                    delay(BLE_CONNECT_TIMEOUT_MS)
                    if (running && gatts[device.address] === g && _devices.value[id]?.connected != true) {
                        Timber.w("🚴 BLE $name: conexión sin respuesta, se reintenta")
                        try { g.disconnect(); g.close() } catch (_: Exception) {}
                        gatts.remove(device.address, g)
                    }
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "🚴 BLE: sin permiso de conexión")
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeNotify(g: BluetoothGatt, desc: BluetoothGattDescriptor) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(desc)
            }
        } catch (e: SecurityException) {
            Timber.w(e, "🚴 BLE: notificaciones sin permiso")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBle() {
        try { scanCallback?.let { bluetoothAdapter?.bluetoothLeScanner?.stopScan(it) } } catch (_: Exception) {}
        scanCallback = null
        gatts.values.forEach { try { it.disconnect(); it.close() } catch (_: Exception) {} }
        gatts.clear()
        parsers.clear()
    }
}
