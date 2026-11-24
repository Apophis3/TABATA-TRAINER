package com.tuapp.tabatatrainer.rawlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RawBatteryRecorderTest {

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

    private fun sample(trigger: String, currentNowUa: Long? = -250_000L, screenOn: Boolean? = true) = BatterySample(
        stamp = stamp(), levelPct = 87, chargeCounterUah = 3_900_000, currentNowUa = currentNowUa, tempDc = 312,
        status = 3, plugged = 0, screenOn = screenOn, trigger = trigger
    )

    private fun batteryFile(c: RawSessionCore) = File(c.dir, RawFiles.BATTERY)

    /** Filas sin la cabecera. */
    private fun lines(file: File): List<String> = file.readText().split("\n").dropLast(1).drop(1)

    @Test
    fun started_creates_the_file_with_header_and_writes_source_started() {
        val c = openCore()
        val r = RawBatteryRecorder(c)
        assertTrue(r.started(stamp(), "period_ms=60000"))

        assertTrue(r.started)
        assertEquals(BatterySample.HEADER + "\n", batteryFile(c).readText())
        val started = lines(File(c.dir, RawFiles.EVENTS)).single { it.split(";")[2] == RawEventType.SOURCE_STARTED }
        assertTrue(started.endsWith("\"file=battery.csv;period_ms=60000\""))
    }

    @Test
    fun every_reading_is_on_disk_at_once_and_counted_by_trigger() {
        val c = openCore()
        val r = RawBatteryRecorder(c)
        r.started(stamp(), "p")
        r.onReading(sample(BatterySample.TRIGGER_START))
        r.onReading(sample(BatterySample.TRIGGER_SCREEN_OFF, screenOn = false))
        r.onReading(sample(BatterySample.TRIGGER_PERIODIC))
        r.onReading(sample(BatterySample.TRIGGER_PERIODIC))

        // Sin esperar los 5 s ni cerrar: ya está escrito.
        val rows = lines(batteryFile(c))
        assertEquals(4, rows.size)
        assertEquals(
            listOf("87", "3900000", "-250000", "312", "3", "0", "false", "screen_off"),
            rows[1].split(";").drop(2)
        )
        assertEquals(mapOf("start" to 1L, "screen_off" to 1L, "periodic" to 2L), r.rowsByTrigger)
    }

    @Test
    fun unknown_values_stay_empty() {
        val c = openCore()
        val r = RawBatteryRecorder(c)
        r.started(stamp(), "p")
        r.onReading(sample(BatterySample.TRIGGER_STOP, currentNowUa = null, screenOn = null))

        val cols = lines(batteryFile(c)).single().split(";")
        assertEquals("", cols[4])
        assertEquals("", cols[8])
    }

    @Test
    fun min_value_from_battery_manager_means_unknown() {
        assertNull(RawBatteryRecorder.rawInt(Int.MIN_VALUE))
        assertNull(RawBatteryRecorder.rawLong(Long.MIN_VALUE))
        assertEquals(0, RawBatteryRecorder.rawInt(0))
        assertEquals(-1_234L, RawBatteryRecorder.rawLong(-1_234L))
    }

    @Test
    fun readings_before_start_or_after_stop_are_ignored() {
        val c = openCore()
        val r = RawBatteryRecorder(c)
        r.onReading(sample(BatterySample.TRIGGER_START))
        assertFalse(batteryFile(c).exists())

        r.started(stamp(), "p")
        r.onReading(sample(BatterySample.TRIGGER_STOP))
        c.stop(stamp(), SERVICE, RawSessionCore.STOP_USER)
        r.onReading(sample(BatterySample.TRIGGER_PERIODIC))

        assertEquals(1, lines(batteryFile(c)).size)
        assertEquals(mapOf("stop" to 1L), r.rowsByTrigger)
    }

    @Test
    fun started_on_a_closed_session_creates_nothing() {
        val c = openCore()
        c.stop(stamp(), SERVICE, RawSessionCore.STOP_USER)
        val r = RawBatteryRecorder(c)

        assertFalse(r.started(stamp(), "p"))
        assertFalse(r.started)
        assertFalse(batteryFile(c).exists())
    }

    private companion object {
        const val SERVICE = "FreeRideService"
    }
}
