package com.tuapp.tabatatrainer.rawlog

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler

/**
 * Barómetro del móvil para `baro.csv` (spec 008, R9; plan §5). Usa `android.hardware.SensorManager`, **no** el
 * `SensorManager` de la app. Ritmo pedido 200 ms y sin lotes (latencia 0), con las muestras en el hilo «RawLog»
 * ([handler]). Qué se escribe lo decide [RawBaroRecorder].
 *
 * Solo la crea [RawSessionLogger] al abrir una sesión con el interruptor encendido (R2).
 */
class RawBaroSource(
    context: Context,
    private val handler: Handler,
    private val recorder: RawBaroRecorder,
    private val clock: () -> RawStamp
) {
    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private var registered = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            recorder.onSample(clock(), event.timestamp, event.values[0], event.accuracy)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // La fiabilidad va en cada fila (`accuracy`); aquí no se anota nada más.
        }
    }

    fun start() {
        val stamp = clock()
        val sm = sensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (sm == null || sensor == null) {
            recorder.unavailable(stamp)
            return
        }
        val info = BaroSensorInfo(
            name = sensor.name,
            vendor = sensor.vendor,
            maxRangeHpa = sensor.maximumRange,
            resolutionHpa = sensor.resolution,
            minDelayUs = sensor.minDelay,
            fifoMaxEvents = sensor.fifoMaxEventCount
        )
        val ok = try {
            sm.registerListener(listener, sensor, SAMPLING_PERIOD_US, MAX_REPORT_LATENCY_US, handler)
        } catch (e: Exception) {
            recorder.failed(stamp, info, "${e.javaClass.simpleName}: ${e.message}")
            return
        }
        if (ok) {
            registered = true
            recorder.started(stamp, info, PARAMS_DETAIL)
        } else {
            recorder.failed(stamp, info, "register_listener_false")
        }
    }

    /** Quita el sensor. Llamarlo dos veces no hace nada. */
    fun stop() {
        if (!registered) return
        registered = false
        try {
            sensorManager?.unregisterListener(listener)
        } catch (_: Exception) {
            // Ya no estaba registrado.
        }
    }

    companion object {
        const val SAMPLING_PERIOD_US = 200_000
        const val MAX_REPORT_LATENCY_US = 0

        const val PARAMS_DETAIL = "sampling_period_us=$SAMPLING_PERIOD_US;max_report_latency_us=$MAX_REPORT_LATENCY_US" +
            ";min_gap_ms=${BaroDecimator.DEFAULT_MIN_GAP_NS / 1_000_000}"
    }
}
