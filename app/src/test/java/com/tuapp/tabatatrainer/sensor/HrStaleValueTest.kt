package com.tuapp.tabatatrainer.sensor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 007, R2.1: al quitar la banda su valor congelado deja de contar (ANT+ sin latidos, BLE sin contacto) */
class HrStaleValueTest {

    private fun watch() = AntBeatWatch(HeartRateHub.ANT_BEAT_STALL_MS)

    @Test
    fun `con latidos nuevos el dato ANT+ cuenta`() {
        val w = watch()
        for (t in 0L..10_000L step 250L) {
            w.onData(100 + t / 1_000L, t)          // 60 bpm: un latido por segundo, datos a 4 Hz
            assertTrue("t=$t", w.beating)
        }
    }

    @Test
    fun `a 25 bpm sigue contando`() {
        val w = watch()
        for (t in 0L..20_000L step 250L) {
            w.onData(t / 2_400L, t)                // un latido cada 2,4 s
            assertTrue("t=$t", w.beating)
        }
    }

    @Test
    fun `sin latidos nuevos deja de contar a los 3 s y vuelve con el siguiente latido`() {
        val w = watch()
        w.onData(50, 0)
        assertFalse(w.onData(50, 2_750))
        assertTrue(w.beating)
        assertTrue("cambia y se registra una vez", w.onData(50, 3_000))
        assertFalse(w.beating)
        assertFalse("no se repite el registro", w.onData(50, 10_000))
        assertTrue(w.onData(51, 10_250))
        assertTrue(w.beating)
    }

    @Test
    fun `el contador que da la vuelta cuenta como latido`() {
        val w = watch()
        w.onData(Long.MAX_VALUE, 0)
        w.onData(0, 2_900)
        w.onData(0, 5_000)
        assertTrue(w.beating)
    }

    @Test
    fun `BLE sin aviso de contacto siempre cuenta`() {
        assertFalse(bleSkinContactLost(byteArrayOf(0x00, 70)))
        assertFalse(bleSkinContactLost(byteArrayOf(0x02, 70)))
        assertFalse(bleSkinContactLost(byteArrayOf()))
    }

    @Test
    fun `BLE con contacto cuenta y sin contacto no`() {
        assertFalse(bleSkinContactLost(byteArrayOf(0x06, 70)))
        assertTrue(bleSkinContactLost(byteArrayOf(0x04, 70)))
        assertTrue("con pulso de 16 bits y RR", bleSkinContactLost(byteArrayOf(0x15, 70, 0, 0, 4)))
        assertFalse(bleSkinContactLost(byteArrayOf(0x17, 70, 0, 0, 4)))
    }

    @Test
    fun `BLE que admite el aviso pero nunca dice contacto sigue contando`() {
        val c = BleContactWatch()
        repeat(10) {
            assertFalse(c.onData(byteArrayOf(0x04, 70)))
            assertFalse(c.lost)
        }
    }

    @Test
    fun `BLE con contacto y luego sin contacto deja de contar y vuelve`() {
        val c = BleContactWatch()
        assertFalse(c.onData(byteArrayOf(0x06, 70)))
        assertFalse(c.lost)
        assertTrue("cambia y se registra una vez", c.onData(byteArrayOf(0x04, 70)))
        assertTrue(c.lost)
        assertFalse("no se repite el registro", c.onData(byteArrayOf(0x04, 70)))
        assertTrue(c.onData(byteArrayOf(0x06, 72)))
        assertFalse(c.lost)
    }
}
