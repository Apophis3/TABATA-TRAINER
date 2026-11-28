package com.tuapp.tabatatrainer.util.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class RouteSimplifierTest {

    private data class P(val lat: Double, val lon: Double)

    private val lat0 = 28.0
    private val lon0 = -16.0
    private val mPerDegLat = 111_320.0
    private val mPerDegLon = 111_320.0 * cos(Math.toRadians(lat0))

    /** Punto a (este, norte) metros del origen. */
    private fun p(eastM: Double, northM: Double) = P(lat0 + northM / mPerDegLat, lon0 + eastM / mPerDegLon)

    private fun simplifier(maxWindow: Int = RouteSimplifier.DEFAULT_MAX_WINDOW) =
        RouteSimplifier<P>({ it.lat }, { it.lon }, maxWindow = maxWindow)

    @Test
    fun empty_route_has_no_points() {
        assertEquals(emptyList<P>(), simplifier().points())
    }

    @Test
    fun single_point_is_kept() {
        val s = simplifier()
        s.add(p(0.0, 0.0))
        assertEquals(listOf(p(0.0, 0.0)), s.points())
    }

    @Test
    fun straight_line_keeps_only_first_and_last() {
        val s = simplifier()
        for (i in 0..100) s.add(p(i * 2.0, 0.0))
        assertEquals(listOf(p(0.0, 0.0), p(200.0, 0.0)), s.points())
        assertEquals(101, s.receivedCount)
    }

    @Test
    fun noise_below_two_meters_is_removed() {
        val s = simplifier()
        for (i in 0..100) s.add(p(i * 2.0, if (i % 2 == 0) 0.8 else -0.8))
        assertTrue("quedaron ${s.points().size}", s.points().size <= 3)
        assertEquals(p(0.0, 0.8), s.points().first())
        assertEquals(p(200.0, 0.8), s.points().last())
    }

    @Test
    fun corner_is_kept() {
        val s = simplifier()
        for (i in 0..50) s.add(p(i * 2.0, 0.0))      // 100 m al este
        for (i in 1..50) s.add(p(100.0, i * 2.0))    // 100 m al norte
        val pts = s.points()
        assertEquals(p(0.0, 0.0), pts.first())
        assertEquals(p(100.0, 100.0), pts.last())
        // La esquina queda representada a menos de 2 m
        assertTrue(pts.any { kotlin.math.abs(it.lon - p(100.0, 0.0).lon) * mPerDegLon < 2.5 &&
                kotlin.math.abs(it.lat - lat0) * mPerDegLat < 2.5 })
        assertTrue(pts.size in 3..4)
    }

    @Test
    fun out_and_back_keeps_turning_point() {
        val s = simplifier()
        for (i in 0..50) s.add(p(i * 2.0, 0.0))
        for (i in 49 downTo 0) s.add(p(i * 2.0, 0.0))
        val pts = s.points()
        assertTrue(pts.any { (it.lon - lon0) * mPerDegLon > 97.0 })
        assertEquals(p(0.0, 0.0), pts.last())
    }

    @Test
    fun first_point_is_always_the_real_start() {
        val s = simplifier()
        s.add(p(0.0, 0.0))
        for (i in 1..3000) s.add(p(i * 1.0, kotlin.math.sin(i / 30.0) * 20.0))
        assertEquals(p(0.0, 0.0), s.points().first())
        assertEquals(p(3000.0, kotlin.math.sin(100.0) * 20.0), s.points().last())
        assertTrue(s.points().size < 3001)
    }

    @Test
    fun window_cap_fixes_a_point() {
        val s = simplifier(maxWindow = 10)
        for (i in 0..30) s.add(p(i * 1.0, 0.0))
        // Recta, pero el tope de ventana obliga a fijar puntos intermedios
        assertTrue(s.points().size > 2)
        assertEquals(p(30.0, 0.0), s.points().last())
    }

    @Test
    fun reset_clears_everything() {
        val s = simplifier()
        for (i in 0..10) s.add(p(i * 5.0, 0.0))
        s.reset()
        assertEquals(emptyList<P>(), s.points())
        assertEquals(0, s.receivedCount)
        s.add(p(7.0, 7.0))
        assertEquals(listOf(p(7.0, 7.0)), s.points())
    }
}
