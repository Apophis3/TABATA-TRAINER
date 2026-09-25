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
import com.tuapp.tabatatrainer.util.Sound
import com.tuapp.tabatatrainer.util.SoundPlayer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class WorkoutService : Service() {

    companion object {
        private const val TAG = "WorkoutService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "workout_channel"

        private const val MAX_SENSOR_READINGS = 21600  // Máximo 6 horas de datos (1 por segundo), cubre cualquier entrenamiento sin recortar la gráfica
    }

    @Inject lateinit var sensorManager: SensorManager
    @Inject lateinit var gpsManager: GpsManager              // ← GPS INYECTADO
    @Inject lateinit var sessionDao: SessionDao
    @Inject lateinit var sensorReadingDao: SensorReadingDao
    @Inject lateinit var gpsDao: GpsDao                      // ← GPS DAO INYECTADO
    @Inject lateinit var soundPlayer: SoundPlayer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val binder = LocalBinder()

    private val _workoutState = MutableStateFlow(WorkoutState())
    val workoutState: StateFlow<WorkoutState> = _workoutState.asStateFlow()

    private val _sensorState = MutableStateFlow(SensorState())
    val sensorState: StateFlow<SensorState> = _sensorState.asStateFlow()

    private val _sessionStats = MutableStateFlow(SessionStats())
    val sessionStats: StateFlow<SessionStats> = _sessionStats.asStateFlow()

    private var currentConfig: WorkoutConfig = WorkoutConfig()
    private var currentSessionId: String? = null
    private var timerJob: Job? = null
    private var hrCollectorJob: Job? = null
    private var hr2CollectorJob: Job? = null
    private var cadenceCollectorJob: Job? = null
    private var cadence2CollectorJob: Job? = null
    private var gpsCollectorJob: Job? = null                 // ← GPS JOB

    private val hrReadings = mutableListOf<Int>()
    private val hr2Readings = mutableListOf<Int>()
    private val cadenceReadings = mutableListOf<Float>()
    
    // Objetos de sincronización para proteger las listas
    private val hrReadingsLock = Any()
    private val hr2ReadingsLock = Any()
    private val cadenceReadingsLock = Any()
    
    // Funciones thread-safe para agregar elementos
    private fun addHrReading(bpm: Int) {
        synchronized(hrReadingsLock) {
            hrReadings.add(bpm)
        }
    }
    
    private fun addHr2Reading(bpm: Int) {
        synchronized(hr2ReadingsLock) {
            hr2Readings.add(bpm)
        }
    }
    
    private fun addCadenceReading(rpm: Float) {
        synchronized(cadenceReadingsLock) {
            cadenceReadings.add(rpm)
        }
    }

    private val cadence2Readings = mutableListOf<Float>()
    private val cadence2ReadingsLock = Any()
    private fun addCadence2Reading(rpm: Float) {
        synchronized(cadence2ReadingsLock) {
            cadence2Readings.add(rpm)
        }
    }

    // Contadores de tiempo por fase
    private var workTimeAccumulated = 0
    private var restTimeAccumulated = 0
    private var warmupTimeAccumulated = 0

    // ============================================================================
    // GPS - Variables de estado
    // ============================================================================
    private var isGpsEnabled = false
    private var gpsPointsBuffer = mutableListOf<GpsPointEntity>()
    private val GPS_BUFFER_SIZE = 10  // Guardar en BD cada 10 puntos

    inner class LocalBinder : Binder() {
        fun getService(): WorkoutService = this@WorkoutService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Preparado", "Entrenamiento listo")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification,
                if (Build.VERSION.SDK_INT >= 34) {
                    // Android 14+ requiere tipo de servicio específico
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
        stopSensors()
        soundPlayer.release()
    }

    // ============================================================================
    // CONFIGURACIÓN Y CONTROL DEL ENTRENAMIENTO
    // ============================================================================

    fun configureWorkout(config: WorkoutConfig) {
        if (_workoutState.value.phase != WorkoutPhase.IDLE && _workoutState.value.phase != WorkoutPhase.FINISHED) return
        currentConfig = config
        isGpsEnabled = config.gpsEnabled
        
        _workoutState.update { 
            it.copy(
                totalRounds = config.rounds,
                remainingSeconds = config.warmupSeconds
            )
        }
        
        // Actualizar estado GPS
        _sensorState.update { 
            it.copy(isGpsEnabled = config.gpsEnabled) 
        }
        
        Log.d(TAG, "Workout configurado: GPS=${config.gpsEnabled}")
    }

    /**
     * Obtiene la configuración actual del entrenamiento
     */
    fun getCurrentConfig(): WorkoutConfig {
        return currentConfig.copy(gpsEnabled = isGpsEnabled)
    }

    /**
     * Habilita/deshabilita GPS (puede llamarse antes de iniciar)
     */
    fun setGpsEnabled(enabled: Boolean) {
        isGpsEnabled = enabled
        _sensorState.update { it.copy(isGpsEnabled = enabled) }
        Log.d(TAG, "GPS ${if (enabled) "habilitado" else "deshabilitado"}")
    }

    fun startWorkout() {
        if (_workoutState.value.isRunning) return
        
        currentSessionId = UUID.randomUUID().toString()
        hrReadings.clear()
        hr2Readings.clear()
        cadenceReadings.clear()
        gpsPointsBuffer.clear()
        
        // Resetear contadores de tiempo
        workTimeAccumulated = 0
        restTimeAccumulated = 0
        warmupTimeAccumulated = 0
        
        // Resetear stats GPS
        if (isGpsEnabled) {
            gpsManager.resetStats()
        }
        
        _sessionStats.value = SessionStats(
            sessionId = currentSessionId,
            totalRounds = currentConfig.rounds
        )

        serviceScope.launch {
            val session = WorkoutSessionEntity(
                id = currentSessionId!!,
                warmupSeconds = currentConfig.warmupSeconds,
                workSeconds = currentConfig.workSeconds,
                restSeconds = currentConfig.restSeconds,
                totalRounds = currentConfig.rounds,
                gpsEnabled = isGpsEnabled  // ← Guardar si GPS está activo
            )
            sessionDao.insertSession(session)
        }

        _workoutState.update {
            it.copy(
                phase = WorkoutPhase.WARMUP,
                currentRound = 0,
                remainingSeconds = currentConfig.warmupSeconds,
                totalElapsedSeconds = 0,
                workSecondsElapsed = 0,
                restSecondsElapsed = 0,
                warmupSecondsElapsed = 0,
                isRunning = true,
                isPaused = false
            )
        }

        // 🔊 SONIDO: Inicio de calentamiento
        soundPlayer.playSound(Sound.START_WARMUP)
        
        startTimer()
        startSensors()
        updateNotification("Calentamiento", "Preparándote...")
    }

    fun pauseWorkout() {
        if (!_workoutState.value.isRunning || _workoutState.value.isPaused) return
        timerJob?.cancel()
        _workoutState.update { it.copy(isPaused = true) }
        updateNotification("Pausado", "Toca para continuar")
    }

    fun resumeWorkout() {
        if (!_workoutState.value.isPaused) return
        _workoutState.update { it.copy(isPaused = false) }
        startTimer()
        updateNotification(
            _workoutState.value.phase.displayName,
            "Ronda ${_workoutState.value.currentRound}/${_workoutState.value.totalRounds}"
        )
    }

    fun stopWorkout() {
        finishWorkout(isCompleted = false)
    }

    fun resetToIdle() {
        timerJob?.cancel()
        _workoutState.value = WorkoutState(totalRounds = currentConfig.rounds, remainingSeconds = currentConfig.warmupSeconds)
        _sessionStats.value = SessionStats()
        currentSessionId = null
        hrReadings.clear()
        cadenceReadings.clear()
        cadence2Readings.clear()
        gpsPointsBuffer.clear()
        workTimeAccumulated = 0
        restTimeAccumulated = 0
        warmupTimeAccumulated = 0
        
        // Resetear estado GPS
        _sensorState.update { 
            it.copy(
                isGpsTracking = false,
                currentSpeedKmh = 0f,
                totalDistanceKm = 0f
            ) 
        }
        
        updateNotification("Preparado", "Entrenamiento listo")
    }

    // ============================================================================
    // RECONEXIÓN MANUAL DE SENSORES
    // ============================================================================

    fun reconnectHeartRate() {
        hrCollectorJob?.cancel()
        hrCollectorJob = serviceScope.launch {
            sensorManager.observeHeartRate().collect { reading ->
                when (reading) {
                    is HeartRateReading.Scanning -> {
                        _sensorState.update { 
                            it.copy(
                                isHrScanning = true,
                                isHrConnected = false,
                                hrErrorMessage = "Buscando..."
                            ) 
                        }
                    }
                    is HeartRateReading.Connecting -> {
                        _sensorState.update { 
                            it.copy(
                                isHrScanning = true,
                                hrErrorMessage = "Conectando..."
                            ) 
                        }
                    }
                    is HeartRateReading.Value -> {
                        addHrReading(reading.bpm)
                        _sensorState.update {
                            it.copy(heartRate = reading.bpm, hrErrorMessage = null)
                        }
                    }
                    is HeartRateReading.Connected -> {
                        _sensorState.update {
                            it.copy(
                                isHrConnected = true,
                                isHrScanning = false,
                                hrDeviceName = reading.deviceName,
                                hrErrorMessage = null
                            )
                        }
                    }
                    is HeartRateReading.Disconnected -> {
                        _sensorState.update {
                            it.copy(
                                isHrConnected = false,
                                isHrScanning = false,
                                heartRate = 0
                            )
                        }
                    }
                    is HeartRateReading.Error -> {
                        _sensorState.update {
                            it.copy(hrErrorMessage = reading.message)
                        }
                    }
                    else -> {
                        // Caso exhaustivo
                    }
                }
            }
        }
    }

    fun reconnectCadence() {
        cadenceCollectorJob?.cancel()
        cadenceCollectorJob = serviceScope.launch {
            sensorManager.observeCadence().collect { reading ->
                when (reading) {
                    is CadenceReading.Scanning -> {
                        _sensorState.update { 
                            it.copy(
                                isCadenceScanning = true,
                                isCadenceConnected = false,
                                cadenceErrorMessage = "Buscando..."
                            ) 
                        }
                    }
                    is CadenceReading.Connecting -> {
                        _sensorState.update { 
                            it.copy(
                                isCadenceScanning = true,
                                cadenceErrorMessage = "Conectando..."
                            ) 
                        }
                    }
                    is CadenceReading.Value -> {
                        addCadenceReading(reading.rpm.toFloat())
                        _sensorState.update {
                            it.copy(cadence = reading.rpm.toFloat(), cadenceErrorMessage = null)
                        }
                    }
                    is CadenceReading.Connected -> {
                        _sensorState.update {
                            it.copy(
                                isCadenceConnected = true,
                                isCadenceScanning = false,
                                cadenceDeviceName = reading.deviceName,
                                cadenceErrorMessage = null
                            )
                        }
                    }
                    is CadenceReading.Disconnected -> {
                        _sensorState.update {
                            it.copy(
                                isCadenceConnected = false,
                                isCadenceScanning = false,
                                cadence = 0f
                            )
                        }
                    }
                    is CadenceReading.Error -> {
                        _sensorState.update {
                            it.copy(cadenceErrorMessage = reading.message)
                        }
                    }
                    else -> {
                        // Caso exhaustivo
                    }
                }
            }
        }
    }

    /**
     * Reconectar GPS manualmente
     */
    fun reconnectGps() {
        if (!isGpsEnabled) return
        
        gpsCollectorJob?.cancel()
        gpsManager.resetStats()
        startGpsTracking()
    }

    /**
     * Refrescar sensores (reconectar HR y Cadencia)
     */
    fun refreshSensors() {
        Log.d(TAG, "🔄 Refrescando sensores HR y Cadencia...")
        // Cancelar jobs existentes para forzar una nueva suscripción y, por ende, una nueva autoconexión
        hrCollectorJob?.cancel()
        hr2CollectorJob?.cancel()
        cadenceCollectorJob?.cancel()
        cadence2CollectorJob?.cancel()
        
        // Resetear el estado de los sensores en la UI
        _sensorState.update {
            it.copy(
                isHrConnected = false,
                isHrScanning = false,
                hrDeviceName = null,
                heartRate = 0,
                hrErrorMessage = null,
                isHr2Connected = false,
                isHr2Scanning = false,
                hr2DeviceName = null,
                heartRate2 = 0,
                hr2ErrorMessage = null,
                isCadenceConnected = false,
                isCadenceScanning = false,
                cadenceDeviceName = null,
                cadence = 0f,
                cadenceErrorMessage = null,
                isCadence2Connected = false,
                isCadence2Scanning = false,
                cadence2DeviceName = null,
                cadence2 = 0f,
                cadence2ErrorMessage = null
            )
        }
        
        // Reiniciar la recolección de datos de los sensores
        startSensors()
    }

    // ============================================================================
    // FINALIZACIÓN DEL ENTRENAMIENTO
    // ============================================================================

    private fun finishWorkout(isCompleted: Boolean) {
        timerJob?.cancel()
        stopSensors()
        
        // 🔊 SONIDO: Fin de sesión
        soundPlayer.playSound(Sound.FINISH)

        // Calcular rondas completadas
        val completedRounds = if (isCompleted) {
            _workoutState.value.totalRounds
        } else {
            when (_workoutState.value.phase) {
                WorkoutPhase.REST -> _workoutState.value.currentRound
                WorkoutPhase.WORK -> (_workoutState.value.currentRound - 1).coerceAtLeast(0)
                else -> _workoutState.value.currentRound
            }
        }

        // Guardar puntos GPS restantes en buffer
        flushGpsBuffer()

        serviceScope.launch {
            saveFinalStats(isCompleted, completedRounds)
        }

        // Obtener stats GPS finales
        val gpsStats = if (isGpsEnabled) gpsManager.getCurrentStats() else null

        _sessionStats.update {
            it.copy(
                totalTimeSeconds = _workoutState.value.totalElapsedSeconds,
                workTimeSeconds = workTimeAccumulated,
                restTimeSeconds = restTimeAccumulated,
                warmupTimeSeconds = warmupTimeAccumulated,
                completedRounds = completedRounds,
                totalRounds = _workoutState.value.totalRounds,
                wasCompleted = isCompleted,
                // GPS Stats
                totalDistanceMeters = gpsStats?.totalDistanceMeters ?: 0f,
                avgSpeedKmh = gpsStats?.avgSpeedKmh ?: 0f,
                maxSpeedKmh = gpsStats?.maxSpeedKmh ?: 0f,
                gpsPointsCount = gpsStats?.pointsCount ?: 0
            )
        }

        _workoutState.update { it.copy(phase = WorkoutPhase.FINISHED, isRunning = false) }
        updateNotification("Entrenamiento finalizado", "¡Buen trabajo!")
    }

    // ============================================================================
    // TIMER Y TRANSICIONES DE FASE
    // ============================================================================

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && _workoutState.value.isRunning && !_workoutState.value.isPaused) {
                delay(1000)
                tick()
            }
        }
    }

    private fun tick() {
        val state = _workoutState.value
        
        // Acumular tiempo según la fase actual
        when (state.phase) {
            WorkoutPhase.WARMUP -> warmupTimeAccumulated++
            WorkoutPhase.WORK -> workTimeAccumulated++
            WorkoutPhase.REST -> restTimeAccumulated++
            else -> { }
        }
        
        if (state.remainingSeconds <= 0) {
            transitionPhase()
        } else {
            val newRemainingSeconds = state.remainingSeconds - 1
            _workoutState.update {
                it.copy(
                    remainingSeconds = newRemainingSeconds,
                    totalElapsedSeconds = it.totalElapsedSeconds + 1,
                    workSecondsElapsed = workTimeAccumulated,
                    restSecondsElapsed = restTimeAccumulated,
                    warmupSecondsElapsed = warmupTimeAccumulated
                )
            }
            
            // 🔊 SONIDO: Cuenta atrás en los últimos 3 segundos
            when (newRemainingSeconds) {
                3, 2, 1 -> {
                    soundPlayer.playSound(Sound.COUNTDOWN_BEEP)
                    vibrate(150)
                }
            }
            
            // 🔊 SONIDO: Beep a mitad del WORK (opcional, para ritmo)
            if (state.phase == WorkoutPhase.WORK) {
                val halfTime = currentConfig.workSeconds / 2
                if (newRemainingSeconds == halfTime && currentConfig.workSeconds >= 20) {
                    soundPlayer.playSound(Sound.BEEP)
                }
            }
        }
        
        updateStats()
        saveSensorReading()
    }

    private fun transitionPhase() {
        vibrate(400)
        val state = _workoutState.value

        when (state.phase) {
            WorkoutPhase.WARMUP -> {
                // 🔊 SONIDO: Inicio de WORK 1 (pistol - disparo de salida)
                soundPlayer.playSound(Sound.PISTOL)
                
                _workoutState.update {
                    it.copy(
                        phase = WorkoutPhase.WORK,
                        currentRound = 1,
                        remainingSeconds = currentConfig.workSeconds
                    )
                }
                updateNotification("¡TRABAJO!", "Ronda 1/${state.totalRounds}")
            }
            
            WorkoutPhase.WORK -> {
                // ⚠️ IMPORTANTE: Si es la última ronda, terminar SIN REST
                if (state.currentRound >= state.totalRounds) {
                    // Última ronda completada - TERMINAR
                    finishWorkout(isCompleted = true)
                } else {
                    // No es la última ronda - ir a REST
                    // 🔊 SONIDO: Inicio de REST (stop_rest)
                    soundPlayer.playSound(Sound.STOP_REST)
                    
                    _workoutState.update {
                        it.copy(
                            phase = WorkoutPhase.REST,
                            remainingSeconds = currentConfig.restSeconds
                        )
                    }
                    updateNotification("Descanso", "Ronda ${state.currentRound}/${state.totalRounds}")
                }
            }
            
            WorkoutPhase.REST -> {
                // Fin del descanso - siguiente ronda de WORK
                // 🔊 SONIDO: Inicio de WORK 2, 3, 4... (lets_go - motivación)
                soundPlayer.playSound(Sound.LETS_GO)
                
                _workoutState.update {
                    it.copy(
                        phase = WorkoutPhase.WORK,
                        currentRound = state.currentRound + 1,
                        remainingSeconds = currentConfig.workSeconds
                    )
                }
                updateNotification("¡TRABAJO!", "Ronda ${state.currentRound + 1}/${state.totalRounds}")
            }
            
            else -> { /* No-op */ }
        }
    }

    // ============================================================================
    // SENSORES - INICIO Y PARADA
    // ============================================================================

    private fun startSensors() {
        // Iniciar HR primero (prioridad)
        hrCollectorJob = serviceScope.launch {
            sensorManager.observeHeartRate().collect { reading ->
                when (reading) {
                    is HeartRateReading.Scanning -> {
                        Log.d(TAG, "Buscando sensor HR...")
                        _sensorState.update { 
                            it.copy(
                                isHrScanning = true,
                                isHrConnected = false,
                                hrErrorMessage = "Buscando..."
                            ) 
                        }
                    }
                    is HeartRateReading.Connecting -> {
                        Log.d(TAG, "Conectando a sensor HR...")
                        _sensorState.update { 
                            it.copy(
                                isHrScanning = true,
                                hrErrorMessage = "Conectando..."
                            ) 
                        }
                    }
                    is HeartRateReading.Value -> {
                        addHrReading(reading.bpm)
                        _sensorState.update { 
                            it.copy(heartRate = reading.bpm, hrErrorMessage = null) 
                        }
                        updateStats()
                    }
                    is HeartRateReading.Connected -> {
                        Log.d(TAG, "Conectado a HR: ${reading.deviceName}")
                        _sensorState.update { 
                            it.copy(
                                isHrConnected = true, 
                                isHrScanning = false,
                                hrDeviceName = reading.deviceName,
                                hrErrorMessage = null
                            ) 
                        }
                    }
                    is HeartRateReading.Disconnected -> {
                        Log.d(TAG, "HR desconectado")
                        _sensorState.update { 
                            it.copy(
                                isHrConnected = false,
                                isHrScanning = false,
                                heartRate = 0
                            ) 
                        }
                    }
                    is HeartRateReading.Error -> {
                        Log.e(TAG, "Error HR: ${reading.message}")
                        _sensorState.update { 
                            it.copy(hrErrorMessage = reading.message) 
                        }
                    }
                    else -> {
                        // Caso exhaustivo
                    }
                }
            }
        }

        // Iniciar HR2 con pequeño delay después de HR1
        hr2CollectorJob = serviceScope.launch {
            delay(1000) // Esperar 1 segundo después de iniciar HR1
            
            sensorManager.observeHeartRate2().collect { reading ->
                when (reading) {
                    is HeartRateReading.Scanning -> {
                        Log.d(TAG, "Buscando sensor HR2...")
                        _sensorState.update { 
                            it.copy(
                                isHr2Scanning = true,
                                isHr2Connected = false,
                                hr2ErrorMessage = "Buscando..."
                            ) 
                        }
                    }
                    is HeartRateReading.Connecting -> {
                        Log.d(TAG, "Conectando a sensor HR2...")
                        _sensorState.update { 
                            it.copy(
                                isHr2Scanning = true,
                                hr2ErrorMessage = "Conectando..."
                            ) 
                        }
                    }
                    is HeartRateReading.Value -> {
                        addHr2Reading(reading.bpm)
                        _sensorState.update { 
                            it.copy(heartRate2 = reading.bpm, hr2ErrorMessage = null) 
                        }
                        updateStats()
                    }
                    is HeartRateReading.Connected -> {
                        Log.d(TAG, "Conectado a HR2: ${reading.deviceName}")
                        _sensorState.update { 
                            it.copy(
                                isHr2Connected = true,
                                isHr2Scanning = false,
                                hr2DeviceName = reading.deviceName,
                                hr2ErrorMessage = null
                            ) 
                        }
                    }
                    is HeartRateReading.Disconnected -> {
                        Log.d(TAG, "HR2 desconectado")
                        _sensorState.update { 
                            it.copy(
                                isHr2Connected = false,
                                isHr2Scanning = false,
                                heartRate2 = 0
                            ) 
                        }
                    }
                    is HeartRateReading.Error -> {
                        Log.e(TAG, "Error HR2: ${reading.message}")
                        _sensorState.update { 
                            it.copy(hr2ErrorMessage = reading.message) 
                        }
                    }
                    else -> {
                        // Caso exhaustivo
                    }
                }
            }
        }

        // Iniciar cadencia con pequeño delay para evitar conflictos BLE
        cadenceCollectorJob = serviceScope.launch {
            delay(2000) // Esperar 2 segundos después de iniciar HR
            
            sensorManager.observeCadence().collect { reading ->
                when (reading) {
                    is CadenceReading.Scanning -> {
                        Log.d(TAG, "Buscando sensor de cadencia...")
                        _sensorState.update { 
                            it.copy(
                                isCadenceScanning = true,
                                isCadenceConnected = false,
                                cadenceErrorMessage = "Buscando..."
                            ) 
                        }
                    }
                    is CadenceReading.Connecting -> {
                        Log.d(TAG, "Conectando a sensor de cadencia...")
                        _sensorState.update { 
                            it.copy(
                                isCadenceScanning = true,
                                cadenceErrorMessage = "Conectando..."
                            ) 
                        }
                    }
                    is CadenceReading.Value -> {
                        addCadenceReading(reading.rpm.toFloat())
                        _sensorState.update { 
                            it.copy(cadence = reading.rpm.toFloat(), cadenceErrorMessage = null) 
                        }
                        updateStats()
                    }
                    is CadenceReading.Connected -> {
                        Log.d(TAG, "Conectado a cadencia: ${reading.deviceName}")
                        _sensorState.update { 
                            it.copy(
                                isCadenceConnected = true, 
                                isCadenceScanning = false,
                                cadenceDeviceName = reading.deviceName,
                                cadenceErrorMessage = null
                            ) 
                        }
                    }
                    is CadenceReading.Disconnected -> {
                        Log.d(TAG, "Cadencia desconectado")
                        _sensorState.update { 
                            it.copy(
                                isCadenceConnected = false,
                                isCadenceScanning = false,
                                cadence = 0f
                            ) 
                        }
                    }
                    is CadenceReading.Error -> {
                        Log.e(TAG, "Error cadencia: ${reading.message}")
                        _sensorState.update { 
                            it.copy(cadenceErrorMessage = reading.message) 
                        }
                    }
                    else -> {
                        // Caso exhaustivo
                    }
                }
            }
        }

        // Iniciar segunda cadencia con delay para evitar conflictos
        cadence2CollectorJob = serviceScope.launch {
            delay(4000) // Esperar 4 segundos después de iniciar CAD1
            
            sensorManager.observeCadence2().collect { reading ->
                when (reading) {
                    is CadenceReading.Scanning -> {
                        Log.d(TAG, "Buscando sensor de cadencia2...")
                        _sensorState.update { 
                            it.copy(
                                isCadence2Scanning = true,
                                isCadence2Connected = false,
                                cadence2ErrorMessage = "Buscando..."
                            ) 
                        }
                    }
                    is CadenceReading.Connecting -> {
                        Log.d(TAG, "Conectando a sensor de cadencia2...")
                        _sensorState.update { 
                            it.copy(
                                isCadence2Scanning = true,
                                cadence2ErrorMessage = "Conectando..."
                            ) 
                        }
                    }
                    is CadenceReading.Value -> {
                        addCadence2Reading(reading.rpm.toFloat())
                        _sensorState.update { 
                            it.copy(cadence2 = reading.rpm.toFloat(), cadence2ErrorMessage = null) 
                        }
                        updateStats()
                    }
                    is CadenceReading.Connected -> {
                        Log.d(TAG, "Conectado a cadencia2: ${reading.deviceName}")
                        _sensorState.update { 
                            it.copy(
                                isCadence2Connected = true, 
                                isCadence2Scanning = false,
                                cadence2DeviceName = reading.deviceName,
                                cadence2ErrorMessage = null
                            ) 
                        }
                    }
                    is CadenceReading.Disconnected -> {
                        Log.d(TAG, "Cadencia2 desconectado")
                        _sensorState.update { 
                            it.copy(
                                isCadence2Connected = false,
                                isCadence2Scanning = false,
                                cadence2 = 0f
                            ) 
                        }
                    }
                    is CadenceReading.Error -> {
                        Log.e(TAG, "Error cadencia2: ${reading.message}")
                        _sensorState.update { 
                            it.copy(cadence2ErrorMessage = reading.message) 
                        }
                    }
                    else -> {
                        // Caso exhaustivo
                    }
                }
            }
        }

        // ============================================================================
        // GPS - INICIAR SI ESTÁ HABILITADO
        // ============================================================================
        if (isGpsEnabled) {
            startGpsTracking()
        }
    }

    /**
     * Inicia el tracking GPS
     */
    private fun startGpsTracking() {
        if (!gpsManager.hasLocationPermission()) {
            Log.w(TAG, "GPS: Sin permisos de ubicación")
            _sensorState.update { 
                it.copy(gpsAccuracy = null) 
            }
            return
        }

        if (!gpsManager.isGpsEnabled()) {
            Log.w(TAG, "GPS: Deshabilitado en el dispositivo")
            return
        }

        gpsCollectorJob = serviceScope.launch {
            Log.d(TAG, "🛰️ Iniciando GPS tracking...")
            _sensorState.update { it.copy(isGpsTracking = true) }

            gpsManager.gpsFlow().collect { reading ->
                Log.d(TAG, "📍 GPS: ${reading.latitude}, ${reading.longitude}, vel=${reading.speed}")

                // Actualizar estado del sensor
                val gpsStats = gpsManager.getCurrentStats()
                _sensorState.update {
                    it.copy(
                        isGpsTracking = true,
                        gpsAccuracy = reading.accuracy,
                        currentSpeedKmh = gpsStats.currentSpeedKmh,
                        totalDistanceKm = gpsStats.totalDistanceMeters / 1000f
                    )
                }

                // Guardar punto GPS
                saveGpsPoint(reading)

                // Actualizar stats de sesión
                _sessionStats.update {
                    it.copy(
                        totalDistanceMeters = gpsStats.totalDistanceMeters,
                        avgSpeedKmh = gpsStats.avgSpeedKmh,
                        maxSpeedKmh = gpsStats.maxSpeedKmh,
                        gpsPointsCount = gpsStats.pointsCount
                    )
                }
            }
        }
    }

    /**
     * Guarda un punto GPS en el buffer y lo persiste cuando el buffer está lleno
     */
    private fun saveGpsPoint(reading: GpsReading) {
        val sessionId = currentSessionId ?: return
        if (!_workoutState.value.isRunning || _workoutState.value.isPaused) return

        val point = GpsPointEntity(
            sessionId = sessionId,
            timestamp = reading.timestamp,
            latitude = reading.latitude,
            longitude = reading.longitude,
            altitude = reading.altitude,
            speed = reading.speed,
            accuracy = reading.accuracy,
            phase = _workoutState.value.phase.name,
            round = _workoutState.value.currentRound
        )

        gpsPointsBuffer.add(point)

        // Guardar en BD cuando el buffer está lleno
        if (gpsPointsBuffer.size >= GPS_BUFFER_SIZE) {
            flushGpsBuffer()
        }
    }

    /**
     * Vacía el buffer de puntos GPS guardándolos en la base de datos
     */
    private fun flushGpsBuffer() {
        if (gpsPointsBuffer.isEmpty()) return

        val pointsToSave = gpsPointsBuffer.toList()
        gpsPointsBuffer.clear()

        serviceScope.launch {
            try {
                gpsDao.insertPoints(pointsToSave)
                Log.d(TAG, "💾 Guardados ${pointsToSave.size} puntos GPS")
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando puntos GPS: ${e.message}")
            }
        }
    }

    private fun stopSensors() {
        hrCollectorJob?.cancel()
        hr2CollectorJob?.cancel()
        cadenceCollectorJob?.cancel()
        cadence2CollectorJob?.cancel()
        gpsCollectorJob?.cancel()
        
        _sensorState.update { 
            it.copy(isGpsTracking = false) 
        }
    }

    // ============================================================================
    // PERSISTENCIA DE DATOS
    // ============================================================================

    private suspend fun saveFinalStats(isCompleted: Boolean, completedRounds: Int) {
        currentSessionId?.let { sessionId ->
            val avgHr = if (hrReadings.isNotEmpty()) hrReadings.average().toInt() else null
            val maxHr = hrReadings.maxOrNull()
            val minHr = hrReadings.filter { it > 0 }.minOrNull()
            val avgHr2 = if (hr2Readings.isNotEmpty()) hr2Readings.average().toInt() else null
            val maxHr2 = hr2Readings.maxOrNull()
            val minHr2 = hr2Readings.filter { it > 0 }.minOrNull()
            val avgCad = if (cadenceReadings.isNotEmpty()) cadenceReadings.average().toFloat() else null
            val maxCad = cadenceReadings.maxOrNull()
            
            // GPS Stats
            val gpsStats = if (isGpsEnabled) gpsManager.getCurrentStats() else null
            
            sessionDao.getSessionById(sessionId)?.let { session ->
                sessionDao.updateSession(
                    session.copy(
                        endTime = System.currentTimeMillis(),
                        completedRounds = completedRounds,
                        avgHeartRate = avgHr,
                        maxHeartRate = maxHr,
                        minHeartRate = minHr,
                        avgHeartRate2 = avgHr2,
                        maxHeartRate2 = maxHr2,
                        minHeartRate2 = minHr2,
                        avgCadence = avgCad,
                        maxCadence = maxCad,
                        totalTimeSeconds = _workoutState.value.totalElapsedSeconds,
                        workTimeSeconds = workTimeAccumulated,
                        restTimeSeconds = restTimeAccumulated,
                        warmupTimeSeconds = warmupTimeAccumulated,
                        isCompleted = isCompleted,
                        // GPS
                        totalDistanceMeters = gpsStats?.totalDistanceMeters,
                        avgSpeedKmh = gpsStats?.avgSpeedKmh,
                        maxSpeedKmh = gpsStats?.maxSpeedKmh
                    )
                )
            }
        }
    }

    private fun saveSensorReading() {
        val sessionId = currentSessionId ?: return
        if (!_workoutState.value.isRunning || _workoutState.value.isPaused) return
        
        serviceScope.launch {
            val reading = SensorReadingEntity(
                sessionId = sessionId,
                timestamp = System.currentTimeMillis(),
                heartRate = _sensorState.value.heartRate.takeIf { it > 0 },
                heartRate2 = _sensorState.value.heartRate2.takeIf { it > 0 },
                cadence = _sensorState.value.cadence.takeIf { it > 0 },
                phase = _workoutState.value.phase.name,
                round = _workoutState.value.currentRound
            )
            sensorReadingDao.insertReading(reading)
        }
    }

    private fun updateStats() {
        // Hacer copias snapshot de las listas para evitar ConcurrentModificationException
        // cuando se modifican desde múltiples coroutines
        val hrReadingsSnapshot: List<Int>
        val hr2ReadingsSnapshot: List<Int>
        val cadenceReadingsSnapshot: List<Float>
        val cadence2ReadingsSnapshot: List<Float>
        
        synchronized(hrReadingsLock) {
        // Limitar tamaño de listas para evitar problemas de memoria
            while (hrReadings.size > MAX_SENSOR_READINGS) {
            hrReadings.removeAt(0)
        }
            hrReadingsSnapshot = hrReadings.toList()
        }
        
        synchronized(hr2ReadingsLock) {
            while (hr2Readings.size > MAX_SENSOR_READINGS) {
                hr2Readings.removeAt(0)
            }
            hr2ReadingsSnapshot = hr2Readings.toList()
        }
        
        synchronized(cadenceReadingsLock) {
            while (cadenceReadings.size > MAX_SENSOR_READINGS) {
            cadenceReadings.removeAt(0)
        }
            cadenceReadingsSnapshot = cadenceReadings.toList()
        }
        
        synchronized(cadence2ReadingsLock) {
            while (cadence2Readings.size > MAX_SENSOR_READINGS) {
                cadence2Readings.removeAt(0)
            }
            cadence2ReadingsSnapshot = cadence2Readings.toList()
        }
        
        // Procesar las copias snapshot (thread-safe)
        val avgHr = if (hrReadingsSnapshot.isNotEmpty()) hrReadingsSnapshot.average().toInt() else 0
        val maxHr = hrReadingsSnapshot.maxOrNull() ?: 0
        val minHr = hrReadingsSnapshot.filter { it > 0 }.minOrNull() ?: 0
        val avgHr2 = if (hr2ReadingsSnapshot.isNotEmpty()) hr2ReadingsSnapshot.average().toInt() else 0
        val maxHr2 = hr2ReadingsSnapshot.maxOrNull() ?: 0
        val minHr2 = hr2ReadingsSnapshot.filter { it > 0 }.minOrNull() ?: 0
        val avgCad = if (cadenceReadingsSnapshot.isNotEmpty()) cadenceReadingsSnapshot.average().toFloat() else 0f
        val maxCad = cadenceReadingsSnapshot.maxOrNull() ?: 0f
        val avgCad2 = if (cadence2ReadingsSnapshot.isNotEmpty()) cadence2ReadingsSnapshot.average().toFloat() else 0f
        val maxCad2 = cadence2ReadingsSnapshot.maxOrNull() ?: 0f

        // GPS Stats
        val gpsStats = if (isGpsEnabled) gpsManager.getCurrentStats() else null

        _sessionStats.update {
            it.copy(
                sessionId = currentSessionId,
                avgHeartRate = avgHr,
                maxHeartRate = maxHr,
                minHeartRate = minHr,
                avgHeartRate2 = avgHr2,
                maxHeartRate2 = maxHr2,
                minHeartRate2 = minHr2,
                avgCadence = avgCad,
                maxCadence = maxCad,
                avgCadence2 = avgCad2,
                maxCadence2 = maxCad2,
                hrReadings = hrReadingsSnapshot,
                hr2Readings = hr2ReadingsSnapshot,
                cadenceReadings = cadenceReadingsSnapshot,
                cadence2Readings = cadence2ReadingsSnapshot,
                totalTimeSeconds = _workoutState.value.totalElapsedSeconds,
                workTimeSeconds = workTimeAccumulated,
                restTimeSeconds = restTimeAccumulated,
                warmupTimeSeconds = warmupTimeAccumulated,
                completedRounds = _workoutState.value.currentRound,
                totalRounds = _workoutState.value.totalRounds,
                // GPS
                totalDistanceMeters = gpsStats?.totalDistanceMeters ?: 0f,
                avgSpeedKmh = gpsStats?.avgSpeedKmh ?: 0f,
                maxSpeedKmh = gpsStats?.maxSpeedKmh ?: 0f,
                gpsPointsCount = gpsStats?.pointsCount ?: 0
            )
        }
    }

    // ============================================================================
    // VIBRACIÓN Y NOTIFICACIONES
    // ============================================================================

    private fun vibrate(durationMs: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Entrenamiento", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notificaciones del entrenamiento en curso"
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play) 
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(title, text))
    }
}
