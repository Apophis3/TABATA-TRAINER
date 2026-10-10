package com.tuapp.tabatatrainer.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.tabatatrainer.data.local.GpsDao
import com.tuapp.tabatatrainer.data.local.SensorReadingDao
import com.tuapp.tabatatrainer.data.local.SessionDao
import com.tuapp.tabatatrainer.util.laps.SessionDeletion
import com.tuapp.tabatatrainer.data.local.WorkoutSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryStats(
    val totalSessions: Int = 0,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val gpsDao: GpsDao,
    private val sensorReadingDao: SensorReadingDao
) : ViewModel() {

    val sessions: StateFlow<List<WorkoutSessionEntity>> = sessionDao
        .getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _stats = MutableStateFlow(HistoryStats())
    val stats: StateFlow<HistoryStats> = _stats.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            val totalSessions = sessionDao.getCompletedSessionsCount()
            val avgHr = sessionDao.getAverageHeartRateAllTime()
            val maxHr = sessionDao.getMaxHeartRateEver()

            _stats.value = HistoryStats(
                totalSessions = totalSessions,
                avgHeartRate = avgHr?.toInt() ?: 0,
                maxHeartRate = maxHr ?: 0
            )
        }
    }

    fun deleteSession(session: WorkoutSessionEntity) {
        viewModelScope.launch {
            SessionDeletion.deleteWithChildren(
                deleteLaps = { gpsDao.deleteLapsForSession(session.id) },
                deleteGpsPoints = { gpsDao.deletePointsForSession(session.id) },
                deleteSensorReadings = { sensorReadingDao.deleteReadingsForSession(session.id) },
                deleteSession = { sessionDao.deleteSession(session) }
            )
            loadStats()
        }
    }
}