package com.tuapp.tabatatrainer.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tuapp.tabatatrainer.MainActivity
import com.tuapp.tabatatrainer.data.local.*
import com.tuapp.tabatatrainer.rawlog.RawSessionCore
import com.tuapp.tabatatrainer.rawlog.RawSessionLogger
import com.tuapp.tabatatrainer.sensor.*
import com.tuapp.tabatatrainer.sensor.StepCounter
import com.tuapp.tabatatrainer.util.Sound
import com.tuapp.tabatatrainer.util.SoundPlayer
import com.tuapp.tabatatrainer.util.laps.AutoLapCrossing
import com.tuapp.tabatatrainer.util.laps.AutoLapTracker
import com.tuapp.tabatatrainer.util.laps.BlockCounters
import com.tuapp.tabatatrainer.util.laps.BlockMetrics
import com.tuapp.tabatatrainer.util.laps.CadenceDisplayPolicy
import com.tuapp.tabatatrainer.util.laps.CadenceSource
import com.tuapp.tabatatrainer.util.laps.CadenceSlotMode
import com.tuapp.tabatatrainer.util.laps.DualSlotSamples
import com.tuapp.tabatatrainer.util.laps.KmAlert
import com.tuapp.tabatatrainer.util.laps.KmAlertGate
import com.tuapp.tabatatrainer.util.laps.LapBlockMetrics
import com.tuapp.tabatatrainer.util.laps.LapBook
import com.tuapp.tabatatrainer.util.laps.LapRecord
import com.tuapp.tabatatrainer.util.laps.LapType
import com.tuapp.tabatatrainer.util.laps.PaceCalculator
import com.tuapp.tabatatrainer.util.map.RouteSimplifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

// ============================================================================
// MODELOS DE DATOS PARA FREE RIDE
// ============================================================================

data class FreeRideStats(
    val sessionId: String? = null,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val totalTimeSeconds: Int = 0,
    val totalDistanceMeters: Float = 0f,
    val currentSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val currentAltitudeM: Float = 0f,
    val elevationGainM: Float = 0f,
    // Pulso y cadencia por hueco (spec 007, T-05): heartRate/cadence y sus medias son solo HR1/C1;
    // HR2/C2 van en sus propios campos (antes HR2 ocupaba heartRate si faltaba HR1 y las medias se mezclaban)
    val heartRate: Int = 0,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val heartRate2: Int = 0,
    val avgHeartRate2: Int = 0,
    val maxHeartRate2: Int = 0,
    val cadence: Float = 0f,
    val avgCadence: Float = 0f,
    val cadence2: Float = 0f,
    val avgCadence2: Float = 0f,
    val gpsAccuracy: Float? = null,
    val isGpsConnected: Boolean = false,
    val isGpsScanning: Boolean = false,
    val isHrConnected: Boolean = false,
    val isHrScanning: Boolean = false,
    val isHr2Connected: Boolean = false,
    val isCadenceConnected: Boolean = false,
    val isCadenceScanning: Boolean = false,
    val isCadence2Connected: Boolean = false,
    // true = el sensor de cadencia (C1 / C2) está emparejado Y emitiendo (dato en los últimos 5 s)
    val cadenceActive: Boolean = false,
    val cadence2Active: Boolean = false,
    // Ritmo en vivo (s/km) sobre ventana de 10 s; null = sin movimiento fiable
    val paceSecPerKm: Int? = null,
    // Total de vueltas (AUTO + MANUAL)
    val lapsCount: Int = 0,
    // Solo vueltas manuales: la tarjeta "Vuelta" de la página 3 cuenta solo estas
    val manualLapsCount: Int = 0,
    val currentLapDistanceMeters: Float = 0f,
    val currentLapTimeSeconds: Int = 0,
    // Pasos y desnivel+ de la vuelta manual en curso (null = sin dato); los pinta la ventana 3
    val currentLapSteps: Int? = null,
    val currentLapElevationGainM: Float? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    // Podómetro
    val steps: Int = 0,
    val stepCadenceSpm: Int = 0,
    val avgStrideM: Float = 0f
) {
    /** Algún cadenciómetro (C1 o C2) emite: la tarjeta "Cadencia / Ritmo" enseña cadencia en vez de ritmo */
    val anyCadenceActive: Boolean get() = cadenceActive || cadence2Active

    /** Cadencia que representa la vuelta en curso cuando solo cabe un valor: C1 si emite, si no C2 */
    val primaryCadence: Float get() = if (cadenceActive || !cadence2Active) cadence else cadence2
}

data class LapData(
    val number: Int,                      // orden global de creación (AUTO y MANUAL mezcladas)
    val type: LapType = LapType.MANUAL,
    val typeNumber: Int = 0,              // número dentro de su serie (AUTO 1,2,3… / MANUAL 1,2,3…)
    val startTimeSeconds: Int,
    val endTimeSeconds: Int,
    val distanceMeters: Float,
    val startDistanceMeters: Float = 0f,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float = 0f,
    val avgHeartRate: Int,
    val maxHeartRate: Int = 0,
    val avgCadence: Float = 0f,
    val steps: Int? = null,
    val elevationGain: Float? = null,
    val cadenceSource: CadenceSource = CadenceSource.NONE,
    val startTimestamp: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()   // fin de la vuelta (reloj de pared)
) {
    val isAutoLap: Boolean get() = type == LapType.AUTO
}

/** Fila Room de una vuelta ya cerrada; 0 en FC/cadencia significa "sin dato" y se guarda como null */
fun LapData.toEntity(sessionId: String) = LapEntity(
    sessionId = sessionId,
    lapNumber = number,
    startTimestamp = startTimestamp,
    endTimestamp = timestamp,
    startDistanceMeters = startDistanceMeters,
    endDistanceMeters = startDistanceMeters + distanceMeters,
    avgSpeedKmh = avgSpeedKmh,
    maxSpeedKmh = maxSpeedKmh,
    avgHeartRate = avgHeartRate.takeIf { it > 0 },
    maxHeartRate = maxHeartRate.takeIf { it > 0 },
    avgCadence = avgCadence.takeIf { it > 0f },
    elevationGain = elevationGain,
    type = type.name,
    typeNumber = typeNumber,
    steps = steps,
    cadenceSource = cadenceSource.name
)

