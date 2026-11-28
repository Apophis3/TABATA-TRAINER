package com.tuapp.tabatatrainer.rawlog

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import java.util.concurrent.Executor

/**
 * Avisos de satélites para `gnss_status.csv` (spec 008, T-08): `LocationManager.registerGnssStatusCallback`, con
 * los avisos en el hilo «RawLog» ([handler]). No pide posiciones: los avisos llegan mientras el GPS del móvil está
 * encendido (lo enciende la petición Fused de alta precisión). Necesita ubicación fina (ya la tiene la app).
 *
 * Solo la crea [RawSessionLogger] al abrir una sesión con el interruptor encendido (R2). Qué se escribe lo decide
 * [RawGnssStatusRecorder].
 */
class RawGnssStatusSource(
    private val context: Context,
    private val handler: Handler,
    private val recorder: RawGnssStatusRecorder,
    private val clock: () -> RawStamp
) {
    private val locationManager: LocationManager? = context.getSystemService(LocationManager::class.java)
    private var registered = false

    private val callback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val stamp = clock()
            recorder.onStatus(stamp) {
                List(status.satelliteCount) { i ->
                    GnssSatInfo(
                        status.getConstellationType(i), status.getSvid(i), status.getCn0DbHz(i), status.usedInFix(i)
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // Se comprueba justo antes; y `SecurityException` se recoge.
    fun start() {
        val stamp = clock()
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            recorder.failed(stamp, "no_fine_location_permission")
            return
        }
        val lm = locationManager
        if (lm == null) {
            recorder.failed(stamp, "no_location_manager")
            return
        }
        val ok = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.registerGnssStatusCallback(Executor { handler.post(it) }, callback)
            } else {
                lm.registerGnssStatusCallback(callback, handler)
            }
        } catch (e: Exception) {
            recorder.failed(stamp, "${e.javaClass.simpleName}: ${e.message}")
            return
        }
        if (ok) {
            registered = true
            recorder.started(stamp, PARAMS_DETAIL)
        } else {
            recorder.failed(stamp, "register_false")
        }
    }

    /** Quita el aviso. Llamarlo dos veces no hace nada. */
    fun stop() {
        if (!registered) return
        registered = false
        try {
            locationManager?.unregisterGnssStatusCallback(callback)
        } catch (_: Exception) {
            // Ya no estaba registrado.
        }
    }

    companion object {
        const val MIN_GAP_MS = MinGapGate.DEFAULT_MIN_GAP_NS / 1_000_000
        const val PARAMS_DETAIL = "callback=gnss_status;min_gap_ms=$MIN_GAP_MS"
    }
}
