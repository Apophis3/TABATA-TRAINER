package com.tuapp.tabatatrainer.util.laps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLapTrackerTest {

    /** Corre [km] kilómetros a [secPerKm] con muestras GPS cada [sampleSeconds] s y recoge los cruces */
    private fun run(tracker: AutoLapTracker, km: Int, secPerKm: Int, sampleSeconds: Int): List<AutoLapCrossing> {
        val out = mutableListOf<AutoLapCrossing>()
        val total = km * secPerKm
        var t = sampleSeconds
        while (t <= total) {
            out += tracker.onSample(t.toDouble(), t * 1000f / secPerKm)
            t += sampleSeconds
        }
        return out
    }

    @Test
    fun five_km_gives_five_exact_autolaps() {
        val crossings = run(AutoLapTracker(), km = 5, secPerKm = 330, sampleSeconds = 1)
        assertEquals(listOf(1, 2, 3, 4, 5), crossings.map { it.km })
        crossings.forEach { assertEquals(it.km * 1000f, it.distanceMeters, 0f) }
        // cada km tarda exactamente 330 s aunque las muestras no caigan justo en el cruce
        crossings.forEachIndexed { i, c -> assertEquals(330.0 * (i + 1), c.timeSeconds, 0.01) }
    }

    @Test
    fun crossing_time_is_interpolated_between_samples() {
        val tracker = AutoLapTracker()
        tracker.onSample(0.0, 0f)
        tracker.onSample(300.0, 900f)
        // 900 m a t=300 y 1100 m a t=310: el cruce de 1000 m cae a mitad = 305 s
        val crossings = tracker.onSample(310.0, 1100f)
        assertEquals(1, crossings.size)
        assertEquals(305.0, crossings[0].timeSeconds, 0.001)
    }

    @Test
    fun gps_gap_that_jumps_2_4_km_creates_two_autolaps() {
        val tracker = AutoLapTracker()
        tracker.onSample(0.0, 0f)
        val crossings = tracker.onSample(720.0, 2400f)
        assertEquals(listOf(1, 2), crossings.map { it.km })
        assertEquals(300.0, crossings[0].timeSeconds, 0.001)
        assertEquals(600.0, crossings[1].timeSeconds, 0.001)
    }

    @Test
    fun no_partial_lap_when_stopping_before_the_next_km() {
        val tracker = AutoLapTracker()
        // 4,6 km a 5:30/km: solo existen los autolaps 1..4, nunca un "km 5" parcial
        val crossings = mutableListOf<AutoLapCrossing>()
        var t = 1
        while (t * 1000f / 330f <= 4600f) {
            crossings += tracker.onSample(t.toDouble(), t * 1000f / 330f)
            t++
        }
        assertEquals(listOf(1, 2, 3, 4), crossings.map { it.km })
        assertTrue(tracker.onSample(t.toDouble(), 4999f).isEmpty())
    }

    @Test
    fun each_km_is_crossed_only_once() {
        val tracker = AutoLapTracker()
        tracker.onSample(0.0, 0f)
        assertEquals(1, tracker.onSample(330.0, 1000f).size)
        assertTrue(tracker.onSample(331.0, 1003f).isEmpty())
        assertTrue(tracker.onSample(332.0, 1001f).isEmpty())   // retroceso por ruido
        assertTrue(tracker.onSample(333.0, 1004f).isEmpty())   // no vuelve a cruzar el mismo km
    }

    @Test
    fun exact_1000_counts_as_crossing() {
        val tracker = AutoLapTracker()
        val crossings = tracker.onSample(300.0, 1000f)
        assertEquals(1, crossings.size)
        assertEquals(300.0, crossings[0].timeSeconds, 0.001)
    }

    @Test
    fun standing_still_does_not_cross_and_keeps_interpolation_correct() {
        val tracker = AutoLapTracker()
        tracker.onSample(0.0, 0f)
        tracker.onSample(300.0, 900f)
        assertTrue(tracker.onSample(400.0, 900f).isEmpty())    // 100 s parado
        // 900 m a t=400, 1100 m a t=410 → cruce a 405 s
        assertEquals(405.0, tracker.onSample(410.0, 1100f)[0].timeSeconds, 0.001)
    }

    @Test
    fun reset_starts_from_zero() {
        val tracker = AutoLapTracker()
        tracker.onSample(300.0, 1500f)
        tracker.reset()
        val crossings = tracker.onSample(330.0, 1000f)
        assertEquals(listOf(1), crossings.map { it.km })
    }
}
