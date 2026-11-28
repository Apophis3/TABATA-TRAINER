package com.tuapp.tabatatrainer.rawlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaroSecondAveragerTest {

    /** Hora a [ms] milisegundos desde el arranque del móvil. */
    private fun at(ms: Long) = RawStamp(1_700_000_000_000L + ms, ms * 1_000_000)

    @Test
    fun seconds_with_several_zero_and_one_reading() {
        val a = BaroSecondAverager()
        // Segundo 10: tres lecturas.
        assertNull(a.add(at(10_000), 1013.0f))
        assertNull(a.add(at(10_400), 1013.5f))
        assertNull(a.add(at(10_900), 1012.5f))
        // Segundo 11: ninguna. Segundo 12: una; al llegar se cierra el 10.
        val s10 = a.add(at(12_300), 1014.25f)!!
        assertEquals(at(10_900), s10.stamp)
        assertEquals(1013.0, s10.pressureHpa, 0.0)
        assertEquals(3, s10.nSamples)
        assertEquals(1012.5f, s10.minHpa)
        assertEquals(1013.5f, s10.maxHpa)

        val s12 = a.drain()!!
        assertEquals(at(12_300), s12.stamp)
        assertEquals("1014.25", RawCsvFormat.num(s12.pressureHpa))
        assertEquals(1, s12.nSamples)
        assertEquals(1014.25f, s12.minHpa)
        assertEquals(1014.25f, s12.maxHpa)

        // El segundo 11 no da fila: 2 filas de 4 lecturas.
        assertEquals(4L, a.received)
        assertEquals(2L, a.rows)
        assertNull(a.drain())
    }

    @Test
    fun one_reading_keeps_its_decimals_without_float_noise() {
        val a = BaroSecondAverager()
        a.add(at(5_000), 941.37f)
        assertEquals("941.37", RawCsvFormat.num(a.drain()!!.pressureHpa))
    }

    @Test
    fun ten_readings_per_second_for_5_s_give_five_rows() {
        val a = BaroSecondAverager()
        val rows = (0 until 50).mapNotNull { i -> a.add(at(20_000L + i * 100), 1000f + (i % 10)) } +
            listOfNotNull(a.drain())
        assertEquals(5, rows.size)
        rows.forEach {
            assertEquals(10, it.nSamples)
            assertEquals(1004.5, it.pressureHpa, 1e-9)
            assertEquals(1000f, it.minHpa)
            assertEquals(1009f, it.maxHpa)
        }
    }

    @Test
    fun close_if_past_only_closes_an_earlier_second() {
        val a = BaroSecondAverager()
        a.add(at(7_100), 1000f)
        assertNull(a.closeIfPast(at(7_999)))
        assertEquals(1, a.closeIfPast(at(8_000))!!.nSamples)
        assertNull(a.closeIfPast(at(9_000)))
    }

    @Test
    fun nothing_received_gives_nothing() {
        val a = BaroSecondAverager()
        assertNull(a.drain())
        assertNull(a.closeIfPast(at(1_000)))
        assertEquals(0L, a.rows)
    }
}