// Punto simplificado para el mapa (más ligero que GpsPointEntity)
data class MapPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

// Spec 009, R3: vuelta manual guardada (para el aviso y el marcador del mapa en vivo).
// latitude/longitude = null si aún no había posición GPS al marcarla.
data class LapMark(
    val number: Int,
    val latitude: Double?,
    val longitude: Double?
)

// ============================================================================
// FREE RIDE SERVICE - CON SOPORTE PARA MAPA EN TIEMPO REAL
// ============================================================================

@AndroidEntryPoint
class FreeRideService : Service() {

    companion object {
        private const val TAG = "FreeRideService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "freeride_channel"
        private const val LAP_VIBRATION_MS = 80L  // spec 009, RF-3.1
        private const val HR_STALE_MS = 5_000L   // un pulso más viejo no se graba (spec 007, R2.1)
    }

    @Inject lateinit var sensorManager: SensorManager
    @Inject lateinit var gpsManager: GpsManager
    @Inject lateinit var sessionDao: SessionDao
    @Inject lateinit var gpsDao: GpsDao
    @Inject lateinit var sensorReadingDao: SensorReadingDao
    @Inject lateinit var stepCounter: StepCounter
    @Inject lateinit var soundPlayer: SoundPlayer
    @Inject lateinit var rawLogger: RawSessionLogger

    // Podómetro: último valor bruto visto y pasos de los últimos 10 s (para pasos/min)
    private var lastRawSteps: Long? = null
    private val recentStepDeltas = ArrayDeque<Int>()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val binder = LocalBinder()

    private val _stats = MutableStateFlow(FreeRideStats())
    val stats: StateFlow<FreeRideStats> = _stats.asStateFlow()

    private val _laps = MutableStateFlow<List<LapData>>(emptyList())
    val laps: StateFlow<List<LapData>> = _laps.asStateFlow()

    // ⚡ NUEVO: Puntos GPS para el mapa en tiempo real
    private val _mapPoints = MutableStateFlow<List<MapPoint>>(emptyList())
    val mapPoints: StateFlow<List<MapPoint>> = _mapPoints.asStateFlow()

    // Spec 009, R1: ruta entera aligerada para el mapa en vivo (sin tope de puntos).
    // Lo que se guarda (Room, registro bruto) no pasa por aquí. Acceso siempre con synchronized.
    private val routeSimplifier = RouteSimplifier<MapPoint>({ it.latitude }, { it.longitude })

    // Spec 009, R3: vueltas manuales guardadas en esta sesión (marcadores del mapa) y aviso puntual
    private val _lapMarks = MutableStateFlow<List<LapMark>>(emptyList())
    val lapMarks: StateFlow<List<LapMark>> = _lapMarks.asStateFlow()
    private val _lapEvents = MutableSharedFlow<LapMark>(extraBufferCapacity = 4)
    val lapEvents: SharedFlow<LapMark> = _lapEvents.asSharedFlow()

    // Aviso de km completado (R3): la UI muestra el banner; sonido y vibración los dispara el
    // servicio para que suenen también con la pantalla apagada
    private val _kmAlerts = MutableSharedFlow<KmAlert>(extraBufferCapacity = 4)
    val kmAlerts: SharedFlow<KmAlert> = _kmAlerts.asSharedFlow()
    private val autoLapTracker = AutoLapTracker()
    private val lapBook = LapBook()
    // Contadores acumulados (pasos, desnivel) al abrirse la ventana de cada serie; la métrica de la vuelta es la diferencia
    private var manualWindowStartCounters = BlockCounters()
    private var autoWindowStartCounters = BlockCounters()
    private var stepsSeen = false   // el podómetro ha emitido alguna vez en esta ruta (si no, "sin dato", no 0)
    private val kmAlertGate = KmAlertGate()

    private var currentSessionId: String? = null
    private var timerJob: Job? = null
    private var hrCollectorJob: Job? = null
    private var hr2CollectorJob: Job? = null
    private var cadenceCollectorJob: Job? = null
    private var cadence2CollectorJob: Job? = null
    private var cadenceWatchdogJob: Job? = null
    private var gpsCollectorJob: Job? = null

    // Instante (reloj de pared) del último dato de C1 [0] y C2 [1]; 0 = ninguno vigente
    private val lastCadenceSampleAtMs = java.util.concurrent.atomic.AtomicLongArray(2)
    private val paceCalculator = PaceCalculator()

    // Lecturas por hueco (spec 007, T-05): 0 = HR1/C1, 1 = HR2/C2. Ruta entera, vuelta manual y vuelta AUTO
    private val rideHr = DualSlotSamples()
    private val rideCadence = DualSlotSamples()
    private val manualLapHr = DualSlotSamples()
    private val manualLapCadence = DualSlotSamples()
    private val autoLapHr = DualSlotSamples()
    private val autoLapCadence = DualSlotSamples()
    // Último valor de cada pulsómetro, para guardar 1 lectura por segundo
    @Volatile private var currentHr1: Int? = null
    @Volatile private var currentHr2: Int? = null
    // Cuándo llegó ese valor: solo se graba si es reciente (spec 007, R2.1 ampliado tras el paseo del 2026-11-22)
    @Volatile private var currentHr1Ms = 0L
    @Volatile private var currentHr2Ms = 0L
    private val gpsPointsBuffer = mutableListOf<GpsPointEntity>()

    // Velocidad máxima de la vuelta manual y de la vuelta AUTO (cada 1 km)
    private var lastLapMaxSpeed = 0f
    private var autoLapMaxSpeed = 0f

    private var previousAltitude: Float? = null
    private var totalElevationGain = 0f

