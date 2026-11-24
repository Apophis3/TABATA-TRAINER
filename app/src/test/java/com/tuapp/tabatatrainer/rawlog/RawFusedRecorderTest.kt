package com.tuapp.tabatatrainer.rawlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RawFusedRecorderTest {

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

    private fun fix(at: RawStamp, index: Int, size: Int) = LocationFix(
        stamp = at, locTimeMs = 1_700_000_000_000L + index, locElapsedNs = 5_000_000_000L + index, provider = "fused",
        lat = 40.0 + index, lon = -3.0, altM = null, vertAccM = null, hAccM = 4.5f, speedMs = 1.25f,
        speedAccMs = null, bearingDeg = null, bearingAccDeg = null, isMock = false, batchIndex = index, batchSize = size
    )

    private fun fusedFile(c: RawSessionCore) = File(c.dir, RawFiles.GPS_FUSED)

    /** Filas sin la cabecera. */
    private fun lines(file: File): List<String> = file.readText().split("\n").dropLast(1).drop(1)

    private fun events(c: RawSessionCore) = lines(File(c.dir, RawFiles.EVENTS))

    private fun eventsOfType(c: RawSessionCore, type: String) = events(c).filter { it.split(";")[2] == type }

    @Test
    fun started_creates_the_file_with_header_and_writes_source_started_with_params() {
        val c = openCore()
        val r = RawFusedRecorder(c)
        assertTrue(r.started(stamp(), "priority=P;interval_ms=1000"))

        assertTrue(r.started)
        assertEquals(LocationFix.HEADER + "\n", fusedFile(c).readText())
        val started = eventsOfType(c, RawEventType.SOURCE_STARTED).single()
        assertTrue(started.endsWith("\"file=gps_fused.csv;priority=P;interval_ms=1000\""))
    }

    @Test
    fun every_location_of_a_result_is_a_row_with_the_same_stamp_and_its_batch_index() {
        val c = openCore()
        val r = RawFusedRecorder(c)
        r.started(stamp(), "p")
        val received = stamp(2_000)
        r.onResult(received, 3) { i -> fix(received, i, 3) }
        c.stop(stamp(3_000), SERVICE, RawSessionCore.STOP_USER)

        val rows = lines(fusedFile(c))
        assertEquals(3, rows.size)
        rows.forEachIndexed { i, row ->
            val cols = row.split(";")
            assertEquals(received.epochMs.toString(), cols[0])
            assertEquals(received.elapsedNs.toString(), cols[1])
            assertEquals(i.toString(), cols[15])
            assertEquals("3", cols[16])
        }
        assertEquals(1L, r.resultsReceived)
        assertEquals(3L, r.locationsReceived)
        assertEquals(3L, c.fileStats().getValue(RawFiles.GPS_FUSED).rows)
    }

    @Test
    fun missing_fields_stay_empty_in_the_file() {
        val c = openCore()
        val r = RawFusedRecorder(c)
        r.started(stamp(), "p")
        r.onResult(stamp(), 1) { i -> fix(stamp(), i, 1) }
        c.stop(stamp(), SERVICE, RawSessionCore.STOP_USER)

        val cols = lines(fusedFile(c)).single().split(";")
        assertEquals("", cols[7]) // alt_m
        assertEquals("", cols[8]) // vert_acc_m
        assertEquals("4.5", cols[9]) // h_acc_m
        assertEquals("", cols[11]) // speed_acc_ms
        assertEquals("", cols[12]) // bearing_deg
        assertEquals("", cols[13]) // bearing_acc_deg
    }

    @Test
    fun an_empty_result_counts_but_writes_no_row() {
        val c = openCore()
        val r = RawFusedRecorder(c)
        r.started(stamp(), "p")
        r.onResult(stamp(), 0) { error("no hay posiciones") }
        c.stop(stamp(), SERVICE, RawSessionCore.STOP_USER)

        assertEquals(1L, r.resultsReceived)
        assertEquals(0L, r.locationsReceived)
        assertTrue(lines(fusedFile(c)).isEmpty())
    }

    @Test
    fun rows_go_to_the_buffer_and_are_not_flushed_at_once() {
        val c = openCore()
        val r = RawFusedRecorder(c)
        r.started(stamp(), "p")
        r.onResult(stamp(), 2) { i -> fix(stamp(), i, 2) }

        assertTrue(lines(fusedFile(c)).isEmpty())
        nowMs += RawFileWriter.DEFAULT_FLUSH_INTERVAL_MS
        c.flushIfDue()
        assertEquals(2, lines(fusedFile(c)).size)
    }

    @Test
    fun availability_writes_location_available_true_or_false() {
        val c = openCore()
        val r = RawFusedRecorder(c)
        r.started(stamp(), "p")
        r.onAvailability(stamp(), false)
        r.onAvailability(stamp(), true)

        val details = eventsOfType(c, RawEventType.LOCATION_AVAILABLE).map { it.split(";")[3] }
        assertEquals(listOf("false", "true"), details)
    }

    @Test
    fun failure_before_starting_writes_source_error_and_no_file() {
        val c = openCore()
        val r = RawFusedRecorder(c)
        r.failed(stamp(), "no_location_permission")

        assertFalse(r.started)
        assertEquals("no_location_permission", r.lastError)
        assertFalse(fusedFile(c).exists())
        val error = eventsOfType(c, RawEventType.SOURCE_ERROR).single()
        assertTrue(error.endsWith("\"file=gps_fused.csv;reason=no_location_permission\""))
    }

    @Test
    fun nothing_is_written_after_the_session_stops() {
        val c = openCore()
        val r = RawFusedRecorder(c)
        r.started(stamp(), "p")
        r.onResult(stamp(), 1) { i -> fix(stamp(), i, 1) }
        c.stop(stamp(), SERVICE, RawSessionCore.STOP_USER)
        val eventsAtStop = events(c).size

        r.onResult(stamp(), 2) { i -> fix(stamp(), i, 2) }
        r.onAvailability(stamp(), true)

        assertEquals(1L, r.resultsReceived)
        assertEquals(1L, r.locationsReceived)
        assertEquals(1, lines(fusedFile(c)).size)
        assertEquals(eventsAtStop, events(c).size)
    }

    @Test
    fun started_on_a_closed_session_does_nothing() {
        val c = RawSessionCore(tmp.root, "abc-123", SERVICE, "FREE_RIDE", clock = { stamp() })
        val r = RawFusedRecorder(c)

        assertFalse(r.started(stamp(), "p"))
        assertFalse(r.started)
        assertNull(r.lastError)
        assertFalse(fusedFile(c).exists())
    }

    private companion object {
        const val SERVICE = "FreeRideService"
    }
}
