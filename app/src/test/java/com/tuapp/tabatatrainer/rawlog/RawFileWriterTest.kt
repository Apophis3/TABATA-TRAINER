package com.tuapp.tabatatrainer.rawlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RawFileWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_000L
    private val errors = mutableListOf<File>()

    private fun writer(file: File = File(tmp.root, "s1/events.csv")) =
        RawFileWriter(file, RawEvent.HEADER, { now }, onError = { f, _ -> errors += f })

    private fun lines(file: File) = file.readText().split("\n").dropLast(1)

    @Test
    fun open_creates_folder_and_writes_header_once() {
        val w = writer()
        assertTrue(w.open())
        assertTrue(w.open())
        assertEquals(listOf(RawEvent.HEADER), lines(w.file))
    }

    @Test
    fun session_without_events_leaves_only_the_header() {
        val w = writer()
        w.open()
        w.close()
        assertEquals(listOf(RawEvent.HEADER), lines(w.file))
        assertEquals(0L, w.rowCount)
    }

    @Test
    fun rows_stay_in_memory_until_five_seconds_pass() {
        val w = writer()
        w.open()
        w.append("1;2;screen_on;")
        now += 4_999
        w.append("3;4;screen_off;")
        assertEquals(listOf(RawEvent.HEADER), lines(w.file))
        assertTrue(w.pendingChars > 0)

        now += 1
        w.append("5;6;screen_on;")
        assertEquals(listOf(RawEvent.HEADER, "1;2;screen_on;", "3;4;screen_off;", "5;6;screen_on;"), lines(w.file))
        assertEquals(0, w.pendingChars)
    }

    @Test
    fun timer_flush_writes_pending_rows_without_new_appends() {
        val w = writer()
        w.open()
        w.append("1;2;screen_on;")
        now += 2_000
        w.flushIfDue()
        assertEquals(1, lines(w.file).size)
        now += 3_000
        w.flushIfDue()
        assertEquals(listOf(RawEvent.HEADER, "1;2;screen_on;"), lines(w.file))
    }

    @Test
    fun important_rows_are_written_at_once() {
        val w = writer()
        w.open()
        w.append("1;2;screen_off;", flushNow = true)
        assertEquals(listOf(RawEvent.HEADER, "1;2;screen_off;"), lines(w.file))
    }

    @Test
    fun close_writes_everything_and_ignores_later_rows() {
        val w = writer()
        w.open()
        w.append("1;2;session_stop;")
        w.close()
        w.append("3;4;screen_on;", flushNow = true)
        assertEquals(listOf(RawEvent.HEADER, "1;2;session_stop;"), lines(w.file))
        assertTrue(w.closed)
        assertEquals(1L, w.rowCount)
    }

    @Test
    fun append_without_open_opens_with_header() {
        val w = writer()
        w.append("1;2;screen_on;", flushNow = true)
        assertEquals(listOf(RawEvent.HEADER, "1;2;screen_on;"), lines(w.file))
    }

    @Test
    fun raw_row_objects_are_written_with_their_format() {
        val w = writer()
        w.append(RawEvent(RawStamp(10L, 20L), RawEventType.SCREEN_OFF, "interactive=false"), flushNow = true)
        assertEquals(listOf(RawEvent.HEADER, "10;20;screen_off;interactive=false"), lines(w.file))
    }

    @Test
    fun existing_file_does_not_get_a_second_header() {
        val file = File(tmp.root, "s1/events.csv")
        writer(file).apply { append("1;2;a;", flushNow = true); close() }
        writer(file).apply { append("3;4;b;", flushNow = true); close() }
        assertEquals(listOf(RawEvent.HEADER, "1;2;a;", "3;4;b;"), lines(file))
    }

    @Test
    fun storage_failure_does_not_throw_and_warns_once() {
        // Un archivo normal donde debería ir la carpeta: no se puede crear el CSV.
        val blocker = tmp.newFile("blocked")
        val w = writer(File(blocker, "events.csv"))
        assertFalse(w.open())
        w.append("1;2;a;", flushNow = true)
        w.append("3;4;b;")
        now += 10_000
        w.flushIfDue()
        w.close()
        assertTrue(w.failed)
        assertEquals(1, errors.size)
        assertEquals(0, w.pendingChars)
    }

    @Test
    fun failure_while_writing_stops_the_file_and_warns_once() {
        val w = writer()
        w.open()
        // El archivo desaparece y en su lugar queda una carpeta: el siguiente vaciado falla.
        w.file.delete()
        w.file.mkdirs()
        w.append("1;2;a;", flushNow = true)
        w.append("3;4;b;", flushNow = true)
        assertTrue(w.failed)
        assertEquals(1, errors.size)
    }

    @Test
    fun an_error_callback_that_throws_does_not_break_the_writer() {
        val blocker = tmp.newFile("blocked2")
        val w = RawFileWriter(File(blocker, "x.csv"), RawEvent.HEADER, { now }, onError = { _, _ -> error("boom") })
        assertFalse(w.open())
        w.append("1;2;a;", flushNow = true)
        w.close()
        assertTrue(w.failed)
    }
}
