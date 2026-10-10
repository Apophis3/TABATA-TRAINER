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
import com.tuapp.tabatatrainer.util.laps.KmAlert
import com.tuapp.tabatatrainer.util.laps.KmAlertGate
import com.tuapp.tabatatrainer.util.laps.LapBlockMetrics
import com.tuapp.tabatatrainer.util.laps.LapBook
import com.tuapp.tabatatrainer.util.laps.LapRecord
import com.tuapp.tabatatrainer.util.laps.LapType
import com.tuapp.tabatatrainer.util.laps.PaceCalculator
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
    val heartRate: Int = 0,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val cadence: Float = 0f,
    val avgCadence: Float = 0f,
    val gpsAccuracy: Float? = null,
    val isGpsConnected: Boolean = false,
    val isGpsScanning: Boolean = false,
    val isHrConnected: Boolean = false,
    val isHrScanning: Boolean = false,
    val isCadenceConnected: Boolean = false,
    val isCadenceScanning: Boolean = false,
    // true = el sensor de cadencia está emparejado Y emitiendo (dato en los últimos 5 s)
    val cadenceActive: Boolean = false,
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
)

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

// ============================================================================
// FREE RIDE SERVICE - CON SOPORTE PARA MAPA EN TIEMPO REAL
// ============================================================================

@AndroidEntryPoint
class FreeRideService : Service() {

    companion object {
        private const val TAG = "FreeRideService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "freeride_channel"
        private const val MAX_MAP_POINTS = 1000  // Máximo puntos para el mapa en memoria
    }

    @Inject lateinit var sensorManager: SensorManager
    @Inject lateinit var gpsManager: GpsManager
    @Inject lateinit var sessionDao: SessionDao
    @Inject lateinit var gpsDao: GpsDao
    @Inject lateinit var sensorReadingDao: SensorReadingDao
    @Inject lateinit var stepCounter: StepCounter
    @Inject lateinit var soundPlayer: SoundPlayer

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
    private var cadenceWatchdogJob: Job? = null
    private var gpsCollectorJob: Job? = null

    // Instante (reloj de pared) del último dato del sensor de cadencia; null = ninguno vigente
    @Volatile private var lastCadenceSampleAtMs: Long? = null
    private val paceCalculator = PaceCalculator()

    private val hrReadings = mutableListOf<Int>()
    private val hr2Readings = mutableListOf<Int>()
    // Último valor de cada pulsómetro, para guardar 1 lectura por segundo
    @Volatile private var currentHr1: Int? = null
    @Volatile private var currentHr2: Int? = null
    private val cadenceReadings = mutableListOf<Float>()
    private val gpsPointsBuffer = mutableListOf<GpsPointEntity>()

    // Para cálculo de LAPs
    private var lastLapHrReadings = mutableListOf<Int>()
    private var lastLapCadenceReadings = mutableListOf<Float>()
    private var lastLapMaxSpeed = 0f
    private var lastLapMaxHr = 0
    
    // Para Auto-Lap independiente (cada 1 km)
    private var autoLapHrReadings = mutableListOf<Int>()
    private var autoLapCadenceReadings = mutableListOf<Float>()
    private var autoLapMaxSpeed = 0f
    private var autoLapMaxHr = 0

    private var previousAltitude: Float? = null
    private var totalElevationGain = 0f

