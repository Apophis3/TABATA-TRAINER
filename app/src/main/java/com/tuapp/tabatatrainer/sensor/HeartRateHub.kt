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
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceType
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Un pulsómetro visto por un protocolo concreto (el mismo aparato puede aparecer por ANT+ y por BLE) */
data class HrDevice(
    val id: String,              // "ANT:12345" o "BLE:AA:BB:..."
    val name: String,
    val protocol: String,        // "ANT+" o "BLE"
    val bpm: Int = 0,
    val lastUpdateMs: Long = 0L,
    val connected: Boolean = false,
    val battery: SensorBattery? = null   // spec 005: null = sin dato
) {
    fun isFresh(now: Long = System.currentTimeMillis()) = connected && bpm > 0 && now - lastUpdateMs < HeartRateHub.STALE_MS
}

/**
 * Gestor único de pulsómetros ANT+ y BLE.
 *
 * - ANT+: búsqueda compartida con la cadencia ([AntRadio]) y un canal por número de dispositivo.
 * - BLE: escaneo continuo del servicio HR y un GATT por dirección.
 * - Si el mismo aparato emite por ANT+ y BLE (bandas "dual") se agrupan en un solo pulsómetro y el
 *   vínculo se guarda en [SensorDeviceRegistry] solo si es seguro ([PairingPolicy]): se usa ANT+ y, si deja
 *   de llegar, BLE, sin cambiar de hueco.
 * - El reparto de huecos (HR1/HR2) lo decide [DualSlotAllocator]: un aparato = un hueco, hueco preferido,
 *   ANT+ desplaza a BLE. Al quedarse sin observadores se paran las radios a los 5 s, pero los huecos se
 *   recuerdan [SLOT_MEMORY_MS] más para no repartirlos de nuevo al cambiar de pantalla (spec 007, R9).
 */
