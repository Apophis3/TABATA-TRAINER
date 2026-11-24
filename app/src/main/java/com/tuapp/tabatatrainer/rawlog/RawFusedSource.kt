package com.tuapp.tabatatrainer.rawlog

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.Executor

/**
 * Petición de posiciones propia del registro en bruto (spec 008, R8; plan §4). Independiente de `GpsManager`:
 * alta precisión, 1000 ms, mínimo 500 ms, **sin** distancia mínima, sin esperar precisión y sin lotes. Todo
 * (callback, fallos y parada) va en el hilo «RawLog» de la sesión ([handler]).
 *
 * Solo la crea [RawSessionLogger] al abrir una sesión con el interruptor encendido (R2). Lo que se escribe lo
 * decide [RawFusedRecorder]; aquí solo se pide, se convierte `Location → LocationFix` y se quita la petición.
 */
class RawFusedSource(
    private val context: Context,
    private val handler: Handler,
    private val recorder: RawFusedRecorder,
    private val clock: () -> RawStamp
) {
    private var client: FusedLocationProviderClient? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val stamp = clock()
            val locations = result.locations
            val size = locations.size
            recorder.onResult(stamp, size) { i -> locations[i].toLocationFix(stamp, i, size) }
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            recorder.onAvailability(clock(), availability.isLocationAvailable)
        }
    }

    /** Pide posiciones. Sin permiso o si Fused la rechaza: `source_error` y sin archivo (o con solo la cabecera). */
    @SuppressLint("MissingPermission") // Se comprueba justo antes; y `SecurityException` se recoge.
    fun start() {
        val stamp = clock()
        if (!hasLocationPermission()) {
            recorder.failed(stamp, "no_location_permission")
            return
        }
        try {
            val c = LocationServices.getFusedLocationProviderClient(context)
            client = c
            c.requestLocationUpdates(REQUEST, callback, handler.looper)
                .addOnFailureListener(Executor { handler.post(it) }) { e ->
                    recorder.failed(clock(), "request_failed: ${e.javaClass.simpleName}: ${e.message}")
                }
            recorder.started(stamp, PARAMS_DETAIL)
        } catch (e: Exception) {
            client = null
            recorder.failed(stamp, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Quita la petición. Llamarlo dos veces no hace nada. */
    fun stop() {
        val c = client ?: return
        client = null
        try {
            c.removeLocationUpdates(callback)
        } catch (_: Exception) {
            // Si ya no estaba, no hay nada que quitar.
        }
    }

    private fun hasLocationPermission(): Boolean {
        fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    companion object {
        const val PRIORITY_NAME = "PRIORITY_HIGH_ACCURACY"
        const val INTERVAL_MS = 1000L
        const val MIN_INTERVAL_MS = 500L
        const val MIN_DISTANCE_M = 0f
        const val MAX_UPDATE_DELAY_MS = 0L
        const val WAIT_FOR_ACCURATE = false

        /** Efecto observador aceptado (spec R8, plan §8 R-1): va a `meta.json`. */
        const val OBSERVER_EFFECT = "peticion_activa_1000ms_sin_distancia_minima"
        const val OBSERVER_EFFECT_NOTE = "Fused junta esta petición con la de la app: con el interruptor encendido " +
            "la app puede recibir más puntos (medido 2026-11-24 en SM-S908B: +14 % andando, igual a ~3 m/s)."

        /** Detalle de `source_started`: los parámetros de la petición tal cual. */
        const val PARAMS_DETAIL = "priority=$PRIORITY_NAME;interval_ms=$INTERVAL_MS;min_interval_ms=$MIN_INTERVAL_MS" +
            ";min_distance_m=0;max_update_delay_ms=$MAX_UPDATE_DELAY_MS;wait_for_accurate=$WAIT_FOR_ACCURATE"

        private val REQUEST: LocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_M)
            .setMaxUpdateDelayMillis(MAX_UPDATE_DELAY_MS)
            .setWaitForAccurateLocation(WAIT_FOR_ACCURATE)
            .build()
    }
}

/**
 * Una posición tal como llega, sin tocar (R7): cada campo solo si su `hasX()` es verdadero; si no, vacío (R5).
 * [stamp] es la hora en que la app recibió el resultado (la misma para todas las posiciones del lote).
 */
internal fun Location.toLocationFix(stamp: RawStamp, batchIndex: Int, batchSize: Int): LocationFix = LocationFix(
    stamp = stamp,
    locTimeMs = time,
    locElapsedNs = elapsedRealtimeNanos,
    provider = provider,
    lat = latitude,
    lon = longitude,
    altM = if (hasAltitude()) altitude else null,
    vertAccM = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
    hAccM = if (hasAccuracy()) accuracy else null,
    speedMs = if (hasSpeed()) speed else null,
    speedAccMs = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
    bearingDeg = if (hasBearing()) bearing else null,
    bearingAccDeg = if (hasBearingAccuracy()) bearingAccuracyDegrees else null,
    isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        isMock
    } else {
        @Suppress("DEPRECATION")
        isFromMockProvider
    },
    batchIndex = batchIndex,
    batchSize = batchSize
)
