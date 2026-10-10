package com.tuapp.tabatatrainer.util.laps

import com.tuapp.tabatatrainer.data.local.LapEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SessionLapSummaryModelTest {

    private fun lap(
        order: Int,
        type: String = "MANUAL",
        typeNumber: Int = 1,
        meters: Float = 500f,
        seconds: Int = 180,
        wallSeconds: Int = seconds,
        hrAvg: Int? = 150,
        hrMax: Int? = 170,
        steps: Int? = 520,
        elevation: Float? = 12f,
        source: String? = "PEDOMETER"
    ) = LapEntity(
        sessionId = "s1",
        lapNumber = order,
        startTimestamp = 0L,
        endTimestamp = wallSeconds * 1000L,
        startDistanceMeters = 0f,
        endDistanceMeters = meters,
        avgSpeedKmh = if (seconds > 0 && meters > 0f) meters / seconds * 3.6f else 0f,
        maxSpeedKmh = 0f,
        avgHeartRate = hrAvg,
        maxHeartRate = hrMax,
        avgCadence = null,
        elevationGain = elevation,
        type = type,
        typeNumber = typeNumber,
        steps = steps,
        cadenceSource = source
    )

    private fun items(s: ManualLapSummary) = s.items.associate { it.label to it.value }
    private fun summary(l: LapEntity) = SessionLapSummaryModel.from(listOf(l), Locale.US).single()

    @Test
    fun only_manual_laps_get_a_summary() {
        val result = SessionLapSummaryModel.from(
            listOf(lap(1, "AUTO", 1, 1000f, 330), lap(2, "MANUAL", 1), lap(3, "AUTO", 2, 1000f, 330)),
            Locale.US
        )
        assertEquals(listOf("M1"), result.map { it.title })
    }

    @Test
    fun running_lap_shows_all_fields_with_ppm() {
        val m = items(summary(lap(1)))
        assertEquals("03:00", m["Tiempo"])
        assertEquals("0.50 km", m["Distancia"])
        assertEquals("6:00 /km", m["Ritmo medio"])
        assertEquals("10.0 km/h", m["Vel. media"])
        assertEquals("150", m["FC media"])
        assertEquals("170", m["FC máx"])
        assertEquals("520", m["Pasos"])
        assertEquals("173 ppm", m["Cadencia"])
        assertEquals("12 m", m["Desnivel +"])
    }

    @Test
    fun bike_sensor_lap_hides_the_cadence_item_but_keeps_the_rest() {
        val s = summary(lap(1, source = "BIKE_SENSOR"))
        assertFalse(s.items.any { it.label == "Cadencia" })
        assertTrue(s.items.any { it.label == "Pasos" })
    }

    @Test
    fun missing_data_is_double_dash_never_zero() {
        val m = items(summary(lap(1, hrAvg = null, hrMax = null, steps = null, elevation = null, source = "NONE")))
        assertEquals("--", m["FC media"])
        assertEquals("--", m["FC máx"])
        assertEquals("--", m["Pasos"])
        assertEquals("--", m["Cadencia"])
        assertEquals("--", m["Desnivel +"])
    }

    @Test
    fun explosive_set_in_place_has_no_pace_or_speed_but_keeps_time_and_steps() {
        val m = items(summary(lap(1, meters = 0f, seconds = 15, steps = 40, elevation = 0f)))
        assertEquals("00:15", m["Tiempo"])
        assertEquals("0.00 km", m["Distancia"])
        assertEquals("--", m["Ritmo medio"])
        assertEquals("--", m["Vel. media"])
        assertEquals("40", m["Pasos"])
        assertEquals("160 ppm", m["Cadencia"])
    }

    @Test
    fun a_pause_inside_the_window_does_not_stretch_the_lap() {
        // 180 s de ruta pero 300 s de reloj de pared (120 s de pausa): el tiempo mostrado es el de ruta
        val m = items(summary(lap(1, seconds = 180, wallSeconds = 300)))
        assertEquals("03:00", m["Tiempo"])
        assertEquals("6:00 /km", m["Ritmo medio"])
    }

    @Test
    fun no_manual_laps_gives_empty_list() {
        assertTrue(SessionLapSummaryModel.from(listOf(lap(1, "AUTO", 1, 1000f, 330)), Locale.US).isEmpty())
    }
}