@Singleton
class HeartRateHub @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: SensorDeviceRegistry,
    private val antRadio: AntRadio
) : AntRadioClient {
    companion object {
        const val STALE_MS = 5_000L
        private const val BLE_CONNECT_TIMEOUT_MS = 20_000L
        private const val SLOTS = 2
        private const val HISTORY = 20             // muestras (1 Hz) guardadas por dispositivo (≥ PairingThresholds.HR.minSamples)
        private val KIND = SensorKind.HR
        private const val HANDOFF_MAX_DIFF = 15    // relevo sin solapamiento: diferencia máxima con el último bpm del hueco
        private const val RADIO_STOP_DELAY_MS = 5_000L   // sin observadores: parar radios
        private const val SLOT_MEMORY_MS = 55_000L       // ...y olvidar los huecos tras este margen adicional
        private val HR_SERVICE = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        private val HR_MEASUREMENT = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
        private val CCC_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val BATTERY_SERVICE = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _devices = MutableStateFlow<Map<String, HrDevice>>(emptyMap())
    val devices: StateFlow<Map<String, HrDevice>> = _devices.asStateFlow()

    private val pairing = PairingPolicy(KIND, PairingThresholds.HR)
    private val allocator = DualSlotAllocator(KIND, registry, "HR", HANDOFF_MAX_DIFF, SLOTS) { Timber.d("💓 $it") }
    private val history = HashMap<String, ArrayDeque<Int>>()

    private val slotFlows = List(SLOTS) {
        MutableSharedFlow<HeartRateReading>(replay = 1, extraBufferCapacity = 16, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    }

    @Volatile private var running = false
    private var tickJob: Job? = null

    fun slotFlow(slot: Int): Flow<HeartRateReading> = slotFlows[slot]

    // Batería del dispositivo que alimenta cada hueco ahora (spec 005, RF-4)
    private val slotBatteries = List(SLOTS) { MutableStateFlow<SensorBattery?>(null) }

    fun slotBattery(slot: Int): StateFlow<SensorBattery?> = slotBatteries[slot].asStateFlow()

    // Estado completo de cada hueco: aparato, señal (ANT+/BLE), valor y batería (spec 007, T-02; la UI lo usa en T-04)
    private val slotStatuses = List(SLOTS) { MutableStateFlow(SlotStatus(it)) }

    fun slotStatus(slot: Int): StateFlow<SlotStatus> = slotStatuses[slot].asStateFlow()

    init {
        // Arranca cuando alguien observa HR1/HR2. Sin observadores: radios fuera a los 5 s y huecos
        // olvidados 55 s después (si vuelve un observador antes, collectLatest cancela la espera).
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
        Timber.d("💓 HeartRateHub: inicio")
        slotFlows.forEach { it.tryEmit(HeartRateReading.Scanning) }
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
        Timber.d("💓 HeartRateHub: parada (huecos recordados)")
        tickJob?.cancel()
        stopAnt()
        stopBle()
        _devices.value = emptyMap()
        // El registro persistente NO se borra: los vínculos ANT+ ↔ BLE sobreviven entre sesiones
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
        Timber.d("💓 HeartRateHub: huecos olvidados")
    }

    /** Fuerza una reconexión completa (botón "reconectar") */
    fun restart() {
        stop()
        if (slotFlows.any { it.subscriptionCount.value > 0 }) start()
    }

    // ------------------------------------------------------------------------
    // Estado común
    // ------------------------------------------------------------------------

    private fun upsert(id: String, f: (HrDevice?) -> HrDevice) {
        _devices.update { it + (id to f(it[id])) }
    }

    /** Solo actualiza un dispositivo ya conocido (la batería nunca crea ni conecta nada, RF-7) */
    private fun onBattery(id: String, battery: SensorBattery?) {
        if (!running || battery == null) return
        _devices.update { m -> m[id]?.let { m + (id to it.copy(battery = battery)) } ?: m }
        Timber.d("🔋 $id: ${battery.percent?.let { "$it %" } ?: battery.level}")
    }

    private fun onBpm(id: String, bpm: Int) {
        if (!running || bpm !in 25..250) return
        upsert(id) { it!!.copy(bpm = bpm, lastUpdateMs = System.currentTimeMillis(), connected = true) }
        for (i in 0 until SLOTS) if (allocator.sourceOf(i) == id) slotFlows[i].tryEmit(HeartRateReading.Value(bpm))
    }

    /** Cada segundo: empareja señales, reparte huecos y publica la fuente de cada hueco */
    @Synchronized
    private fun tick() {
        val now = System.currentTimeMillis()
        val devs = _devices.value
        updateHistory(devs, now)
        detectTwins(devs, now)

        val signals = devs.values.map {
            SignalState(it.id, it.name, it.protocol == "ANT+", it.connected, it.isFresh(now), it.bpm, it.battery)
        }
        for (st in allocator.tick(signals, now)) {
            val i = st.slot
            val changed = st.sourceId != slotStatuses[i].value.sourceId
            slotStatuses[i].value = st
            slotBatteries[i].value = st.battery
            val out = slotFlows[i]
            if (st.sourceId == null) {
                if (changed) out.tryEmit(if (st.deviceKey == null) HeartRateReading.Scanning else HeartRateReading.Disconnected)
            } else {
                // Reemitir Connected en cada tick: como el flow tiene replay=1 y entre ticks
                // llegan muchos Value, un suscriptor tardío solo replayaría el último Value y
                // nunca vería el protocolo. Reemitirlo cada segundo lo mantiene visible (badge).
                out.tryEmit(HeartRateReading.Connected(st.sourceName ?: st.sourceId, st.protocol ?: "BLE"))
                if (changed && st.value > 0) out.tryEmit(HeartRateReading.Value(st.value))
            }
        }
    }

    /** Serie de bpm a 1 Hz por dispositivo fresco (se reinicia al perder datos para no desalinear) */
    private fun updateHistory(devs: Map<String, HrDevice>, now: Long) {
        history.keys.retainAll(devs.keys)
        for (d in devs.values) {
            if (d.isFresh(now)) {
                val h = history.getOrPut(d.id) { ArrayDeque() }
                h.addLast(d.bpm)
                if (h.size > HISTORY) h.removeFirst()
            } else history.remove(d.id)
        }
    }

    /** Emparejado ANT+ ↔ BLE según [PairingPolicy] (spec 007, R3): persistente solo si es seguro */
    private fun detectTwins(devs: Map<String, HrDevice>, now: Long) {
        val signals = devs.values.filter { it.connected }.map {
            SignalSnapshot(it.id, it.name, it.isFresh(now), history[it.id]?.toList().orEmpty())
        }
        for (p in pairing.evaluate(signals, registry)) {
            val a = devs[p.antId] ?: continue
            val b = devs[p.bleId] ?: continue
            if (p.persistent) {
                if (registry.link(KIND, a.id, b.id, a.name, manual = false)) {
                    allocator.leaveSession(a.id, b.id)
                    Timber.d("💓 ${b.name} (BLE) es el mismo aparato que ${a.name} (ANT+) — guardado (${p.reason})")
                }
            } else if (allocator.groupOf(a.id) != allocator.groupOf(b.id)) {
                // Agrupar solo en esta sesión, sin guardar: se une al que ya tiene hueco
                val (joiner, target) = if (allocator.hasSlot(allocator.groupOf(a.id))) b to a else a to b
                allocator.joinSession(joiner.id, allocator.groupOf(target.id))
                Timber.d("💓 ${b.name} (BLE) y ${a.name} (ANT+) agrupados en la sesión (${p.reason})")
            }
        }
    }

    // ------------------------------------------------------------------------
    // ANT+
    // ------------------------------------------------------------------------

    // La búsqueda ANT+ la lleva [AntRadio], compartida con la cadencia (spec 007, T-03): una sola búsqueda por
    // ventanas, cerrada antes de pedir acceso y parada mientras alguien conecta. Aquí solo los canales de pulso.
    private val antHandles = ConcurrentHashMap<Int, PccReleaseHandle<AntPlusHeartRatePcc>>()
    private val antConnecting = ConcurrentHashMap.newKeySet<Int>()

    // AntRadio llama a estos métodos con su propio cerrojo: no deben tomar el del hub (orden hub → radio)
    override val antType: DeviceType = DeviceType.HEARTRATE
    override val antTag = "💓"
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
        scope.launch(Dispatchers.Main) {
            try {
                antRadio.awaitClosed()   // la búsqueda se cierra en segundo plano: radio libre antes de pedir acceso
                val handle = AntPlusHeartRatePcc.requestAccess(context, number, 0,
                    { pcc, code, _ ->
                        antConnecting.remove(number)
                        if (code == RequestAccessResult.SUCCESS && pcc != null && running) {
                            val name = pcc.deviceName?.takeIf { it.isNotBlank() } ?: displayName ?: "ANT+ $number"
                            upsert(id) { HrDevice(id, name, "ANT+", connected = true) }
                            Timber.d("💓 ANT+ $name: conectado")
                            pcc.subscribeHeartRateDataEvent { _, _, hr, _, _, state ->
                                if (state == AntPlusHeartRatePcc.DataState.LIVE_DATA) onBpm(id, hr)
                            }
                        } else {
                            Timber.w("💓 ANT+ $number: acceso $code")
                            antHandles.remove(number)?.close()
                        }
                        // Seguir buscando (otro pulsómetro, o este mismo si ha fallado)
                        antRadio.endConnect(this@HeartRateHub, number)
                    },
                    { state ->
                        if (state == DeviceState.DEAD) {
                            Timber.w("💓 ANT+ $number: perdido, se volverá a buscar")
                            upsert(id) { (it ?: HrDevice(id, displayName ?: id, "ANT+")).copy(connected = false) }
                            antHandles.remove(number)?.close()
                            if (running) antRadio.request(AntRadio.RETRY_MS)
                        }
                    })
                if (handle != null) antHandles[number] = handle
                else { antConnecting.remove(number); antRadio.endConnect(this@HeartRateHub, number) }
            } catch (e: Exception) {
                antConnecting.remove(number)
                Timber.e(e, "💓 ANT+ $number: error al conectar")
                antRadio.endConnect(this@HeartRateHub, number, AntRadio.SEARCH_PAUSE_MS)
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
    private val bleNames = ConcurrentHashMap<String, String>()
    private var scanCallback: ScanCallback? = null
    private var bleUnavailableLogged = false

    /** Arranca el escaneo si el Bluetooth se enciende con el hub ya en marcha (antes había que pulsar actualizar) */
    private fun ensureBle() {
        if (!running) return
        val enabled = bluetoothAdapter?.isEnabled == true
        if (scanCallback != null && !enabled) { scanCallback = null; Timber.w("💓 BLE: Bluetooth apagado") }
        if (scanCallback == null && enabled) startBle()
    }

    @SuppressLint("MissingPermission")
    private fun startBle() {
        val scanner = bluetoothAdapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: run {
            if (!bleUnavailableLogged) Timber.w("💓 BLE: no disponible")
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
                Timber.e("💓 BLE: escaneo falló ($errorCode)")
                scanCallback = null
                if (running) scope.launch { delay(5_000); if (running && scanCallback == null) startBle() }
            }
        }
        try {
            val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build()
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
            scanner.startScan(listOf(filter), settings, cb)
            scanCallback = cb
            bleUnavailableLogged = false
            Timber.d("💓 BLE: escaneo iniciado")
        } catch (e: SecurityException) {
            Timber.e(e, "💓 BLE: sin permiso de escaneo")
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeName(d: BluetoothDevice): String? = try { d.name } catch (_: SecurityException) { null }

    @SuppressLint("MissingPermission")
    private fun connectBle(device: BluetoothDevice, name: String) {
        if (!running) return
        val id = "BLE:${device.address}"
        bleNames[device.address] = name
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Timber.w("💓 BLE $name: desconectado ($status)")
                    upsert(id) { (it ?: HrDevice(id, name, "BLE")).copy(connected = false) }
                    g.close()
                    gatts.remove(device.address, g)
                    // Reconectar al mismo aparato
                    if (running) scope.launch { delay(3_000); if (running && !gatts.containsKey(device.address)) connectBle(device, name) }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val ch = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT) ?: run {
                    Timber.w("💓 BLE $name: sin característica HR"); return
                }
                g.setCharacteristicNotification(ch, true)
                val desc = ch.getDescriptor(CCC_DESCRIPTOR) ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(desc)
                }
                upsert(id) { HrDevice(id, name, "BLE", connected = true) }
                Timber.d("💓 BLE $name: conectado")
            }

            // Cola GATT: una operación cada vez. Tras activar las notificaciones de HR se lee la
            // batería; tras leerla se activan sus notificaciones (si el aparato las admite).
            override fun onDescriptorWrite(g: BluetoothGatt, desc: BluetoothGattDescriptor, status: Int) {
                if (desc.characteristic.uuid != HR_MEASUREMENT) return
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
                if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) enableNotify(g, ch)
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
                    else -> onBpm(id, parseHr(value))
                }
            }
        }
        try {
            val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (g != null) {
                gatts[device.address] = g
                // Vigilante: una conexión que no llega a suscribirse en 20 s se queda colgada y,
                // mientras siga en `gatts`, el escaneo ignora el aparato. Se cierra para que el
                // escaneo lo vuelva a enganchar en cuanto emita.
                scope.launch {
                    delay(BLE_CONNECT_TIMEOUT_MS)
                    if (running && gatts[device.address] === g && _devices.value[id]?.connected != true) {
                        Timber.w("💓 BLE $name: conexión sin respuesta, se reintenta")
                        try { g.disconnect(); g.close() } catch (_: Exception) {}
                        gatts.remove(device.address, g)
                    }
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "💓 BLE: sin permiso de conexión")
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        try {
            g.setCharacteristicNotification(ch, true)
            val desc = ch.getDescriptor(CCC_DESCRIPTOR) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(desc)
            }
        } catch (e: SecurityException) {
            Timber.w(e, "🔋 notificaciones de batería sin permiso")
        }
    }

    private fun parseHr(data: ByteArray): Int {
        if (data.size < 2) return 0
        return if (data[0].toInt() and 0x01 != 0) {
            if (data.size >= 3) (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8) else 0
        } else data[1].toInt() and 0xFF
    }

    @SuppressLint("MissingPermission")
    private fun stopBle() {
        try { scanCallback?.let { bluetoothAdapter?.bluetoothLeScanner?.stopScan(it) } } catch (_: Exception) {}
        scanCallback = null
        gatts.values.forEach { try { it.disconnect(); it.close() } catch (_: Exception) {} }
        gatts.clear()
    }
}
