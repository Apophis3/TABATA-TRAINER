package com.tuapp.tabatatrainer.rawlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinGapGateTest {

    private val ms = 1_000_000L

    @Test
    fun five_samples_per_second_keep_one_per_second() {
        val d = MinGapGate()
        val kept = (0 until 50).map { i -> i * 200 * ms }.filter { d.accept(it) }
        assertEquals((0 until 10).map { it * 1_000 * ms }, kept)
        assertEquals(50L, d.received)
        assertEquals(10L, d.written)
        assertEquals(40L, d.discarded)
    }

    @Test
    fun a_one_hertz_sensor_with_jitter_loses_nothing() {
        val d = MinGapGate()
        // Separación entre muestras seguidas de 1000 ms ± 40 ms (960–1040 ms).
        val gapsMs = listOf(1_040L, 960L, 1_025L, 970L, 1_010L, 960L, 1_040L, 990L, 960L)
        val times = gapsMs.runningFold(0L) { t, g -> t + g * ms }
        assertTrue(times.all { d.accept(it) })
        assertEquals(10L, d.written)
        assertEquals(0L, d.discarded)
    }

    @Test
    fun the_first_sample_is_always_written() {
        val d = MinGapGate()
        assertTrue(d.accept(123_456L))
    }

    @Test
    fun a_gap_of_exactly_950_ms_is_written() {
        val d = MinGapGate()
        d.accept(0L)
        assertTrue(d.accept(950 * ms))
        assertEquals(false, d.accept(1_899 * ms))
    }

    @Test
    fun sensor_clock_going_back_is_written_and_resets_the_reference() {
        val d = MinGapGate()
        d.accept(10_000 * ms)
        assertTrue(d.accept(500 * ms))
        assertEquals(false, d.accept(900 * ms))
        assertTrue(d.accept(1_450 * ms))
    }

    @Test
    fun a_pause_in_the_sensor_is_not_filled() {
        val d = MinGapGate()
        d.accept(0L)
        // 30 s sin muestras (p. ej. pantalla apagada): la siguiente se escribe, no se inventa nada en medio.
        assertTrue(d.accept(30_000 * ms))
        assertEquals(2L, d.written)
    }
}
