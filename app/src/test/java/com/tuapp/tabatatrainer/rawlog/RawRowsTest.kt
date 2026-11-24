package com.tuapp.tabatatrainer.rawlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawRowsTest {

    private val stamp = RawStamp(epochMs = 1_790_000_000_123L, elapsedNs = 987_654_321_000L)

    private fun columns(line: String) = line.split(";").size

    private val fullFix = LocationFix(
        stamp = stamp, locTimeMs = 1_790_000_000_000L, locElapsedNs = 987_000_000_000L, provider = "fused",
        lat = 40.4168, lon = -3.7038, altM = 657.5, vertAccM = 3.5f, hAccM = 4.2f, speedMs = 1.25f,
        speedAccMs = 0.5f, bearingDeg = 182.5f, bearingAccDeg = 12.0f, isMock = false, batchIndex = 0, batchSize = 1
    )

    @Test
    fun fused_row_with_every_field() {
        assertEquals(
            "1790000000123;987654321000;1790000000000;987000000000;fused;40.4168;-3.7038;657.5;3.5;4.2;1.25;0.5;182.5;12.0;false;0;1",
            fullFix.toRow()
        )
        assertEquals(columns(LocationFix.HEADER), columns(fullFix.toRow()))
    }

    @Test
    fun fused_row_with_missing_fields_leaves_them_empty() {
        val fix = fullFix.copy(
            locElapsedNs = null, provider = null, altM = null, vertAccM = null, hAccM = null, speedMs = null,
            speedAccMs = null, bearingDeg = null, bearingAccDeg = null, isMock = null, batchIndex = 2, batchSize = 3
        )
        assertEquals(
            "1790000000123;987654321000;1790000000000;;;40.4168;-3.7038;;;;;;;;;2;3",
            fix.toRow()
        )
        assertEquals(columns(LocationFix.HEADER), columns(fix.toRow()))
    }

    @Test
    fun fused_header_matches_the_spec() {
        assertEquals(
            "epoch_ms;elapsed_ns;loc_time_ms;loc_elapsed_ns;provider;lat;lon;alt_m;vert_acc_m;h_acc_m;speed_ms;" +
                "speed_acc_ms;bearing_deg;bearing_acc_deg;is_mock;batch_index;batch_size",
            LocationFix.HEADER
        )
    }

    @Test
    fun baro_row() {
        val s = BaroSample(stamp, sensorTsNs = 987_600_000_000L, pressureHpa = 941.37f, accuracy = 3)
        assertEquals("1790000000123;987654321000;987600000000;941.37;3", s.toRow())
        assertEquals("epoch_ms;elapsed_ns;sensor_ts_ns;pressure_hpa;accuracy", BaroSample.HEADER)
        assertEquals(columns(BaroSample.HEADER), columns(s.copy(accuracy = null).toRow()))
    }

    @Test
    fun battery_row_keeps_raw_values_and_sign() {
        val b = BatterySample(
            stamp, levelPct = 87, chargeCounterUah = 4_123_000, currentNowUa = -412_000L, tempDc = 312,
            status = 3, plugged = 0, screenOn = false, trigger = BatterySample.TRIGGER_SCREEN_OFF
        )
        assertEquals("1790000000123;987654321000;87;4123000;-412000;312;3;0;false;screen_off", b.toRow())
        assertEquals(columns(BatterySample.HEADER), columns(b.toRow()))
    }

    @Test
    fun battery_row_without_counters_leaves_them_empty() {
        val b = BatterySample(stamp, null, null, null, null, null, null, null, BatterySample.TRIGGER_PERIODIC)
        assertEquals("1790000000123;987654321000;;;;;;;;periodic", b.toRow())
    }

    @Test
    fun event_row_quotes_detail_with_separator() {
        val e = RawEvent(stamp, RawEventType.PERMISSIONS, "fine=true;coarse=true;background=false")
        assertEquals("1790000000123;987654321000;permissions;\"fine=true;coarse=true;background=false\"", e.toRow())
        assertEquals("epoch_ms;elapsed_ns;type;detail", RawEvent.HEADER)
    }

    @Test
    fun event_row_without_detail() {
        assertEquals(
            "1790000000123;987654321000;screen_off;",
            RawEvent(stamp, RawEventType.SCREEN_OFF, null).toRow()
        )
    }

    @Test
    fun gnss_sat_row() {
        val s = GnssSat(stamp, 12L, 7, 1, 38.5f, true, 45.0f, 270.0f, true, false)
        assertEquals("1790000000123;987654321000;12;7;1;38.5;true;45.0;270.0;true;false", s.toRow())
        assertEquals(columns(GnssSat.HEADER), columns(s.toRow()))
    }

    @Test
    fun every_header_starts_with_epoch_and_elapsed() {
        listOf(LocationFix.HEADER, BaroSample.HEADER, BatterySample.HEADER, RawEvent.HEADER, GnssSat.HEADER)
            .forEach { assertTrue(it, it.startsWith("epoch_ms;elapsed_ns;")) }
    }
}
