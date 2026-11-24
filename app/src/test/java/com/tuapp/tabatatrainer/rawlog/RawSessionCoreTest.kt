package com.tuapp.tabatatrainer.rawlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RawSessionCoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var nowMs = 1_000L
    private val logs = mutableListOf<String>()
    private val seen = mutableListOf<RawEvent>()

    private fun stamp(ms: Long = nowMs) = RawStamp(1_700_000_000_000L + ms, ms * 1_000_000)

    private fun core(
        sessionId: String = "abc-123",
        service: String = FREE_RIDE,
        activity: String = "FREE_RIDE"
    ) = RawSessionCore(
        root = tmp.root,
        sessionId = sessionId,
        service = service,
        activityType = activity,
        clock = { stamp() },
        log = { logs += it },
        onEvent = { seen += it }
    )

    /** Filas de `events.csv` sin la cabecera. */
    private fun rows(c: RawSessionCore): List<String> =
        File(c.dir, RawFiles.EVENTS).readText().split("\n").dropLast(1).drop(1)

    private fun types(c: RawSessionCore) = rows(c).map { it.split(";")[2] }

    @Test
    fun open_writes_services_created_in_time_order_with_their_own_time_then_session_start() {
        val c = core()
        val workoutCreated = stamp(300)
        val rideCreated = stamp(200)
        assertTrue(c.open(stamp(500), mapOf(WORKOUT to workoutCreated, FREE_RIDE to rideCreated)))

        val r = rows(c)
        assertEquals(
            listOf(RawEventType.SERVICE_CREATED, RawEventType.SERVICE_CREATED, RawEventType.SESSION_START),
            types(c)
        )
        assertTrue(r[0].startsWith("${rideCreated.epochMs};${rideCreated.elapsedNs};service_created;"))
        assertTrue(r[0].endsWith("service=$FREE_RIDE"))
        assertTrue(r[1].endsWith("service=$WORKOUT"))
        assertEquals("${stamp(500).epochMs};${stamp(500).elapsedNs};session_start;\"activity=FREE_RIDE;service=$FREE_RIDE\"", r[2])
    }

    @Test
    fun session_without_events_has_header_and_session_start_only() {
        val c = core()
        c.open(stamp(), emptyMap())
        assertEquals(listOf(RawEventType.SESSION_START), types(c))
        assertEquals(1L, c.fileStats()[RawFiles.EVENTS]?.rows)
    }

    @Test
    fun events_are_flushed_at_once() {
        val c = core()
        c.open(stamp(), emptyMap())
        c.event(stamp(), RawEventType.SCREEN_OFF, "interactive=false")
        // Sin cerrar ni esperar 5 s: ya está en disco
        assertEquals(listOf(RawEventType.SESSION_START, RawEventType.SCREEN_OFF), types(c))
    }

    @Test
    fun nothing_is_written_before_open_or_after_stop() {
        val c = core()
        c.event(stamp(), RawEventType.SCREEN_ON, null)
        assertFalse(c.dir.exists())
        assertNull(c.openWriter(RawFiles.BARO, BaroSample.HEADER))

        c.open(stamp(), emptyMap())
        assertTrue(c.stop(stamp(), FREE_RIDE, RawSessionCore.STOP_USER))
        c.event(stamp(), RawEventType.SCREEN_ON, null)
        assertNull(c.openWriter(RawFiles.BARO, BaroSample.HEADER))
        assertEquals(listOf(RawEventType.SESSION_START, RawEventType.SESSION_STOP), types(c))
    }

    @Test
    fun pause_and_resume_from_the_other_service_are_ignored() {
        val c = core()
        c.open(stamp(), emptyMap())
        c.sessionEvent(stamp(), WORKOUT, RawEventType.SESSION_PAUSE)
        c.sessionEvent(stamp(), FREE_RIDE, RawEventType.SESSION_PAUSE)
        c.sessionEvent(stamp(), WORKOUT, RawEventType.SESSION_RESUME)
        c.sessionEvent(stamp(), FREE_RIDE, RawEventType.SESSION_RESUME)
        assertEquals(
            listOf(RawEventType.SESSION_START, RawEventType.SESSION_PAUSE, RawEventType.SESSION_RESUME),
            types(c)
        )
    }

    @Test
    fun stop_from_the_other_service_does_not_close() {
        val c = core()
        c.open(stamp(), emptyMap())
        assertFalse(c.stop(stamp(), WORKOUT, RawSessionCore.STOP_USER))
        assertTrue(c.isOpen)
    }

    @Test
    fun destroying_the_other_service_is_noted_but_the_session_stays_open() {
        val c = core()
        c.open(stamp(), emptyMap())
        assertFalse(c.serviceDestroyed(stamp(), WORKOUT))
        assertTrue(c.isOpen)
        assertEquals(listOf(RawEventType.SESSION_START, RawEventType.SERVICE_DESTROYED), types(c))
        assertTrue(rows(c)[1].endsWith("service=$WORKOUT"))
    }

    @Test
    fun destroying_the_session_service_closes_it_with_reason_service_destroyed() {
        val c = core()
        c.open(stamp(), emptyMap())
        nowMs = 9_000
        assertTrue(c.serviceDestroyed(stamp(), FREE_RIDE))
        assertFalse(c.isOpen)
        assertEquals(RawSessionCore.STOP_SERVICE_DESTROYED, c.stopReason)
        assertEquals(stamp(9_000), c.stopStamp)
        assertEquals(
            listOf(RawEventType.SESSION_START, RawEventType.SERVICE_DESTROYED, RawEventType.SESSION_STOP),
            types(c)
        )
        assertTrue(rows(c)[2].endsWith("reason=service_destroyed"))
    }

    @Test
    fun stopping_twice_does_nothing_the_second_time() {
        val c = core()
        c.open(stamp(), emptyMap())
        assertTrue(c.stop(stamp(), FREE_RIDE, RawSessionCore.STOP_COMPLETED))
        assertFalse(c.stop(stamp(), FREE_RIDE, RawSessionCore.STOP_USER))
        assertEquals(RawSessionCore.STOP_COMPLETED, c.stopReason)
        assertEquals(1, types(c).count { it == RawEventType.SESSION_STOP })
    }

    @Test
    fun replaced_session_is_closed_with_its_reason() {
        val c = core()
        c.open(stamp(), emptyMap())
        assertTrue(c.stop(stamp(), FREE_RIDE, RawSessionCore.STOP_REPLACED))
        assertTrue(rows(c).last().endsWith("session_stop;reason=replaced"))
    }

    @Test
    fun opening_twice_or_with_an_unsafe_id_is_refused() {
        val c = core()
        assertTrue(c.open(stamp(), emptyMap()))
        assertFalse(c.open(stamp(), emptyMap()))
        assertEquals(1, types(c).count { it == RawEventType.SESSION_START })

        val bad = core(sessionId = "../fuera")
        assertFalse(bad.open(stamp(), emptyMap()))
        assertFalse(bad.isOpen)
        assertFalse(File(tmp.root.parentFile, "fuera").exists())
    }

    @Test
    fun write_error_of_another_file_is_noted_in_events_and_logcat() {
        val c = core()
        c.open(stamp(), emptyMap())
        // Una carpeta con el nombre del archivo: escribir en ella falla
        File(c.dir, RawFiles.BARO).mkdirs()
        val baro = c.openWriter(RawFiles.BARO, BaroSample.HEADER)!!
        baro.append("x", flushNow = true)

        assertTrue(baro.failed)
        assertEquals(1, c.writeErrors)
        assertEquals(1, logs.size)
        assertTrue(logs[0].startsWith("write_error abc-123/baro.csv"))
        val last = rows(c).last()
        assertEquals(RawEventType.WRITE_ERROR, last.split(";")[2])
        assertTrue(last.contains("file=baro.csv;reason="))
        assertEquals(true, c.fileStats()[RawFiles.BARO]?.failed)
        assertEquals(false, c.fileStats()[RawFiles.EVENTS]?.failed)
    }

    @Test
    fun error_in_events_file_goes_only_to_logcat() {
        val c = core()
        c.open(stamp(), emptyMap())
        c.reportWriteError(RawFiles.EVENTS, java.io.IOException("disco lleno"))
        assertEquals(1, c.writeErrors)
        assertEquals(1, logs.size)
        assertEquals(listOf(RawEventType.SESSION_START), types(c))
    }

    @Test
    fun open_writer_returns_the_same_writer_for_the_same_file() {
        val c = core()
        c.open(stamp(), emptyMap())
        val a = c.openWriter(RawFiles.BARO, BaroSample.HEADER)
        val b = c.openWriter(RawFiles.BARO, BaroSample.HEADER)
        assertTrue(a === b)
        a!!.append("1;2;3;1013.25;3")
        assertEquals(1L, c.fileStats()[RawFiles.BARO]?.rows)
    }

    @Test
    fun stop_closes_and_flushes_every_file() {
        val c = core()
        c.open(stamp(), emptyMap())
        val baro = c.openWriter(RawFiles.BARO, BaroSample.HEADER)!!
        baro.append("1;2;3;1013.25;3")   // queda en memoria (menos de 5 s)
        assertEquals(listOf(BaroSample.HEADER), File(c.dir, RawFiles.BARO).readText().split("\n").dropLast(1))

        c.stop(stamp(), FREE_RIDE, RawSessionCore.STOP_USER)
        assertTrue(baro.closed)
        assertEquals(
            listOf(BaroSample.HEADER, "1;2;3;1013.25;3"),
            File(c.dir, RawFiles.BARO).readText().split("\n").dropLast(1)
        )
    }

    @Test
    fun on_event_sees_only_events_really_written() {
        val c = core()
        c.event(stamp(), RawEventType.SCREEN_ON, null)          // antes de abrir
        c.open(stamp(), emptyMap())
        c.sessionEvent(stamp(), WORKOUT, RawEventType.SESSION_PAUSE)   // de otro servicio
        c.event(stamp(), RawEventType.POWER_SAVE, "enabled=true")
        assertEquals(listOf(RawEventType.SESSION_START, RawEventType.POWER_SAVE), seen.map { it.type })
    }

    @Test
    fun initial_detail_appends_initial_suffix() {
        assertEquals("interactive=true,initial", RawSessionCore.initial("interactive=true"))
        assertEquals("initial", RawSessionCore.initial(null))
        assertEquals("initial", RawSessionCore.initial(""))
    }

    private companion object {
        const val FREE_RIDE = "FreeRideService"
        const val WORKOUT = "WorkoutService"
    }
}
