package com.tuapp.tabatatrainer.util.laps

import kotlin.math.roundToInt

/**
 * Ritmo (segundos por km) sobre una ventana móvil de tiempo en movimiento.
 *
 * Lógica pura: el tiempo entra por parámetro (segundos del cronómetro de la ruta,
 * que no avanza en pausa). No usa reloj ni Android.
 */
class PaceCalculator(private val windowSeconds: Int = WINDOW_SECONDS) {

    private data class Sample(val timeSeconds: Int, val distanceMeters: Float)

    private val samples = ArrayDeque<Sample>()

    fun reset() = samples.clear()

    fun add(timeSeconds: Int, distanceMeters: Float) {
        samples.addLast(Sample(timeSeconds, distanceMeters))
        while (samples.size > 1 && timeSeconds - samples.first().timeSeconds > windowSeconds) {
            samples.removeFirst()
        }
    }

    /** Ritmo en vivo, o null si no hay movimiento fiable o queda fuera de 2:00–30:00 /km */
    fun livePaceSecPerKm(): Int? {
        if (samples.size < 2) return null
        val first = samples.first()
        val last = samples.last()
        val seconds = last.timeSeconds - first.timeSeconds
        val meters = last.distanceMeters - first.distanceMeters
        if (seconds < MIN_SPAN_SECONDS) return null
        val pace = secPerKm(seconds, meters) ?: return null
        return pace.takeIf { it in MIN_PACE_SEC_PER_KM..MAX_PACE_SEC_PER_KM }
    }

    companion object {
        const val WINDOW_SECONDS = 10
        const val MIN_SPAN_SECONDS = 4
        const val MIN_PACE_SEC_PER_KM = 120   // 2:00 /km
        const val MAX_PACE_SEC_PER_KM = 1800  // 30:00 /km

        /** Tiempo / distancia -> s/km. Null si no hay distancia o tiempo (nunca 0). */
        fun secPerKm(seconds: Int, meters: Float): Int? {
            if (seconds <= 0 || meters <= 0f) return null
            return (seconds / meters * 1000f).roundToInt()
        }

        /** "m:ss" o "--" si no hay ritmo */
        fun format(secPerKm: Int?): String {
            if (secPerKm == null || secPerKm <= 0) return "--"
            return "%d:%02d".format(secPerKm / 60, secPerKm % 60)
        }
    }
}