    inner class LocalBinder : Binder() {
        fun getService(): FreeRideService = this@FreeRideService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        rawLogger.serviceCreated(RawSessionLogger.SERVICE_FREE_RIDE)   // spec 008: solo guarda la hora
        createNotificationChannel()
        startSensorScanning()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Ruta Libre", "Buscando sensores...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= 34) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        rawLogger.serviceDestroyed(RawSessionLogger.SERVICE_FREE_RIDE)   // spec 008: cierra el registro si sigue abierto
        super.onDestroy()
        serviceScope.cancel()
        stopAllTracking()
    }

    // Spec 008 (registro en bruto): solo se anotan. Mismo comportamiento que el `Service` por defecto.
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        rawLogger.taskRemoved(RawSessionLogger.SERVICE_FREE_RIDE)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        rawLogger.trimMemory(RawSessionLogger.SERVICE_FREE_RIDE, level)
    }

    // ============================================================================
    // ESCANEO PREVIO DE SENSORES
    // ============================================================================

    private fun startSensorScanning() {
        Log.d(TAG, "🔍 Iniciando escaneo de sensores...")

        // GPS
        gpsCollectorJob?.cancel()
        gpsCollectorJob = serviceScope.launch {
            _stats.update { it.copy(isGpsScanning = true) }

            if (!gpsManager.hasLocationPermission()) {
                Log.w(TAG, "❌ GPS: Sin permisos")
                _stats.update { it.copy(isGpsScanning = false, isGpsConnected = false) }
                return@launch
            }

            if (!gpsManager.isGpsEnabled()) {
                Log.w(TAG, "❌ GPS: Desactivado")
                _stats.update { it.copy(isGpsScanning = false, isGpsConnected = false) }
                return@launch
            }

            Log.d(TAG, "📍 GPS: Iniciando flow...")
            gpsManager.gpsFlow().catch { e ->
                Log.e(TAG, "❌ Error en flow GPS: ${e.message}", e)
                _stats.update { it.copy(isGpsScanning = false, isGpsConnected = false) }
            }.collect { reading ->
                Log.d(TAG, "📍 GPS lectura: lat=${reading.latitude}, lon=${reading.longitude}, vel=${reading.speed}, isRunning=${_stats.value.isRunning}, isPaused=${_stats.value.isPaused}")
                
                _stats.update {
                    it.copy(
                        isGpsScanning = false,
                        isGpsConnected = true,
                        latitude = reading.latitude,
                        longitude = reading.longitude,
                        currentAltitudeM = reading.altitude?.toFloat() ?: 0f,
                        gpsAccuracy = reading.accuracy
                    )
                }

                // Si estamos en ruta, actualizar métricas y mapa
                if (_stats.value.isRunning && !_stats.value.isPaused) {
                    Log.d(TAG, "📍 Procesando lectura GPS en ruta...")
                    processGpsReading(reading)
                } else {
                    Log.d(TAG, "📍 GPS recibido pero no en ruta (isRunning=${_stats.value.isRunning}, isPaused=${_stats.value.isPaused})")
                }
            }
        }

        // HR1 y HR2 por separado (spec 007, T-05): cada hueco con su valor, su estado y sus medias
        hrCollectorJob?.cancel()
        hrCollectorJob = launchHeartRateCollector(slot = 0, startDelayMs = 0L)
        hr2CollectorJob?.cancel()
        hr2CollectorJob = launchHeartRateCollector(slot = 1, startDelayMs = 1000L) // no interferir con HR1

        // C1 y C2 por separado: escuchar C2 es lo que arranca su hueco
        cadenceCollectorJob?.cancel()
        cadenceCollectorJob = launchCadenceCollector(slot = 0, startDelayMs = 2000L)
        cadence2CollectorJob?.cancel()
        cadence2CollectorJob = launchCadenceCollector(slot = 1, startDelayMs = 2500L)

        // Vigilante: sin datos recientes del sensor la cadencia cede el hueco al ritmo (y viceversa), por hueco
        cadenceWatchdogJob?.cancel()
        cadenceWatchdogJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val s = _stats.value
                val active1 = isCadenceEmitting(0, s.isCadenceConnected, now)
                val active2 = isCadenceEmitting(1, s.isCadence2Connected, now)
                _stats.update { it.copy(cadenceActive = active1, cadence2Active = active2) }
            }
        }

        updateNotification("Ruta Libre", "Sensores listos - Pulsa iniciar")
    }

    /**
     * Escucha un hueco de pulso (0 = HR1, 1 = HR2). Cada hueco escribe solo sus campos; HR1 lleva además el
     * indicador de búsqueda (`isHrScanning`).
     */
    private fun launchHeartRateCollector(slot: Int, startDelayMs: Long): Job = serviceScope.launch {
        if (startDelayMs > 0) delay(startDelayMs)
        val tag = if (slot == 0) "❤️ HR1" else "❤️2 HR2"
        if (slot == 0) _stats.update { it.copy(isHrScanning = true) }
        Log.d(TAG, "$tag: Iniciando flow...")
        val flow = if (slot == 0) sensorManager.observeHeartRate() else sensorManager.observeHeartRate2()
        try {
            flow.catch { e ->
                Log.e(TAG, "❌ Error en flow $tag: ${e.message}", e)
                setHeartRateState(slot, connected = false, scanning = false)
            }.collect { reading ->
                when (reading) {
                    is HeartRateReading.Scanning -> {
                        Log.d(TAG, "$tag: Escaneando...")
                        setHeartRateState(slot, connected = false, scanning = true)
                    }
                    is HeartRateReading.Connecting -> {
                        Log.d(TAG, "$tag: Conectando a ${reading.deviceName}...")
                        setHeartRateState(slot, connected = false, scanning = true)
                    }
                    is HeartRateReading.Connected -> {
                        Log.d(TAG, "$tag: Conectado a ${reading.deviceName}")
                        setHeartRateState(slot, connected = true, scanning = false)
                    }
                    is HeartRateReading.Value -> {
                        Log.d(TAG, "$tag: Valor recibido: ${reading.bpm} bpm")
                        if (slot == 0) { currentHr1 = reading.bpm; currentHr1Ms = reading.timestamp }
                        else { currentHr2 = reading.bpm; currentHr2Ms = reading.timestamp }
                        _stats.update {
                            if (slot == 0) it.copy(isHrScanning = false, isHrConnected = true, heartRate = reading.bpm)
                            else it.copy(isHr2Connected = true, heartRate2 = reading.bpm)
                        }
                        recordHeartRate(slot, reading.bpm)
                    }
                    is HeartRateReading.Disconnected -> {
                        if (slot == 0) currentHr1 = null else currentHr2 = null
                        Log.d(TAG, "$tag: Desconectado")
                        setHeartRateState(slot, connected = false, scanning = false)
                    }
                    is HeartRateReading.Error -> {
                        if (slot == 0) currentHr1 = null else currentHr2 = null
                        Log.e(TAG, "$tag: Error - ${reading.message}")
                        setHeartRateState(slot, connected = false, scanning = false)
                    }
                    else -> Log.d(TAG, "$tag: Estado desconocido: ${reading::class.simpleName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción en collector $tag: ${e.message}", e)
            setHeartRateState(slot, connected = false, scanning = false)
        }
    }

    private fun setHeartRateState(slot: Int, connected: Boolean, scanning: Boolean) = _stats.update {
        if (slot == 0) it.copy(isHrConnected = connected, isHrScanning = scanning) else it.copy(isHr2Connected = connected)
    }

    /** Lectura de pulso en ruta: suma a la ruta, a la vuelta manual y a la AUTO de su hueco; medias sin mezclar */
    private fun recordHeartRate(slot: Int, bpm: Int) {
        val s = _stats.value
        if (!s.isRunning || s.isPaused) return
        val v = bpm.toFloat()
        rideHr.add(slot, v)
        manualLapHr.add(slot, v)
        autoLapHr.add(slot, v)
        val avg = rideHr.avg(slot)?.toInt() ?: 0
        val max = rideHr.max(slot)?.toInt() ?: 0
        _stats.update {
            if (slot == 0) it.copy(avgHeartRate = avg, maxHeartRate = max)
            else it.copy(avgHeartRate2 = avg, maxHeartRate2 = max)
        }
    }

    /** Escucha un hueco de cadencia (0 = C1, 1 = C2). 0 rpm con señal es un dato válido */
    private fun launchCadenceCollector(slot: Int, startDelayMs: Long): Job = serviceScope.launch {
        if (startDelayMs > 0) delay(startDelayMs)
        val tag = if (slot == 0) "🚴 C1" else "🚴2 C2"
        if (slot == 0) _stats.update { it.copy(isCadenceScanning = true) }
        Log.d(TAG, "$tag: Iniciando flow...")
        val flow = if (slot == 0) sensorManager.observeCadence() else sensorManager.observeCadence2()
        try {
            flow.catch { e ->
                Log.e(TAG, "❌ Error en flow $tag: ${e.message}", e)
                setCadenceState(slot, connected = false, scanning = false)
            }.collect { reading ->
                when (reading) {
                    is CadenceReading.Scanning -> {
                        Log.d(TAG, "$tag: Escaneando...")
                        setCadenceState(slot, connected = false, scanning = true)
                    }
                    is CadenceReading.Connecting -> {
                        Log.d(TAG, "$tag: Conectando a ${reading.deviceName}...")
                        setCadenceState(slot, connected = false, scanning = true)
                    }
                    is CadenceReading.Connected -> {
                        Log.d(TAG, "$tag: Conectado a ${reading.deviceName}")
                        setCadenceState(slot, connected = true, scanning = false)
                    }
                    is CadenceReading.Value -> {
                        Log.d(TAG, "$tag: Valor recibido: ${reading.rpm} rpm")
                        val rpm = reading.rpm.toFloat()
                        lastCadenceSampleAtMs.set(slot, System.currentTimeMillis())
                        _stats.update {
                            if (slot == 0) {
                                it.copy(isCadenceScanning = false, isCadenceConnected = true, cadenceActive = true, cadence = rpm)
                            } else {
                                it.copy(isCadence2Connected = true, cadence2Active = true, cadence2 = rpm)
                            }
                        }
                        recordCadence(slot, rpm)
                    }
                    is CadenceReading.Disconnected -> {
                        Log.d(TAG, "$tag: Desconectado")
                        lastCadenceSampleAtMs.set(slot, 0L)
                        setCadenceState(slot, connected = false, scanning = false)
                    }
                    is CadenceReading.Error -> {
                        Log.e(TAG, "$tag: Error - ${reading.message}")
                        lastCadenceSampleAtMs.set(slot, 0L)
                        setCadenceState(slot, connected = false, scanning = false)
                    }
                    else -> Log.d(TAG, "$tag: Estado desconocido: ${reading::class.simpleName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción en collector $tag: ${e.message}", e)
            setCadenceState(slot, connected = false, scanning = false)
        }
    }

    /** Sin conexión el hueco deja de contar como "emitiendo" en el acto (no espera al vigilante) */
    private fun setCadenceState(slot: Int, connected: Boolean, scanning: Boolean) = _stats.update {
        if (slot == 0) {
            it.copy(isCadenceConnected = connected, isCadenceScanning = scanning,
                cadenceActive = it.cadenceActive && connected)
        } else {
            it.copy(isCadence2Connected = connected, cadence2Active = it.cadence2Active && connected)
        }
    }

    private fun isCadenceEmitting(slot: Int, paired: Boolean, nowMs: Long): Boolean =
        CadenceDisplayPolicy.mode(
            paired = paired,
            lastSampleAtMs = lastCadenceSampleAtMs.get(slot).takeIf { it > 0L },
            nowMs = nowMs
        ) == CadenceSlotMode.CADENCE

    /** Lectura de cadencia en ruta: suma a la ruta, a la vuelta manual y a la AUTO de su hueco */
    private fun recordCadence(slot: Int, rpm: Float) {
        val s = _stats.value
        if (!s.isRunning || s.isPaused) return
        rideCadence.add(slot, rpm)
        manualLapCadence.add(slot, rpm)
        autoLapCadence.add(slot, rpm)
        val avg = rideCadence.avg(slot) ?: 0f
        _stats.update { if (slot == 0) it.copy(avgCadence = avg) else it.copy(avgCadence2 = avg) }
    }

    fun reconnectSensors() {
        Log.d(TAG, "🔄 Reconectando sensores...")
        // Solo sensores: el cronómetro de la ruta debe seguir corriendo
        currentHr1 = null
        currentHr2 = null
        stopSensorCollectors()

        serviceScope.launch {
            // Reinicio real de las radios ANT+ y BLE (spec 007, R8.2): sin esto, volver a escuchar los huecos no
            // reconectaba una banda perdida. Conserva huecos y señales ignoradas
            sensorManager.resetFlows()
            delay(500)
            startSensorScanning()
        }
    }

    // ============================================================================
    // CONTROL DE LA RUTA
    // ============================================================================

    fun startRide() {
        if (_stats.value.isRunning) return

        Log.d(TAG, "🚴 Iniciando ruta...")
        Log.d(TAG, "📍 GPS estado: conectado=${_stats.value.isGpsConnected}, scanning=${_stats.value.isGpsScanning}")

        currentSessionId = UUID.randomUUID().toString()
        rideHr.clear()
        rideCadence.clear()
        currentHr1 = null
        currentHr2 = null
        paceCalculator.reset()
        gpsPointsBuffer.clear()
        _laps.update { emptyList() }
        clearLiveMap()
        resetLapCounters()
        resetAutoLapCounters()
        lapBook.reset(System.currentTimeMillis())
        stepsSeen = false
        manualWindowStartCounters = BlockCounters()
        autoWindowStartCounters = BlockCounters()
        autoLapTracker.reset()
        kmAlertGate.reset()
        totalElevationGain = 0f
        previousAltitude = null

        gpsManager.resetStats()
        Log.d(TAG, "📍 GPS stats reseteadas")

        stepCounter.start()
        lastRawSteps = stepCounter.rawSteps.value
        recentStepDeltas.clear()

        serviceScope.launch {
            val session = WorkoutSessionEntity(
                id = currentSessionId!!,
                warmupSeconds = 0,
                workSeconds = 0,
                restSeconds = 0,
                totalRounds = 0,
                gpsEnabled = true,
                activityType = "FREE_RIDE"
            )
            sessionDao.insertSession(session)
        }

        _stats.update {
            it.copy(
                sessionId = currentSessionId,
                isRunning = true,
                isPaused = false,
                totalTimeSeconds = 0,
                totalDistanceMeters = 0f,
                avgSpeedKmh = 0f,
                maxSpeedKmh = 0f,
                avgHeartRate = 0,
                maxHeartRate = 0,
                avgHeartRate2 = 0,
                maxHeartRate2 = 0,
                avgCadence = 0f,
                avgCadence2 = 0f,
                elevationGainM = 0f,
                steps = 0,
                stepCadenceSpm = 0,
                avgStrideM = 0f,
                paceSecPerKm = null,
                lapsCount = 0,
                manualLapsCount = 0,
                currentLapDistanceMeters = 0f,
                currentLapTimeSeconds = 0,
                currentLapSteps = null,
                currentLapElevationGainM = null
            )
        }

        startTimer()
        updateNotification("En Ruta", "Grabando actividad...")

        Log.d(TAG, "✅ Ruta iniciada: $currentSessionId")
        rawLogger.start(RawSessionLogger.SERVICE_FREE_RIDE, currentSessionId!!, RawSessionLogger.ACTIVITY_FREE_RIDE)
    }

    fun pauseRide() {
        if (!_stats.value.isRunning || _stats.value.isPaused) return

        timerJob?.cancel()
        gpsManager.pause()
        paceCalculator.reset()
        _stats.update { it.copy(isPaused = true, paceSecPerKm = null) }
        updateNotification("Pausado", "Toca para continuar")

        Log.d(TAG, "⏸️ Ruta pausada")
        rawLogger.pause(RawSessionLogger.SERVICE_FREE_RIDE)
    }

    fun resumeRide() {
        if (!_stats.value.isPaused) return

        _stats.update { it.copy(isPaused = false) }
        gpsManager.resume()
        lastRawSteps = stepCounter.rawSteps.value  // los pasos dados en pausa no cuentan
        recentStepDeltas.clear()
        startTimer()
        updateNotification("En Ruta", "Grabando actividad...")

        Log.d(TAG, "▶️ Ruta reanudada")
        rawLogger.resume(RawSessionLogger.SERVICE_FREE_RIDE)
    }

    /** Finaliza la ruta; [onSaved] se llama cuando las estadísticas ya están en la DB */
    fun stopRide(onSaved: (() -> Unit)? = null) {
        Log.d(TAG, "🛑 Finalizando ruta...")

        timerJob?.cancel()
        flushGpsBuffer()

        // Foto de las vueltas AHORA: el reset a idle no debe poder vaciarlas antes de guardarlas
        val lapsToSave = _laps.value
        serviceScope.launch {
            saveFinalStats(lapsToSave)
            onSaved?.let { withContext(Dispatchers.Main) { it() } }
        }

        stepCounter.stop()
        paceCalculator.reset()
        _stats.update { it.copy(isRunning = false, isPaused = false, paceSecPerKm = null) }
        updateNotification("Ruta Finalizada", "¡Buen trabajo!")
        rawLogger.stop(RawSessionLogger.SERVICE_FREE_RIDE, RawSessionCore.STOP_USER)
    }

    /**
     * Añade una vuelta manual (botón). Independiente de las AUTO: cada serie lleva su numeración y su ventana.
     * @return true si la vuelta se creó; false si se ignoró (parado, en pausa o demasiado corta, ver LapGuard)
     */
    fun addLap(): Boolean {
        val currentStats = _stats.value
        if (!currentStats.isRunning || currentStats.isPaused) {
            Log.d(TAG, "addLap ignorada: isRunning=${currentStats.isRunning} isPaused=${currentStats.isPaused}")
            return false
        }

        val now = System.currentTimeMillis()
        val record = lapBook.addManual(currentStats.totalTimeSeconds, currentStats.totalDistanceMeters, now)
        if (record == null) {
            Log.d(TAG, "addLap ignorada por LapGuard (ventana de ${currentStats.totalTimeSeconds - lapBook.currentManualStartSeconds}s)")
            return false
        }

        val endCounters = currentCounters()
        val lap = record.toLapData(
            avgSpeedKmh = speedOf(record.distanceMeters, record.durationSeconds),
            maxSpeedKmh = lastLapMaxSpeed,
            hr = manualLapHr,
            cadence = manualLapCadence,
            metrics = LapBlockMetrics.of(manualWindowStartCounters, endCounters, !manualLapCadence.isEmpty())
        )
        _laps.update { it + lap }
        resetLapCounters()   // solo la serie manual; NO afecta a las AUTO
        manualWindowStartCounters = endCounters

        _stats.update {
            it.copy(
                lapsCount = lapBook.laps.size,
                manualLapsCount = lapBook.manualCount,
                currentLapTimeSeconds = 0   // empieza la siguiente vuelta manual
            )
        }
        updateCurrentLapBlock()
        confirmManualLap(record.typeNumber, currentStats.latitude, currentStats.longitude)

        Log.d(TAG, "🏁 LAP MANUAL ${record.typeNumber}: ${String.format("%.2f", record.distanceMeters / 1000)}km en ${formatDuration(record.durationSeconds)}")
        return true
    }

    /**
     * Añade un auto-lap de 1 km exacto. Basado en la distancia total acumulada, independiente de las manuales.
     */
    private fun addAutoLap(crossing: AutoLapCrossing, resetCounters: Boolean, inGpsJump: Boolean) {
        val record = lapBook.addAuto(crossing, System.currentTimeMillis())
        val endCounters = currentCounters()
        // Si un hueco de GPS cruza varios km de golpe, los pasos/desnivel no se pueden repartir entre ellos
        // con fiabilidad: esas vueltas quedan sin dato (`--`) en vez de duplicar o inventar la cifra
        val bikeUsed = !autoLapCadence.isEmpty()
        val metrics = if (inGpsJump) {
            BlockMetrics(steps = null, elevationGain = null,
                cadenceSource = if (bikeUsed) CadenceSource.BIKE_SENSOR else CadenceSource.NONE)
        } else {
            LapBlockMetrics.of(autoWindowStartCounters, endCounters, bikeUsed)
        }
        val lap = record.toLapData(
            avgSpeedKmh = speedOf(record.distanceMeters, record.durationSeconds),
            maxSpeedKmh = autoLapMaxSpeed,
            hr = autoLapHr,
            cadence = autoLapCadence,
            metrics = metrics
        )
        _laps.update { it + lap }
        if (resetCounters) autoWindowStartCounters = endCounters

        // En un salto de varios km, los contadores (FC, vel. máx.) se reinician solo tras el último
        if (resetCounters) resetAutoLapCounters()

        // Una AUTO no toca el tiempo de la vuelta manual en curso ni el contador de manuales
        _stats.update { it.copy(lapsCount = lapBook.laps.size) }

        Log.d(TAG, "⚡ AUTO-LAP ${record.typeNumber}: ${String.format("%.2f", record.distanceMeters / 1000)}km en ${formatDuration(record.durationSeconds)}")
    }

    /** Pasos y desnivel acumulados ahora mismo; null = aún sin dato (sin podómetro / sin altitud) */
    private fun currentCounters() = BlockCounters(
        steps = if (stepsSeen) _stats.value.steps else null,
        elevationGain = if (previousAltitude != null) totalElevationGain else null
    )

    /** Pasos y desnivel+ de la vuelta manual en curso = contadores de ahora − contadores al abrirla */
    private fun updateCurrentLapBlock() {
        val block = LapBlockMetrics.of(manualWindowStartCounters, currentCounters(), bikeSensorUsed = false)
        _stats.update { it.copy(currentLapSteps = block.steps, currentLapElevationGainM = block.elevationGain) }
    }

    private fun speedOf(distanceMeters: Float, seconds: Int): Float =
        if (seconds > 0) (distanceMeters / 1000f) / (seconds / 3600f) else 0f

    private fun LapRecord.toLapData(
        avgSpeedKmh: Float,
        maxSpeedKmh: Float,
        hr: DualSlotSamples,
        cadence: DualSlotSamples,
        metrics: BlockMetrics
    ) = LapData(
        number = order,
        type = type,
        typeNumber = typeNumber,
        startTimeSeconds = startSeconds,
        endTimeSeconds = endSeconds,
        distanceMeters = distanceMeters,
        startDistanceMeters = startMeters,
        avgSpeedKmh = avgSpeedKmh,
        maxSpeedKmh = maxSpeedKmh,
        // Una vuelta guarda un solo pulso y una sola cadencia (Room): HR1/C1 si hay lecturas, si no HR2/C2
        avgHeartRate = hr.primaryAvg()?.toInt() ?: 0,
        maxHeartRate = hr.primaryMax()?.toInt() ?: 0,
        avgCadence = cadence.primaryAvg() ?: 0f,
        // avgCadence = rpm del sensor de pedal; la cadencia de carrera (ppm) se deriva de pasos y duración
        steps = metrics.steps,
        elevationGain = metrics.elevationGain,
        cadenceSource = metrics.cadenceSource,
        startTimestamp = startWallMs,
        timestamp = endWallMs
    )

    /** Aviso de km completado: evento para el banner + sonido + vibración (una vez por km) */
    private fun announceKm(km: Int) {
        val lastKmLap = _laps.value.lastOrNull { it.isAutoLap }
        val pace = lastKmLap?.let { PaceCalculator.secPerKm(it.endTimeSeconds - it.startTimeSeconds, it.distanceMeters) }
        _kmAlerts.tryEmit(KmAlert(km, pace))
        soundPlayer.playSound(Sound.BEEP)
        vibrateKmAlert()
    }

    private fun vibrateKmAlert() {
        vibrate { VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1) }
    }

    /**
     * Spec 009, R3: la vuelta manual se ha guardado → marcador en el mapa, aviso en pantalla y
     * vibración corta (RF-3.1). Solo se llama cuando la vuelta NO se ignora (RF-3.4).
     */
    private fun confirmManualLap(number: Int, latitude: Double, longitude: Double) {
        val hasFix = latitude != 0.0 || longitude != 0.0
        val mark = LapMark(number, latitude.takeIf { hasFix }, longitude.takeIf { hasFix })
        _lapMarks.update { it + mark }
        _lapEvents.tryEmit(mark)
        vibrate { VibrationEffect.createOneShot(LAP_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE) }
    }

    private fun vibrate(effect: () -> VibrationEffect) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(effect())
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo vibrar: ${e.message}")
        }
    }

    private fun resetLapCounters() {
        manualLapHr.clear()
        manualLapCadence.clear()
        lastLapMaxSpeed = 0f
    }

    private fun resetAutoLapCounters() {
        autoLapHr.clear()
        autoLapCadence.clear()
        autoLapMaxSpeed = 0f
    }

    fun resetToIdle() {
        _stats.update {
            FreeRideStats(
                isGpsConnected = it.isGpsConnected,
                isHrConnected = it.isHrConnected,
                isHr2Connected = it.isHr2Connected,
                isCadenceConnected = it.isCadenceConnected,
                isCadence2Connected = it.isCadence2Connected,
                cadenceActive = it.cadenceActive,
                cadence2Active = it.cadence2Active,
                heartRate = it.heartRate,
                heartRate2 = it.heartRate2,
                cadence = it.cadence,
                cadence2 = it.cadence2
            )
        }
        _laps.update { emptyList() }
        clearLiveMap()
        currentSessionId = null
        updateNotification("Ruta Libre", "Preparado para iniciar")
    }

    // ============================================================================
    // TIMER
    // ============================================================================

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && _stats.value.isRunning && !_stats.value.isPaused) {
                delay(1000)
                // Capturar el inicio de la vuelta manual antes de actualizar para evitar race conditions
                val lapStartTime = lapBook.currentManualStartSeconds
                _stats.update {
                    val newTotalTime = it.totalTimeSeconds + 1
                    it.copy(
                        totalTimeSeconds = newTotalTime,
                        currentLapTimeSeconds = newTotalTime - lapStartTime
                    )
                }

                updateSteps()
                updateCurrentLapBlock()
                updatePace()
                saveSensorReading()

                if (_stats.value.totalTimeSeconds % 30 == 0) {
                    val time = formatDuration(_stats.value.totalTimeSeconds)
                    val dist = String.format("%.2f km", _stats.value.totalDistanceMeters / 1000f)
                    updateNotification("En Ruta", "$time • $dist")
                }
            }
        }
    }

    /** Suma los pasos del último segundo y recalcula pasos/min y zancada media */
    private fun updateSteps() {
        val raw = stepCounter.rawSteps.value ?: return
        stepsSeen = true
        val delta = lastRawSteps?.let { (raw - it).toInt().coerceAtLeast(0) } ?: 0
        lastRawSteps = raw

        recentStepDeltas.addLast(delta)
        while (recentStepDeltas.size > 10) recentStepDeltas.removeFirst()
        val spm = recentStepDeltas.sum() * 60 / recentStepDeltas.size

        _stats.update {
            val steps = it.steps + delta
            it.copy(
                steps = steps,
                stepCadenceSpm = spm,
                avgStrideM = strideOf(it.totalDistanceMeters, steps)
            )
        }
    }

    /** Ritmo en vivo (s/km) sobre los últimos 10 s en movimiento */
    private fun updatePace() {
        val s = _stats.value
        paceCalculator.add(s.totalTimeSeconds, s.totalDistanceMeters)
        val pace = paceCalculator.livePaceSecPerKm()
        _stats.update { it.copy(paceSecPerKm = pace) }
    }

    /** Zancada = distancia GPS / pasos; con pocos pasos el dato no es fiable */
    private fun strideOf(distanceM: Float, steps: Int): Float =
        if (steps >= 20 && distanceM > 0f) distanceM / steps else 0f

    // ============================================================================
    // PROCESAMIENTO GPS
    // ============================================================================

    private fun processGpsReading(reading: GpsReading) {
        val gpsStats = gpsManager.getCurrentStats()
        Log.d(TAG, "📍 Procesando GPS: dist=${gpsStats.totalDistanceMeters}, vel=${gpsStats.currentSpeedKmh}")

        // Calcular ganancia de elevación
        reading.altitude?.let { alt ->
            previousAltitude?.let { prevAlt ->
                val diff = alt - prevAlt
                if (diff > 0) {
                    totalElevationGain += diff.toFloat()
                }
            }
            previousAltitude = alt.toFloat()
        }

        // Actualizar máximos de velocidad para ambos tipos de laps
        if (gpsStats.currentSpeedKmh > lastLapMaxSpeed) {
            lastLapMaxSpeed = gpsStats.currentSpeedKmh
        }
        if (gpsStats.currentSpeedKmh > autoLapMaxSpeed) {
            autoLapMaxSpeed = gpsStats.currentSpeedKmh
        }

        // ⚡ AUTO-LAP: un cruce por cada múltiplo de 1000 m, con el instante interpolado (R3)
        val live = _stats.value
        if (live.isRunning && !live.isPaused) {
            val crossings = autoLapTracker.onSample(live.totalTimeSeconds.toDouble(), gpsStats.totalDistanceMeters)
            crossings.forEachIndexed { i, crossing ->
                addAutoLap(crossing, resetCounters = i == crossings.lastIndex, inGpsJump = crossings.size > 1)
            }
            kmAlertGate.alertFor(crossings.map { it.km })?.let { km -> announceKm(km) }
        }

        _stats.update {
            it.copy(
                totalDistanceMeters = gpsStats.totalDistanceMeters,
                currentSpeedKmh = gpsStats.currentSpeedKmh,
                avgSpeedKmh = gpsStats.avgSpeedKmh,
                maxSpeedKmh = gpsStats.maxSpeedKmh,
                currentAltitudeM = reading.altitude?.toFloat() ?: it.currentAltitudeM,
                elevationGainM = totalElevationGain,
                gpsAccuracy = reading.accuracy,
                latitude = reading.latitude,
                longitude = reading.longitude,
                currentLapDistanceMeters = gpsStats.totalDistanceMeters - lapBook.currentManualStartMeters
            )
        }

        // ⚡ Añadir punto al mapa
        addMapPoint(reading.latitude, reading.longitude)

        // Guardar punto GPS en BD
        saveGpsPoint(reading)
    }

    /**
     * Añade un punto al mapa en tiempo real (spec 009, R1): la ruta entera, aligerada.
     */
    private fun addMapPoint(latitude: Double, longitude: Double) {
        synchronized(routeSimplifier) {
            routeSimplifier.add(MapPoint(latitude, longitude))
            _mapPoints.value = routeSimplifier.points()
        }
    }

    /** Vacía lo que se dibuja en el mapa en vivo (al empezar y al volver a reposo). */
    private fun clearLiveMap() {
        synchronized(routeSimplifier) {
            routeSimplifier.reset()
            _mapPoints.value = emptyList()
        }
        _lapMarks.value = emptyList()
    }

    // ============================================================================
    // PERSISTENCIA
    // ============================================================================

    private fun saveGpsPoint(reading: GpsReading) {
        val sessionId = currentSessionId ?: return
        if (!_stats.value.isRunning || _stats.value.isPaused) return

        val point = GpsPointEntity(
            sessionId = sessionId,
            timestamp = reading.timestamp,
            latitude = reading.latitude,
            longitude = reading.longitude,
            altitude = reading.altitude,
            speed = reading.speed,
            accuracy = reading.accuracy,
            phase = "FREE_RIDE",
            round = _laps.value.size + 1
        )

        gpsPointsBuffer.add(point)

        if (gpsPointsBuffer.size >= 10) {
            flushGpsBuffer()
        }
    }

    private fun flushGpsBuffer() {
        if (gpsPointsBuffer.isEmpty()) return

        val pointsToSave = gpsPointsBuffer.toList()
        gpsPointsBuffer.clear()

        serviceScope.launch {
            try {
                gpsDao.insertPoints(pointsToSave)
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando puntos GPS: ${e.message}")
            }
        }
    }

    /** Guarda HR1/HR2/cadencia cada segundo para la gráfica del detalle de sesión */
    private fun saveSensorReading() {
        val sessionId = currentSessionId ?: return
        val s = _stats.value
        val now = System.currentTimeMillis()
        val reading = SensorReadingEntity(
            sessionId = sessionId,
            timestamp = now,
            // Sin valor congelado (R2.1): un pulso de hace más de HR_STALE_MS no se graba
            heartRate = currentHr1?.takeIf { it > 0 && now - currentHr1Ms < HR_STALE_MS },
            heartRate2 = currentHr2?.takeIf { it > 0 && now - currentHr2Ms < HR_STALE_MS },
            cadence = s.cadence.takeIf { s.isCadenceConnected && it > 0f },
            phase = "FREE_RIDE",
            round = s.lapsCount + 1
        )
        if (reading.heartRate == null && reading.heartRate2 == null && reading.cadence == null) return
        serviceScope.launch {
            try { sensorReadingDao.insertReading(reading) } catch (e: Exception) {
                Log.e(TAG, "Error guardando lectura: ${e.message}")
            }
        }
    }

    private suspend fun saveFinalStats(lapsToSave: List<LapData>) {
        currentSessionId?.let { sessionId ->
            val stats = _stats.value
            val gpsStats = gpsManager.getCurrentStats()
            val hrBoth = rideHr.count(0) > 0   // HR2 solo va aparte si también hubo HR1

            sessionDao.getSessionById(sessionId)?.let { session ->
                sessionDao.updateSession(
                    session.copy(
                        endTime = System.currentTimeMillis(),
                        totalTimeSeconds = stats.totalTimeSeconds,
                        // HR1 y HR2 por separado; si solo hubo HR2, va en las columnas de HR1 (como antes)
                        avgHeartRate = rideHr.primaryAvg()?.toInt(),
                        maxHeartRate = rideHr.primaryMax()?.toInt(),
                        minHeartRate = rideHr.primarySlot()?.let { rideHr.minPositive(it) }?.toInt(),
                        avgHeartRate2 = if (hrBoth) rideHr.avg(1)?.toInt() else null,
                        maxHeartRate2 = if (hrBoth) rideHr.max(1)?.toInt() else null,
                        minHeartRate2 = if (hrBoth) rideHr.minPositive(1)?.toInt() else null,
                        // Room guarda una sola cadencia: C1, si no C2 (C2 en vivo solo, R7)
                        avgCadence = rideCadence.primaryAvg(),
                        maxCadence = rideCadence.primaryMax(),
                        totalDistanceMeters = gpsStats.totalDistanceMeters,
                        avgSpeedKmh = gpsStats.avgSpeedKmh,
                        maxSpeedKmh = gpsStats.maxSpeedKmh,
                        elevationGain = gpsStats.elevationGain,
                        totalSteps = stats.steps.takeIf { it > 0 },
                        avgStrideM = strideOf(gpsStats.totalDistanceMeters, stats.steps).takeIf { it > 0f },
                        avgStepCadence = if (stats.steps > 0 && stats.totalTimeSeconds > 0)
                            stats.steps * 60f / stats.totalTimeSeconds else null,
                        isCompleted = true
                    )
                )
            }

            saveLaps(sessionId, lapsToSave)
        }
    }

    /** Guarda las vueltas; si falla, la sesión ya está guardada igualmente (resiliencia) */
    private suspend fun saveLaps(sessionId: String, lapsToSave: List<LapData>) {
        try {
            gpsDao.deleteLapsForSession(sessionId)   // idempotente si se finaliza dos veces
            lapsToSave.forEach { gpsDao.insertLap(it.toEntity(sessionId)) }
            Log.d(TAG, "💾 ${lapsToSave.size} vueltas guardadas")
        } catch (e: Exception) {
            Log.e(TAG, "No se pudieron guardar las vueltas: ${e.message}", e)
        }
    }

    private fun stopAllTracking() {
        timerJob?.cancel()
        stepCounter.stop()
        stopSensorCollectors()
    }

    private fun stopSensorCollectors() {
        hrCollectorJob?.cancel()
        hr2CollectorJob?.cancel()
        cadenceCollectorJob?.cancel()
        cadence2CollectorJob?.cancel()
        cadenceWatchdogJob?.cancel()
        gpsCollectorJob?.cancel()
    }

    // ============================================================================
    // NOTIFICACIONES
    // ============================================================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ruta Libre",
                NotificationManager.IMPORTANCE_HIGH  // ⚡ Cambiado a HIGH para evitar que Android mate el servicio
            ).apply {
                description = "Tracking de actividad en curso"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                // ⚡ Importante: No deshabilitar completamente para que el sistema no lo mate
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)  // ⚡ Prioridad alta
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .apply {
                // ⚡ Android 14+ requiere comportamiento específico
                if (Build.VERSION.SDK_INT >= 34) {
                    setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(title, text))
    }

    private fun formatDuration(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}