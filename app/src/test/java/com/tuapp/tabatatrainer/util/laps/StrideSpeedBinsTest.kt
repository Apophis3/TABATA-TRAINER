package com.tuapp.tabatatrainer.util.laps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrideSpeedBinsTest {

    /** Simula [seconds] segundos a [kmh] con [spm] pasos/min desde los acumulados dados. */
    private fun StrideSpeedBins.walk(
        seconds: Int, kmh: Double, spm: Int, from: Pair<Int, Double>
    ): Pair<Int, Double> {
        var steps = from.first.toDouble()
        var dist = from.second
        repeat(seconds) {
            steps += spm / 60.0
            dist += kmh / 3.6
            onSecond(steps.toInt(), dist)
        }
        return steps.toInt() to dist
    }

    @Test
    fun bins_are_one_kmh_wide_below_seven_and_two_above() {
        assertNull(StrideSpeedBins.binOf(0.0))
        assertNull(StrideSpeedBins.binOf(0.99))
        assertEquals(1, StrideSpeedBins.binOf(1.0))
        assertEquals(2, StrideSpeedBins.binOf(2.8))
        assertEquals(4, StrideSpeedBins.binOf(4.99))
        assertEquals(6, StrideSpeedBins.binOf(6.99))
        assertEquals(7, StrideSpeedBins.binOf(7.0))
        assertEquals(7, StrideSpeedBins.binOf(8.9))
        assertEquals(9, StrideSpeedBins.binOf(9.0))
        assertEquals(11, StrideSpeedBins.binOf(12.0))
        assertNull(StrideSpeedBins.binOf(30.0))       // salto de GPS
        assertEquals(3, StrideSpeedBins.highOf(2))
        assertEquals(9, StrideSpeedBins.highOf(7))
    }

    @Test
    fun first_call_only_opens_the_block() {
        val s = StrideSpeedBins()
        s.onSecond(0, 0.0)
        assertTrue(s.bins().isEmpty())
    }

    @Test
    fun ten_second_block_goes_to_its_speed_bin() {
        val s = StrideSpeedBins()
        s.onSecond(0, 0.0)
        // 10 s a 5,4 km/h = 15 m con 21 pasos
        for (i in 1..10) s.onSecond(i * 21 / 10, i * 1.5)
        val bins = s.bins()
        assertEquals(1, bins.size)
        assertEquals(5, bins[0].lowKmh)
        assertEquals(21, bins[0].steps)
        assertEquals(15.0, bins[0].distanceM, 1e-6)
        assertEquals(10, bins[0].seconds)
        assertEquals(15.0 / 21, bins[0].strideM, 1e-6)
    }

    @Test
    fun incomplete_block_does_not_count() {
        val s = StrideSpeedBins()
        s.onSecond(0, 0.0)
        for (i in 1..9) s.onSecond(i * 2, i * 1.5)
        assertTrue(s.bins().isEmpty())
    }

    @Test
    fun standing_still_and_few_steps_do_not_count() {
        val s = StrideSpeedBins()
        s.onSecond(0, 0.0)
        for (i in 1..10) s.onSecond(i * 2, 0.0)           // parado moviendo los pies
        for (i in 1..10) s.onSecond(20, i * 1.5)          // 15 m sin pasos (en coche/bici)
        assertTrue(s.bins().isEmpty())
    }

    @Test
    fun several_speeds_accumulate_separately() {
        val s = StrideSpeedBins()
        var at = 0 to 0.0
        s.onSecond(0, 0.0)
        at = s.walk(600, 2.8, 76, at)    // paseo lento
        at = s.walk(600, 5.2, 124, at)   // andar normal (5,0 caería justo en el borde del tramo)
        s.walk(300, 8.0, 148, at)        // trote
        val bins = s.bins().associateBy { it.lowKmh }
        assertEquals(setOf(2, 5, 7), bins.keys)
        assertEquals(0.61, bins.getValue(2).strideM, 0.02)
        assertEquals(0.70, bins.getValue(5).strideM, 0.02)
        assertEquals(0.90, bins.getValue(7).strideM, 0.02)
    }

    @Test
    fun interrupt_discards_half_block_and_restarts() {
        val s = StrideSpeedBins()
        s.onSecond(0, 0.0)
        for (i in 1..5) s.onSecond(i * 2, i * 1.5)
        s.interrupt()
        // Al reanudar, los pasos andados en pausa llegan de golpe: no deben contar
        s.onSecond(500, 400.0)
        for (i in 1..10) s.onSecond(500 + i * 2, 400.0 + i * 1.5)
        val bins = s.bins()
        assertEquals(1, bins.size)
        assertEquals(20, bins[0].steps)
        assertEquals(15.0, bins[0].distanceM, 1e-6)
    }

    @Test
    fun encode_decode_round_trip() {
        val bins = listOf(StrideBin(5, 1414, 1000.0, 705), StrideBin(2, 354, 216.04, 280))
        val text = StrideSpeedBins.encode(bins)
        assertEquals("2:354:216.0:280;5:1414:1000.0:705", text)
        val back = StrideSpeedBins.decode(text)
        assertEquals(listOf(2, 5), back.map { it.lowKmh })
        assertEquals(354, back[0].steps)
        assertEquals(216.0, back[0].distanceM, 1e-9)
        assertEquals(705, back[1].seconds)
    }

    @Test
    fun decode_tolerates_missing_or_broken_text() {
        assertTrue(StrideSpeedBins.decode(null).isEmpty())
        assertTrue(StrideSpeedBins.decode("").isEmpty())
        assertTrue(StrideSpeedBins.decode("basura").isEmpty())
        assertTrue(StrideSpeedBins.decode("2:x:1.0:3;;:::").isEmpty())
        assertEquals(listOf(5), StrideSpeedBins.decode("roto;5:150:100.0:80").map { it.lowKmh })
    }

    @Test
    fun only_bins_with_enough_steps_are_visible() {
        val bins = listOf(StrideBin(3, 99, 60.0, 50), StrideBin(4, 100, 65.0, 52), StrideBin(1, 400, 200.0, 600))
        assertEquals(listOf(1, 4), StrideSpeedBins.visible(bins).map { it.lowKmh })
    }

    @Test
    fun reset_clears_bins() {
        val s = StrideSpeedBins()
        s.onSecond(0, 0.0)
        for (i in 1..10) s.onSecond(i * 2, i * 1.5)
        s.reset()
        assertTrue(s.bins().isEmpty())
        assertEquals("", s.encode())
    }
}
