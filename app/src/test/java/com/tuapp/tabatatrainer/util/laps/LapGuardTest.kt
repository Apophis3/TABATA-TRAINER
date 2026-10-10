package com.tuapp.tabatatrainer.util.laps

import com.tuapp.tabatatrainer.util.laps.LapGuard.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class LapGuardTest {

    @Test
    fun ghost_lap_of_zero_seconds_is_rejected() {
        assertEquals(Verdict.TOO_SHORT_TIME, LapGuard.check(0))
    }

    @Test
    fun lap_shorter_than_5_seconds_is_rejected() {
        assertEquals(Verdict.TOO_SHORT_TIME, LapGuard.check(1))
        assertEquals(Verdict.TOO_SHORT_TIME, LapGuard.check(4))
    }

    @Test
    fun lap_of_exactly_5_seconds_is_accepted() {
        assertEquals(Verdict.OK, LapGuard.check(5))
    }

    @Test
    fun explosive_series_of_10_and_20_seconds_are_accepted() {
        // Series en el sitio: el GPS no se mueve, pero la vuelta es válida.
        assertEquals(Verdict.OK, LapGuard.check(10))
        assertEquals(Verdict.OK, LapGuard.check(20))
    }

    @Test
    fun long_lap_is_accepted() {
        assertEquals(Verdict.OK, LapGuard.check(330))
    }

    @Test
    fun negative_duration_is_rejected() {
        assertEquals(Verdict.TOO_SHORT_TIME, LapGuard.check(-3))
    }
}
