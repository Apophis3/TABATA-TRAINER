package com.tuapp.tabatatrainer.util.laps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LapBookTest {

    private val t0 = 1_000_000L

    private fun book() = LapBook().also { it.reset(t0) }

    /** Corre 10 km a 5:30/km con muestras cada 1 s; en [manualAtSeconds] se pulsa una vuelta manual */
    private fun tenKmWithManuals(manualAtSeconds: List<Int>): LapBook {
        val book = book()
        val tracker = AutoLapTracker()
        val secPerKm = 330
        for (t in 1..(10 * secPerKm)) {
            val meters = t * 1000f / secPerKm
            tracker.onSample(t.toDouble(), meters).forEach { book.addAuto(it, t0 + t * 1000L) }
            if (t in manualAtSeconds) book.addManual(t, meters, t0 + t * 1000L)
        }
        return book
    }

    @Test
    fun ten_km_with_a_manual_at_3_5_km_gives_ten_untouched_autos_plus_the_manual() {
        val manualAt = (3.5f * 330).toInt()   // 1155 s ≈ 3,5 km
        val book = tenKmWithManuals(listOf(manualAt))

        val autos = book.laps.filter { it.type == LapType.AUTO }
        assertEquals(10, autos.size)
        assertEquals((1..10).toList(), autos.map { it.typeNumber })
        autos.forEach {
            assertEquals(1000f, it.distanceMeters, 0.01f)
            assertEquals(330, it.durationSeconds)
        }
        assertEquals(1, book.manualCount)
        assertEquals(1, book.laps.count { it.type == LapType.MANUAL })
    }

    @Test
    fun autos_are_identical_with_or_without_manuals() {
        val without = tenKmWithManuals(emptyList()).laps.filter { it.type == LapType.AUTO }
        val with = tenKmWithManuals(listOf(200, 500, 1155)).laps.filter { it.type == LapType.AUTO }
        assertEquals(without.map { it.startSeconds to it.endSeconds }, with.map { it.startSeconds to it.endSeconds })
        assertEquals(without.map { it.distanceMeters }, with.map { it.distanceMeters })
    }

    @Test
    fun chained_manuals_cover_contiguous_intervals() {
        val book = book()
        val a = book.addManual(20, 60f, t0 + 20_000)!!
        val b = book.addManual(45, 140f, t0 + 45_000)!!
        val c = book.addManual(70, 230f, t0 + 70_000)!!

        assertEquals(0 to 20, a.startSeconds to a.endSeconds)
        assertEquals(a.endSeconds to b.endSeconds, b.startSeconds to b.endSeconds)
        assertEquals(b.endSeconds to c.endSeconds, c.startSeconds to c.endSeconds)
        assertEquals(a.endMeters, b.startMeters, 0f)
        assertEquals(b.endMeters, c.startMeters, 0f)
        assertEquals(t0, a.startWallMs)
        assertEquals(a.endWallMs, b.startWallMs)
        // La suma de las manuales cubre todo el recorrido hasta la última pulsación
        assertEquals(230f, listOf(a, b, c).sumOf { it.distanceMeters.toDouble() }.toFloat(), 0.01f)
    }

    @Test
    fun series_have_independent_numbering_and_global_order() {
        val book = book()
        book.addAuto(AutoLapCrossing(1, 300.0), t0 + 300_000)
        book.addManual(100, 330f, t0 + 100_000)
        book.addAuto(AutoLapCrossing(2, 600.0), t0 + 600_000)
        book.addManual(700, 2100f, t0 + 700_000)

        assertEquals(listOf(1, 2, 3, 4), book.laps.map { it.order })
        assertEquals(listOf(LapType.AUTO, LapType.MANUAL, LapType.AUTO, LapType.MANUAL), book.laps.map { it.type })
        assertEquals(listOf(1, 1, 2, 2), book.laps.map { it.typeNumber })
        assertEquals(2, book.autoCount)
        assertEquals(2, book.manualCount)
    }

    @Test
    fun last_manual_keeps_real_distance_and_time() {
        val book = book()
        assertNull(book.lastManual)
        book.addManual(30, 100f, t0 + 30_000)
        book.addAuto(AutoLapCrossing(1, 240.0), t0 + 240_000)   // una AUTO en medio no cambia "última manual"
        book.addManual(90, 321f, t0 + 90_000)

        val last = book.lastManual!!
        assertEquals(2, last.typeNumber)
        assertEquals(60, last.durationSeconds)
        assertEquals(221f, last.distanceMeters, 0.001f)
    }

    @Test
    fun too_short_manual_is_rejected_and_does_not_move_the_window() {
        val book = book()
        book.addManual(30, 100f, t0 + 30_000)
        assertNull(book.addManual(34, 120f, t0 + 34_000))        // 4 s < 5 s
        assertEquals(1, book.manualCount)
        assertEquals(30, book.currentManualStartSeconds)          // la ventana no se movió
        val ok = book.addManual(35, 125f, t0 + 35_000)!!          // 5 s: ahora sí
        assertEquals(5, ok.durationSeconds)
        assertEquals(2, ok.typeNumber)
    }

    @Test
    fun explosive_series_with_zero_distance_are_accepted() {
        val book = book()
        assertTrue(book.addManual(10, 0f, t0 + 10_000) != null)
        assertTrue(book.addManual(25, 0f, t0 + 25_000) != null)
        assertEquals(2, book.manualCount)
    }

    @Test
    fun start_stop_pause_without_pressing_lap_produces_no_laps() {
        val book = book()
        // Sin pulsaciones ni km completos no hay vueltas (ni fantasma ni parcial)
        assertTrue(book.laps.isEmpty())
        assertEquals(0, book.manualCount)
        assertEquals(0, book.autoCount)
    }

    @Test
    fun reset_clears_everything() {
        val book = book()
        book.addManual(30, 100f, t0 + 30_000)
        book.addAuto(AutoLapCrossing(1, 240.0), t0 + 240_000)
        book.reset(t0 + 5_000_000)
        assertTrue(book.laps.isEmpty())
        assertEquals(0, book.currentManualStartSeconds)
        assertEquals(0f, book.currentAutoStartMeters, 0f)
        val first = book.addManual(10, 30f, t0 + 5_010_000)!!
        assertEquals(1, first.typeNumber)
        assertEquals(t0 + 5_000_000, first.startWallMs)
    }
}
