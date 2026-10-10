package com.tuapp.tabatatrainer.util.laps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaceCalculatorTest {

    /** Alimenta 1 muestra por segundo a velocidad constante [metersPerSecond] */
    private fun feed(calc: PaceCalculator, seconds: Int, metersPerSecond: Float, fromSecond: Int = 0, fromMeters: Float = 0f) {
        for (i in 0..seconds) calc.add(fromSecond + i, fromMeters + metersPerSecond * i)
    }

    @Test
    fun pace_5_30_per_km() {
        val calc = PaceCalculator()
        feed(calc, 12, 1000f / 330f)
        assertEquals(330, calc.livePaceSecPerKm())
        assertEquals("5:30", PaceCalculator.format(calc.livePaceSecPerKm()))
    }

    @Test
    fun pace_6_00_per_km() {
        val calc = PaceCalculator()
        feed(calc, 12, 1000f / 360f)
        assertEquals(360, calc.livePaceSecPerKm())
        assertEquals("6:00", PaceCalculator.format(calc.livePaceSecPerKm()))
    }

    @Test
    fun walking_pace_is_valid() {
        val calc = PaceCalculator()
        feed(calc, 12, 1000f / 720f) // 12:00 /km
        assertEquals("12:00", PaceCalculator.format(calc.livePaceSecPerKm()))
    }

    @Test
    fun window_only_uses_last_10_seconds() {
        val calc = PaceCalculator()
        feed(calc, 20, 1000f / 600f)                                  // 10:00 /km al principio
        feed(calc, 15, 1000f / 300f, fromSecond = 21, fromMeters = 1000f / 600f * 20 + 1000f / 300f) // luego 5:00 /km
        assertEquals(300, calc.livePaceSecPerKm())
    }

    @Test
    fun without_enough_samples_is_null() {
        val calc = PaceCalculator()
        assertNull(calc.livePaceSecPerKm())
        calc.add(0, 0f)
        assertNull(calc.livePaceSecPerKm())
        calc.add(2, 5f) // tramo de 2 s: demasiado corto para fiarse
        assertNull(calc.livePaceSecPerKm())
    }

    @Test
    fun stopped_is_null_never_zero() {
        val calc = PaceCalculator()
        feed(calc, 12, 0f)
        assertNull(calc.livePaceSecPerKm())
        assertEquals("--", PaceCalculator.format(calc.livePaceSecPerKm()))
    }

    @Test
    fun slower_than_30_min_per_km_is_null() {
        val calc = PaceCalculator()
        feed(calc, 12, 1000f / 1900f) // 31:40 /km
        assertNull(calc.livePaceSecPerKm())
    }

    @Test
    fun faster_than_2_min_per_km_is_null() {
        val calc = PaceCalculator()
        feed(calc, 12, 1000f / 100f) // 1:40 /km: ruido GPS
        assertNull(calc.livePaceSecPerKm())
    }

    @Test
    fun reset_clears_window() {
        val calc = PaceCalculator()
        feed(calc, 12, 1000f / 330f)
        calc.reset()
        assertNull(calc.livePaceSecPerKm())
    }

    @Test
    fun secPerKm_for_a_lap() {
        assertEquals(330, PaceCalculator.secPerKm(330, 1000f))
        assertEquals(360, PaceCalculator.secPerKm(180, 500f))
        assertNull(PaceCalculator.secPerKm(120, 0f))
        assertNull(PaceCalculator.secPerKm(0, 500f))
    }

    @Test
    fun format_handles_null_and_non_positive() {
        assertEquals("--", PaceCalculator.format(null))
        assertEquals("--", PaceCalculator.format(0))
        assertEquals("0:59", PaceCalculator.format(59))
        assertEquals("30:00", PaceCalculator.format(1800))
    }
}
