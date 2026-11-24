package com.tuapp.tabatatrainer.rawlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class RawLogExporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val id = "bb4a1d9b-60c0-4bb4-82b2-7b49501e59ec"

    private fun exporterWithSession(): Pair<RawLogExporter, File> {
        val root = tmp.newFolder(RawFiles.ROOT_DIR)
        val dir = File(root, id).apply { mkdirs() }
        File(dir, RawFiles.EVENTS).writeText(RawEvent.HEADER + "\n1;2;session_start;\n")
        File(dir, RawFiles.BARO).writeText(BaroSample.HEADER + "\n")
        File(dir, RawFiles.META).writeText("{\"sessionId\":\"$id\"}")
        return RawLogExporter(root) to dir
    }

    @Test
    fun size_is_the_sum_of_the_files() {
        val (exporter, dir) = exporterWithSession()
        val expected = dir.listFiles()!!.sumOf { it.length() }
        assertEquals(expected, exporter.sizeBytes(id))
        assertTrue(exporter.exists(id))
    }

    @Test
    fun missing_session_has_size_zero_and_no_zip() {
        val exporter = RawLogExporter(tmp.newFolder(RawFiles.ROOT_DIR))
        assertFalse(exporter.exists("otra"))
        assertEquals(0L, exporter.sizeBytes("otra"))
        assertNull(exporter.zip("otra", File(tmp.root, "otra.zip")))
    }

    @Test
    fun zip_contains_every_file_with_the_same_content() {
        val (exporter, dir) = exporterWithSession()
        val out = exporter.zip(id, File(tmp.root, "cache/rawlog_$id.zip"))
        assertNotNull(out)
        ZipFile(out!!).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertEquals(
                listOf(RawFiles.BARO, RawFiles.EVENTS, RawFiles.META).map { "$id/$it" },
                names
            )
            dir.listFiles()!!.forEach { file ->
                val entry = zip.getEntry("$id/${file.name}")
                val content = zip.getInputStream(entry).use { it.readBytes() }
                assertTrue(file.name, content.contentEquals(file.readBytes()))
            }
        }
    }

    @Test
    fun zipping_keeps_the_session_folder_untouched() {
        val (exporter, dir) = exporterWithSession()
        val before = dir.listFiles()!!.associate { it.name to it.readBytes().toList() }
        assertNotNull(exporter.zip(id, File(tmp.root, "cache/rawlog_$id.zip")))
        assertTrue(dir.isDirectory)
        assertEquals(before, dir.listFiles()!!.associate { it.name to it.readBytes().toList() })
        assertEquals(before.values.sumOf { it.size.toLong() }, exporter.sizeBytes(id))
    }

    @Test
    fun delete_removes_the_folder_and_is_fine_if_it_is_already_gone() {
        val (exporter, dir) = exporterWithSession()
        assertTrue(exporter.delete(id))
        assertFalse(dir.exists())
        assertTrue(exporter.delete(id))
    }

    @Test
    fun zip_name_is_rawlog_underscore_id() {
        assertEquals("rawlog_$id.zip", RawLogExporter.zipName(id))
    }

    @Test
    fun clearing_old_zips_leaves_other_cache_files_alone() {
        val cache = tmp.newFolder("cache")
        File(cache, "rawlog_viejo1.zip").writeText("x")
        File(cache, "rawlog_viejo2.zip").writeText("y")
        val excel = File(cache, "sesion.xlsx").apply { writeText("e") }
        val otherZip = File(cache, "otro.zip").apply { writeText("o") }
        val sub = File(cache, "sub").apply { mkdirs() }
        val nested = File(sub, "rawlog_dentro.zip").apply { writeText("n") }

        assertEquals(2, RawLogExporter.clearOldZips(cache))
        assertEquals(setOf("sesion.xlsx", "otro.zip", "sub"), cache.list()!!.toSet())
        assertTrue(excel.exists())
        assertTrue(otherZip.exists())
        assertTrue(nested.exists())
        assertEquals(0, RawLogExporter.clearOldZips(File(tmp.root, "no_existe")))
    }

    @Test
    fun export_zip_replaces_old_zips_and_keeps_only_the_new_one() {
        val (exporter, _) = exporterWithSession()
        val cache = tmp.newFolder("cache")
        File(cache, "rawlog_otra-sesion.zip").writeText("viejo")
        val keep = File(cache, "notas.txt").apply { writeText("k") }

        val out = exporter.exportZip(id, cache)
        assertNotNull(out)
        assertEquals(File(cache, "rawlog_$id.zip"), out)
        assertEquals(setOf("rawlog_$id.zip", "notas.txt"), cache.list()!!.toSet())
        assertTrue(keep.exists())
        ZipFile(out!!).use { assertEquals(3, it.size()) }

        // Sin carpeta de sesión no se crea nada ni se borra nada.
        assertNull(exporter.exportZip("otra", cache))
        assertTrue(File(cache, "rawlog_$id.zip").exists())
    }

    @Test
    fun size_is_formatted_in_spanish() {
        assertEquals("0 B", RawLogExporter.formatSize(0))
        assertEquals("850 B", RawLogExporter.formatSize(850))
        assertEquals("1,0 KB", RawLogExporter.formatSize(1024))
        assertEquals("12,4 KB", RawLogExporter.formatSize(12_698))
        assertEquals("1,2 MB", RawLogExporter.formatSize(1_258_291))
    }

    @Test
    fun unsafe_ids_are_rejected() {
        val (exporter, _) = exporterWithSession()
        val outside = tmp.newFile("secreto.txt")
        assertNull(exporter.sessionDir("../secreto.txt"))
        assertNull(exporter.sessionDir("a/b"))
        assertNull(exporter.sessionDir(""))
        assertFalse(exporter.delete(".."))
        assertTrue(outside.exists())
    }
}
