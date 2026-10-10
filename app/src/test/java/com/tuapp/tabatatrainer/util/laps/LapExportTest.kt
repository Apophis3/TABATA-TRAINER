package com.tuapp.tabatatrainer.util.laps

import com.tuapp.tabatatrainer.data.local.LapEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LapExportTest {

    private fun lap(
        order: Int,
        type: String,
        typeNumber: Int,
        meters: Float,
        seconds: Int,
        hr: Int? = null,
        steps: Int? = null
    ) = LapEntity(
        sessionId = "s1",
        lapNumber = order,
        startTimestamp = 1_000_000L,
        endTimestamp = 1_000_000L + seconds * 1000L,
        startDistanceMeters = 0f,
        endDistanceMeters = meters,
        avgSpeedKmh = if (seconds > 0) meters / seconds * 3.6f else 0f,
        maxSpeedKmh = 0f,
        avgHeartRate = hr,
        maxHeartRate = hr,
        avgCadence = null,
        elevationGain = null,
        type = type,
        typeNumber = typeNumber,
        steps = steps
    )

    @Test
    fun type_comes_from_the_persisted_type_not_from_the_distance() {
        // Una manual de 1000 m (antes se habría marcado "Auto") y una auto de 400 m (antes "Manual")
        val rows = LapExportRows.from(
            listOf(
                lap(1, "MANUAL", 1, 1000f, 330),
                lap(2, "AUTO", 1, 400f, 120)
            )
        )
        assertEquals(LapType.MANUAL, rows[0].type)
        assertEquals("Manual", rows[0].typeLabel)
        assertEquals(LapType.AUTO, rows[1].type)
        assertEquals("Auto", rows[1].typeLabel)
    }

    @Test
    fun rows_keep_global_order_and_per_series_numbers() {
        val rows = LapExportRows.from(
            listOf(
                lap(3, "AUTO", 2, 1000f, 330),
                lap(1, "AUTO", 1, 1000f, 330),
                lap(2, "MANUAL", 1, 600f, 200)
            )
        )
        assertEquals(listOf(1, 2, 3), rows.map { it.order })
        assertEquals(listOf(1, 1, 2), rows.map { it.typeNumber })
    }

    @Test
    fun duration_distance_and_sensor_values_come_from_the_lap_row() {
        val r = LapExportRows.from(listOf(lap(1, "MANUAL", 1, 500f, 180, hr = 150, steps = 520))).single()
        assertEquals(180, r.durationSeconds)
        assertEquals(500.0, r.distanceMeters, 0.001)
        assertEquals(150, r.avgHeartRate)
        assertEquals(520, r.steps)
    }

    @Test
    fun export_duration_ignores_a_pause_inside_the_window() {
        // 180 s de ruta (500 m a 10 km/h) pero 300 s de reloj de pared
        val l = lap(1, "MANUAL", 1, 500f, 180).copy(endTimestamp = 1_000_000L + 300_000L)
        assertEquals(180, LapExportRows.from(listOf(l)).single().durationSeconds)
    }

    @Test
    fun missing_sensor_values_stay_null_not_zero() {
        val r = LapExportRows.from(listOf(lap(1, "MANUAL", 1, 0f, 15))).single()
        assertEquals(null, r.avgHeartRate)
        assertEquals(null, r.steps)
        assertEquals(0.0, r.distanceMeters, 0.001)   // serie explosiva en el sitio
    }

    @Test
    fun unknown_type_falls_back_to_manual_and_no_laps_gives_no_rows() {
        assertEquals(LapType.MANUAL, LapExportRows.typeOf("OTRO"))
        assertTrue(LapExportRows.from(emptyList()).isEmpty())
    }

    @Test
    fun deleting_a_session_deletes_its_laps_and_other_children_before_the_session() {
        val calls = mutableListOf<String>()
        runBlocking {
            SessionDeletion.deleteWithChildren(
                deleteLaps = { calls += "laps" },
                deleteGpsPoints = { calls += "gps" },
                deleteSensorReadings = { calls += "readings" },
                deleteSession = { calls += "session" }
            )
        }
        assertTrue("laps" in calls)
        assertEquals("session", calls.last())
        assertEquals(4, calls.size)
    }

    @Test
    fun if_a_child_delete_fails_the_session_is_kept() {
        val calls = mutableListOf<String>()
        try {
            runBlocking {
                SessionDeletion.deleteWithChildren(
                    deleteLaps = { calls += "laps" },
                    deleteGpsPoints = { error("fallo de disco") },
                    deleteSensorReadings = { calls += "readings" },
                    deleteSession = { calls += "session" }
                )
            }
        } catch (_: IllegalStateException) {
        }
        assertTrue("session" !in calls)
    }
}
