package com.tuapp.tabatatrainer.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 007, R12: una conexión BLE muda se cierra para que el escaneo la vuelva a enganchar */
class BleLinkWatchTest {

    private val a = "AA:BB:CC:DD:05:69"
    private val b = "AA:BB:CC:DD:BD:00"

    @Test
    fun `con notificaciones seguidas la conexion no se cierra`() {
        val w = BleLinkWatch(20_000L)
        w.onConnected(a, 0L)
        for (t in 1_000L..120_000L step 1_000L) {
            w.onNotify(a, t)
            assertTrue("t=$t", w.silent(t).isEmpty())
        }
    }

    @Test
    fun `sin notificaciones 20 s la conexion se da por muda`() {
        val w = BleLinkWatch(20_000L)
        w.onConnected(a, 0L)
        w.onNotify(a, 5_000L)
        assertTrue(w.silent(24_999L).isEmpty())
        assertEquals(listOf(a), w.silent(25_000L))
    }

    @Test
    fun `una conexion aun sin suscribir no se vigila`() {
        val w = BleLinkWatch(20_000L)
        w.onNotify(a, 0L)                   // llega antes de suscribirse: no la apunta
        assertTrue(w.silent(60_000L).isEmpty())
    }

    @Test
    fun `olvidar una direccion la quita y las demas siguen`() {
        val w = BleLinkWatch(20_000L)
        w.onConnected(a, 0L)
        w.onConnected(b, 0L)
        w.forget(a)
        assertEquals(listOf(b), w.silent(30_000L))
        w.onNotify(a, 31_000L)              // tras olvidarla, otra notificación no la revive
        w.clear()
        assertTrue(w.silent(90_000L).isEmpty())
    }

    @Test
    fun `cadencia con su propio umbral de 30 s`() {
        val w = BleLinkWatch(30_000L)
        w.onConnected(b, 0L)
        assertTrue(w.silent(29_999L).isEmpty())
        assertEquals(listOf(b), w.silent(30_000L))
    }
}
