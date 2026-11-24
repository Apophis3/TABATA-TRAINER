package com.tuapp.tabatatrainer.rawlog

import java.io.File
import java.io.FileOutputStream
import java.util.Locale
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

    /**
     * Borra los zips viejos de [cacheDir] y comprime la sesión en `cacheDir/rawlog_<id>.zip` (T-05). Los viejos
     * se borran aquí y no al compartir, porque la app que recibe el zip lo lee más tarde.
     */
    fun exportZip(sessionId: String, cacheDir: File): File? {
        if (!exists(sessionId)) return null
        clearOldZips(cacheDir)
        return zip(sessionId, File(cacheDir, zipName(sessionId)))
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

    companion object {
        private const val ZIP_PREFIX = "rawlog_"
        private const val ZIP_SUFFIX = ".zip"

        fun zipName(sessionId: String): String = "$ZIP_PREFIX$sessionId$ZIP_SUFFIX"

        /**
         * Borra los `rawlog_*.zip` del primer nivel de [cacheDir] (nada más de la caché). Devuelve cuántos
         * borró. Nunca lanza excepciones.
         */
        fun clearOldZips(cacheDir: File): Int = try {
            cacheDir.listFiles()
                ?.filter { it.isFile && it.name.startsWith(ZIP_PREFIX) && it.name.endsWith(ZIP_SUFFIX) }
                ?.count { it.delete() }
                ?: 0
        } catch (_: Exception) {
            0
        }

        /** Tamaño para la interfaz, en español: «850 B», «12,4 KB», «1,2 MB». */
        fun formatSize(bytes: Long): String {
            val kb = 1024.0
            val mb = kb * 1024
            return when {
                bytes < kb -> "$bytes B"
                bytes < mb -> oneDecimal(bytes / kb) + " KB"
                else -> oneDecimal(bytes / mb) + " MB"
            }
        }

        private fun oneDecimal(value: Double): String =
            String.format(Locale.ROOT, "%.1f", value).replace('.', ',')
    }
}
