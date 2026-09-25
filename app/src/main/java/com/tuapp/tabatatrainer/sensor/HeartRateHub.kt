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
import com.dsi.ant.plugins.antplus.pcc.MultiDeviceSearch
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceType
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.EnumSet
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
    val connected: Boolean = false
) {
    fun isFresh(now: Long = System.currentTimeMillis()) = connected && bpm > 0 && now - lastUpdateMs < HeartRateHub.STALE_MS
}

/**
 * Gestor único de pulsómetros ANT+ y BLE.
 *
 * - ANT+: búsqueda multi-dispositivo continua y un canal por número de dispositivo.
 * - BLE: escaneo continuo del servicio HR y un GATT por dirección.
 * - Si el mismo aparato emite por ANT+ y BLE (bandas "dual") se agrupan en un solo pulsómetro:
 *   se usa ANT+ y, si deja de llegar, BLE como respaldo, sin cambiar de hueco.
 * - Cada pulsómetro físico ocupa un hueco fijo (HR1 o HR2) durante la sesión.
 */
@Singleton
class HeartRateHub @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val STALE_MS = 5_000L
        private const val SLOTS = 2
        private const val TWIN_WINDOW = 20        // segundos comparados para decidir si ANT+ y BLE son el mismo aparato
        private const val TWIN_MIN_EQUAL = 18     // de ellos, cuántos con bpm idéntico
        private val HR_SERVICE = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        private val HR_MEASUREMENT = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
        private val CCC_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _devices = MutableStateFlow<Map<String, HrDevice>>(emptyMap())
    val devices: StateFlow<Map<String, HrDevice>> = _devices.asStateFlow()

    // bleId -> antId cuando se ha detectado que son el mismo aparato
    private val twins = ConcurrentHashMap<String, String>()
    private val twinScore = ConcurrentHashMap<Pair<String, String>, ArrayDeque<Boolean>>()

    // Hueco -> id del grupo (id ANT si lo tiene, si no el BLE)
    private val slotGroup = arrayOfNulls<String>(SLOTS)
    private val slotSource = arrayOfNulls<String>(SLOTS)   // dispositivo que alimenta el hueco ahora
    private val slotFlows = List(SLOTS) {
        MutableSharedFlow<HeartRateReading>(replay = 1, extraBufferCapacity = 16, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    }

    private var running = false
    private var tickJob: Job? = null

    fun slotFlow(slot: Int): Flow<HeartRateReading> = slotFlows[slot]

    init {
        // Arranca cuando alguien observa HR1/HR2 y se para 5 s después de que nadie lo haga
        scope.launch {
            combine(slotFlows.map { it.subscriptionCount }) { counts -> counts.sum() }
                .map { it > 0 }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (active) start() else { delay(5_000); stop() }
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
            while (isActive) { tick(); delay(1_000) }
        }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        Timber.d("💓 HeartRateHub: parada")
        tickJob?.cancel()
        stopAnt()
        stopBle()
        _devices.value = emptyMap()
        twins.clear(); twinScore.clear()
        for (i in 0 until SLOTS) { slotGroup[i] = null; slotSource[i] = null }
        slotFlows.forEach { it.resetReplayCache() }
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

    private fun onBpm(id: String, bpm: Int) {
        if (!running || bpm !in 25..250) return
        upsert(id) { it!!.copy(bpm = bpm, lastUpdateMs = System.currentTimeMillis(), connected = true) }
        for (i in 0 until SLOTS) if (slotSource[i] == id) slotFlows[i].tryEmit(HeartRateReading.Value(bpm))
    }

    private fun groupOf(id: String): String = twins[id] ?: id

    /** Cada segundo: detecta gemelos ANT+/BLE, reparte huecos y elige la fuente de cada hueco */
    @Synchronized
    private fun tick() {
        val now = System.currentTimeMillis()
        val devs = _devices.value
        detectTwins(devs, now)

        // Grupos vivos (con algún dispositivo conectado)
        val groups = devs.values.filter { it.connected }.map { groupOf(it.id) }.toSet()

        // Un grupo que ahora es gemelo de otro deja libre su hueco
        for (i in 0 until SLOTS) {
            val g = slotGroup[i] ?: continue
            if (twins.containsKey(g)) {
                val merged = twins.getValue(g)
                slotGroup[i] = if (slotGroup.contains(merged)) null else merged
            }
        }
        // Si un aparato quedó en dos huecos tras agruparse, conservar el más bajo (HR1)
        for (i in 0 until SLOTS) for (j in i + 1 until SLOTS)
            if (slotGroup[i] != null && slotGroup[i] == slotGroup[j]) slotGroup[j] = null

        // Asignar grupos nuevos a huecos libres (ANT+ primero). Un hueco cuyo grupo ya no existe se reutiliza.
        val pending = groups.filter { it !in slotGroup }.sortedBy { if (it.startsWith("ANT:")) 0 else 1 }
        for (g in pending) {
            val free = (0 until SLOTS).firstOrNull { slotGroup[it] == null }
                ?: (0 until SLOTS).firstOrNull { slotGroup[it] !in groups }
                ?: break
            slotGroup[free] = g
        }

        // Fuente de cada hueco: ANT+ si está fresco, si no su gemelo BLE
        for (i in 0 until SLOTS) {
            val g = slotGroup[i]
            val members = if (g == null) emptyList() else devs.values.filter { groupOf(it.id) == g }
            val best = members.filter { it.isFresh(now) }.minByOrNull { if (it.protocol == "ANT+") 0 else 1 }
                ?: members.firstOrNull { it.connected && it.protocol == "ANT+" }
                ?: members.firstOrNull { it.connected }
            val newSource = best?.id
            if (newSource != slotSource[i]) {
                slotSource[i] = newSource
                val out = slotFlows[i]
                if (best == null) {
                    out.tryEmit(if (g == null) HeartRateReading.Scanning else HeartRateReading.Disconnected)
                } else {
                    out.tryEmit(HeartRateReading.Connected(best.name, best.protocol))
                    if (best.bpm > 0) out.tryEmit(HeartRateReading.Value(best.bpm))
                }
                Timber.d("💓 Hueco HR${i + 1}: ${best?.let { "${it.name} (${it.protocol})" } ?: "—"}")
            }
        }
    }

    private fun detectTwins(devs: Map<String, HrDevice>, now: Long) {
        val ants = devs.values.filter { it.protocol == "ANT+" && it.isFresh(now) }
        val bles = devs.values.filter { it.protocol == "BLE" && it.isFresh(now) && !twins.containsKey(it.id) }
        for (b in bles) for (a in ants) {
            if (twins.containsValue(a.id)) continue
            // Garmin y otros ponen el número ANT+ en el nombre BLE ("HRM-Pro:12345")
            val antNumber = a.id.removePrefix("ANT:")
            val nameMatch = antNumber.length >= 4 && b.name.filter { it.isDigit() }.endsWith(antNumber)
            val window = twinScore.getOrPut(b.id to a.id) { ArrayDeque() }
            window.addLast(a.bpm == b.bpm)
            if (window.size > TWIN_WINDOW) window.removeFirst()
            if (nameMatch || (window.size == TWIN_WINDOW && window.count { it } >= TWIN_MIN_EQUAL)) {
                twins[b.id] = a.id
                Timber.d("💓 ${b.name} (BLE) es el mismo aparato que ${a.name} (ANT+)")
            }
        }
    }

    // ------------------------------------------------------------------------
    // ANT+
    // ------------------------------------------------------------------------

    private var antSearch: MultiDeviceSearch? = null
    private val antHandles = ConcurrentHashMap<Int, PccReleaseHandle<AntPlusHeartRatePcc>>()
    private val antConnecting = ConcurrentHashMap.newKeySet<Int>()
    private var antRetryJob: Job? = null

    private fun startAnt() {
        scope.launch(Dispatchers.Main) {
            try {
                antSearch = MultiDeviceSearch(context, EnumSet.of(DeviceType.HEARTRATE), object : MultiDeviceSearch.SearchCallbacks {
                    override fun onSearchStarted(rssi: MultiDeviceSearch.RssiSupport?) {
                        Timber.d("💓 ANT+: búsqueda iniciada")
                    }
                    override fun onDeviceFound(result: com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult) {
                        val number = result.antDeviceNumber
                        Timber.d("💓 ANT+: encontrado ${result.deviceDisplayName} ($number)")
                        connectAnt(number, result.deviceDisplayName)
                    }
                    override fun onSearchStopped(reason: RequestAccessResult?) {
                        Timber.d("💓 ANT+: búsqueda parada ($reason)")
                        antSearch = null
                        // Seguir buscando ANT+ mientras el hub esté activo
                        if (running && reason != RequestAccessResult.DEPENDENCY_NOT_INSTALLED) {
                            antRetryJob = scope.launch { delay(5_000); if (running && antSearch == null) startAnt() }
                        }
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "💓 ANT+: no disponible")
            }
        }
    }

    private fun connectAnt(number: Int, displayName: String?) {
        if (!running || antHandles.containsKey(number) || !antConnecting.add(number)) return
        val id = "ANT:$number"
        scope.launch(Dispatchers.Main) {
            try {
                val handle = AntPlusHeartRatePcc.requestAccess(context, number, 0,
                    { pcc, code, _ ->
                        antConnecting.remove(number)
                        if (code == RequestAccessResult.SUCCESS && pcc != null && running) {
                            val name = pcc.deviceName?.takeIf { it.isNotBlank() } ?: displayName ?: "ANT+ $number"
                            upsert(id) { HrDevice(id, name, "ANT+", connected = true) }
                            pcc.subscribeHeartRateDataEvent { _, _, hr, _, _, state ->
                                if (state == AntPlusHeartRatePcc.DataState.LIVE_DATA) onBpm(id, hr)
                            }
                        } else {
                            Timber.w("💓 ANT+ $number: acceso $code")
                            antHandles.remove(number)?.close()
                            if (running) scope.launch { delay(5_000); connectAnt(number, displayName) }
                        }
                    },
                    { state ->
                        if (state == DeviceState.DEAD) {
                            Timber.w("💓 ANT+ $number: perdido, reintentando")
                            upsert(id) { (it ?: HrDevice(id, displayName ?: id, "ANT+")).copy(connected = false) }
                            antHandles.remove(number)?.close()
                            if (running) scope.launch { delay(3_000); connectAnt(number, displayName) }
                        }
                    })
                if (handle != null) antHandles[number] = handle else antConnecting.remove(number)
            } catch (e: Exception) {
                antConnecting.remove(number)
                Timber.e(e, "💓 ANT+ $number: error al conectar")
            }
        }
    }

    private fun stopAnt() {
        antRetryJob?.cancel()
        try { antSearch?.close() } catch (_: Exception) {}
        antSearch = null
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

    @SuppressLint("MissingPermission")
    private fun startBle() {
        val scanner = bluetoothAdapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: run {
            Timber.w("💓 BLE: no disponible")
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
                    gatts.remove(device.address)
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

            override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
                onBpm(id, parseHr(value))
            }

            @Deprecated("API < 33")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onBpm(id, parseHr(ch.value ?: return))
            }
        }
        try {
            val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (g != null) gatts[device.address] = g
        } catch (e: SecurityException) {
            Timber.e(e, "💓 BLE: sin permiso de conexión")
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
