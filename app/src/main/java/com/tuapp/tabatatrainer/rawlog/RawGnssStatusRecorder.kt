package com.tuapp.tabatatrainer.rawlog

/**
 * Parte sin Android de `gnss_status.csv` (spec 008, T-08): abre el archivo, escribe como mucho una fila por
 * segundo (un aviso de `GnssStatus` cuando han pasado ≥ 950 ms desde la última fila escrita, [MinGapGate]) y anota
 * `source_started` / `source_error`. El registro del aviso está en [RawGnssStatusSource].
 *
 * Sin filtros: solo los recuentos y medias de [GnssStatusRow.from]. No es seguro entre hilos: solo desde el hilo
 * «RawLog» de la sesión.
 */
class RawGnssStatusRecorder(
    private val core: RawSessionCore,
    val gate: MinGapGate = MinGapGate()
) {
    private var writer: RawFileWriter? = null

    var started = false
        private set

    var lastError: String? = null
        private set

    /** Crea `gnss_status.csv` y anota `source_started`. @return `false` si la sesión ya no está abierta. */
    fun started(stamp: RawStamp, params: String): Boolean {
        val w = core.openWriter(RawFiles.GNSS_STATUS, GnssStatusRow.HEADER) ?: return false
        writer = w
        started = true
        core.event(stamp, RawEventType.SOURCE_STARTED, "file=${RawFiles.GNSS_STATUS};$params")
        return true
    }

    fun failed(stamp: RawStamp, reason: String) {
        lastError = reason
        core.event(stamp, RawEventType.SOURCE_ERROR, "file=${RawFiles.GNSS_STATUS};reason=$reason")
    }

    /**
     * Un aviso de satélites. [sats] solo se lee si toca escribir (así no se copian los satélites de los avisos que
     * se descartan). Va al búfer normal (vaciado cada 5 s).
     */
    fun onStatus(stamp: RawStamp, sats: () -> List<GnssSatInfo>) {
        if (!core.isOpen || !started) return
        if (gate.accept(stamp.elapsedNs)) writer?.append(GnssStatusRow.from(stamp, sats()))
    }
}
