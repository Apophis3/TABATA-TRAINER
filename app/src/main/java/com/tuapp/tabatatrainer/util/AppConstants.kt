package com.tuapp.tabatatrainer.util

/**
 * Constantes de la aplicación
 * Centraliza valores mágicos para facilitar mantenimiento
 */
object AppConstants {
    
    // ============================================================================
    // Notificaciones
    // ============================================================================
    const val WORKOUT_NOTIFICATION_ID = 1001
    const val FREERIDE_NOTIFICATION_ID = 2001
    const val WORKOUT_CHANNEL_ID = "workout_channel"
    const val FREERIDE_CHANNEL_ID = "freeride_channel"
    
    // ============================================================================
    // Límites de datos
    // ============================================================================
    /**
     * Máximo número de lecturas de sensores a guardar (1 por segundo)
     * Equivale a aproximadamente 1 hora de datos
     */
    const val MAX_SENSOR_READINGS = 600
    
    /**
     * Máximo número de puntos GPS para el mapa en memoria
     */
    const val MAX_MAP_POINTS = 1000
    
    /**
     * Tamaño del buffer de puntos GPS antes de guardar en BD
     */
    const val GPS_BUFFER_SIZE = 10
    
    // ============================================================================
    // Delays y timeouts
    // ============================================================================
    /**
     * Delay antes de iniciar cadencia para evitar conflictos BLE (ms)
     * Reducido para mejorar detección
     */
    const val CADENCE_START_DELAY_MS = 500L
    
    /**
     * Delay para reconexión de sensores (ms)
     */
    const val SENSOR_RECONNECT_DELAY_MS = 500L
    
    /**
     * Delay antes de iniciar búsqueda ANT+ de cadencia para evitar conflictos con HR (ms)
     * Permite que HR tenga tiempo de conectarse primero
     */
    const val CADENCE_ANT_PLUS_DELAY_MS = 2000L
    
    // ============================================================================
    // Valores por defecto
    // ============================================================================
    const val DEFAULT_WARMUP_SECONDS = 10
    const val DEFAULT_WORK_SECONDS = 20
    const val DEFAULT_REST_SECONDS = 10
    const val DEFAULT_ROUNDS = 8
    
    // ============================================================================
    // Validaciones
    // ============================================================================
    const val MIN_HEART_RATE = 30
    const val MAX_HEART_RATE = 250
    const val MIN_CADENCE = 0
    const val MAX_CADENCE = 200
    const val MIN_WORKOUT_SECONDS = 1
    const val MAX_WORKOUT_SECONDS = 3600
    const val MIN_ROUNDS = 1
    const val MAX_ROUNDS = 100
}
