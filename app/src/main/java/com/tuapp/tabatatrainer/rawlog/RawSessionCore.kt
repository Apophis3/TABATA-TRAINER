package com.tuapp.tabatatrainer.rawlog

import java.io.File

/**
 * Una sesión del registro en bruto sin nada de Android (spec 008, plan §3.1): abre su carpeta y `events.csv`,
 * decide qué servicio manda, anota eventos, cierra y lleva la cuenta de filas y errores.
 *
 * No es seguro entre hilos: se usa solo desde el hilo «RawLog» de la sesión. Nunca lanza excepciones por fallos
 * de almacenamiento (los recoge [RawFileWriter]).
 *
 * @param clock hora de reloj y tiempo desde el arranque en el momento de la llamada (inyectable en los tests).
 * @param log línea de aviso para Logcat (en la app, `Log.w("RawLog", …)`).
 * @param onEvent cada evento que de verdad se escribe en `events.csv` (en la app, una línea `Log.i` en «RawLog»).
 */
class RawSessionCore(
    root: File,
    val sessionId: String,
    val service: String,
    val activityType: String,
    private val clock: () -> RawStamp,
    private val log: (String) -> Unit = {},
    private val onEvent: (RawEvent) -> Unit = {}
) {
    val dir = File(root, sessionId)

    var startStamp: RawStamp? = null
        private set
    var stopStamp: RawStamp? = null
        private set
    var stopReason: String? = null
        private set
    var writeErrors = 0
        private set

    val isOpen: Boolean get() = startStamp != null && stopStamp == null

    private val writers = linkedMapOf<String, RawFileWriter>()
    private var events: RawFileWriter? = null

    /**
     * Crea la carpeta y `events.csv`; anota un `service_created` por cada servicio vivo, con la hora real en que se
     * creó (en orden de tiempo), y después `session_start`.
     * @param servicesCreated servicio → hora de su `onCreate`, guardada antes de que hubiera sesión.
     * @return `false` si ya se abrió antes o el identificador no es seguro como nombre de carpeta.
     */
    fun open(stamp: RawStamp, servicesCreated: Map<String, RawStamp> = emptyMap()): Boolean {
        if (startStamp != null || !RawFiles.isSafeSessionId(sessionId)) return false
        startStamp = stamp
        events = openWriter(RawFiles.EVENTS, RawEvent.HEADER)
        servicesCreated.entries.sortedBy { it.value.elapsedNs }.forEach { (name, at) ->
            event(at, RawEventType.SERVICE_CREATED, "service=$name")
        }
        event(stamp, RawEventType.SESSION_START, "activity=$activityType;service=$service")
        return true
    }

    /** Abre (o devuelve, si ya existe) el archivo [name] de la sesión. `null` si la sesión no está abierta. */
    fun openWriter(name: String, header: String): RawFileWriter? {
        if (!isOpen) return null
        writers[name]?.let { return it }
        val writer = RawFileWriter(
            file = File(dir, name),
            header = header,
            nowMs = { clock().elapsedNs / 1_000_000 },
            onError = { file, e -> reportWriteError(file.name, e) }
        )
        writers[name] = writer
        writer.open()
        return writer
    }

    /** Anota un evento y lo vacía al instante (plan §3.1). Sin sesión abierta no hace nada. */
    fun event(stamp: RawStamp, type: String, detail: String?) {
        if (!isOpen) return
        val e = RawEvent(stamp, type, detail)
        events?.append(e, flushNow = true)
        onEvent(e)
    }

    /** Evento propio de la sesión (pausa, reanudación): solo si viene del servicio de la sesión. */
    fun sessionEvent(stamp: RawStamp, fromService: String, type: String, detail: String? = null) {
        if (fromService == service) event(stamp, type, detail)
    }

    /**
     * Anota `service_destroyed` de [fromService]. Si es el servicio de la sesión, la cierra.
     * @return `true` si la sesión se cerró.
     */
    fun serviceDestroyed(stamp: RawStamp, fromService: String): Boolean {
        event(stamp, RawEventType.SERVICE_DESTROYED, "service=$fromService")
        return stop(stamp, fromService, STOP_SERVICE_DESTROYED)
    }

    /**
     * Anota `session_stop` y cierra todos los archivos. Solo si [fromService] es el de la sesión y sigue abierta.
     * @return `true` si la sesión se cerró ahora.
     */
    fun stop(stamp: RawStamp, fromService: String, reason: String): Boolean {
        if (!isOpen || fromService != service) return false
        event(stamp, RawEventType.SESSION_STOP, "reason=$reason")
        stopStamp = stamp
        stopReason = reason
        writers.values.forEach { it.close() }
        return true
    }

    /** Vacía los archivos a los que les toca (temporizador de 5 s). */
    fun flushIfDue() {
        if (isOpen) writers.values.forEach { it.flushIfDue() }
    }

    /**
     * Un archivo no se pudo escribir: se cuenta, va al Logcat y, si no es el propio `events.csv`, se anota
     * `write_error` en él. Nunca una ventana (decisión del usuario tras T-01).
     */
    fun reportWriteError(fileName: String, e: Exception) {
        writeErrors++
        log("write_error $sessionId/$fileName: ${e.javaClass.simpleName}: ${e.message}")
        if (fileName != RawFiles.EVENTS && isOpen) {
            events?.append(
                RawEvent(clock(), RawEventType.WRITE_ERROR, "file=$fileName;reason=${e.javaClass.simpleName}: ${e.message}"),
                flushNow = true
            )
        }
    }

    data class FileStat(val rows: Long, val failed: Boolean)

    /** Filas escritas (sin cabecera) y estado de cada archivo, para `meta.json`. */
    fun fileStats(): Map<String, FileStat> = writers.mapValues { (_, w) -> FileStat(w.rowCount, w.failed) }

    companion object {
        const val STOP_USER = "user"
        const val STOP_COMPLETED = "completed"
        const val STOP_SERVICE_DESTROYED = "service_destroyed"
        const val STOP_REPLACED = "replaced"

        /** Detalle de una fila de estado de partida: el mismo que un cambio, con `,initial` al final (plan §3.1). */
        fun initial(detail: String?): String = if (detail.isNullOrEmpty()) "initial" else "$detail,initial"
    }
}
