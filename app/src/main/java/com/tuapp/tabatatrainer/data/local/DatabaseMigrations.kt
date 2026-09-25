package com.tuapp.tabatatrainer.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migraciones de la base de datos Room
 * 
 * Historial de versiones:
 * - Versión 1-3: Versiones iniciales (sin migraciones específicas)
 * - Versión 4: Base estable con workout_sessions, sensor_readings, gps_points
 * - Versión 5: Añadidas tablas poi_points y lap_records
 * - Versión 6: Añadida columna heartRate2 a sensor_readings
 * - Versión 7: Añadida tabla device_profiles para persistencia de sensores
 * - Versión 9: Añadidos totalSteps, avgStrideM y avgStepCadence a workout_sessions
 */
object DatabaseMigrations {

    /**
     * Migración de versión 4 a 5
     * Añade las tablas poi_points y lap_records
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Crear tabla poi_points
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS poi_points (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sessionId TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    altitude REAL,
                    name TEXT,
                    description TEXT,
                    phase TEXT NOT NULL,
                    round INTEGER NOT NULL,
                    heartRate INTEGER,
                    speedKmh REAL,
                    distanceMeters REAL,
                    elapsedSeconds INTEGER NOT NULL
                )
            """.trimIndent())

            // Crear tabla lap_records
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS lap_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sessionId TEXT NOT NULL,
                    lapNumber INTEGER NOT NULL,
                    startTimestamp INTEGER NOT NULL,
                    endTimestamp INTEGER NOT NULL,
                    startDistanceMeters REAL NOT NULL,
                    endDistanceMeters REAL NOT NULL,
                    avgSpeedKmh REAL NOT NULL,
                    maxSpeedKmh REAL NOT NULL,
                    avgHeartRate INTEGER,
                    maxHeartRate INTEGER,
                    avgCadence REAL,
                    elevationGain REAL
                )
            """.trimIndent())

            // Crear índices para mejorar rendimiento
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS index_poi_points_sessionId 
                ON poi_points(sessionId)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS index_lap_records_sessionId 
                ON lap_records(sessionId)
            """.trimIndent())
        }
    }

    /**
     * Migración de versión 5 a 6
     * Añade la columna heartRate2 a la tabla sensor_readings
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Verificar si la columna heartRate2 ya existe antes de añadirla
            val cursor = database.query("PRAGMA table_info(sensor_readings)")
            var columnExists = false
            try {
                while (cursor.moveToNext()) {
                    val columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    if (columnName == "heartRate2") {
                        columnExists = true
                        break
                    }
                }
            } finally {
                cursor.close()
            }
            
            // Solo añadir la columna si no existe
            if (!columnExists) {
                database.execSQL("""
                    ALTER TABLE sensor_readings 
                    ADD COLUMN heartRate2 INTEGER
                """.trimIndent())
            }
        }
    }

    /**
     * Migración de versión 6 a 7
     * Añade la tabla device_profiles para persistencia de sensores conocidos
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Crear tabla device_profiles
            // IMPORTANTE: El orden de las columnas y los valores por defecto deben coincidir exactamente con Room
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS device_profiles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    deviceId TEXT NOT NULL,
                    sensorType TEXT NOT NULL,
                    protocolType TEXT NOT NULL,
                    deviceName TEXT,
                    macAddress TEXT,
                    antDeviceNumber INTEGER,
                    alias TEXT,
                    isFavorite INTEGER NOT NULL,
                    lastConnected INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    connectionCount INTEGER NOT NULL
                )
            """.trimIndent())
            
            // Insertar valores por defecto para registros existentes (si los hay)
            // Esto no es necesario para una tabla nueva, pero lo dejamos por si acaso

            // Crear índices para mejorar rendimiento
            database.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS index_device_profiles_deviceId 
                ON device_profiles(deviceId)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS index_device_profiles_sensorType 
                ON device_profiles(sensorType)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS index_device_profiles_macAddress 
                ON device_profiles(macAddress)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS index_device_profiles_antDeviceNumber 
                ON device_profiles(antDeviceNumber)
            """.trimIndent())
        }
    }

    /**
     * Migración de versión 8 a 9: pasos del podómetro en la sesión
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE workout_sessions ADD COLUMN totalSteps INTEGER")
            database.execSQL("ALTER TABLE workout_sessions ADD COLUMN avgStrideM REAL")
            database.execSQL("ALTER TABLE workout_sessions ADD COLUMN avgStepCadence REAL")
        }
    }

    /**
     * Lista de todas las migraciones disponibles
     */
    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_8_9
    )
}
