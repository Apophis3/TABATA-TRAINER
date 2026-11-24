package com.tuapp.tabatatrainer.rawlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RawBaroRecorderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var nowMs = 1_000L

    private fun stamp(ms: Long = nowMs) = RawStamp(1_700_000_000_000L + ms, ms * 1_000_000)

    private fun openCore(): RawSessionCore = RawSessionCore(
        root = tmp.root,
        sessionId = "abc-123",
        service = SERVICE,
        activityType = "FREE_RIDE",
        clock = { stamp() }
    ).also { assertTrue(it.open(stamp())) }

    private val info = BaroSensorInfo("LPS22", "STM", 1260f, 0.0002f, 40_000, 300)

    private fun baroFile(c: RawSessionCore) = File(c.dir, RawFiles.BARO)

    /** Filas sin la cabecera. */
    private fun lines(file: File): List<String> = file.readText().split("\n").dropLast(1).drop(1)

    private fun eventsOfType(c: RawSessionCore, type: String) =
        lines(File(c.dir, RawFiles.EVENTS)).filter { it.split(";")[2] == type }

    @Test
    fun started_creates_the_file_with_header_and_keeps_the_sensor_info() {
        val c = openCore()
        val r = RawBaroRecorder(c)
        assertTrue(r.started(stamp(), info, "sampling_period_us=200000"))

        assertEquals(true, r.available)
        assertEquals(info, r.sensorInfo)
        assertEquals(BaroSample.HEADER + "\n", baroFile(c).readText())
        val started = eventsOfType(c, RawEventType.SOURCE_STARTED).single()
        assertTrue(started.endsWith("\"file=baro.csv;sampling_period_us=200000\""))
    }

    @Test
    fun samples_every_200_ms_for_10_s_give_about_one_row_per_second() {
        val c = openCore()
        val r = RawBaroRecorder(c)
        r.started(stamp(), info, "p")
        for (i in 0 until 50) r.onSample(stamp(), 9_000_000_000L + i * 200_000_000L, 1013.25f, 3)
        c.stop(stamp(), SERVICE, RawSessionCore.STOP_USER)

        val rows = lines(baroFile(c))
        assertEquals(10, rows.size)
        assertEquals(50L, r.decimator.received)
        assertEquals(10L, r.decimator.written)
        assertEquals(40L, r.decimator.discarded)
        val cols = rows.first().split(";")
        assertEquals("9000000000", cols[2])
        assertEquals("1013.25", cols[3])
        assertEquals("3", cols[4])
    }

    @Test
    fun missing_accuracy_stays_empty() {
        val c = openCore()
        val r = RawBaroRecorder(c)
        r.started(stamp(), info, "p")
        r.onSample(stamp(), 1L, 1000f, null)
        c.stop(stamp(), SERVICE, RawSessionCore.STOP_USER)

        assertEquals("", lines(baroFile(c)).single().split(";")[4])
    }

    @Test
    fun no_sensor_means_no_file_and_a_source_error() {
        val c = openCore()
        val r = RawBaroRecorder(c)
        r.unavailable(stamp())

        assertEquals(false, r.available)
        assertFalse(r.started)
        assertFalse(baroFile(c).exists())
        assertEquals(RawBaroRecorder.REASON_NO_SENSOR, r.lastError)
        val error = eventsOfType(c, RawEventType.SOURCE_ERROR).single()
        assertTrue(error.endsWith("\"file=baro.csv;reason=no_pressure_sensor\""))
    }

    @Test
    fun register_failure_keeps_the_info_without_a_file() {
        val c = openCore()
        val r = RawBaroRecorder(c)
        r.failed(stamp(), info, "register_listener_false")

        assertEquals(true, r.available)
        assertEquals(info, r.sensorInfo)
        assertFalse(baroFile(c).exists())
        assertEquals("register_listener_false", r.lastError)
    }

    @Test
    fun samples_before_start_or_after_stop_are_ignored() {
        val c = openCore()
        val r = RawBaroRecorder(c)
        r.onSample(stamp(), 1L, 1000f, 3)
        assertEquals(0L, r.decimator.received)

        r.started(stamp(), info, "p")
        r.onSample(stamp(), 2_000_000_000L, 1000f, 3)
        c.stop(stamp(), SERVICE, RawSessionCore.STOP_USER)
        r.onSample(stamp(), 4_000_000_000L, 1000f, 3)

        assertEquals(1L, r.decimator.received)
        assertEquals(1, lines(baroFile(c)).size)
    }

    @Test
    fun rows_go_to_the_buffer_and_are_not_flushed_at_once() {
        val c = openCore()
        val r = RawBaroRecorder(c)
        r.started(stamp(), info, "p")
        r.onSample(stamp(), 1L, 1000f, 3)

        assertTrue(lines(baroFile(c)).isEmpty())
        nowMs += RawFileWriter.DEFAULT_FLUSH_INTERVAL_MS
        c.flushIfDue()
        assertEquals(1, lines(baroFile(c)).size)
        assertNull(r.lastError)
    }

    private companion object {
        const val SERVICE = "FreeRideService"
    }
}
