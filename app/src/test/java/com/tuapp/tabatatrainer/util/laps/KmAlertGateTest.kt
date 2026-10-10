package com.tuapp.tabatatrainer.util.laps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KmAlertGateTest {

    @Test
    fun alerts_once_per_km() {
        val gate = KmAlertGate()
        assertEquals(1, gate.alertFor(listOf(1)))
        assertNull(gate.alertFor(listOf(1)))
        assertEquals(2, gate.alertFor(listOf(2)))
        assertNull(gate.alertFor(listOf(2)))
    }

    @Test
    fun no_crossings_no_alert() {
        assertNull(KmAlertGate().alertFor(emptyList()))
    }

    @Test
    fun multi_km_jump_alerts_only_the_last_one() {
        val gate = KmAlertGate()
        assertEquals(3, gate.alertFor(listOf(1, 2, 3)))
        assertNull(gate.alertFor(listOf(3)))
        assertEquals(4, gate.alertFor(listOf(4)))
    }

    @Test
    fun never_goes_backwards() {
        val gate = KmAlertGate()
        gate.alertFor(listOf(5))
        assertNull(gate.alertFor(listOf(4)))
    }

    @Test
    fun reset_allows_a_new_session_to_alert_km_1_again() {
        val gate = KmAlertGate()
        gate.alertFor(listOf(1, 2))
        gate.reset()
        assertEquals(1, gate.alertFor(listOf(1)))
    }

    @Test
    fun banner_text_with_and_without_pace() {
        assertEquals("KM 3 · 5:42/km", KmAlert(3, 342).bannerText())
        assertEquals("KM 10", KmAlert(10, null).bannerText())
    }
}
