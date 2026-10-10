package com.tuapp.tabatatrainer.util.laps

import com.tuapp.tabatatrainer.service.LapData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LapTableModelTest {

    private fun lap(
        number: Int,
        type: LapType = LapType.AUTO,
        typeNumber: Int = number,
        meters: Float = 1000f,
        seconds: Int = 330,
        hrAvg: Int = 150,
        hrMax: Int = 170,
        steps: Int? = 940,
        elevation: Float? = 8f,
        source: CadenceSource = CadenceSource.PEDOMETER,
        avgCadence: Float = 0f
    ) = LapData(
        number = number,
        type = type,
        typeNumber = typeNumber,
        startTimeSeconds = number * 1000,
        endTimeSeconds = number * 1000 + seconds,
        distanceMeters = meters,
        avgSpeedKmh = 10.9f,
        avgHeartRate = hrAvg,
        maxHeartRate = hrMax,
        avgCadence = avgCadence,
        steps = steps,
        elevationGain = elevation,
        cadenceSource = source
    )

    private fun cells(l: LapData) = LapTableModel.rows(listOf(l), Locale.US).single().cells
    private fun cell(l: LapData, header: String) = cells(l)[LapTableModel.SCROLL_HEADERS.indexOf(header)]

    @Test
    fun table_has_ten_columns_the_first_one_fixed() {
        assertEquals(10, LapTableModel.COLUMN_COUNT)
        assertEquals(9, LapTableModel.SCROLL_HEADERS.size)
        assertEquals("#", LapTableModel.FIXED_HEADER)
    }

    @Test
    fun twenty_laps_give_twenty_rows_newest_first_each_with_nine_cells() {
        // 20 vueltas mezcladas: una MANUAL cada 4 (series independientes)
        var autos = 0
        var manuals = 0
        val laps = (1..20).map { n ->
            if (n % 4 == 0) lap(n, LapType.MANUAL, ++manuals) else lap(n, LapType.AUTO, ++autos)
        }
        val rows = LapTableModel.rows(laps, Locale.US)

        assertEquals(20, rows.size)
        assertEquals((20 downTo 1).toList(), rows.map { it.order })
        assertTrue(rows.all { it.cells.size == LapTableModel.SCROLL_HEADERS.size })
        assertEquals("M5", rows.first().cells[0])      // la 20 es la 5ª manual
        assertEquals("A1", rows.last().cells[0])       // la 1 es la 1ª auto
        assertEquals(15, rows.count { it.cells[0].startsWith("A") })
        assertEquals(5, rows.count { it.cells[0].startsWith("M") })
    }

    @Test
    fun style_comes_from_the_lap_type() {
        val rows = LapTableModel.rows(listOf(lap(1, LapType.AUTO), lap(2, LapType.MANUAL, 1)), Locale.US)
        val manual = rows[0]
        val auto = rows[1]
        assertTrue(manual.style.bold)
        assertTrue(manual.style.showFlag)
        assertFalse(auto.style.bold)
        assertFalse(auto.style.showFlag)
    }

    @Test
    fun running_lap_shows_every_value() {
        val l = lap(1)
        assertEquals("A1", cell(l, "Tipo"))
        assertEquals("1.00", cell(l, "Km"))
        assertEquals("05:30", cell(l, "Tiempo"))
        assertEquals("5:30", cell(l, "Ritmo"))
        assertEquals("150", cell(l, "FC"))
        assertEquals("170", cell(l, "FC máx"))
        assertEquals("940", cell(l, "Pasos"))
        assertEquals("171 ppm", cell(l, "Cadencia"))
        assertEquals("8", cell(l, "Desnivel +"))
    }

    @Test
    fun missing_data_is_double_dash_never_zero() {
        val l = lap(1, hrAvg = 0, hrMax = 0, steps = null, elevation = null, source = CadenceSource.NONE)
        assertEquals("--", cell(l, "FC"))
        assertEquals("--", cell(l, "FC máx"))
        assertEquals("--", cell(l, "Pasos"))
        assertEquals("--", cell(l, "Cadencia"))
        assertEquals("--", cell(l, "Desnivel +"))
    }

    @Test
    fun set_in_place_has_no_pace_but_keeps_time_and_steps() {
        val l = lap(1, LapType.MANUAL, 1, meters = 0f, seconds = 15, steps = 40, elevation = 0f)
        assertEquals("0.00", cell(l, "Km"))
        assertEquals("00:15", cell(l, "Tiempo"))
        assertEquals("--", cell(l, "Ritmo"))
        assertEquals("40", cell(l, "Pasos"))
        assertEquals("160 ppm", cell(l, "Cadencia"))
        assertEquals("0", cell(l, "Desnivel +"))   // 0 m medido es un dato real, no "sin dato"
    }

    @Test
    fun bike_sensor_never_shows_ppm() {
        val withRpm = lap(1, steps = 940, source = CadenceSource.BIKE_SENSOR, avgCadence = 87.4f)
        assertEquals("87 rpm", cell(withRpm, "Cadencia"))
        val noRpm = lap(2, steps = 940, source = CadenceSource.BIKE_SENSOR, avgCadence = 0f)
        assertEquals("--", cell(noRpm, "Cadencia"))
        assertFalse(cell(noRpm, "Cadencia").contains("ppm"))
    }

    @Test
    fun very_slow_lap_shows_no_pace_instead_of_a_huge_one() {
        // 10 m en 1 h → más de 60:00 /km
        val l = lap(1, meters = 10f, seconds = 3600)
        assertEquals("--", cell(l, "Ritmo"))
        assertEquals("1:00:00", cell(l, "Tiempo"))
    }

    @Test
    fun no_laps_gives_no_rows() {
        assertTrue(LapTableModel.rows(emptyList(), Locale.US).isEmpty())
    }
}
