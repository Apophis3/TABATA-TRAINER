package com.tuapp.tabatatrainer.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO para gestionar perfiles de dispositivos conocidos
 * Permite persistencia y priorización de sensores
 */
@Dao
interface DeviceProfileDao {
    
    /**
     * Obtener todos los dispositivos conocidos, ordenados por prioridad
     * Prioridad: isFavorite DESC, lastConnected DESC
     */
    @Query("""
        SELECT * FROM device_profiles 
        ORDER BY isFavorite DESC, lastConnected DESC
    """)
    fun getAllDevices(): Flow<List<DeviceProfileEntity>>
    
    /**
     * Obtener dispositivos por tipo de sensor, ordenados por prioridad
     */
    @Query("""
        SELECT * FROM device_profiles 
        WHERE sensorType = :sensorType
        ORDER BY isFavorite DESC, lastConnected DESC
    """)
    fun getDevicesByType(sensorType: String): Flow<List<DeviceProfileEntity>>
    
    /**
     * Obtener dispositivo por ID único (Device Number ANT+ o MAC BLE)
     */
    @Query("""
        SELECT * FROM device_profiles 
        WHERE deviceId = :deviceId
        LIMIT 1
    """)
    suspend fun getDeviceById(deviceId: String): DeviceProfileEntity?
    
    /**
     * Obtener dispositivo por MAC address (para vincular ANT+ y BLE del mismo dispositivo)
     */
    @Query("""
        SELECT * FROM device_profiles 
        WHERE macAddress = :macAddress
        LIMIT 1
    """)
    suspend fun getDeviceByMacAddress(macAddress: String): DeviceProfileEntity?
    
    /**
     * Obtener dispositivo por ANT+ Device Number
     */
    @Query("""
        SELECT * FROM device_profiles 
        WHERE antDeviceNumber = :deviceNumber AND protocolType = 'ANT_PLUS'
        LIMIT 1
    """)
    suspend fun getDeviceByAntDeviceNumber(deviceNumber: Int): DeviceProfileEntity?
    
    /**
     * Obtener favoritos de un tipo específico
     */
    @Query("""
        SELECT * FROM device_profiles 
        WHERE sensorType = :sensorType AND isFavorite = 1
        ORDER BY lastConnected DESC
    """)
    suspend fun getFavoriteDevices(sensorType: String): List<DeviceProfileEntity>
    
    /**
     * Obtener dispositivos conocidos por tipo, ordenados por prioridad
     * (Para autoconexión - incluye favoritos y no favoritos)
     */
    @Query("""
        SELECT * FROM device_profiles 
        WHERE sensorType = :sensorType
        ORDER BY isFavorite DESC, lastConnected DESC
    """)
    suspend fun getKnownDevicesByType(sensorType: String): List<DeviceProfileEntity>
    
    /**
     * Insertar o actualizar dispositivo (UPSERT)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDevice(device: DeviceProfileEntity)
    
    /**
     * Actualizar última conexión y contador
     */
    @Query("""
        UPDATE device_profiles 
        SET lastConnected = :timestamp, 
            connectionCount = connectionCount + 1
        WHERE deviceId = :deviceId
    """)
    suspend fun updateLastConnected(deviceId: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Marcar/desmarcar como favorito
     */
    @Query("""
        UPDATE device_profiles 
        SET isFavorite = :isFavorite
        WHERE deviceId = :deviceId
    """)
    suspend fun setFavorite(deviceId: String, isFavorite: Boolean)
    
    /**
     * Actualizar alias del dispositivo
     */
    @Query("""
        UPDATE device_profiles 
        SET alias = :alias
        WHERE deviceId = :deviceId
    """)
    suspend fun updateAlias(deviceId: String, alias: String?)
    
    /**
     * Eliminar dispositivo
     */
    @Query("DELETE FROM device_profiles WHERE deviceId = :deviceId")
    suspend fun deleteDevice(deviceId: String)
    
    /**
     * Eliminar todos los dispositivos
     */
    @Query("DELETE FROM device_profiles")
    suspend fun deleteAllDevices()
}
