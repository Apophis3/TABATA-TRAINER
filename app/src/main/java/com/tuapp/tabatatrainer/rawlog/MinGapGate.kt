package com.tuapp.tabatatrainer.rawlog

/**
 * Deja pasar un dato cuando han pasado al menos [minGapNs] desde el último que dejó pasar; los demás se cuentan
 * como descartados. Lo usa `gnss_status.csv` (spec 008, T-08) para escribir como mucho una fila por segundo.
 * Antes se llamaba `BaroDecimator` y decidía qué muestras del barómetro se escribían (hasta T-08).
 *
 * El margen por defecto (950 ms en vez de 1000) evita perder datos de una fuente de 1 Hz que llegan con
 * pequeños retrasos. Si la hora va hacia atrás (reinicio de la fuente), el dato pasa y se toma como nueva
 * referencia.
 */
class MinGapGate(private val minGapNs: Long = DEFAULT_MIN_GAP_NS) {

    private var lastWrittenNs: Long? = null

    var received = 0L
        private set
    var written = 0L
        private set
    val discarded: Long get() = received - written

    /** @return `true` si el dato con hora [tsNs] debe escribirse. */
    fun accept(tsNs: Long): Boolean {
        received++
        val last = lastWrittenNs
        val gap = if (last == null) null else tsNs - last
        val keep = gap == null || gap >= minGapNs || gap < 0
        if (keep) {
            lastWrittenNs = tsNs
            written++
        }
        return keep
    }

    companion object {
        const val DEFAULT_MIN_GAP_NS = 950_000_000L
    }
}
