package com.tuapp.tabatatrainer.ui.home

import com.tuapp.tabatatrainer.data.local.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HomeSummaryTest {

    private val zone = ZoneId.of("Europe/Madrid")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun session(
        id: String, start: Long, secs: Int = 600, hr: Int? = null,
        dist: Float? = null, type: String = "TABATA"
    ) = WorkoutSessionEntity(
        id = id, startTime = start, warmupSeconds = 0, workSeconds = 20, restSeconds = 10,
        totalRounds = 8, completedRounds = 8, totalTimeSeconds = secs, avgHeartRate = hr,
        totalDistanceMeters = dist, activityType = type
    )

    // Domingo 27/09/2026 → la semana empieza el lunes 21/09
    private val now = at(2026, 9, 27, 18)

    @Test
    fun emptyHistory_givesNoLastAndZeroWeek() {
        val s = buildHomeSummary(emptyList(), now, zone)
        assertNull(s.last)
        assertEquals(0, s.week.sessions)
        assertNull(s.week.avgHeartRate)
    }

    @Test
    fun weekStartsOnMondayMidnight() {
        assertEquals(at(2026, 9, 21, 0), startOfWeekMillis(now, zone))
    }

    @Test
    fun weeklyTotals_onlyCountCurrentWeek() {
        val list = listOf(
            session("old", at(2026, 9, 20), secs = 999, hr = 180),            // domingo anterior
            session("a", at(2026, 9, 21, 7), secs = 600, hr = 140),
            session("b", at(2026, 9, 25), secs = 1200, hr = 0, dist = 5000f, type = "FREE_RIDE"),
            session("c", at(2026, 9, 27, 9), secs = 300, hr = 160, dist = 2500f)
        )
        val s = buildHomeSummary(list, now, zone)
        assertEquals(3, s.week.sessions)
        assertEquals(2100, s.week.totalSeconds)
        assertEquals(7500f, s.week.distanceMeters, 0.01f)
        assertEquals(150, s.week.avgHeartRate)   // hr = 0 se ignora
    }

    @Test
    fun lastActivity_isMostRecent() {
        val list = listOf(
            session("a", at(2026, 9, 21)),
            session("b", at(2026, 9, 26), type = "FREE_RIDE", dist = 12000f, hr = 0)
        )
        val last = buildHomeSummary(list, now, zone).last!!
        assertTrue(last.isFreeRide)
        assertEquals(12000f, last.distanceMeters!!, 0.01f)
        assertNull(last.avgHeartRate)
    }
}
