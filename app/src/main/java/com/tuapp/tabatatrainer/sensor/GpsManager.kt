package com.tuapp.tabatatrainer.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// Modelos de datos GPS
// ============================================================================

data class GpsReading(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val speed: Float?,              // m/s
    val accuracy: Float?,
    val timestamp: Long = System.currentTimeMillis()
)

data class GpsStats(
    val totalDistanceMeters: Float = 0f,
    val currentSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val currentAltitude: Double? = null,
    val isTracking: Boolean = false,
    val pointsCount: Int = 0,
    val elevationGain: Float = 0f
)

sealed class GpsStatus {
    object Disabled : GpsStatus()
    object NoPermission : GpsStatus()
    object Searching : GpsStatus()
    data class Active(val accuracy: Float) : GpsStatus()
    data class Error(val message: String) : GpsStatus()
}

// ============================================================================
// GPS Manager - ACTUALIZADO
// ============================================================================

@Singleton
class GpsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GpsManager"
        private const val UPDATE_INTERVAL = 1000L
        private const val FASTEST_INTERVAL = 500L
        private const val SMALLEST_DISPLACEMENT = 1f
        
        // ⚡ Filtros profesionales para eliminar GPS drift
        private const val ACCURACY_THRESHOLD_METERS = 15f  // Precisión máxima aceptable
        private const val SNT_THRESHOLD_MS = 0.7f  // Static Navigation Threshold: 2.5 km/h = 0.7 m/s
        private const val MAX_DISTANCE_JUMP_METERS = 100f  // Evitar saltos GPS anómalos
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var lastLocation: Location? = null
    private var lastGpsReading: GpsReading? = null  // ← NUEVO: Para POI
    private var lastValidLocation: Location? = null  // ⚡ Última posición válida (cuando está parado)
    private var totalDistance: Float = 0f
    private var maxSpeed: Float = 0f
    private var pointsCount: Int = 0
    private var filteredPointsCount: Int = 0  // ⚡ Contador de puntos filtrados

    // Para cálculo de desnivel
    private var previousAltitude: Double? = null
    private var elevationGain: Float = 0f

    // Tiempo en movimiento (excluye pausas) para la velocidad media = distancia / tiempo
    @Volatile private var isPaused = false
    private var activeStartMs: Long = 0L
    private var accumulatedActiveMs: Long = 0L

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isGpsEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
    }

    fun resetStats() {
        lastLocation = null
        lastGpsReading = null
        lastValidLocation = null
        totalDistance = 0f
        maxSpeed = 0f
        pointsCount = 0
        filteredPointsCount = 0
        previousAltitude = null
        elevationGain = 0f
        isPaused = false
        accumulatedActiveMs = 0L
        activeStartMs = System.currentTimeMillis()
        Log.d(TAG, "Stats reseteadas")
    }

    /**
     * Pausa la acumulación de distancia, desnivel y velocidad máxima.
     * Las posiciones se siguen recibiendo (para el mapa), pero no cuentan para las estadísticas.
     */
    fun pause() {
        if (isPaused) return
        accumulatedActiveMs += System.currentTimeMillis() - activeStartMs
        isPaused = true
    }

    fun resume() {
        if (!isPaused) return
        activeStartMs = System.currentTimeMillis()
        isPaused = false
    }

    private fun activeSeconds(): Float {
        val runningMs = if (isPaused) 0L else System.currentTimeMillis() - activeStartMs
        return (accumulatedActiveMs + runningMs) / 1000f
    }

    /**
     * Obtiene la última lectura GPS (para POI)
     */
    fun getLastLocation(): GpsReading? = lastGpsReading

    /**
     * Obtiene las estadísticas actuales
     */
    fun getCurrentStats(): GpsStats {
        // Velocidad media real: distancia total / tiempo en movimiento (sin pausas)
        val seconds = activeSeconds()
        val avgSpeed = if (seconds > 0f) totalDistance / seconds else 0f

        // Calcular velocidad actual: si está parado (velocidad < SNT), mostrar 0
        val currentSpeed = lastLocation?.speed ?: 0f
        val currentSpeedKmh = if (currentSpeed > SNT_THRESHOLD_MS) {
            currentSpeed * 3.6f
        } else {
            0f  // Si está parado, mostrar velocidad 0
        }
        
        return GpsStats(
            totalDistanceMeters = totalDistance,
            currentSpeedKmh = currentSpeedKmh,
            avgSpeedKmh = avgSpeed * 3.6f,
            maxSpeedKmh = maxSpeed * 3.6f,
            currentAltitude = lastLocation?.altitude,
            isTracking = lastLocation != null,
            pointsCount = pointsCount,
            elevationGain = elevationGain
        )
    }

    /**
     * Flow de lecturas GPS
     */
    fun gpsFlow(): Flow<GpsReading> = callbackFlow {
        if (!hasLocationPermission()) {
            Log.e(TAG, "No hay permiso de ubicación")
            close(SecurityException("No location permission"))
            return@callbackFlow
        }

        if (!isGpsEnabled()) {
            Log.e(TAG, "GPS deshabilitado")
            close(IllegalStateException("GPS disabled"))
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_INTERVAL)
            setMinUpdateDistanceMeters(SMALLEST_DISPLACEMENT)
            setWaitForAccurateLocation(false)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val accuracy = location.accuracy
                    val speed = if (location.hasSpeed()) location.speed else 0f
                    val speedKmh = speed * 3.6f
                    
                    Log.d(TAG, "📍 GPS: lat=${location.latitude}, lon=${location.longitude}, " +
                            "precisión=${accuracy}m, velocidad=${speedKmh}km/h (${speed}m/s)")

                    // ⚡ FILTRO PROFESIONAL: Solo procesar si cumple ambas condiciones
                    val hasGoodAccuracy = location.hasAccuracy() && accuracy <= ACCURACY_THRESHOLD_METERS
                    val hasValidSpeed = speed > SNT_THRESHOLD_MS
                    val isValidPoint = hasGoodAccuracy && hasValidSpeed

                    if (!isValidPoint) {
                        filteredPointsCount++
                        val reason = when {
                            !hasGoodAccuracy -> "precisión insuficiente (${accuracy}m > ${ACCURACY_THRESHOLD_METERS}m)"
                            !hasValidSpeed -> "velocidad baja (${speedKmh}km/h < ${SNT_THRESHOLD_MS * 3.6f}km/h) - SNT"
                            else -> "condiciones no cumplidas"
                        }
                        Log.d(TAG, "🚫 Punto GPS filtrado: $reason")
                        
                        // Si está parado (velocidad < SNT) pero tiene buena precisión, mantener última posición válida
                        val lastValid = lastValidLocation  // Guardar referencia local para smart cast
                        if (!hasValidSpeed && hasGoodAccuracy && lastValid != null) {
                            // Está parado: mantener posición anclada, velocidad = 0
                            // Emitir última posición válida con velocidad 0 (para actualizar UI sin mover punto)
                            val reading = GpsReading(
                                latitude = lastValid.latitude,
                                longitude = lastValid.longitude,
                                altitude = if (lastValid.hasAltitude()) lastValid.altitude else null,
                                speed = 0f,  // Velocidad 0 cuando está parado
                                accuracy = lastValid.accuracy,
                                timestamp = location.time
                            )
                            lastGpsReading = reading
                            trySend(reading)
                            return@let
                        }
                        
                        // Si no hay buena precisión, descartar completamente (no emitir nada)
                        return@let
                    }

                    // ✅ Punto válido: procesar normalmente
                    val locationToUse = location
                    
                    // Calcular distancia desde último punto válido
                    val lastValid = lastValidLocation  // Guardar referencia local para smart cast
                    val distanceToAdd = if (lastValid != null) {
                        val distance = lastValid.distanceTo(locationToUse)
                        
                        // Verificar que no sea un salto GPS anómalo (y no sumar en pausa)
                        if (isPaused) {
                            0f
                        } else if (distance <= MAX_DISTANCE_JUMP_METERS) {
                            totalDistance += distance
                            distance
                        } else {
                            Log.w(TAG, "⚠️ Salto GPS detectado: ${distance}m (ignorado)")
                            0f
                        }
                    } else {
                        // Primer punto válido
                        0f
                    }

                    // Calcular ganancia de elevación
                    locationToUse.altitude.let { altitude ->
                        previousAltitude?.let { prevAlt ->
                            val diff = altitude - prevAlt
                            if (!isPaused && diff > 0 && diff < 50) {  // Evitar saltos de altitud
                                elevationGain += diff.toFloat()
                            }
                        }
                        previousAltitude = altitude
                    }

                    // Actualizar velocidad máxima
                    if (!isPaused && locationToUse.hasSpeed() && locationToUse.speed > maxSpeed) {
                        maxSpeed = locationToUse.speed
                    }

                    // Actualizar última ubicación válida
                    lastLocation = locationToUse
                    lastValidLocation = locationToUse
                    pointsCount++

                    // Crear y guardar GpsReading
                    val reading = GpsReading(
                        latitude = locationToUse.latitude,
                        longitude = locationToUse.longitude,
                        altitude = if (locationToUse.hasAltitude()) locationToUse.altitude else null,
                        speed = if (locationToUse.hasSpeed()) locationToUse.speed else null,
                        accuracy = if (locationToUse.hasAccuracy()) locationToUse.accuracy else null,
                        timestamp = locationToUse.time
                    )

                    // ⚡ NUEVO: Guardar última lectura para POI
                    lastGpsReading = reading

                    Log.d(TAG, "✅ Punto GPS válido registrado: dist=${distanceToAdd}m, total=${totalDistance}m")
                    trySend(reading)
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                Log.d(TAG, "GPS disponible: ${availability.isLocationAvailable}")
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "🛰️ GPS tracking iniciado")
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos GPS: ${e.message}")
            close(e)
        }

        awaitClose {
            Log.d(TAG, "🛰️ GPS tracking detenido")
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }.catch { e ->
        Log.e(TAG, "Error en flow GPS: ${e.message}")
        // No emitir error, solo loguear
    }
}