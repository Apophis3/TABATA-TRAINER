package com.tuapp.tabatatrainer.util.laps

import java.util.Locale
import kotlin.math.floor

/** Spec 009, R4: un tramo de velocidad con los pasos, la distancia y el tiempo que sumó. */
data class StrideBin(
    val lowKmh: Int,
    val steps: Int,
    val distanceM: Double,
    val seconds: Int
) {
    val highKmh: Int get() = StrideSpeedBins.highOf(lowKmh)

    /** Zancada media del tramo (m). */
    val strideM: Double get() = if (steps > 0) distanceM / steps else 0.0
}

/**
 * Spec 009, R4: zancada según la velocidad.
 *
 * Se llama una vez por segundo mientras la ruta está en marcha ([onSecond]) con los pasos y la
 * distancia ACUMULADOS. Cada [blockSeconds] cierra un bloque, calcula su velocidad media y suma sus
 * pasos y su distancia al tramo que le toca (RF-4.1, RF-4.2):
 * - de 1 km/h hasta 7 km/h, tramos de 1 km/h; desde 7 km/h, tramos de 2 km/h;
 * - por debajo de [MIN_KMH] (parado) o por encima de [MAX_KMH] (salto de GPS) no cuenta;
 * - un bloque con menos de [minBlockSteps] pasos no cuenta.
 * Al pausar se llama a [interrupt]: el bloque a medias se descarta.
 *
 * Kotlin puro (sin Android). No es seguro entre hilos.
 */
class StrideSpeedBins(
    private val blockSeconds: Int = BLOCK_SECONDS,
    private val minBlockSteps: Int = MIN_BLOCK_STEPS
) {
    companion object {
        const val BLOCK_SECONDS = 10
        const val MIN_BLOCK_STEPS = 5
        const val MIN_KMH = 1.0
        const val MAX_KMH = 25.0
        const val FINE_LIMIT_KMH = 7

        /** RF-4.4: en el resumen solo se enseñan los tramos con al menos estos pasos. */
        const val MIN_VISIBLE_STEPS = 100

        /** Límite inferior del tramo de [speedKmh], o null si no cuenta. */
        fun binOf(speedKmh: Double): Int? {
            if (speedKmh.isNaN() || speedKmh < MIN_KMH || speedKmh > MAX_KMH) return null
            if (speedKmh < FINE_LIMIT_KMH) return floor(speedKmh).toInt()
            return FINE_LIMIT_KMH + 2 * floor((speedKmh - FINE_LIMIT_KMH) / 2).toInt()
        }

        fun highOf(lowKmh: Int): Int = if (lowKmh < FINE_LIMIT_KMH) lowKmh + 1 else lowKmh + 2

        /** Formato guardado en Room: `tramo:pasos:distancia_m:segundos` separados por `;`. */
        fun encode(bins: List<StrideBin>): String =
            bins.sortedBy { it.lowKmh }.joinToString(";") {
                String.format(Locale.ROOT, "%d:%d:%.1f:%d", it.lowKmh, it.steps, it.distanceM, it.seconds)
            }

        /** Lee el texto guardado; las partes rotas se saltan. Null o vacío → lista vacía. */
        fun decode(text: String?): List<StrideBin> {
            if (text.isNullOrBlank()) return emptyList()
            return text.split(';').mapNotNull { part ->
                val f = part.trim().split(':')
                if (f.size != 4) return@mapNotNull null
                val low = f[0].toIntOrNull() ?: return@mapNotNull null
                val steps = f[1].toIntOrNull() ?: return@mapNotNull null
                val dist = f[2].toDoubleOrNull() ?: return@mapNotNull null
                val secs = f[3].toIntOrNull() ?: return@mapNotNull null
                if (steps <= 0 || dist < 0 || secs < 0) return@mapNotNull null
                StrideBin(low, steps, dist, secs)
            }.sortedBy { it.lowKmh }
        }

        /** RF-4.4: tramos que se enseñan en el resumen. */
        fun visible(bins: List<StrideBin>, minSteps: Int = MIN_VISIBLE_STEPS): List<StrideBin> =
            bins.filter { it.steps >= minSteps }.sortedBy { it.lowKmh }
    }

    private class Acc(var steps: Int = 0, var distanceM: Double = 0.0, var seconds: Int = 0)

    private val acc = sortedMapOf<Int, Acc>()
    private var blockStartSteps: Int? = null
    private var blockStartDistanceM = 0.0
    private var secondsInBlock = 0

    fun onSecond(totalSteps: Int, totalDistanceM: Double) {
        val startSteps = blockStartSteps
        if (startSteps == null) {
            startBlock(totalSteps, totalDistanceM)
            return
        }
        secondsInBlock++
        if (secondsInBlock < blockSeconds) return

        val steps = totalSteps - startSteps
        val distance = totalDistanceM - blockStartDistanceM
        val speedKmh = distance / secondsInBlock * 3.6
        val bin = binOf(speedKmh)
        if (bin != null && steps >= minBlockSteps) {
            val a = acc.getOrPut(bin) { Acc() }
            a.steps += steps
            a.distanceM += distance
            a.seconds += secondsInBlock
        }
        startBlock(totalSteps, totalDistanceM)
    }

    /** Pausa: se descarta el bloque a medias; el siguiente empieza en el próximo [onSecond]. */
    fun interrupt() {
        blockStartSteps = null
        secondsInBlock = 0
    }

    fun reset() {
        acc.clear()
        interrupt()
    }

    fun bins(): List<StrideBin> = acc.map { (low, a) -> StrideBin(low, a.steps, a.distanceM, a.seconds) }

    fun encode(): String = encode(bins())

    private fun startBlock(totalSteps: Int, totalDistanceM: Double) {
        blockStartSteps = totalSteps
        blockStartDistanceM = totalDistanceM
        secondsInBlock = 0
    }
}
