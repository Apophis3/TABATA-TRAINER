package com.tuapp.tabatatrainer.util.laps

import com.tuapp.tabatatrainer.util.laps.HeartRateCardLayout.SINGLE_HR1
import com.tuapp.tabatatrainer.util.laps.HeartRateCardLayout.SINGLE_HR2
import com.tuapp.tabatatrainer.util.laps.HeartRateCardLayout.SPLIT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateCardPolicyTest {

    @Test
    fun `dos bandas con senal - tarjeta partida`() {
        assertEquals(SPLIT, HeartRateCardPolicy.desired(true, true, SINGLE_HR1))
    }

    @Test
    fun `solo una con senal - tarjeta unica de ese hueco`() {
        assertEquals(SINGLE_HR1, HeartRateCardPolicy.desired(true, false, SPLIT))
        assertEquals(SINGLE_HR2, HeartRateCardPolicy.desired(false, true, SPLIT))
    }

    @Test
    fun `ninguna con senal - se queda como estaba`() {
        assertEquals(SINGLE_HR2, HeartRateCardPolicy.desired(false, false, SINGLE_HR2))
        assertEquals(SPLIT, HeartRateCardPolicy.desired(false, false, SPLIT))
    }

    @Test
    fun `de unica a partida espera mas que la frescura - un dato suelto no parte la tarjeta`() {
        assertTrue(HeartRateCardPolicy.holdMs(SINGLE_HR1, SPLIT) > HeartRateCardPolicy.STALE_MS)
        assertTrue(HeartRateCardPolicy.holdMs(SINGLE_HR2, SPLIT) > HeartRateCardPolicy.STALE_MS)
    }

    @Test
    fun `de partida a unica y entre unicas espera poco - sin cambio no espera`() {
        assertEquals(HeartRateCardPolicy.DEFAULT_HOLD_MS, HeartRateCardPolicy.holdMs(SPLIT, SINGLE_HR1))
        assertEquals(HeartRateCardPolicy.DEFAULT_HOLD_MS, HeartRateCardPolicy.holdMs(SINGLE_HR1, SINGLE_HR2))
        assertEquals(0L, HeartRateCardPolicy.holdMs(SPLIT, SPLIT))
    }
}
