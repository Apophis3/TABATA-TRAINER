package com.tuapp.tabatatrainer.util.map

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * R1 (spec 009): versión aligerada de la ruta para el mapa en vivo.
 *
 * Recibe los puntos uno a uno y quita los que no cambian la forma más de [epsilonM] metros
 * (ventana abierta, tipo Douglas-Peucker incremental). Siempre conserva el primer punto (salida
 * real) y el último recibido (posición actual). Coste por punto acotado por [maxWindow].
 *
 * Solo afecta al dibujo: los puntos de Room y el registro bruto no pasan por aquí.
 * No es seguro entre hilos: quien lo use debe sincronizar.
 */
class RouteSimplifier<T>(
    private val latOf: (T) -> Double,
    private val lonOf: (T) -> Double,
    private val epsilonM: Double = DEFAULT_EPSILON_M,
    private val maxWindow: Int = DEFAULT_MAX_WINDOW
) {
    companion object {
        const val DEFAULT_EPSILON_M = 2.0
        const val DEFAULT_MAX_WINDOW = 200
        private const val METERS_PER_DEGREE = 111_320.0
    }

    /** Puntos ya fijados; el último es el ancla del tramo abierto. */
    private val kept = ArrayList<T>()

    /** Puntos recibidos después del ancla (el último es el más reciente). */
    private val pending = ArrayList<T>()

    /** Número de puntos recibidos desde el último [reset]. */
    var receivedCount: Int = 0
        private set

    fun add(point: T) {
        receivedCount++
        if (kept.isEmpty()) {
            kept.add(point)
            return
        }
        val anchor = kept.last()
        if (pending.any { distanceToSegmentM(it, anchor, point) > epsilonM }) {
            // El tramo ancla → nuevo punto ya no representa los intermedios: se fija el anterior
            kept.add(pending.last())
            pending.clear()
        }
        pending.add(point)
        if (pending.size > maxWindow) {
            kept.add(pending.last())
            pending.clear()
        }
    }

    /** Ruta aligerada: puntos fijados + último punto recibido. */
    fun points(): List<T> {
        val out = ArrayList<T>(kept.size + 1)
        out.addAll(kept)
        pending.lastOrNull()?.let { out.add(it) }
        return out
    }

    fun reset() {
        kept.clear()
        pending.clear()
        receivedCount = 0
    }

    /** Distancia en metros de [p] al segmento [a]–[b] (proyección local, válida a escala de km). */
    private fun distanceToSegmentM(p: T, a: T, b: T): Double {
        val lat0 = latOf(a)
        val lon0 = lonOf(a)
        val kx = METERS_PER_DEGREE * cos(Math.toRadians(lat0))
        val ky = METERS_PER_DEGREE
        val px = (lonOf(p) - lon0) * kx
        val py = (latOf(p) - lat0) * ky
        val bx = (lonOf(b) - lon0) * kx
        val by = (latOf(b) - lat0) * ky
        val len2 = bx * bx + by * by
        if (len2 < 1e-9) return sqrt(px * px + py * py)
        val t = ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val dx = px - t * bx
        val dy = py - t * by
        return sqrt(dx * dx + dy * dy)
    }
}
