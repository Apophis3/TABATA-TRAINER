package com.tuapp.tabatatrainer.rawlog

/**
 * Parte sin Android de `gps_fused.csv` (spec 008, R8; plan §4): abre el archivo, escribe una fila por posición de
 * cada resultado, anota `source_started`, `source_error` y `location_available` y lleva la cuenta para `meta.json`.
 * La petición a Fused y la conversión `Location → LocationFix` están en [RawFusedSource].
 *
 * Sin filtros ni cálculos (R7): cada posición recibida es una fila. No es seguro entre hilos: solo desde el hilo
 * «RawLog» de la sesión, igual que [RawSessionCore].
 */
class RawFusedRecorder(private val core: RawSessionCore) {

    private var writer: RawFileWriter? = null

    /** `true` si se llegó a pedir posiciones en esta sesión (aunque luego fallara: ver [lastError]). */
    var started = false
        private set

    /** Resultados de Fused recibidos (cada uno puede traer varias posiciones o ninguna). */
    var resultsReceived = 0L
        private set

    /** Posiciones recibidas (= filas que se intentaron escribir). */
    var locationsReceived = 0L
        private set

    /** Último motivo de `source_error`, para `meta.json`. */
    var lastError: String? = null
        private set

    /**
     * Crea `gps_fused.csv` con su cabecera y anota `source_started` con los parámetros de la petición.
     * @return `false` si la sesión ya no está abierta (no se pide nada).
     */
    fun started(stamp: RawStamp, params: String): Boolean {
        val w = core.openWriter(RawFiles.GPS_FUSED, LocationFix.HEADER) ?: return false
        writer = w
        started = true
        core.event(stamp, RawEventType.SOURCE_STARTED, "file=${RawFiles.GPS_FUSED};$params")
        return true
    }

    /** La petición no se pudo hacer o Fused la rechazó: `source_error` con archivo y motivo. */
    fun failed(stamp: RawStamp, reason: String) {
        lastError = reason
        core.event(stamp, RawEventType.SOURCE_ERROR, "file=${RawFiles.GPS_FUSED};reason=$reason")
    }

    /**
     * Un resultado de Fused: una fila por posición, todas con la misma hora de llegada [stamp] (R5),
     * `batch_index` desde 0 y `batch_size` = [size]. Un resultado vacío cuenta pero no escribe nada.
     * Las filas van al búfer normal (vaciado cada 5 s), no al instante.
     * @param fixAt convierte la posición número `index` del resultado (con su `index` y `size`) en fila.
     */
    fun onResult(stamp: RawStamp, size: Int, fixAt: (index: Int) -> LocationFix) {
        if (!core.isOpen) return
        resultsReceived++
        for (i in 0 until size) {
            locationsReceived++
            writer?.append(fixAt(i))
        }
    }

    /** `onLocationAvailability`: evento `location_available` con detalle `true`/`false` (R10), vaciado al instante. */
    fun onAvailability(stamp: RawStamp, available: Boolean) {
        core.event(stamp, RawEventType.LOCATION_AVAILABLE, available.toString())
    }
}
