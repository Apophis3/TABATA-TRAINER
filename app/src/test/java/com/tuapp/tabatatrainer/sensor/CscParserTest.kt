package com.tuapp.tabatatrainer.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CscParserTest {

    /** Paquete CSC solo con biela: flags 0x02, vueltas (u16), hora del último evento (u16, 1/1024 s) */
    private fun crank(revs: Int, time: Int) = byteArrayOf(
        0x02, (revs and 0xFF).toByte(), (revs shr 8 and 0xFF).toByte(), (time and 0xFF).toByte(), (time shr 8 and 0xFF).toByte()
    )

    /** Paquete con rueda (6 bytes) y biela: flags 0x03 */
    private fun wheelAndCrank(revs: Int, time: Int) = byteArrayOf(
        0x03, 9, 9, 9, 9, 9, 9,
        (revs and 0xFF).toByte(), (revs shr 8 and 0xFF).toByte(), (time and 0xFF).toByte(), (time shr 8 and 0xFF).toByte()
    )

    @Test
    fun `el primer paquete solo es referencia`() {
        assertNull(CscParser().onMeasurement(crank(10, 1000), 0))
    }

    @Test
    fun `una vuelta en un segundo son 60 rpm`() {
        val p = CscParser()
        p.onMeasurement(crank(10, 1000), 0)
        assertEquals(60, p.onMeasurement(crank(11, 2024), 1000))
    }

    @Test
    fun `salta los datos de rueda`() {
        val p = CscParser()
        p.onMeasurement(wheelAndCrank(10, 1000), 0)
        assertEquals(90, p.onMeasurement(wheelAndCrank(13, 3048), 2000))
    }

    @Test
    fun `corrige el desbordamiento de los contadores de 16 bits`() {
        val p = CscParser()
        p.onMeasurement(crank(65535, 65000), 0)
        assertEquals(60, p.onMeasurement(crank(0, (65000 + 1024) % 65536), 1000))
    }

    @Test
    fun `sin vuelta nueva da null hasta 3 s y luego 0 rpm`() {
        val p = CscParser()
        p.onMeasurement(crank(10, 1000), 0)
        assertEquals(60, p.onMeasurement(crank(11, 2024), 1000))
        assertNull(p.onMeasurement(crank(11, 2024), 2000))
        assertNull(p.onMeasurement(crank(11, 2024), 3500))
        assertEquals(0, p.onMeasurement(crank(11, 2024), 4000))
        assertEquals(0, p.onMeasurement(crank(11, 2024), 9000))
        assertEquals(60, p.onMeasurement(crank(12, 3048), 10000))
    }

    @Test
    fun `descarta tiempos imposibles y rpm fuera de rango`() {
        val p = CscParser()
        p.onMeasurement(crank(10, 1000), 0)
        assertNull("intervalo demasiado corto", p.onMeasurement(crank(11, 1010), 100))
        assertNull("más de 300 rpm", p.onMeasurement(crank(17, 2034), 1100))   // 6 vueltas en ~1 s = 360 rpm
    }

    @Test
    fun `paquete sin datos de biela no sirve`() {
        val p = CscParser()
        assertNull(p.onMeasurement(byteArrayOf(0x01, 1, 0, 0, 0, 0, 0), 0))
        assertNull(p.onMeasurement(byteArrayOf(), 0))
    }

    @Test
    fun `reset olvida la referencia`() {
        val p = CscParser()
        p.onMeasurement(crank(10, 1000), 0)
        p.reset()
        assertNull(p.onMeasurement(crank(500, 3000), 1000))
    }
}
