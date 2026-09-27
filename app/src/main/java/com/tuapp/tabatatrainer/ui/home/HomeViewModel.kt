package com.tuapp.tabatatrainer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.tabatatrainer.data.local.SessionDao
import com.tuapp.tabatatrainer.data.local.WorkoutSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class LastActivitySummary(
    val isFreeRide: Boolean,
    val startTime: Long,
    val durationSeconds: Int,
    val distanceMeters: Float?,
    val completedRounds: Int,
    val totalRounds: Int,
    val avgHeartRate: Int?
)

data class WeeklySummary(
    val sessions: Int = 0,
    val totalSeconds: Int = 0,
    val distanceMeters: Float = 0f,
    val avgHeartRate: Int? = null
)

data class HomeSummary(
    val last: LastActivitySummary? = null,
    val week: WeeklySummary = WeeklySummary()
)

/** Inicio de la semana actual (lunes 00:00 hora local) en epoch millis */
fun startOfWeekMillis(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(zone).toInstant().toEpochMilli()

/** Resumen de la Home a partir de las sesiones guardadas (lógica pura, testeable) */
fun buildHomeSummary(
    sessions: List<WorkoutSessionEntity>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault()
): HomeSummary {
    val last = sessions.maxByOrNull { it.startTime }?.let { s ->
        LastActivitySummary(
            isFreeRide = s.activityType == "FREE_RIDE",
            startTime = s.startTime,
            durationSeconds = s.totalTimeSeconds,
            distanceMeters = s.totalDistanceMeters,
            completedRounds = s.completedRounds,
            totalRounds = s.totalRounds,
            avgHeartRate = s.avgHeartRate?.takeIf { it > 0 }
        )
    }

    val weekStart = startOfWeekMillis(now, zone)
    val thisWeek = sessions.filter { it.startTime in weekStart..now }
    val hrs = thisWeek.mapNotNull { it.avgHeartRate?.takeIf { hr -> hr > 0 } }
    val week = WeeklySummary(
        sessions = thisWeek.size,
        totalSeconds = thisWeek.sumOf { it.totalTimeSeconds },
        distanceMeters = thisWeek.sumOf { (it.totalDistanceMeters ?: 0f).toDouble() }.toFloat(),
        avgHeartRate = if (hrs.isEmpty()) null else hrs.average().toInt()
    )
    return HomeSummary(last, week)
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    sessionDao: SessionDao
) : ViewModel() {

    val summary: StateFlow<HomeSummary> = sessionDao
        .getAllSessions()
        .map { buildHomeSummary(it, System.currentTimeMillis()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeSummary()
        )
}
