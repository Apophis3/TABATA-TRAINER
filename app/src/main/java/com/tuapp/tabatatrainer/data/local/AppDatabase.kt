package com.tuapp.tabatatrainer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkoutSessionEntity::class,
        SensorReadingEntity::class,
        GpsPointEntity::class,
        PoiEntity::class,           // ← NUEVO: Puntos de Interés
        LapEntity::class,           // ← NUEVO: Vueltas/Laps
        DeviceProfileEntity::class  // ← NUEVO: Perfiles de dispositivos
    ],
    version = 8,                    // ← INCREMENTADO (agregado avgHeartRate2, maxHeartRate2)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun sensorReadingDao(): SensorReadingDao
    abstract fun gpsDao(): GpsDao
    abstract fun deviceProfileDao(): DeviceProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tabata_trainer_db"
                )
                    .fallbackToDestructiveMigration()  // Borra datos si cambia esquema
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
