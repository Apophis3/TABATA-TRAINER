package com.tuapp.tabatatrainer.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tuapp.tabatatrainer.MainActivity
import com.tuapp.tabatatrainer.data.local.*
import com.tuapp.tabatatrainer.sensor.*
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
    val lapsCount: Int = 0,
    val currentLapDistanceMeters: Float = 0f,
    val currentLapTimeSeconds: Int = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

data class LapData(
    val number: Int,
    val startTimeSeconds: Int,
    val endTimeSeconds: Int,
    val distanceMeters: Float,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float = 0f,
    val avgHeartRate: Int,
    val maxHeartRate: Int = 0,
    val avgCadence: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val isAutoLap: Boolean = false  // true = auto-lap cada 1 km, false = lap manual
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val binder = LocalBinder()

    private val _stats = MutableStateFlow(FreeRideStats())
    val stats: StateFlow<FreeRideStats> = _stats.asStateFlow()

    private val _laps = MutableStateFlow<List<LapData>>(emptyList())
    val laps: StateFlow<List<LapData>> = _laps.asStateFlow()

    // ⚡ NUEVO: Puntos GPS para el mapa en tiempo real
    private val _mapPoints = MutableStateFlow<List<MapPoint>>(emptyList())
    val mapPoints: StateFlow<List<MapPoint>> = _mapPoints.asStateFlow()

    private var currentSessionId: String? = null
    private var timerJob: Job? = null
    private var hrCollectorJob: Job? = null
    private var hr2CollectorJob: Job? = null
    private var cadenceCollectorJob: Job? = null
    private var gpsCollectorJob: Job? = null

    private val hrReadings = mutableListOf<Int>()
    private val hr2Readings = mutableListOf<Int>()
    private val cadenceReadings = mutableListOf<Float>()
    private val gpsPointsBuffer = mutableListOf<GpsPointEntity>()

    // Para cálculo de LAPs
    private var lastLapTimeSeconds = 0
    private var lastLapDistanceMeters = 0f
    private var lastLapHrReadings = mutableListOf<Int>()
    private var lastLapCadenceReadings = mutableListOf<Float>()
    private var lastLapMaxSpeed = 0f
    private var lastLapMaxHr = 0
    
    // Para Auto-Lap independiente (cada 1 km)
    private var lastAutoLapDistanceMeters = 0f
    private var lastAutoLapTimeSeconds = 0
    private var autoLapHrReadings = mutableListOf<Int>()
    private var autoLapCadenceReadings = mutableListOf<Float>()
    private var autoLapMaxSpeed = 0f
    private var autoLapMaxHr = 0
    private var lastAutoLapTimestamp = 0L // Protección contra auto-laps duplicados

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
                            Log.d(TAG, "❤️ HR1: Desconectado")
                            _stats.update { it.copy(isHrScanning = false, isHrConnected = false) }
                        }
                        is HeartRateReading.Error -> {
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
                            _stats.update {
                                it.copy(
                                    isCadenceScanning = false,
                                    isCadenceConnected = true,
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
                            _stats.update { it.copy(isCadenceScanning = false, isCadenceConnected = false) }
                        }
                        is CadenceReading.Error -> {
                            Log.e(TAG, "🚴 Cadencia: Error - ${reading.message}")
                            _stats.update { it.copy(isCadenceScanning = false, isCadenceConnected = false) }
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

        updateNotification("Ruta Libre", "Sensores listos - Pulsa iniciar")
    }

    fun reconnectSensors() {
        Log.d(TAG, "🔄 Reconectando sensores...")
        stopAllTracking()

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
        cadenceReadings.clear()
        gpsPointsBuffer.clear()
        _laps.update { emptyList() }
        _mapPoints.update { emptyList() }  // ⚡ Limpiar puntos del mapa
        resetLapCounters()
        resetAutoLapCounters()
        lastLapTimeSeconds = 0
        lastLapDistanceMeters = 0f
        lastAutoLapTimeSeconds = 0
        lastAutoLapDistanceMeters = 0f
        lastAutoLapTimestamp = 0L
        totalElevationGain = 0f
        previousAltitude = null

        gpsManager.resetStats()
        Log.d(TAG, "📍 GPS stats reseteadas")

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
                lapsCount = 0,
                currentLapDistanceMeters = 0f,
                currentLapTimeSeconds = 0
            )
        }

        startTimer()
        updateNotification("En Ruta", "Grabando actividad...")

        Log.d(TAG, "✅ Ruta iniciada: $currentSessionId")
    }

    fun pauseRide() {
        if (!_stats.value.isRunning || _stats.value.isPaused) return

        timerJob?.cancel()
        _stats.update { it.copy(isPaused = true) }
        updateNotification("Pausado", "Toca para continuar")

        Log.d(TAG, "⏸️ Ruta pausada")
    }

    fun resumeRide() {
        if (!_stats.value.isPaused) return

        _stats.update { it.copy(isPaused = false) }
        startTimer()
        updateNotification("En Ruta", "Grabando actividad...")

        Log.d(TAG, "▶️ Ruta reanudada")
    }

    fun stopRide() {
        Log.d(TAG, "🛑 Finalizando ruta...")

        timerJob?.cancel()
        flushGpsBuffer()

        serviceScope.launch {
            saveFinalStats()
        }

        _stats.update { it.copy(isRunning = false, isPaused = false) }
        updateNotification("Ruta Finalizada", "¡Buen trabajo!")
    }

    /**
     * Añade un lap manual (presionando el botón)
     * NO afecta el contador de auto-laps
     */
    fun addLap() {
        if (!_stats.value.isRunning) return

        val currentStats = _stats.value
        val lapNumber = _laps.value.size + 1

        val lapDistance = currentStats.totalDistanceMeters - lastLapDistanceMeters
        val lapTime = currentStats.totalTimeSeconds - lastLapTimeSeconds
        val lapAvgSpeed = if (lapTime > 0) (lapDistance / 1000f) / (lapTime / 3600f) else 0f
        val lapAvgHr = if (lastLapHrReadings.isNotEmpty()) lastLapHrReadings.average().toInt() else 0
        val lapAvgCadence = if (lastLapCadenceReadings.isNotEmpty()) lastLapCadenceReadings.average().toFloat() else 0f

        val lap = LapData(
            number = lapNumber,
            startTimeSeconds = lastLapTimeSeconds,
            endTimeSeconds = currentStats.totalTimeSeconds,
            distanceMeters = lapDistance,
            avgSpeedKmh = lapAvgSpeed,
            maxSpeedKmh = lastLapMaxSpeed,
            avgHeartRate = lapAvgHr,
            maxHeartRate = lastLapMaxHr,
            avgCadence = lapAvgCadence,
            isAutoLap = false  // Lap manual
        )

        _laps.update { it + lap }
        
        // Resetear contadores de lap manual (NO afecta auto-lap)
        resetLapCounters()
        lastLapTimeSeconds = currentStats.totalTimeSeconds
        lastLapDistanceMeters = currentStats.totalDistanceMeters
        
        // Actualizar estado con nuevo lap y resetear tiempo del lap actual
        _stats.update { 
            it.copy(
                lapsCount = lapNumber,
                currentLapTimeSeconds = 0  // Resetear tiempo del lap actual
            ) 
        }

        Log.d(TAG, "🏁 LAP MANUAL $lapNumber: ${String.format("%.2f", lapDistance/1000)}km en ${formatDuration(lapTime)}")
    }
    
    /**
     * Añade un auto-lap automático cada 1 km
     * Basado en distancia total acumulada, independiente de laps manuales
     */
    private fun addAutoLap() {
        if (!_stats.value.isRunning) return

        val currentStats = _stats.value
        val lapNumber = _laps.value.size + 1

        val lapDistance = currentStats.totalDistanceMeters - lastAutoLapDistanceMeters
        val lapTime = currentStats.totalTimeSeconds - lastAutoLapTimeSeconds
        val lapAvgSpeed = if (lapTime > 0) (lapDistance / 1000f) / (lapTime / 3600f) else 0f
        val lapAvgHr = if (autoLapHrReadings.isNotEmpty()) autoLapHrReadings.average().toInt() else 0
        val lapAvgCadence = if (autoLapCadenceReadings.isNotEmpty()) autoLapCadenceReadings.average().toFloat() else 0f

        val lap = LapData(
            number = lapNumber,
            startTimeSeconds = lastAutoLapTimeSeconds,
            endTimeSeconds = currentStats.totalTimeSeconds,
            distanceMeters = lapDistance,
            avgSpeedKmh = lapAvgSpeed,
            maxSpeedKmh = autoLapMaxSpeed,
            avgHeartRate = lapAvgHr,
            maxHeartRate = autoLapMaxHr,
            avgCadence = lapAvgCadence,
            isAutoLap = true  // Auto-lap
        )

        _laps.update { it + lap }
        
        // Resetear contadores de auto-lap
        resetAutoLapCounters()
        lastAutoLapTimeSeconds = currentStats.totalTimeSeconds
        lastAutoLapDistanceMeters = currentStats.totalDistanceMeters
        
        // Actualizar estado con nuevo lap (auto-lap no resetea el tiempo del lap manual)
        _stats.update { it.copy(lapsCount = lapNumber) }

        Log.d(TAG, "⚡ AUTO-LAP $lapNumber: ${String.format("%.2f", lapDistance/1000)}km en ${formatDuration(lapTime)}")
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
                // Capturar lastLapTimeSeconds antes de actualizar para evitar race conditions
                val lapStartTime = lastLapTimeSeconds
                _stats.update {
                    val newTotalTime = it.totalTimeSeconds + 1
                    it.copy(
                        totalTimeSeconds = newTotalTime,
                        currentLapTimeSeconds = newTotalTime - lapStartTime
                    )
                }

                if (_stats.value.totalTimeSeconds % 30 == 0) {
                    val time = formatDuration(_stats.value.totalTimeSeconds)
                    val dist = String.format("%.2f km", _stats.value.totalDistanceMeters / 1000f)
                    updateNotification("En Ruta", "$time • $dist")
                }
            }
        }
    }

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

        // ⚡ AUTO-LAP: Verificar si se ha alcanzado un nuevo km completo
        val currentDistanceKm = gpsStats.totalDistanceMeters / 1000f
        val lastAutoLapDistanceKm = lastAutoLapDistanceMeters / 1000f
        val nextAutoLapKm = (lastAutoLapDistanceKm.toInt() + 1).toFloat()
        
        // Protección: mínimo 2 segundos entre auto-laps para evitar duplicados
        val timeSinceLastAutoLap = System.currentTimeMillis() - lastAutoLapTimestamp
        val minTimeBetweenAutoLaps = 2000L // 2 segundos
        
        val shouldTriggerAutoLap = if (lastAutoLapDistanceMeters > 0) {
            // Auto-laps subsecuentes: verificar km completo Y tiempo mínimo
            currentDistanceKm >= nextAutoLapKm && timeSinceLastAutoLap >= minTimeBetweenAutoLaps
        } else {
            // Primer auto-lap: solo verificar distancia
            currentDistanceKm >= 1.0f
        }
        
        if (shouldTriggerAutoLap) {
            addAutoLap()
            lastAutoLapTimestamp = System.currentTimeMillis()
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
                currentLapDistanceMeters = gpsStats.totalDistanceMeters - lastLapDistanceMeters
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

    private suspend fun saveFinalStats() {
        currentSessionId?.let { sessionId ->
            val stats = _stats.value
            val gpsStats = gpsManager.getCurrentStats()

            sessionDao.getSessionById(sessionId)?.let { session ->
                sessionDao.updateSession(
                    session.copy(
                        endTime = System.currentTimeMillis(),
                        totalTimeSeconds = stats.totalTimeSeconds,
                        avgHeartRate = if (hrReadings.isNotEmpty() || hr2Readings.isNotEmpty()) {
                            (hrReadings + hr2Readings).average().toInt()
                        } else null,
                        maxHeartRate = (hrReadings + hr2Readings).maxOrNull(),
                        minHeartRate = (hrReadings + hr2Readings).filter { it > 0 }.minOrNull(),
                        avgCadence = if (cadenceReadings.isNotEmpty()) cadenceReadings.average().toFloat() else null,
                        maxCadence = cadenceReadings.maxOrNull(),
                        totalDistanceMeters = gpsStats.totalDistanceMeters,
                        avgSpeedKmh = gpsStats.avgSpeedKmh,
                        maxSpeedKmh = gpsStats.maxSpeedKmh,
                        elevationGain = gpsStats.elevationGain,
                        isCompleted = true
                    )
                )
            }
        }
    }

    private fun stopAllTracking() {
        timerJob?.cancel()
        hrCollectorJob?.cancel()
        hr2CollectorJob?.cancel()
        cadenceCollectorJob?.cancel()
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