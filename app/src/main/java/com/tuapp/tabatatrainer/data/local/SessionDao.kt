package com.tuapp.tabatatrainer.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSessionEntity)
    
    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)
    
    @Delete
    suspend fun deleteSession(session: WorkoutSessionEntity)
    
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): WorkoutSessionEntity?
    
    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<WorkoutSessionEntity>>
    
    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentSessions(limit: Int = 10): Flow<List<WorkoutSessionEntity>>
    
    @Query("SELECT * FROM workout_sessions WHERE isCompleted = 1 ORDER BY startTime DESC")
    fun getCompletedSessions(): Flow<List<WorkoutSessionEntity>>
    
    // Estadísticas
    @Query("SELECT AVG(avgHeartRate) FROM workout_sessions WHERE avgHeartRate IS NOT NULL")
    suspend fun getAverageHeartRateAllTime(): Float?
    
    @Query("SELECT MAX(maxHeartRate) FROM workout_sessions")
    suspend fun getMaxHeartRateEver(): Int?
    
    @Query("SELECT COUNT(*) FROM workout_sessions WHERE isCompleted = 1")
    suspend fun getCompletedSessionsCount(): Int
    
    @Query("""
        SELECT COUNT(*) FROM workout_sessions 
        WHERE startTime >= :startOfDay AND startTime < :endOfDay
    """)
    suspend fun getSessionsCountForDay(startOfDay: Long, endOfDay: Long): Int
}

@Dao
interface SensorReadingDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: SensorReadingEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadings(readings: List<SensorReadingEntity>)
    
    @Query("SELECT * FROM sensor_readings WHERE sessionId = :sessionId ORDER BY timestamp")
    fun getReadingsForSession(sessionId: String): Flow<List<SensorReadingEntity>>
    
    @Query("SELECT * FROM sensor_readings WHERE sessionId = :sessionId ORDER BY timestamp")
    suspend fun getReadingsForSessionSync(sessionId: String): List<SensorReadingEntity>
    
    @Query("""
        SELECT AVG(heartRate) FROM sensor_readings 
        WHERE sessionId = :sessionId AND heartRate IS NOT NULL
    """)
    suspend fun getAvgHeartRate(sessionId: String): Float?
    
    @Query("""
        SELECT MAX(heartRate) FROM sensor_readings 
        WHERE sessionId = :sessionId AND heartRate IS NOT NULL
    """)
    suspend fun getMaxHeartRate(sessionId: String): Int?
    
    @Query("""
        SELECT MIN(heartRate) FROM sensor_readings 
        WHERE sessionId = :sessionId AND heartRate IS NOT NULL AND heartRate > 0
    """)
    suspend fun getMinHeartRate(sessionId: String): Int?
    
    @Query("""
        SELECT AVG(cadence) FROM sensor_readings 
        WHERE sessionId = :sessionId AND cadence IS NOT NULL
    """)
    suspend fun getAvgCadence(sessionId: String): Float?
    
    @Query("""
        SELECT MAX(cadence) FROM sensor_readings 
        WHERE sessionId = :sessionId AND cadence IS NOT NULL
    """)
    suspend fun getMaxCadence(sessionId: String): Float?
    
    @Query("DELETE FROM sensor_readings WHERE sessionId = :sessionId")
    suspend fun deleteReadingsForSession(sessionId: String)
}
