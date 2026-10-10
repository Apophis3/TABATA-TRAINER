package com.tuapp.tabatatrainer.util.laps

/**
 * Contadores acumulados de la sesión en un instante. `null` = sin dato (no hay podómetro / aún no ha
 * llegado ninguna altitud), que es distinto de 0.
 */
data class BlockCounters(
    val steps: Int? = null,
    val elevationGain: Float? = null
)

/** Métricas de una ventana de vuelta que no salen de la distancia ni del tiempo */
data class BlockMetrics(
    val steps: Int?,
    val elevationGain: Float?,
    val cadenceSource: CadenceSource
)

/**
 * Pasos, desnivel y origen de la cadencia de UNA ventana de vuelta (spec 006, R7).
 * Se calcula como diferencia entre los contadores acumulados al final y al principio de la ventana,
 * de modo que las ventanas contiguas de una misma serie no cuentan nada dos veces
 * (la suma de pasos de las manuales ≤ pasos totales). Sin Android; los contadores entran por parámetro.
 */
object LapBlockMetrics {

    /**
     * @param start contadores al abrirse la ventana; un `null` se trata como 0 (el contador aún no existía)
     * @param end contadores al cerrarse; un `null` deja la métrica sin dato
     * @param bikeSensorUsed el sensor de pedal (CSC/ANT+) emitió dentro de la ventana → actividad de bici
     */
    fun of(start: BlockCounters, end: BlockCounters, bikeSensorUsed: Boolean): BlockMetrics {
        val steps = end.steps?.let { (it - (start.steps ?: 0)).coerceAtLeast(0) }
        val elevation = end.elevationGain?.let { (it - (start.elevationGain ?: 0f)).coerceAtLeast(0f) }
        val source = when {
            bikeSensorUsed -> CadenceSource.BIKE_SENSOR
            steps != null -> CadenceSource.PEDOMETER
            else -> CadenceSource.NONE
        }
        return BlockMetrics(steps = steps, elevationGain = elevation, cadenceSource = source)
    }

    /**
     * Cadencia de carrera del bloque en pasos/min = pasos ÷ minutos del bloque (RF-7.4).
     * `null` (se pinta `--`) si la fuente es el sensor de bici (rpm ≠ ppm, RF-7.5), si no hay pasos
     * (RF-7.6) o si el bloque no tiene duración.
     */
    fun cadencePpm(steps: Int?, durationSeconds: Int, source: CadenceSource?): Int? {
        if (source == CadenceSource.BIKE_SENSOR) return null
        if (steps == null || durationSeconds <= 0) return null
        return Math.round(steps * 60.0 / durationSeconds).toInt()
    }
}