    inner class LocalBinder : Binder() {
        fun getService(): FreeRideService = this@FreeRideService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
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
        super.onDestroy()
        serviceScope.cancel()
        stopAllTracking()
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

        // HR1
        hrCollectorJob?.cancel()
        hrCollectorJob = serviceScope.launch {
            _stats.update { it.copy(isHrScanning = true) }
            Log.d(TAG, "❤️ HR1: Iniciando flow...")

            try {
                sensorManager.observeHeartRate().catch { e ->
                    Log.e(TAG, "❌ Error en flow HR1: ${e.message}", e)
                    _stats.update { it.copy(isHrScanning = false, isHrConnected = false) }
                }.collect { reading ->
                    Log.d(TAG, "❤️ HR1 lectura recibida: ${reading::class.simpleName}")
                    when (reading) {
                        is HeartRateReading.Scanning -> {
                            Log.d(TAG, "❤️ HR1: Escaneando...")
                            _stats.update { it.copy(isHrScanning = true, isHrConnected = false) }
                        }
                        is HeartRateReading.Connecting -> {
                            Log.d(TAG, "❤️ HR1: Conectando a ${reading.deviceName}...")
                            _stats.update { it.copy(isHrScanning = true, isHrConnected = false) }
                        }
                        is HeartRateReading.Connected -> {
                            Log.d(TAG, "❤️ HR1: Conectado a ${reading.deviceName}")
                            _stats.update { it.copy(isHrScanning = false, isHrConnected = true) }
                        }
                        is HeartRateReading.Value -> {
                            Log.d(TAG, "❤️ HR1: Valor recibido: ${reading.bpm} bpm")
                            currentHr1 = reading.bpm
                            _stats.update {
                                it.copy(
                                    isHrScanning = false,
                                    isHrConnected = true,
                                    heartRate = reading.bpm
                                )
                            }

                            if (_stats.value.isRunning && !_stats.value.isPaused) {
                                hrReadings.add(reading.bpm)
                                lastLapHrReadings.add(reading.bpm)
                                autoLapHrReadings.add(reading.bpm)
                                if (reading.bpm > lastLapMaxHr) lastLapMaxHr = reading.bpm
                                if (reading.bpm > autoLapMaxHr) autoLapMaxHr = reading.bpm

                                val allHrReadings = hrReadings + hr2Readings
                                val avg = if (allHrReadings.isNotEmpty()) allHrReadings.average().toInt() else 0
                                val max = allHrReadings.maxOrNull() ?: 0
                                _stats.update { it.copy(avgHeartRate = avg, maxHeartRate = max) }
                            }
                        }
                        is HeartRateReading.Disconnected -> {
                            currentHr1 = null
                            Log.d(TAG, "❤️ HR1: Desconectado")
                            _stats.update { it.copy(isHrScanning = false, isHrConnected = false) }
                        }
                        is HeartRateReading.Error -> {
                            currentHr1 = null
                            Log.e(TAG, "❤️ HR1: Error - ${reading.message}")
                            _stats.update { it.copy(isHrScanning = false, isHrConnected = false) }
                        }
                        else -> {
                            Log.d(TAG, "❤️ HR1: Estado desconocido: ${reading::class.simpleName}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Excepción en collector HR1: ${e.message}", e)
                _stats.update { it.copy(isHrScanning = false, isHrConnected = false) }
            }
        }

        // HR2 - Escuchar también HR2 para detectar pulsómetros que se conecten como segundo sensor
        hr2CollectorJob?.cancel()
        hr2CollectorJob = serviceScope.launch {
            delay(1000) // Pequeño delay para no interferir con HR1
            Log.d(TAG, "❤️2 HR2: Iniciando flow...")

            try {
                sensorManager.observeHeartRate2().catch { e ->
                    Log.e(TAG, "❌ Error en flow HR2: ${e.message}", e)
                }.collect { reading ->
                    Log.d(TAG, "❤️2 HR2 lectura recibida: ${reading::class.simpleName}")
                    when (reading) {
                        is HeartRateReading.Value -> {
                            Log.d(TAG, "❤️2 HR2: Valor recibido: ${reading.bpm} bpm")
                            currentHr2 = reading.bpm
                            // Si HR1 no está conectado o no tiene valor, usar HR2
                            val currentHr = _stats.value.heartRate
                            if (currentHr == 0 || !_stats.value.isHrConnected) {
                                _stats.update {
                                    it.copy(
                                        isHrScanning = false,
                                        isHrConnected = true,
                                        heartRate = reading.bpm
                                    )
                                }
                            }

                            if (_stats.value.isRunning && !_stats.value.isPaused) {
                                hr2Readings.add(reading.bpm)
                                lastLapHrReadings.add(reading.bpm)
                                autoLapHrReadings.add(reading.bpm)
                                if (reading.bpm > lastLapMaxHr) lastLapMaxHr = reading.bpm
                                if (reading.bpm > autoLapMaxHr) autoLapMaxHr = reading.bpm

                                val allHrReadings = hrReadings + hr2Readings
                                val avg = if (allHrReadings.isNotEmpty()) allHrReadings.average().toInt() else 0
                                val max = allHrReadings.maxOrNull() ?: 0
                                _stats.update { it.copy(avgHeartRate = avg, maxHeartRate = max) }
                            }
                        }
                        is HeartRateReading.Connected -> {
                            Log.d(TAG, "❤️2 HR2: Conectado a ${reading.deviceName}")
                            // Si HR1 no está conectado, marcar como conectado usando HR2
                            if (!_stats.value.isHrConnected) {
                                _stats.update { it.copy(isHrScanning = false, isHrConnected = true) }
                            }
                        }
                        is HeartRateReading.Disconnected -> {
                            currentHr2 = null
                            Log.d(TAG, "❤️2 HR2: Desconectado")
                            // Solo actualizar si HR1 tampoco está conectado
                            if (!_stats.value.isHrConnected) {
                                _stats.update { it.copy(isHrScanning = false, isHrConnected = false) }
                            }
                        }
                        else -> {
                            // Otros estados no son críticos para HR2
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Excepción en collector HR2: ${e.message}", e)
            }
        }

        // Cadencia
        cadenceCollectorJob?.cancel()
        cadenceCollectorJob = serviceScope.launch {
            delay(2000)
            _stats.update { it.copy(isCadenceScanning = true) }
            Log.d(TAG, "🚴 Cadencia: Iniciando flow...")

            try {
                sensorManager.observeCadence().catch { e ->
                    Log.e(TAG, "❌ Error en flow Cadencia: ${e.message}", e)
                    _stats.update { it.copy(isCadenceScanning = false, isCadenceConnected = false) }
                }.collect { reading ->
                    Log.d(TAG, "🚴 Cadencia lectura recibida: ${reading::class.simpleName}")
                    when (reading) {
                        is CadenceReading.Scanning -> {
                            Log.d(TAG, "🚴 Cadencia: Escaneando...")
                            _stats.update { it.copy(isCadenceScanning = true, isCadenceConnected = false) }
                        }
                        is CadenceReading.Connecting -> {
                            Log.d(TAG, "🚴 Cadencia: Conectando a ${reading.deviceName}...")
                            _stats.update { it.copy(isCadenceScanning = true, isCadenceConnected = false) }
                        }
                        is CadenceReading.Connected -> {
                            Log.d(TAG, "🚴 Cadencia: Conectado a ${reading.deviceName}")
                            _stats.update { it.copy(isCadenceScanning = false, isCadenceConnected = true) }
                        }
                        is CadenceReading.Value -> {
                            Log.d(TAG, "🚴 Cadencia: Valor recibido: ${reading.rpm} rpm")
                            lastCadenceSampleAtMs = System.currentTimeMillis()
                            _stats.update {
                                it.copy(
                                    isCadenceScanning = false,
                                    isCadenceConnected = true,
                                    cadenceActive = true,
                                    cadence = reading.rpm.toFloat()
                                )
                            }

                            if (_stats.value.isRunning && !_stats.value.isPaused) {
                                cadenceReadings.add(reading.rpm.toFloat())
                                lastLapCadenceReadings.add(reading.rpm.toFloat())
                                autoLapCadenceReadings.add(reading.rpm.toFloat())
                                val avg = cadenceReadings.average().toFloat()
                                _stats.update { it.copy(avgCadence = avg) }
                            }
                        }
                        is CadenceReading.Disconnected -> {
                            Log.d(TAG, "🚴 Cadencia: Desconectado")
                            lastCadenceSampleAtMs = null
                            _stats.update { it.copy(isCadenceScanning = false, isCadenceConnected = false, cadenceActive = false) }
                        }
                        is CadenceReading.Error -> {
                            Log.e(TAG, "🚴 Cadencia: Error - ${reading.message}")
                            lastCadenceSampleAtMs = null
                            _stats.update { it.copy(isCadenceScanning = false, isCadenceConnected = false, cadenceActive = false) }
                        }
                        else -> {
                            Log.d(TAG, "🚴 Cadencia: Estado desconocido: ${reading::class.simpleName}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Excepción en collector Cadencia: ${e.message}", e)
                _stats.update { it.copy(isCadenceScanning = false, isCadenceConnected = false) }
            }
        }

        // Vigilante: sin datos recientes del sensor la cadencia cede el hueco al ritmo (y viceversa)
        cadenceWatchdogJob?.cancel()
        cadenceWatchdogJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val active = CadenceDisplayPolicy.mode(
                    paired = _stats.value.isCadenceConnected,
                    lastSampleAtMs = lastCadenceSampleAtMs,
                    nowMs = System.currentTimeMillis()
                ) == CadenceSlotMode.CADENCE
                _stats.update { it.copy(cadenceActive = active) }
            }
        }

        updateNotification("Ruta Libre", "Sensores listos - Pulsa iniciar")
    }

    fun reconnectSensors() {
        Log.d(TAG, "🔄 Reconectando sensores...")
        // Solo sensores: el cronómetro de la ruta debe seguir corriendo
        stopSensorCollectors()

        serviceScope.launch {
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
        hrReadings.clear()
        hr2Readings.clear()
        currentHr1 = null
        currentHr2 = null
        cadenceReadings.clear()
        paceCalculator.reset()
        gpsPointsBuffer.clear()
        _laps.update { emptyList() }
        _mapPoints.update { emptyList() }  // ⚡ Limpiar puntos del mapa
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
    }

    fun pauseRide() {
        if (!_stats.value.isRunning || _stats.value.isPaused) return

        timerJob?.cancel()
        gpsManager.pause()
        paceCalculator.reset()
        _stats.update { it.copy(isPaused = true, paceSecPerKm = null) }
        updateNotification("Pausado", "Toca para continuar")

        Log.d(TAG, "⏸️ Ruta pausada")
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
            hrReadings = lastLapHrReadings,
            maxHr = lastLapMaxHr,
            cadenceReadings = lastLapCadenceReadings,
            metrics = LapBlockMetrics.of(manualWindowStartCounters, endCounters, lastLapCadenceReadings.isNotEmpty())
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
        val bikeUsed = autoLapCadenceReadings.isNotEmpty()
        val metrics = if (inGpsJump) {
            BlockMetrics(steps = null, elevationGain = null,
                cadenceSource = if (bikeUsed) CadenceSource.BIKE_SENSOR else CadenceSource.NONE)
        } else {
            LapBlockMetrics.of(autoWindowStartCounters, endCounters, bikeUsed)
        }
        val lap = record.toLapData(
            avgSpeedKmh = speedOf(record.distanceMeters, record.durationSeconds),
            maxSpeedKmh = autoLapMaxSpeed,
            hrReadings = autoLapHrReadings,
            maxHr = autoLapMaxHr,
            cadenceReadings = autoLapCadenceReadings,
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
        hrReadings: List<Int>,
        maxHr: Int,
        cadenceReadings: List<Float>,
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
        avgHeartRate = if (hrReadings.isNotEmpty()) hrReadings.average().toInt() else 0,
        maxHeartRate = maxHr,
        avgCadence = if (cadenceReadings.isNotEmpty()) cadenceReadings.average().toFloat() else 0f,
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
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo vibrar: ${e.message}")
        }
    }

    private fun resetLapCounters() {
        lastLapHrReadings.clear()
        lastLapCadenceReadings.clear()
        lastLapMaxSpeed = 0f
        lastLapMaxHr = 0
    }
    
    private fun resetAutoLapCounters() {
        autoLapHrReadings.clear()
        autoLapCadenceReadings.clear()
        autoLapMaxSpeed = 0f
        autoLapMaxHr = 0
    }

    fun resetToIdle() {
        _stats.update {
            FreeRideStats(
                isGpsConnected = it.isGpsConnected,
                isHrConnected = it.isHrConnected,
                isCadenceConnected = it.isCadenceConnected,
                cadenceActive = it.cadenceActive,
                heartRate = it.heartRate,
                cadence = it.cadence
            )
        }
        _laps.update { emptyList() }
        _mapPoints.update { emptyList() }
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
     * Añade un punto al mapa en tiempo real
     */
    private fun addMapPoint(latitude: Double, longitude: Double) {
        val newPoint = MapPoint(latitude, longitude)

        _mapPoints.update { currentPoints ->
            val newList = currentPoints + newPoint
            // Limitar el número de puntos en memoria
            if (newList.size > MAX_MAP_POINTS) {
                newList.takeLast(MAX_MAP_POINTS)
            } else {
                newList
            }
        }
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
        val reading = SensorReadingEntity(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            heartRate = currentHr1?.takeIf { it > 0 },
            heartRate2 = currentHr2?.takeIf { it > 0 },
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

            sessionDao.getSessionById(sessionId)?.let { session ->
                sessionDao.updateSession(
                    session.copy(
                        endTime = System.currentTimeMillis(),
                        totalTimeSeconds = stats.totalTimeSeconds,
                        // HR1 y HR2 por separado (antes se mezclaban en una sola media)
                        avgHeartRate = hrReadings.takeIf { it.isNotEmpty() }?.average()?.toInt()
                            ?: hr2Readings.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                        maxHeartRate = hrReadings.maxOrNull() ?: hr2Readings.maxOrNull(),
                        minHeartRate = (hrReadings.ifEmpty { hr2Readings }).filter { it > 0 }.minOrNull(),
                        avgHeartRate2 = if (hrReadings.isNotEmpty()) hr2Readings.takeIf { it.isNotEmpty() }?.average()?.toInt() else null,
                        maxHeartRate2 = if (hrReadings.isNotEmpty()) hr2Readings.maxOrNull() else null,
                        minHeartRate2 = if (hrReadings.isNotEmpty()) hr2Readings.filter { it > 0 }.minOrNull() else null,
                        avgCadence = if (cadenceReadings.isNotEmpty()) cadenceReadings.average().toFloat() else null,
                        maxCadence = cadenceReadings.maxOrNull(),
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