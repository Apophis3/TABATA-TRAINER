package com.tuapp.tabatatrainer.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ============================================================================
// Entidad para puntos GPS
// ============================================================================

@Entity(tableName = "gps_points")
data class GpsPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val speed: Float?,
    val accuracy: Float?,
    val phase: String,
    val round: Int
)

// ============================================================================
// DAO para GPS
// ============================================================================

@Dao
interface GpsDao {

    // === Puntos GPS ===
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: GpsPointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<GpsPointEntity>)

    @Query("SELECT * FROM gps_points WHERE sessionId = :sessionId ORDER BY timestamp")
    fun getPointsForSession(sessionId: String): Flow<List<GpsPointEntity>>

    @Query("SELECT * FROM gps_points WHERE sessionId = :sessionId ORDER BY timestamp")
    suspend fun getPointsForSessionSync(sessionId: String): List<GpsPointEntity>

    @Query("DELETE FROM gps_points WHERE sessionId = :sessionId")
    suspend fun deletePointsForSession(sessionId: String)

    @Query("SELECT COUNT(*) FROM gps_points WHERE sessionId = :sessionId")
    suspend fun getPointsCount(sessionId: String): Int

    // === POI (Puntos de Interés) ===
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoi(poi: PoiEntity)

    @Query("SELECT * FROM poi_points WHERE sessionId = :sessionId ORDER BY timestamp")
    fun getPoisForSession(sessionId: String): Flow<List<PoiEntity>>

    @Query("SELECT * FROM poi_points WHERE sessionId = :sessionId ORDER BY timestamp")
    suspend fun getPoisForSessionSync(sessionId: String): List<PoiEntity>

    @Query("SELECT COUNT(*) FROM poi_points WHERE sessionId = :sessionId")
    suspend fun getPoiCount(sessionId: String): Int

    @Query("DELETE FROM poi_points WHERE sessionId = :sessionId")
    suspend fun deletePoisForSession(sessionId: String)

    @Query("DELETE FROM poi_points WHERE id = :poiId")
    suspend fun deletePoi(poiId: Int)

    // === LAPs (Vueltas) ===
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLap(lap: LapEntity)

    @Query("SELECT * FROM lap_records WHERE sessionId = :sessionId ORDER BY lapNumber")
    fun getLapsForSession(sessionId: String): Flow<List<LapEntity>>

    @Query("SELECT * FROM lap_records WHERE sessionId = :sessionId ORDER BY lapNumber")
    suspend fun getLapsForSessionSync(sessionId: String): List<LapEntity>

    @Query("SELECT COUNT(*) FROM lap_records WHERE sessionId = :sessionId")
    suspend fun getLapCount(sessionId: String): Int

    @Query("DELETE FROM lap_records WHERE sessionId = :sessionId")
    suspend fun deleteLapsForSession(sessionId: String)

    // === Estadísticas GPS para una sesión ===
    
    @Query("""
        SELECT 
            MAX(speed) * 3.6 as maxSpeedKmh,
            AVG(speed) * 3.6 as avgSpeedKmh,
            MAX(altitude) as maxAltitude,
            MIN(altitude) as minAltitude
        FROM gps_points 
        WHERE sessionId = :sessionId AND speed IS NOT NULL
    """)
    suspend fun getGpsStatsForSession(sessionId: String): GpsSessionStats?
}

// Clase auxiliar para estadísticas GPS
data class GpsSessionStats(
    val maxSpeedKmh: Float?,
    val avgSpeedKmh: Float?,
    val maxAltitude: Double?,
    val minAltitude: Double?
)
