package com.tuapp.tabatatrainer.util.laps

import org.junit.Assert.assertEquals
import org.junit.Test

class CadenceDisplayPolicyTest {

    private val now = 1_000_000L

    @Test
    fun paired_and_recent_sample_shows_cadence() {
        assertEquals(
            CadenceSlotMode.CADENCE,
            CadenceDisplayPolicy.mode(paired = true, lastSampleAtMs = now - 1_000, nowMs = now)
        )
    }

    @Test
    fun sample_exactly_at_threshold_is_still_cadence() {
        assertEquals(
            CadenceSlotMode.CADENCE,
            CadenceDisplayPolicy.mode(true, now - CadenceDisplayPolicy.CADENCE_STALE_MS, now)
        )
    }

    @Test
    fun sensor_silent_beyond_threshold_shows_pace() {
        assertEquals(
            CadenceSlotMode.PACE,
            CadenceDisplayPolicy.mode(true, now - CadenceDisplayPolicy.CADENCE_STALE_MS - 1, now)
        )
    }

    @Test
    fun no_paired_sensor_shows_pace() {
        assertEquals(CadenceSlotMode.PACE, CadenceDisplayPolicy.mode(false, null, now))
        assertEquals(CadenceSlotMode.PACE, CadenceDisplayPolicy.mode(false, now - 500, now))
    }

    @Test
    fun paired_but_never_emitted_shows_pace() {
        assertEquals(CadenceSlotMode.PACE, CadenceDisplayPolicy.mode(true, null, now))
    }

    @Test
    fun live_switch_both_ways() {
        // emitiendo -> cae -> vuelve
        assertEquals(CadenceSlotMode.CADENCE, CadenceDisplayPolicy.mode(true, now, now + 2_000))
        assertEquals(CadenceSlotMode.PACE, CadenceDisplayPolicy.mode(true, now, now + 6_000))
        assertEquals(CadenceSlotMode.CADENCE, CadenceDisplayPolicy.mode(true, now + 6_500, now + 7_000))
    }

    @Test
    fun slot_shows_cadence_in_rpm() {
        val slot = CadenceDisplayPolicy.slot(cadenceActive = true, cadenceRpm = 87.4f, paceSecPerKm = 330)
        assertEquals(CadenceSlotMode.CADENCE, slot.mode)
        assertEquals("Cadencia", slot.label)
        assertEquals("87", slot.value)
        assertEquals("rpm", slot.unit)
    }

    @Test
    fun slot_shows_pace_when_sensor_inactive() {
        val slot = CadenceDisplayPolicy.slot(cadenceActive = false, cadenceRpm = 87f, paceSecPerKm = 330)
        assertEquals(CadenceSlotMode.PACE, slot.mode)
        assertEquals("Ritmo", slot.label)
        assertEquals("5:30", slot.value)
        assertEquals("/km", slot.unit)
    }

    @Test
    fun slot_pace_without_data_is_dashes_not_zero() {
        val slot = CadenceDisplayPolicy.slot(cadenceActive = false, cadenceRpm = 0f, paceSecPerKm = null)
        assertEquals("--", slot.value)
    }

    @Test
    fun slot_active_sensor_with_zero_rpm_shows_zero() {
        val slot = CadenceDisplayPolicy.slot(cadenceActive = true, cadenceRpm = 0f, paceSecPerKm = 330)
        assertEquals("0", slot.value)
    }
}
