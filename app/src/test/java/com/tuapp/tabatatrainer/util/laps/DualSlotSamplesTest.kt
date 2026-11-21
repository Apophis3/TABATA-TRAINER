package com.tuapp.tabatatrainer.util.laps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualSlotSamplesTest {

    @Test
    fun hr1_and_hr2_averages_are_not_mixed() {
        val s = DualSlotSamples()
        listOf(100f, 110f, 120f).forEach { s.add(0, it) }
        listOf(140f, 150f).forEach { s.add(1, it) }
        assertEquals(110f, s.avg(0)!!, 0.001f)
        assertEquals(145f, s.avg(1)!!, 0.001f)
        assertEquals(120f, s.max(0)!!, 0.001f)
        assertEquals(150f, s.max(1)!!, 0.001f)
    }

    @Test
    fun empty_slot_gives_null() {
        val s = DualSlotSamples()
        s.add(0, 90f)
        assertNull(s.avg(1))
        assertNull(s.max(1))
        assertNull(s.minPositive(1))
    }

    @Test
    fun primary_is_slot_1_when_it_has_readings() {
        val s = DualSlotSamples()
        s.add(1, 150f)
        s.add(0, 100f)
        assertEquals(0, s.primarySlot())
        assertEquals(100f, s.primaryAvg()!!, 0.001f)
    }

    @Test
    fun primary_falls_back_to_slot_2_when_slot_1_has_none() {
        val s = DualSlotSamples()
        s.add(1, 140f)
        s.add(1, 160f)
        assertEquals(1, s.primarySlot())
        assertEquals(150f, s.primaryAvg()!!, 0.001f)
        assertEquals(160f, s.primaryMax()!!, 0.001f)
    }

    @Test
    fun no_readings_no_primary() {
        val s = DualSlotSamples()
        assertTrue(s.isEmpty())
        assertNull(s.primarySlot())
        assertNull(s.primaryAvg())
        assertNull(s.primaryMax())
    }

    @Test
    fun zero_rpm_counts_in_average_but_not_in_min() {
        val s = DualSlotSamples()
        listOf(0f, 80f, 90f).forEach { s.add(0, it) }
        assertEquals(170f / 3, s.avg(0)!!, 0.001f)
        assertEquals(80f, s.minPositive(0)!!, 0.001f)
    }

    @Test
    fun clear_empties_both_slots() {
        val s = DualSlotSamples()
        s.add(0, 1f)
        s.add(1, 2f)
        s.clear()
        assertTrue(s.isEmpty())
        assertEquals(0, s.count(0))
        assertEquals(0, s.count(1))
    }
}
