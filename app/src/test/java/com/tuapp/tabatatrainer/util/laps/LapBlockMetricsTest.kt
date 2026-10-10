package com.tuapp.tabatatrainer.util.laps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LapBlockMetricsTest {

    private fun c(steps: Int?, elev: Float?) = BlockCounters(steps, elev)

    @Test
    fun steps_and_elevation_are_the_difference_between_window_ends() {
        val m = LapBlockMetrics.of(c(1000, 20f), c(1520, 35.5f), bikeSensorUsed = false)
        assertEquals(520, m.steps)
        assertEquals(15.5f, m.elevationGain!!, 0.001f)
        assertEquals(CadenceSource.PEDOMETER, m.cadenceSource)
    }

    @Test
    fun cadence_is_steps_over_block_minutes() {
        // 520 pasos en 3:00 → 173 ppm
        assertEquals(173, LapBlockMetrics.cadencePpm(520, 180, CadenceSource.PEDOMETER))
        // 180 pasos en 1:00 → 180 ppm
        assertEquals(180, LapBlockMetrics.cadencePpm(180, 60, CadenceSource.PEDOMETER))
    }

    @Test
    fun sum_of_contiguous_manual_blocks_never_exceeds_total_steps() {
        val total = 2000
        val marks = listOf(c(0, 0f), c(700, 5f), c(1350, 9f), c(2000, 12f))
        val blocks = marks.zipWithNext { a, b -> LapBlockMetrics.of(a, b, false).steps!! }
        assertEquals(total, blocks.sum())
        assertTrue(blocks.sum() <= total)
        // y con una manual que solo cubre parte de la sesión, la suma es menor
        val partial = LapBlockMetrics.of(c(700, 5f), c(1350, 9f), false).steps!!
        assertTrue(partial <= total)
    }

    @Test
    fun no_pedometer_means_no_steps_no_cadence_never_zero() {
        val m = LapBlockMetrics.of(c(null, null), c(null, null), bikeSensorUsed = false)
        assertNull(m.steps)
        assertNull(m.elevationGain)
        assertEquals(CadenceSource.NONE, m.cadenceSource)
        assertNull(LapBlockMetrics.cadencePpm(m.steps, 180, m.cadenceSource))
    }

    @Test
    fun bike_sensor_hides_running_cadence_even_if_the_phone_counted_steps() {
        val m = LapBlockMetrics.of(c(0, 0f), c(300, 10f), bikeSensorUsed = true)
        assertEquals(CadenceSource.BIKE_SENSOR, m.cadenceSource)
        assertNull(LapBlockMetrics.cadencePpm(m.steps, 180, m.cadenceSource))
    }

    @Test
    fun counter_that_appears_mid_window_counts_from_zero() {
        // el podómetro empezó a emitir dentro de la ventana: el inicio sin dato vale 0
        val m = LapBlockMetrics.of(c(null, null), c(120, 3f), false)
        assertEquals(120, m.steps)
        assertEquals(3f, m.elevationGain!!, 0.001f)
    }

    @Test
    fun explosive_set_in_place_has_steps_but_zero_elevation_and_no_distance_needed() {
        val m = LapBlockMetrics.of(c(100, 4f), c(140, 4f), false)
        assertEquals(40, m.steps)
        assertEquals(0f, m.elevationGain!!, 0.001f)
        assertEquals(160, LapBlockMetrics.cadencePpm(m.steps, 15, m.cadenceSource))
    }

    @Test
    fun counters_that_go_backwards_are_clamped_to_zero() {
        val m = LapBlockMetrics.of(c(500, 10f), c(480, 9f), false)
        assertEquals(0, m.steps)
        assertEquals(0f, m.elevationGain!!, 0.001f)
    }

    @Test
    fun zero_duration_gives_no_cadence() {
        assertNull(LapBlockMetrics.cadencePpm(50, 0, CadenceSource.PEDOMETER))
    }
}
