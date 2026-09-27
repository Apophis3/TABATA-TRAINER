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
 * - Si el mismo aparato emite por ANT+ y BLE (bandas "dual") se agrupan en un solo pulsómetro y el
 *   vínculo se guarda en [HrDeviceRegistry]: se usa ANT+ y, si deja de llegar, BLE, sin cambiar de hueco.
 * - Cada pulsómetro físico ocupa un hueco fijo (HR1 o HR2) durante la sesión.
 */
@Singleton
class HeartRateHub @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: HrDeviceRegistry
) {
    companion object {
        const val STALE_MS = 5_000L
        private const val BLE_CONNECT_TIMEOUT_MS = 20_000L
        private const val SLOTS = 2
        private const val HISTORY = 20             // muestras (1 Hz) guardadas por dispositivo
        private const val TWIN_MIN_SAMPLES = 15    // muestras mínimas antes de comparar ANT+ y BLE
        private const val TWIN_MAX_LAG = 5         // desfase máximo ANT+/BLE probado (s)
        private const val TWIN_MAX_AVG_DIFF = 4.0  // diferencia media (con desfase) para vincular de forma permanente
        private const val TWIN_MIN_RANGE = 4       // variación mínima de HR para que la comparación sea fiable
        private const val TWIN_FLAT_MAX_DIFF = 2.0 // con HR plana: solo se agrupan en la sesión (no se guarda)
        private const val QUARANTINE_MS = 30_000L  // espera de un desconocido antes de darle hueco propio
        private const val HANDOFF_MAX_DIFF = 15    // relevo sin solapamiento: diferencia máxima con el último bpm del hueco
        private val HR_SERVICE = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        private val HR_MEASUREMENT = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
        private val CCC_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _devices = MutableStateFlow<Map<String, HrDevice>>(emptyMap())
    val devices: StateFlow<Map<String, HrDevice>> = _devices.asStateFlow()

    // Vínculos solo de esta sesión (relevos sin solapamiento o HR plana): id -> grupo
    private val sessionTwins = ConcurrentHashMap<String, String>()
    private val history = HashMap<String, ArrayDeque<Int>>()
    private val quarantineSince = HashMap<String, Long>()

    // Hueco -> id del grupo (clave del registro si está vinculado, si no el id del dispositivo)
    private val slotGroup = arrayOfNulls<String>(SLOTS)
    private val slotLastBpm = IntArray(SLOTS)
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
        // El registro persistente NO se borra: los vínculos ANT+ ↔ BLE sobreviven entre sesiones
        sessionTwins.clear(); history.clear(); quarantineSince.clear()
        for (i in 0 until SLOTS) { slotGroup[i] = null; slotSource[i] = null; slotLastBpm[i] = 0 }
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

    /** Aparato físico al que pertenece una identidad: registro persistente > vínculo de sesión > ella misma */
    private fun groupOf(id: String): String =
        registry.keyOf(id) ?: sessionTwins[id]?.let { registry.keyOf(it) ?: it } ?: id

    private fun isKnown(id: String) = registry.keyOf(id) != null || sessionTwins.containsKey(id)

    /** Cada segundo: detecta gemelos ANT+/BLE, reparte huecos y elige la fuente de cada hueco */
    @Synchronized
    private fun tick() {
        val now = System.currentTimeMillis()
        val devs = _devices.value
        updateHistory(devs, now)
        detectTwins(devs, now)
        detectHandoffs(devs, now)

        // Grupos vivos (con algún dispositivo conectado)
        val groups = devs.values.filter { it.connected }.map { groupOf(it.id) }.toSet()

        // Un hueco cuyo aparato se ha vinculado a otro pasa a la clave del aparato físico
        for (i in 0 until SLOTS) {
            val g = slotGroup[i] ?: continue
            val merged = groupOf(g)
            if (merged != g) slotGroup[i] = if (slotGroup.contains(merged)) null else merged
        }
        // Si un aparato quedó en dos huecos tras agruparse, conservar el más bajo (HR1)
        for (i in 0 until SLOTS) for (j in i + 1 until SLOTS)
            if (slotGroup[i] != null && slotGroup[i] == slotGroup[j]) slotGroup[j] = null

        // Asignar grupos nuevos a huecos libres (ANT+ primero). Un hueco cuyo grupo ya no existe se
        // reserva para su aparato y solo se reutiliza si no queda otro libre.
        val pending = groups.filter { it !in slotGroup }.sortedBy { if (it.startsWith("ANT:")) 0 else 1 }
        quarantineSince.keys.retainAll(pending.toSet())
        for (g in pending) {
            if (inQuarantine(g, devs, groups, now)) continue
            quarantineSince.remove(g)
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
            val changed = newSource != slotSource[i]
            slotSource[i] = newSource
            if (best != null && best.bpm > 0) slotLastBpm[i] = best.bpm
            val out = slotFlows[i]
            if (best == null) {
                if (changed) out.tryEmit(if (g == null) HeartRateReading.Scanning else HeartRateReading.Disconnected)
            } else {
                // Reemitir Connected en cada tick: como el flow tiene replay=1 y entre ticks
                // llegan muchos Value, un suscriptor tardío solo replayaría el último Value y
                // nunca vería el protocolo. Reemitirlo cada segundo lo mantiene visible (badge).
                out.tryEmit(HeartRateReading.Connected(best.name, best.protocol))
                if (changed && best.bpm > 0) out.tryEmit(HeartRateReading.Value(best.bpm))
            }
            if (changed) Timber.d("💓 Hueco HR${i + 1}: ${best?.let { "${it.name} (${it.protocol})" } ?: "—"}")
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

    /**
     * Un desconocido no recibe hueco propio durante QUARANTINE_MS si podría ser la otra cara de un
     * aparato que ya tiene hueco: otro protocolo sin vincular con hueco, o un hueco caído que podría relevar.
     */
    private fun inQuarantine(g: String, devs: Map<String, HrDevice>, groups: Set<String>, now: Long): Boolean {
        val d = devs[g] ?: return false
        if (isKnown(d.id)) return false
        val possibleTwin = devs.values.any { o ->
            o.protocol != d.protocol && o.connected && registry.partnerOf(o.id) == null && groupOf(o.id) in slotGroup
        }
        val orphanSlot = (0 until SLOTS).any { i -> slotGroup[i]?.let { it !in groups && canHandoff(it, d, devs) } == true }
        if (!possibleTwin && !orphanSlot) return false
        val since = quarantineSince.getOrPut(g) {
            Timber.d("💓 ${d.name} (${d.protocol}): en espera, ¿es el mismo aparato que uno ya asignado?")
            now
        }
        return now - since < QUARANTINE_MS
    }

    /** Solo puede relevar si el aparato del hueco no tiene ya ese protocolo (si lo tuviera, sería otro aparato) */
    private fun canHandoff(slotGroupId: String, d: HrDevice, devs: Map<String, HrDevice>): Boolean {
        val protocols = devs.values.filter { groupOf(it.id) == slotGroupId }.map { it.protocol }.toSet()
        return protocols.isNotEmpty() && d.protocol !in protocols && registry.partnerOf(d.id) == null
    }

    /**
     * Relevo sin solapamiento: el aparato del hueco se ha caído por un protocolo (p. ej. se quitó el
     * pincho ANT+) y aparece un desconocido por el otro con un bpm compatible → hereda el hueco.
     * Solo se vincula en la sesión; se guardará cuando ambos coincidan y se confirme.
     */
    private fun detectHandoffs(devs: Map<String, HrDevice>, now: Long) {
        val groups = devs.values.filter { it.connected }.map { groupOf(it.id) }.toSet()
        val orphans = (0 until SLOTS).filter { i -> slotGroup[i].let { it != null && it !in groups } }
        if (orphans.isEmpty()) return
        for (d in devs.values) {
            if (!d.isFresh(now) || isKnown(d.id) || d.id in slotGroup) continue
            val candidates = orphans.filter { i ->
                canHandoff(slotGroup[i]!!, d, devs) && slotLastBpm[i] > 0 &&
                    kotlin.math.abs(d.bpm - slotLastBpm[i]) <= HANDOFF_MAX_DIFF
            }
            if (candidates.size == 1) {
                val i = candidates.single()
                sessionTwins[d.id] = slotGroup[i]!!
                Timber.d("💓 ${d.name} (${d.protocol}) releva al aparato de HR${i + 1}")
            }
        }
    }

    private fun detectTwins(devs: Map<String, HrDevice>, now: Long) {
        val ants = devs.values.filter { it.protocol == "ANT+" && it.isFresh(now) }
        val bles = devs.values.filter { it.protocol == "BLE" && it.isFresh(now) }
        for (b in bles) for (a in ants) {
            if (registry.partnerOf(a.id) != null || registry.partnerOf(b.id) != null) continue
            // Garmin y otros ponen el número ANT+ en el nombre BLE ("HRM-Pro:12345")
            val antNumber = a.id.removePrefix("ANT:")
            val nameMatch = antNumber.length >= 4 && b.name.filter { it.isDigit() }.endsWith(antNumber)
            // Comparar series con desfase: ANT+ y BLE del mismo aparato llegan con unos segundos de retraso
            val ha = history[a.id].orEmpty()
            val hb = history[b.id].orEmpty()
            val enough = ha.size >= TWIN_MIN_SAMPLES && hb.size >= TWIN_MIN_SAMPLES
            val diff = if (enough) minLaggedAvgDiff(ha, hb, TWIN_MAX_LAG) else Double.MAX_VALUE
            // Con la HR plana dos personas distintas podrían parecer iguales: exigir variación para guardarlo
            val varies = enough && (ha.max() - ha.min()) >= TWIN_MIN_RANGE && (hb.max() - hb.min()) >= TWIN_MIN_RANGE
            if (nameMatch || (diff <= TWIN_MAX_AVG_DIFF && varies)) {
                registry.link(a.id, b.id, a.name)
                sessionTwins.remove(a.id); sessionTwins.remove(b.id)
                Timber.d("💓 ${b.name} (BLE) es el mismo aparato que ${a.name} (ANT+) — guardado")
            } else if (diff <= TWIN_FLAT_MAX_DIFF && groupOf(a.id) != groupOf(b.id)) {
                // Sin variación suficiente: agrupar solo en esta sesión, sin guardar
                val (joiner, target) = if (groupOf(a.id) in slotGroup) b to a else a to b
                sessionTwins[joiner.id] = groupOf(target.id)
                Timber.d("💓 ${b.name} (BLE) y ${a.name} (ANT+) agrupados en la sesión (HR plana)")
            }
        }
    }

    // ------------------------------------------------------------------------
    // ANT+
    // ------------------------------------------------------------------------

    // La búsqueda multi-dispositivo ocupa la radio del pincho: mientras corre, requestAccess falla con
    // SEARCH_TIMEOUT / CHANNEL_NOT_AVAILABLE (y la cadencia ANT+ con ALL_CHANNELS_IN_USE). Por eso:
    // se busca por ventanas cortas, se cierra la búsqueda ANTES de conectar y no se busca mientras
    // se conecta. Un aparato perdido se recupera volviendo a buscar.
    private var antSearch: MultiDeviceSearch? = null
    private val antHandles = ConcurrentHashMap<Int, PccReleaseHandle<AntPlusHeartRatePcc>>()
    private val antConnecting = ConcurrentHashMap.newKeySet<Int>()
    private var antRetryJob: Job? = null
    private var antWindowJob: Job? = null
    private val ANT_SEARCH_WINDOW_MS = 15_000L   // duración de cada ventana de búsqueda
    private val ANT_SEARCH_PAUSE_MS = 15_000L    // pausa entre ventanas (deja canales libres a la cadencia)

    /** Programa la próxima ventana de búsqueda (sustituye a cualquiera pendiente) */
    private fun scheduleAntSearch(delayMs: Long) {
        antRetryJob?.cancel()
        antRetryJob = scope.launch { delay(delayMs); startAnt() }
    }

    private fun startAnt() {
        scope.launch(Dispatchers.Main) {
            if (!running || antSearch != null || antConnecting.isNotEmpty()) return@launch
            if (antHandles.size >= SLOTS) { scheduleAntSearch(ANT_SEARCH_PAUSE_MS); return@launch }
            try {
                antSearch = MultiDeviceSearch(context, EnumSet.of(DeviceType.HEARTRATE), object : MultiDeviceSearch.SearchCallbacks {
                    override fun onSearchStarted(rssi: MultiDeviceSearch.RssiSupport?) {
                        Timber.d("💓 ANT+: búsqueda iniciada")
                    }
                    override fun onDeviceFound(result: com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult) {
                        val number = result.antDeviceNumber
                        if (antHandles.containsKey(number) || antConnecting.contains(number)) return
                        Timber.d("💓 ANT+: encontrado ${result.deviceDisplayName} ($number)")
                        closeAntSearch()   // liberar la radio antes de pedir acceso
                        connectAnt(number, result.deviceDisplayName)
                    }
                    override fun onSearchStopped(reason: RequestAccessResult?) {
                        Timber.d("💓 ANT+: búsqueda parada ($reason)")
                        antSearch = null
                        antWindowJob?.cancel()
                        if (running && reason != RequestAccessResult.DEPENDENCY_NOT_INSTALLED && antConnecting.isEmpty())
                            scheduleAntSearch(ANT_SEARCH_PAUSE_MS)
                    }
                })
                antWindowJob?.cancel()
                antWindowJob = scope.launch {
                    delay(ANT_SEARCH_WINDOW_MS)
                    if (antSearch != null) { closeAntSearch(); scheduleAntSearch(ANT_SEARCH_PAUSE_MS) }
                }
            } catch (e: Exception) {
                antSearch = null
                Timber.e(e, "💓 ANT+: no disponible")
                if (running) scheduleAntSearch(ANT_SEARCH_PAUSE_MS)
            }
        }
    }

    private fun closeAntSearch() {
        antWindowJob?.cancel()
        val s = antSearch
        antSearch = null
        try { s?.close() } catch (_: Exception) {}
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
                            Timber.d("💓 ANT+ $name: conectado")
                            pcc.subscribeHeartRateDataEvent { _, _, hr, _, _, state ->
                                if (state == AntPlusHeartRatePcc.DataState.LIVE_DATA) onBpm(id, hr)
                            }
                        } else {
                            Timber.w("💓 ANT+ $number: acceso $code")
                            antHandles.remove(number)?.close()
                        }
                        // Seguir buscando (otro pulsómetro, o este mismo si ha fallado)
                        if (running) scheduleAntSearch(3_000)
                    },
                    { state ->
                        if (state == DeviceState.DEAD) {
                            Timber.w("💓 ANT+ $number: perdido, se volverá a buscar")
                            upsert(id) { (it ?: HrDevice(id, displayName ?: id, "ANT+")).copy(connected = false) }
                            antHandles.remove(number)?.close()
                            if (running) scheduleAntSearch(3_000)
                        }
                    })
                if (handle != null) antHandles[number] = handle else { antConnecting.remove(number); scheduleAntSearch(3_000) }
            } catch (e: Exception) {
                antConnecting.remove(number)
                Timber.e(e, "💓 ANT+ $number: error al conectar")
                if (running) scheduleAntSearch(ANT_SEARCH_PAUSE_MS)
            }
        }
    }

    private fun stopAnt() {
        antRetryJob?.cancel()
        closeAntSearch()
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
