package com.tuapp.tabatatrainer.rawlog

/**
 * Decide qué muestras del barómetro se escriben (spec 008, R9): una cuando han pasado al menos
 * [minGapNs] (hora del sensor) desde la última escrita. Las demás se descartan sin promediar y se cuentan.
 *
 * El margen por defecto (950 ms en vez de 1000) evita perder muestras de un sensor de 1 Hz que llegan
 * con pequeños retrasos. Si la hora del sensor va hacia atrás (reinicio del sensor), la muestra se
 * escribe y se toma como nueva referencia.
 */
class BaroDecimator(private val minGapNs: Long = DEFAULT_MIN_GAP_NS) {

    private var lastWrittenNs: Long? = null

    var received = 0L
        private set
    var written = 0L
        private set
    val discarded: Long get() = received - written

    /** @return `true` si la muestra con hora de sensor [sensorTsNs] debe escribirse. */
    fun accept(sensorTsNs: Long): Boolean {
        received++
        val last = lastWrittenNs
        val gap = if (last == null) null else sensorTsNs - last
        val keep = gap == null || gap >= minGapNs || gap < 0
        if (keep) {
            lastWrittenNs = sensorTsNs
            written++
        }
        return keep
    }

    companion object {
        const val DEFAULT_MIN_GAP_NS = 950_000_000L
    }
}
