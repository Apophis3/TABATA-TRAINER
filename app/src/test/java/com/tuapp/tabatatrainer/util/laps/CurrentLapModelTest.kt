package com.tuapp.tabatatrainer.util.laps

import com.tuapp.tabatatrainer.service.LapData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class CurrentLapModelTest {

    private fun view(
        seconds: Int = 330,
        meters: Float = 1000f,
        steps: Int? = 940,
        elevation: Float? = 8f,
        cadenceActive: Boolean = false,
        rpm: Float = 0f
    ) = CurrentLapModel.of(seconds, meters, steps, elevation, cadenceActive, rpm, Locale.US)

    @Test
    fun running_lap_shows_pace_steps_ppm_and_elevation() {
        val v = view()
        assertEquals("5:30", v.pace)
        assertEquals("940", v.steps)
        assertEquals("171", v.cadenceValue)
        assertEquals("ppm", v.cadenceUnit)
        assertEquals("8", v.elevation)
    }

    @Test
    fun missing_data_is_double_dash_never_zero() {
        val v = view(steps = null, elevation = null)
        assertEquals("--", v.steps)
        assertEquals("--", v.cadenceValue)
        assertEquals("--", v.elevation)
    }

    @Test
    fun a_measured_zero_elevation_is_a_real_value() {
        assertEquals("0", view(elevation = 0f).elevation)
    }

    @Test
    fun just_started_lap_has_no_pace_or_cadence_yet() {
        val v = view(seconds = 3, meters = 4f, steps = 6)
        assertEquals("--", v.pace)
        assertEquals("--", v.cadenceValue)
        assertEquals("6", v.steps)
    }

    @Test
    fun set_in_place_has_no_pace_but_keeps_steps_and_cadence() {
        val v = view(seconds = 15, meters = 0f, steps = 40)
        assertEquals("--", v.pace)
        assertEquals("40", v.steps)
        assertEquals("160", v.cadenceValue)
    }

    @Test
    fun active_bike_sensor_shows_rpm_never_ppm() {
        val v = view(cadenceActive = true, rpm = 87.4f)
        assertEquals("87", v.cadenceValue)
        assertEquals("rpm", v.cadenceUnit)
    }

    @Test
    fun very_slow_lap_shows_no_pace() {
        assertEquals("--", view(seconds = 3600, meters = 20f).pace)
    }

    private fun lap(number: Int, type: LapType, typeNumber: Int) = LapData(
        number = number,
        type = type,
        typeNumber = typeNumber,
        startTimeSeconds = 0,
        endTimeSeconds = 300,
        distanceMeters = 1000f,
        avgSpeedKmh = 12f,
        avgHeartRate = 150,
        steps = 800
    )

    @Test
    fun last_manual_lap_is_the_highest_ordered_manual_and_ignores_autos() {
        val laps = listOf(
            lap(1, LapType.AUTO, 1),
            lap(2, LapType.MANUAL, 1),
            lap(3, LapType.AUTO, 2),
            lap(4, LapType.MANUAL, 2),
            lap(5, LapType.AUTO, 3)
        )
        val last = CurrentLapModel.lastManual(laps, Locale.US)!!
        assertEquals("M2", last.label)
        assertEquals("05:00", last.time)
        assertEquals("5:00", last.pace)
        assertEquals("150", last.heartRate)
        assertEquals("800", last.steps)
    }

    @Test
    fun no_manual_laps_gives_null() {
        assertNull(CurrentLapModel.lastManual(listOf(lap(1, LapType.AUTO, 1)), Locale.US))
        assertNull(CurrentLapModel.lastManual(emptyList(), Locale.US))
    }
}
