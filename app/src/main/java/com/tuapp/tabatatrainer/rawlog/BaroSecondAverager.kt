package com.tuapp.tabatatrainer.rawlog

/**
 * Junta las lecturas del barómetro por segundo (spec 008, R9 tras T-08): una fila por segundo con la media, el
 * número de lecturas, la mínima y la máxima. El segundo es el de llegada a la app (`elapsed_ns` / 1 s, reloj
 * desde el arranque del móvil, que no se mueve si cambia la hora). Un segundo sin lecturas no da fila (no se
 * rellena nada). La fila lleva la hora de la última lectura de su segundo. Sin altitud ni otros cálculos.
 *
 * Un segundo se cierra cuando llega una lectura de otro segundo, con [closeIfPast] (temporizador de 1 s) o con
 * [drain] (al parar). No es seguro entre hilos: solo desde el hilo «RawLog».
 */
class BaroSecondAverager {

    private var second: Long? = null
    private var sum = 0.0
    private var count = 0
    private var min = 0f
    private var max = 0f
    private var lastStamp: RawStamp? = null

    /** Lecturas recibidas en total. */
    var received = 0L
        private set

    /** Filas (segundos) entregadas. */
    var rows = 0L
        private set

    /** Añade una lectura. @return la fila del segundo anterior si esta lectura es de un segundo nuevo. */
    fun add(stamp: RawStamp, pressureHpa: Float): BaroSample? {
        received++
        val s = stamp.elapsedNs / NS_PER_S
        val closed = if (second != null && s != second) drain() else null
        if (count == 0) {
            second = s
            min = pressureHpa
            max = pressureHpa
        } else {
            if (pressureHpa < min) min = pressureHpa
            if (pressureHpa > max) max = pressureHpa
        }
        // Con el texto del Float para que 941.37f no se convierta en 941.3699951171875.
        sum += pressureHpa.toString().toDouble()
        count++
        lastStamp = stamp
        return closed
    }

    /** Cierra el segundo abierto si [now] ya es de un segundo posterior. */
    fun closeIfPast(now: RawStamp): BaroSample? {
        val s = second ?: return null
        return if (now.elapsedNs / NS_PER_S > s) drain() else null
    }

    /** Cierra el segundo abierto, si tiene lecturas. */
    fun drain(): BaroSample? {
        val stamp = lastStamp
        if (count == 0 || stamp == null) return null
        val row = BaroSample(stamp, sum / count, count, min, max)
        second = null
        sum = 0.0
        count = 0
        lastStamp = null
        rows++
        return row
    }

    companion object {
        const val NS_PER_S = 1_000_000_000L
        const val MODE = "media_por_segundo"
    }
}
