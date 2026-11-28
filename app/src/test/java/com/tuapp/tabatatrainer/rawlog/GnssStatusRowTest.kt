package com.tuapp.tabatatrainer.rawlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GnssStatusRowTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val stamp = RawStamp(epochMs = 1_790_000_000_123L, elapsedNs = 987_654_321_000L)

    private var nextSvid = 1

    /** Cada llamada es un satélite distinto, salvo que se dé [svid]. */
    private fun sat(constellation: Int, cn0: Float, used: Boolean, svid: Int = nextSvid++) =
        GnssSatInfo(constellation, svid, cn0, used)

    @Test
    fun header_matches_the_request() {
        assertEquals(
            "epoch_ms;elapsed_ns;sats_visible;sats_used;cn0_mean_used_dbhz;cn0_max_dbhz;used_gps;used_glonass;" +
                "used_galileo;used_beidou;used_other",
            GnssStatusRow.HEADER
        )
    }

    @Test
    fun counts_means_and_constellations() {
        val sats = listOf(
            sat(1, 40.0f, true), sat(1, 30.0f, true), sat(1, 45.5f, false), // GPS
            sat(3, 35.0f, true),                                            // GLONASS
            sat(6, 38.0f, true),                                            // Galileo
            sat(5, 33.0f, true), sat(5, 20.0f, false),                      // BeiDou
            sat(4, 29.0f, true), sat(2, 31.0f, true)                        // QZSS y SBAS → otros
        )
        val row = GnssStatusRow.from(stamp, sats)
        // Usados: 40+30+35+38+33+29+31 = 236 / 7
        assertEquals(
            "1790000000123;987654321000;9;7;" + RawCsvFormat.num(236.0 / 7) + ";45.5;2;1;1;1;2",
            row.toRow()
        )
        assertEquals(GnssStatusRow.HEADER.split(";").size, row.toRow().split(";").size)
    }

    @Test
    fun the_same_satellite_in_two_bands_counts_once_but_both_signals_go_to_cn0() {
        val row = GnssStatusRow.from(
            stamp,
            listOf(
                sat(1, 40.0f, true, svid = 7), sat(1, 30.0f, true, svid = 7),   // GPS 7 en L1 y L5
                sat(6, 35.0f, false, svid = 7), sat(6, 45.0f, true, svid = 7),  // Galileo 7: una banda usada
                sat(1, 20.0f, false, svid = 9)                                  // GPS 9, no usado
            )
        )
        // Distintos: GPS 7, Galileo 7, GPS 9 = 3; usados: GPS 7, Galileo 7 = 2. C/N0 usadas: (40+30+45)/3.
        assertEquals(
            "1790000000123;987654321000;3;2;" + RawCsvFormat.num(115.0 / 3) + ";45.0;1;0;1;0;0",
            row.toRow()
        )
    }

    @Test
    fun mean_keeps_the_float_decimals() {
        val row = GnssStatusRow.from(stamp, listOf(sat(1, 38.7f, true)))
        assertEquals("38.7", row.toRow().split(";")[4])
    }

    @Test
    fun none_used_leaves_the_mean_empty_and_no_satellites_leaves_the_max_empty() {
        val noneUsed = GnssStatusRow.from(stamp, listOf(sat(1, 25.0f, false), sat(6, 18.5f, false)))
        assertEquals("1790000000123;987654321000;2;0;;25.0;0;0;0;0;0", noneUsed.toRow())

        val empty = GnssStatusRow.from(stamp, emptyList())
        assertEquals("1790000000123;987654321000;0;0;;;0;0;0;0;0", empty.toRow())
    }

    @Test
    fun recorder_writes_at_most_one_row_per_second_and_only_while_open() {
        var nowMs = 1_000L
        fun at(ms: Long = nowMs) = RawStamp(1_700_000_000_000L + ms, ms * 1_000_000)
        val core = RawSessionCore(tmp.root, "abc-123", "FreeRideService", "FREE_RIDE", clock = { at() })
        assertTrue(core.open(at()))
        val r = RawGnssStatusRecorder(core)

        var reads = 0
        val sats = { reads++; listOf(sat(1, 40f, true)) }
        r.onStatus(at(), sats)                       // antes de started: nada
        assertTrue(r.started(at(), RawGnssStatusSource.PARAMS_DETAIL))
        // Avisos cada 500 ms durante 3 s: 6 avisos, 3 filas (0, 1000, 2000 ms).
        for (i in 0 until 6) r.onStatus(at(10_000L + i * 500), sats)
        core.stop(at(), "FreeRideService", RawSessionCore.STOP_USER)
        r.onStatus(at(20_000), sats)                 // después de parar: nada

        val lines = File(core.dir, RawFiles.GNSS_STATUS).readText().split("\n").dropLast(1)
        assertEquals(GnssStatusRow.HEADER, lines.first())
        assertEquals(3, lines.size - 1)
        assertEquals(3, reads)                       // los descartados no copian satélites
        assertEquals(6L, r.gate.received)
        assertFalse(lines.drop(1).any { it.split(";")[1] == "10500000000" })
    }
}
