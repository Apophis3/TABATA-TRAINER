package com.tuapp.tabatatrainer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class WorkoutPhase(val displayName: String) {
    IDLE("Preparado"),
    WARMUP("Calentamiento"),
    WORK("Trabajo"),
    REST("Descanso"),
    FINISHED("Finalizado")
}

// Tipo de actividad
enum class ActivityType(val displayName: String) {
    TABATA("Tabata/HIIT"),
    FREE_RIDE("Ruta Libre"),
    HR_ZONE("Zonas HR"),
    STOPWATCH("Cronómetro")
}

data class WorkoutConfig(
    val warmupSeconds: Int = 10,
    val workSeconds: Int = 20,
    val restSeconds: Int = 10,
    val rounds: Int = 8,
    val gpsEnabled: Boolean = false,
    val activityType: ActivityType = ActivityType.TABATA
) {
    val totalPlannedWorkSeconds: Int get() = workSeconds * rounds
    val totalPlannedSeconds: Int get() = warmupSeconds + (workSeconds + restSeconds) * rounds
}

data class WorkoutState(
    val phase: WorkoutPhase = WorkoutPhase.IDLE,
    val currentRound: Int = 0,
    val totalRounds: Int = 8,
    val remainingSeconds: Int = 0,
    val totalElapsedSeconds: Int = 0,
    val workSecondsElapsed: Int = 0,
    val restSecondsElapsed: Int = 0,
    val warmupSecondsElapsed: Int = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false
)

data class SensorState(
    val isHrConnected: Boolean = false,
    val isHrScanning: Boolean = false,
    val isHr2Connected: Boolean = false,
    val isHr2Scanning: Boolean = false,
    val isCadenceConnected: Boolean = false,
    val isCadenceScanning: Boolean = false,
    val isCadence2Connected: Boolean = false,
    val isCadence2Scanning: Boolean = false,
    val hrDeviceName: String? = null,
    val hr2DeviceName: String? = null,
    val cadenceDeviceName: String? = null,
    val cadence2DeviceName: String? = null,
    // Protocolo por el que llega cada sensor ("ANT+" / "BLE"), para mostrarlo en pantalla
    val hrProtocol: String? = null,
    val hr2Protocol: String? = null,
    val cadenceProtocol: String? = null,
    val cadence2Protocol: String? = null,
    val heartRate: Int = 0,
    val heartRate2: Int = 0,
    val cadence: Float = 0f,
    val cadence2: Float = 0f,
    val hrErrorMessage: String? = null,
    val hr2ErrorMessage: String? = null,
    val cadenceErrorMessage: String? = null,
    val cadence2ErrorMessage: String? = null,
    // GPS
    val isGpsEnabled: Boolean = false,
    val isGpsTracking: Boolean = false,
    val gpsAccuracy: Float? = null,
    val currentSpeedKmh: Float = 0f,
    val totalDistanceKm: Float = 0f,
    val currentAltitude: Float? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

data class SessionStats(
    val sessionId: String? = null,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val minHeartRate: Int = 0,
    val avgHeartRate2: Int = 0,
    val maxHeartRate2: Int = 0,
    val minHeartRate2: Int = 0,
    val avgCadence: Float = 0f,
    val maxCadence: Float = 0f,
    val avgCadence2: Float = 0f,
    val maxCadence2: Float = 0f,
    val hrReadings: List<Int> = emptyList(),
    val hr2Readings: List<Int> = emptyList(),
    val cadenceReadings: List<Float> = emptyList(),
    val cadence2Readings: List<Float> = emptyList(),
    // Tiempos
    val totalTimeSeconds: Int = 0,
    val workTimeSeconds: Int = 0,
    val restTimeSeconds: Int = 0,
    val warmupTimeSeconds: Int = 0,
    val completedRounds: Int = 0,
    val totalRounds: Int = 0,
    val wasCompleted: Boolean = false,
    // GPS
    val totalDistanceMeters: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val gpsPointsCount: Int = 0,
    val elevationGain: Float = 0f,
    val poiCount: Int = 0
)

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    var warmupSeconds: Int,
    var workSeconds: Int,
    var restSeconds: Int,
    var totalRounds: Int,
    var completedRounds: Int = 0,
    var avgHeartRate: Int? = null,
    var maxHeartRate: Int? = null,
    var minHeartRate: Int? = null,
    var avgHeartRate2: Int? = null,
    var maxHeartRate2: Int? = null,
    var minHeartRate2: Int? = null,
    var avgCadence: Float? = null,
    var maxCadence: Float? = null,
    // Tiempos
    var totalTimeSeconds: Int = 0,
    var workTimeSeconds: Int = 0,
    var restTimeSeconds: Int = 0,
    var warmupTimeSeconds: Int = 0,
    var notes: String? = null,
    var rating: Int = 0,
    var isCompleted: Boolean = false,
    // GPS
    var totalDistanceMeters: Float? = null,
    var avgSpeedKmh: Float? = null,
    var maxSpeedKmh: Float? = null,
    var elevationGain: Float? = null,
    var gpsEnabled: Boolean = false,
    // Tipo de actividad
    var activityType: String = "TABATA"
)

@Entity(tableName = "sensor_readings")
data class SensorReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: String,
    val timestamp: Long,
    val heartRate: Int?,
    val heartRate2: Int?,
    val cadence: Float?,
    val phase: String,
    val round: Int
)

// ============================================================================
// NUEVA ENTIDAD: Puntos de Interés (POI)
// ============================================================================

@Entity(tableName = "poi_points")
data class PoiEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val name: String? = null,           // Nombre opcional del POI
    val description: String? = null,    // Descripción opcional
    val phase: String,                  // Fase en la que se marcó
    val round: Int,                     // Ronda en la que se marcó
    val heartRate: Int?,                // HR en ese momento
    val speedKmh: Float?,               // Velocidad en ese momento
    val distanceMeters: Float?,         // Distancia acumulada en ese momento
    val elapsedSeconds: Int             // Tiempo transcurrido
)

// ============================================================================
// NUEVA ENTIDAD: Vueltas/Laps
// ============================================================================

@Entity(tableName = "lap_records")
data class LapEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: String,
    val lapNumber: Int,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val startDistanceMeters: Float,
    val endDistanceMeters: Float,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val avgCadence: Float?,
    val elevationGain: Float?
)

// ============================================================================
// ENUMS PARA PERFILES DE DISPOSITIVOS
// ============================================================================

enum class SensorType {
    HEART_RATE,
    CADENCE
}

enum class ProtocolType {
    BLE,
    ANT_PLUS
}

// ============================================================================
// ENTIDAD: Perfiles de Dispositivos
// ============================================================================

@Entity(tableName = "device_profiles")
data class DeviceProfileEntity(
    @PrimaryKey val deviceId: String,  // MAC address BLE o Device Number ANT+
    val sensorType: String,            // Nombre del enum SensorType
    val protocolType: String,           // Nombre del enum ProtocolType
    val deviceName: String,
    val macAddress: String? = null,    // Para BLE
    val antDeviceNumber: Int? = null,  // Para ANT+
    val alias: String? = null,         // Alias personalizado del usuario
    val lastConnected: Long = System.currentTimeMillis(),
    val connectionCount: Int = 0,
    val isFavorite: Boolean = false
)
