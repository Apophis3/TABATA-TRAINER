package com.tuapp.tabatatrainer.service

import com.tuapp.tabatatrainer.data.local.GpsPointEntity
import com.tuapp.tabatatrainer.sensor.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber

/**
 * Helper compartido para el tracking de sensores
 * Extrae la lógica común entre WorkoutService y FreeRideService
 */
class SensorTrackingHelper(
    private val sensorManager: SensorManager,
    private val gpsManager: GpsManager,
    private val scope: CoroutineScope
) {
    
    // Jobs para cancelar cuando sea necesario
    var hrCollectorJob: Job? = null
    var cadenceCollectorJob: Job? = null
    var gpsCollectorJob: Job? = null

    /**
     * Inicia el tracking de frecuencia cardíaca
     * @param onReading Callback cuando se recibe una lectura
     * @param onStateChange Callback cuando cambia el estado de conexión
     */
    fun startHeartRateTracking(
        onReading: (Int) -> Unit = {},
        onStateChange: (isConnected: Boolean, deviceName: String?, error: String?) -> Unit = { _, _, _ -> }
    ) {
        hrCollectorJob?.cancel()
        hrCollectorJob = scope.launch {
            sensorManager.observeHeartRate().collect { reading ->
                when (reading) {
                    is HeartRateReading.Scanning -> {
                        Timber.d("Buscando sensor HR...")
                        onStateChange(false, null, "Buscando...")
                    }
                    is HeartRateReading.Connecting -> {
                        Timber.d("Conectando a sensor HR...")
                        onStateChange(false, null, "Conectando...")
                    }
                    is HeartRateReading.Value -> {
                        onReading(reading.bpm)
                    }
                    is HeartRateReading.Connected -> {
                        Timber.d("Conectado a HR: ${reading.deviceName}")
                        onStateChange(true, reading.deviceName, null)
                    }
                    is HeartRateReading.Disconnected -> {
                        Timber.d("HR desconectado")
                        onStateChange(false, null, null)
                    }
                    is HeartRateReading.Error -> {
                        Timber.e("Error HR: ${reading.message}")
                        onStateChange(false, null, reading.message)
                    }
                    else -> {
                        // Caso exhaustivo
                    }
                }
            }
        }
    }

    /**
     * Inicia el tracking de cadencia
     * @param onReading Callback cuando se recibe una lectura
     * @param onStateChange Callback cuando cambia el estado de conexión
     */
    fun startCadenceTracking(
        delayMs: Long = 2000,
        onReading: (Float) -> Unit = {},
        onStateChange: (isConnected: Boolean, deviceName: String?, error: String?) -> Unit = { _, _, _ -> }
    ) {
        cadenceCollectorJob?.cancel()
        cadenceCollectorJob = scope.launch {
            delay(delayMs) // Delay para evitar conflictos BLE
            
            sensorManager.observeCadence().collect { reading ->
                when (reading) {
                    is CadenceReading.Scanning -> {
                        Timber.d("Buscando sensor de cadencia...")
                        onStateChange(false, null, "Buscando...")
                    }
                    is CadenceReading.Connecting -> {
                        Timber.d("Conectando a sensor de cadencia...")
                        onStateChange(false, null, "Conectando...")
                    }
                    is CadenceReading.Value -> {
                        onReading(reading.rpm.toFloat())
                    }
                    is CadenceReading.Connected -> {
                        Timber.d("Conectado a cadencia: ${reading.deviceName}")
                        onStateChange(true, reading.deviceName, null)
                    }
                    is CadenceReading.Disconnected -> {
                        Timber.d("Cadencia desconectado")
                        onStateChange(false, null, null)
                    }
                    is CadenceReading.Error -> {
                        Timber.e("Error cadencia: ${reading.message}")
                        onStateChange(false, null, reading.message)
                    }
                    else -> {
                        // Caso exhaustivo
                    }
                }
            }
        }
    }

    /**
     * Inicia el tracking GPS
     * @param onLocation Callback cuando se recibe una lectura GPS
     * @param onError Callback cuando hay un error
     */
    fun startGpsTracking(
        onLocation: (GpsReading) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!gpsManager.hasLocationPermission()) {
            Timber.w("GPS: Sin permisos de ubicación")
            onError("Sin permisos de ubicación")
            return
        }

        if (!gpsManager.isGpsEnabled()) {
            Timber.w("GPS: Deshabilitado en el dispositivo")
            onError("GPS deshabilitado")
            return
        }

        gpsCollectorJob?.cancel()
        gpsCollectorJob = scope.launch {
            Timber.d("🛰️ Iniciando GPS tracking...")
            
            gpsManager.gpsFlow().collect { reading ->
                onLocation(reading)
            }
        }
    }

    /**
     * Detiene todo el tracking de sensores
     */
    fun stopAllTracking() {
        hrCollectorJob?.cancel()
        cadenceCollectorJob?.cancel()
        gpsCollectorJob?.cancel()
        hrCollectorJob = null
        cadenceCollectorJob = null
        gpsCollectorJob = null
    }

    /**
     * Crea un GpsPointEntity desde una Location
     */
    fun createGpsPointEntity(
        sessionId: String,
        location: android.location.Location,
        phase: String = "WORK",
        round: Int = 0
    ): GpsPointEntity {
        return GpsPointEntity(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude.takeIf { it != 0.0 },
            speed = location.speed.takeIf { it > 0 },
            accuracy = location.accuracy.takeIf { it > 0 },
            phase = phase,
            round = round
        )
    }

    /**
     * Crea un GpsPointEntity desde un GpsReading
     */
    fun createGpsPointEntityFromReading(
        sessionId: String,
        reading: GpsReading,
        phase: String = "WORK",
        round: Int = 0
    ): GpsPointEntity {
        return GpsPointEntity(
            sessionId = sessionId,
            timestamp = reading.timestamp,
            latitude = reading.latitude,
            longitude = reading.longitude,
            altitude = reading.altitude?.takeIf { it != 0.0 },
            speed = reading.speed?.takeIf { it > 0 },
            accuracy = reading.accuracy?.takeIf { it > 0 },
            phase = phase,
            round = round
        )
    }
}
