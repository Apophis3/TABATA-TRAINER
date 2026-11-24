package com.tuapp.tabatatrainer.rawlog

import java.io.File
import java.io.FileOutputStream

/**
 * Escritor de un archivo CSV del registro en bruto (spec 008, R6).
 *
 * - [open] crea la carpeta y el archivo con su cabecera (una sesión sin datos deja solo la cabecera).
 * - [append] guarda la fila en un búfer en memoria; se vacía al disco cuando han pasado [flushIntervalMs]
 *   desde el último vaciado, al pedirlo (`flushNow`, eventos importantes) y al cerrar.
 * - Cada vaciado abre el archivo en modo añadir y lo cierra: no queda nada abierto entre vaciados.
 * - Nunca lanza excepciones. Al primer fallo marca el archivo como roto, descarta lo pendiente, deja de
 *   escribir y avisa **una sola vez** con [onError].
 *
 * No es seguro entre hilos: se usa siempre desde el mismo hilo (el hilo «RawLog» del registrador).
 *
 * @param nowMs reloj monotónico en milisegundos (en la app, `SystemClock.elapsedRealtime`); inyectable
 *   para los tests.
 */
class RawFileWriter(
    val file: File,
    private val header: String,
    private val nowMs: () -> Long,
    private val flushIntervalMs: Long = DEFAULT_FLUSH_INTERVAL_MS,
    private val onError: (File, Exception) -> Unit = { _, _ -> }
) {
    private val buffer = StringBuilder()
    private var lastFlushMs = 0L
    private var opened = false

    var failed = false
        private set
    var closed = false
        private set

    /** Filas aceptadas (escritas o en el búfer), sin contar la cabecera. */
    var rowCount = 0L
        private set

    fun open(): Boolean {
        if (opened) return !failed
        opened = true
        lastFlushMs = nowMs()
        try {
            file.parentFile?.mkdirs()
            if (!file.exists() || file.length() == 0L) {
                FileOutputStream(file, true).use {
                    it.write((header + RawCsvFormat.LINE_END).toByteArray(Charsets.UTF_8))
                }
            }
        } catch (e: Exception) {
            fail(e)
        }
        return !failed
    }

    fun append(row: String, flushNow: Boolean = false) {
        if (!opened) open()
        if (failed || closed) return
        buffer.append(row).append(RawCsvFormat.LINE_END)
        rowCount++
        if (flushNow) flush() else flushIfDue()
    }

    fun append(row: RawRow, flushNow: Boolean = false) = append(row.toRow(), flushNow)

    /** Vacía si han pasado [flushIntervalMs] desde el último vaciado. Se llama también desde un temporizador. */
    fun flushIfDue() {
        if (nowMs() - lastFlushMs >= flushIntervalMs) flush()
    }

    fun flush() {
        lastFlushMs = nowMs()
        if (failed || buffer.isEmpty()) return
        try {
            FileOutputStream(file, true).use {
                it.write(buffer.toString().toByteArray(Charsets.UTF_8))
            }
            buffer.setLength(0)
        } catch (e: Exception) {
            fail(e)
        }
    }

    fun close() {
        if (closed) return
        flush()
        closed = true
    }

    /** Caracteres pendientes de escribir (para los tests). */
    internal val pendingChars: Int get() = buffer.length

    private fun fail(e: Exception) {
        if (failed) return
        failed = true
        buffer.setLength(0)
        try {
            onError(file, e)
        } catch (_: Exception) {
            // El aviso de error tampoco puede romper la app.
        }
    }

    companion object {
        const val DEFAULT_FLUSH_INTERVAL_MS = 5_000L
    }
}
