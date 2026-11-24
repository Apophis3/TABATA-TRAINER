package com.tuapp.tabatatrainer.rawlog

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Carpetas del registro en bruto en disco (spec 008, R4, R13, R14): localizar, medir, comprimir y borrar.
 * Solo `java.io` / `java.util.zip` (sin dependencias nuevas). Nunca lanza excepciones.
 *
 * @param root carpeta raíz, en la app `File(context.filesDir, RawFiles.ROOT_DIR)`.
 */
class RawLogExporter(private val root: File) {

    /**
     * Carpeta de la sesión. `null` si el identificador no es seguro como nombre de carpeta (evita salir de
     * [root] con `..` o barras). Los `sessionId` de la app son UUID.
     */
    fun sessionDir(sessionId: String): File? =
        if (RawFiles.isSafeSessionId(sessionId)) File(root, sessionId) else null

    fun exists(sessionId: String): Boolean = sessionDir(sessionId)?.isDirectory == true

    /** Suma de los tamaños de los archivos de la carpeta (0 si no existe). */
    fun sizeBytes(sessionId: String): Long {
        val dir = sessionDir(sessionId) ?: return 0L
        if (!dir.isDirectory) return 0L
        return try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Comprime la carpeta de la sesión en [outFile]. Las entradas van dentro de una carpeta con el nombre
     * de la sesión (`<sessionId>/gps_fused.csv`, …). Devuelve [outFile] o `null` si no hay carpeta o falla.
     */
    fun zip(sessionId: String, outFile: File): File? {
        val dir = sessionDir(sessionId) ?: return null
        if (!dir.isDirectory) return null
        return try {
            outFile.parentFile?.mkdirs()
            ZipOutputStream(FileOutputStream(outFile)).use { zip ->
                dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { file ->
                    zip.putNextEntry(ZipEntry("$sessionId/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            outFile
        } catch (_: Exception) {
            outFile.delete()
            null
        }
    }

    /** Borra la carpeta de la sesión. `true` si ya no existe al terminar. */
    fun delete(sessionId: String): Boolean {
        val dir = sessionDir(sessionId) ?: return false
        return try {
            if (dir.exists()) dir.deleteRecursively() else true
        } catch (_: Exception) {
            false
        }
    }
}
