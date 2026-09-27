package com.tuapp.tabatatrainer.sensor

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeCadencePcc
import timber.log.Timber
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag
import android.os.Build
import com.tuapp.tabatatrainer.util.PermissionHelper
import com.tuapp.tabatatrainer.util.AppConstants
import com.tuapp.tabatatrainer.data.local.DeviceProfileDao
import com.tuapp.tabatatrainer.data.local.DeviceProfileEntity
import com.tuapp.tabatatrainer.data.local.ProtocolType
import com.tuapp.tabatatrainer.data.local.SensorType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// Modelos de datos (Sin cambios)
// ============================================================================

sealed class HeartRateReading {
    object Disconnected : HeartRateReading()
    data class Connecting(val deviceName: String = "") : HeartRateReading()
    object Scanning : HeartRateReading()
    data class Connected(val deviceName: String, val protocol: String = "BLE") : HeartRateReading()
    data class Value(val bpm: Int, val timestamp: Long = System.currentTimeMillis()) : HeartRateReading()
    data class Error(val message: String) : HeartRateReading()
}

sealed class CadenceReading {
    object Disconnected : CadenceReading()
    data class Connecting(val deviceName: String = "") : CadenceReading()
    object Scanning : CadenceReading()
    data class Connected(val deviceName: String, val protocol: String = "BLE") : CadenceReading()
    data class Value(val rpm: Int, val timestamp: Long = System.currentTimeMillis()) : CadenceReading()
    data class Error(val message: String) : CadenceReading()
}

@Singleton
class SensorManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceProfileDao: DeviceProfileDao,
    private val heartRateHub: HeartRateHub
) {

    // --- BLE Setup ---
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val scanner = bluetoothAdapter?.bluetoothLeScanner

    // UUIDs BLE
    private val HR_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
    private val CSC_SERVICE_UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
    private val CCC_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // --- Batería de cadencia C1/C2 (spec 005, T-04). Solo informativa: nunca conecta ni desconecta nada ---
    private val cadenceBatteries = List(2) { MutableStateFlow<SensorBattery?>(null) }
    fun cadenceBattery(slot: Int): StateFlow<SensorBattery?> = cadenceBatteries[slot].asStateFlow()
    fun hrBattery(slot: Int): StateFlow<SensorBattery?> = heartRateHub.slotBattery(slot)

    private fun setCadenceBattery(slot: Int, battery: SensorBattery?) {
        cadenceBatteries[slot].value = battery
        if (battery != null) Timber.d("🔋 CAD${slot + 1}: ${battery.percent?.let { "$it %" } ?: battery.level}")
    }

    /** Tras activar las notificaciones CSC: leer la batería (una operación GATT cada vez) */
    @SuppressLint("MissingPermission")
    private fun readBleBatteryAfterCsc(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor) {
        if (!descriptor.characteristic.uuid.toString().lowercase().startsWith("00002a5b")) return
        val ch = gatt.getService(BATTERY_SERVICE_UUID)?.getCharacteristic(BATTERY_LEVEL_UUID) ?: return
        try { gatt.readCharacteristic(ch) } catch (e: SecurityException) { Timber.w("🔋 CAD: sin permiso") }
    }

    @SuppressLint("MissingPermission")
    private fun onBleBatteryRead(slot: Int, gatt: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray?, status: Int) {
        if (ch.uuid != BATTERY_LEVEL_UUID) return
        if (status != BluetoothGatt.GATT_SUCCESS) { Timber.w("🔋 CAD${slot + 1}: lectura fallida ($status)"); return }
        setCadenceBattery(slot, SensorBattery.parseBleBatteryLevel(value))
        if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            try {
                gatt.setCharacteristicNotification(ch, true)
                ch.getDescriptor(CCC_UUID)?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                }
            } catch (e: SecurityException) { Timber.w("🔋 CAD${slot + 1}: sin permiso") }
        }
    }

    // --- ANT+ Handles (Para cerrar conexión limpiamente) ---
    private var hrAntHandle: PccReleaseHandle<AntPlusHeartRatePcc>? = null
    private var hr2AntHandle: PccReleaseHandle<AntPlusHeartRatePcc>? = null // Segunda HR
    private var cadAntHandle: PccReleaseHandle<AntPlusBikeCadencePcc>? = null
    private var cad2AntHandle: PccReleaseHandle<AntPlusBikeCadencePcc>? = null // Segunda Cadencia
    
    // Para rastrear información del primer HR (para evitar duplicados en HR2)
    private var firstHrDeviceName: String? = null // Nombre ANT+ del primer HR
    private var firstHrMacAddress: String? = null // MAC address BLE del primer HR
    
    // Para rastrear información del primer Cadence (para evitar duplicados en Cadence2)
    private var firstCadenceDeviceName: String? = null // Nombre ANT+ del primer Cadence
    private var firstCadenceMacAddress: String? = null // MAC address BLE del primer Cadence
    
    // Validación mínima de cadencia (solo valores imposibles)
    companion object {
        // Validación de timing básica
        private const val MIN_VALID_DIFF_TIME = 50 // Tiempo mínimo entre lecturas válidas (muy permisivo)
        private const val MAX_VALID_DIFF_TIME = 4096 // Tiempo máximo (4 segundos) - detecta pérdida de datos
    }
    
    /**
     * Validación mínima: solo rechaza valores claramente erróneos
     * Sin filtrado estadístico, sin suavizado - máxima responsividad para HIIT
     */
    private fun validateCadence(rpm: Int): Int? {
        // Solo validar rango razonable (0-300 RPM)
        return if (rpm in 0..300) {
            rpm
        } else {
            Timber.w("⚠️ Cadencia fuera de rango: $rpm RPM, rechazando")
            null
        }
    }
    
    // Scope para mantener los flows compartidos activos
    // IMPORTANTE: Usar Dispatchers.Main porque ANT+ necesita el hilo principal para crear Handlers
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // ========================================================================
    // FUNCIONES AUXILIARES PARA PERSISTENCIA
    // ========================================================================
    
    /**
     * Guarda o actualiza un dispositivo en la base de datos cuando se conecta
     */
    private fun saveDeviceProfile(
        deviceId: String,
        sensorType: SensorType,
        protocolType: ProtocolType,
        deviceName: String?,
        macAddress: String? = null,
        antDeviceNumber: Int? = null
    ) {
        managerScope.launch(Dispatchers.IO) {
            try {
                val existing = deviceProfileDao.getDeviceById(deviceId)
                if (existing != null) {
                    // Actualizar último uso
                    deviceProfileDao.updateLastConnected(deviceId)
                    Timber.d("💾 Dispositivo conocido actualizado: $deviceId")
                } else {
                    // Crear nuevo perfil
                    val profile = DeviceProfileEntity(
                        deviceId = deviceId,
                        sensorType = sensorType.name,
                        protocolType = protocolType.name,
                        deviceName = deviceName ?: "Unknown Device",
                        macAddress = macAddress,
                        antDeviceNumber = antDeviceNumber,
                        alias = null, // El usuario puede añadir un alias después
                        lastConnected = System.currentTimeMillis(),
                        connectionCount = 1
                    )
                    deviceProfileDao.insertOrUpdateDevice(profile)
                    Timber.d("💾 Nuevo dispositivo guardado: $deviceId ($deviceName)")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error al guardar perfil de dispositivo")
            }
        }
    }
    
    /**
     * Obtiene dispositivos conocidos de un tipo específico, ordenados por prioridad
     */
    private suspend fun getKnownDevices(sensorType: SensorType): List<DeviceProfileEntity> {
        return try {
            deviceProfileDao.getFavoriteDevices(sensorType.name)
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener dispositivos conocidos")
            emptyList()
        }
    }
    
    // ========================================================================
    // FASE A: AUTOCONEXIÓN CON PRIORIZACIÓN
    // ========================================================================
    
    /**
     * Estado compartido para rastrear si un favorito se conectó (por tipo de sensor)
     */
    private val favoriteConnectedFlow = MutableStateFlow<Map<SensorType, Boolean>>(
        mapOf(SensorType.HEART_RATE to false, SensorType.CADENCE to false)
    )
    
    /**
     * Intenta conectar con un dispositivo ANT+ específico por deviceNumber
     * @return true si se conectó exitosamente, false en caso contrario
     */
    private suspend fun tryConnectAntDevice(
        deviceNumber: Int,
        sensorType: SensorType,
        timeout: Long = 3000
    ): Boolean {
        return withContext(Dispatchers.Main) {
            val connectionResult = MutableStateFlow<Boolean?>(null)
            
            try {
                val receiver = when (sensorType) {
                    SensorType.HEART_RATE -> {
                        AntPluginPcc.IPluginAccessResultReceiver<AntPlusHeartRatePcc> { result, resultCode, state ->
                            when (resultCode) {
                                RequestAccessResult.SUCCESS -> {
                                    connectionResult.value = true
                                    try {
                                        (state as? PccReleaseHandle<AntPlusHeartRatePcc>)?.close()
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error al cerrar handle temporal HR")
                                    }
                                }
                                else -> connectionResult.value = false
                            }
                        }
                    }
                    SensorType.CADENCE -> {
                        AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeCadencePcc> { result, resultCode, state ->
                            when (resultCode) {
                                RequestAccessResult.SUCCESS -> {
                                    connectionResult.value = true
                                    try {
                                        (state as? PccReleaseHandle<AntPlusBikeCadencePcc>)?.close()
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error al cerrar handle temporal CAD")
                                    }
                                }
                                else -> connectionResult.value = false
                            }
                        }
                    }
                }
                
                when (sensorType) {
                    SensorType.HEART_RATE -> {
                        try {
                            AntPlusHeartRatePcc.requestAccess(
                                context,
                                deviceNumber, // DeviceNumber específico (no 0)
                                0,
                                receiver as AntPluginPcc.IPluginAccessResultReceiver<AntPlusHeartRatePcc>,
                                null
                            )
                        } catch (e: IllegalArgumentException) {
                            Timber.e(e, "❌ ANT+ HR: Error de PendingIntent (Android 12+). Continuando con BLE...")
                            connectionResult.value = false
                            return@withContext false
                        } catch (e: Exception) {
                            Timber.e(e, "❌ ANT+ HR: Error inesperado. Continuando con BLE...")
                            connectionResult.value = false
                            return@withContext false
                        }
                    }
                    SensorType.CADENCE -> {
                        try {
                            AntPlusBikeCadencePcc.requestAccess(
                                context,
                                deviceNumber,
                                0,
                                false,
                                receiver as AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeCadencePcc>,
                                null
                            )
                        } catch (e: IllegalArgumentException) {
                            Timber.e(e, "❌ ANT+ CAD: Error de PendingIntent (Android 12+). Continuando con BLE...")
                            connectionResult.value = false
                            return@withContext false
                        } catch (e: Exception) {
                            Timber.e(e, "❌ ANT+ CAD: Error inesperado. Continuando con BLE...")
                            connectionResult.value = false
                            return@withContext false
                        }
                    }
                }
                
                // Esperar resultado con timeout
                var elapsed = 0L
                val checkInterval = 100L
                while (connectionResult.value == null && elapsed < timeout) {
                    delay(checkInterval)
                    elapsed += checkInterval
                }
                
                connectionResult.value ?: false
            } catch (e: Exception) {
                Timber.e(e, "Error al intentar conectar ANT+ deviceNumber: $deviceNumber (${sensorType.name})")
                false
            }
        }
    }
    
    /**
     * Intenta conectar con un dispositivo BLE específico por MAC address
     * @return true si se conectó exitosamente, false en caso contrario
     */
    @SuppressLint("MissingPermission")
    private suspend fun tryConnectBleDevice(
        macAddress: String,
        serviceUuid: UUID,
        timeout: Long = 3000
    ): Boolean {
        return withContext(Dispatchers.IO) {
            if (!PermissionHelper.hasBleScanPermission(context)) {
                return@withContext false
            }
            
            val connectionResult = MutableStateFlow<Boolean?>(null)
            
            try {
                val device = bluetoothAdapter?.getRemoteDevice(macAddress)
                if (device == null) {
                    Timber.w("🔍 BLE: Dispositivo no encontrado: $macAddress")
                    return@withContext false
                }
                
                val gatt = device.connectGatt(
                    context,
                    false,
                    object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(
                            gatt: BluetoothGatt,
                            status: Int,
                            newState: Int
                        ) {
                            if (newState == BluetoothProfile.STATE_CONNECTED) {
                                connectionResult.value = true
                                gatt.disconnect()
                                gatt.close()
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                if (connectionResult.value == null) {
                                    connectionResult.value = false
                                }
                            }
                        }
                    },
                    BluetoothDevice.TRANSPORT_LE
                )
                
                // Esperar resultado con timeout
                var elapsed = 0L
                val checkInterval = 100L
                while (connectionResult.value == null && elapsed < timeout) {
                    delay(checkInterval)
                    elapsed += checkInterval
                }
                
                gatt?.disconnect()
                gatt?.close()
                connectionResult.value ?: false
            } catch (e: Exception) {
                Timber.e(e, "Error al intentar conectar BLE MAC: $macAddress")
                false
            }
        }
    }
    
    /**
     * Fase A: Autoconexión con priorización
     * Intenta conectar primero con dispositivos conocidos en orden de prioridad
     */
    private suspend fun attemptAutoConnect(
        sensorType: SensorType,
        onDeviceFound: (DeviceProfileEntity) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Cargar dispositivos conocidos ordenados por prioridad
                val knownDevices = deviceProfileDao.getKnownDevicesByType(sensorType.name)
                
                if (knownDevices.isEmpty()) {
                    Timber.d("📋 ${sensorType.name}: No hay dispositivos conocidos, saltando autoconexión")
                    return@withContext false
                }
                
                Timber.d("📋 ${sensorType.name}: Intentando autoconexión con ${knownDevices.size} dispositivo(s) conocido(s)")
                
                // Resetear flag de favorito conectado para este tipo de sensor
                favoriteConnectedFlow.value = favoriteConnectedFlow.value.toMutableMap().apply {
                    put(sensorType, false)
                }
                
                // Intentar conectar con cada dispositivo en orden de prioridad
                for (device in knownDevices) {
                    val currentFavoriteState = favoriteConnectedFlow.value[sensorType] ?: false
                    if (currentFavoriteState) {
                        Timber.d("⭐ ${sensorType.name}: Favorito ya conectado, deteniendo autoconexión")
                        break
                    }
                    
                    Timber.d("🔍 ${sensorType.name}: Intentando conectar con dispositivo conocido: ${device.alias ?: device.deviceName}")
                    
                    val connected = when (device.protocolType) {
                        ProtocolType.ANT_PLUS.name -> {
                            if (device.antDeviceNumber != null) {
                                tryConnectAntDevice(
                                    deviceNumber = device.antDeviceNumber,
                                    sensorType = sensorType,
                                    timeout = 3000
                                )
                            } else {
                                false
                            }
                        }
                        ProtocolType.BLE.name -> {
                            if (device.macAddress != null) {
                                val serviceUuid = when (sensorType) {
                                    SensorType.HEART_RATE -> HR_SERVICE_UUID
                                    SensorType.CADENCE -> CSC_SERVICE_UUID
                                }
                                tryConnectBleDevice(
                                    macAddress = device.macAddress,
                                    serviceUuid = serviceUuid,
                                    timeout = 3000
                                )
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                    
                    if (connected) {
                        Timber.d("✅ ${sensorType.name}: Dispositivo conocido conectado: ${device.alias ?: device.deviceName}")
                        
                        // Si es favorito, marcar y conectar inmediatamente
                        if (device.isFavorite) {
                            favoriteConnectedFlow.value = favoriteConnectedFlow.value.toMutableMap().apply {
                                put(sensorType, true)
                            }
                            onDeviceFound(device)
                            return@withContext true
                        } else {
                            // Si no es favorito, esperar 2 segundos por si aparece un favorito
                            Timber.d("⏳ ${sensorType.name}: Dispositivo no-favorito conectado, esperando 2s por favorito...")
                            delay(2000)
                            
                            // Si no apareció un favorito, usar este dispositivo
                            val finalFavoriteState = favoriteConnectedFlow.value[sensorType] ?: false
                            if (!finalFavoriteState) {
                                onDeviceFound(device)
                                return@withContext true
                            } else {
                                Timber.d("⭐ ${sensorType.name}: Favorito apareció durante espera, usando favorito")
                                return@withContext true
                            }
                        }
                    } else {
                        Timber.d("❌ ${sensorType.name}: No se pudo conectar con ${device.alias ?: device.deviceName}")
                    }
                }
                
                false // No se conectó ningún dispositivo conocido
            } catch (e: Exception) {
                Timber.e(e, "Error en autoconexión ${sensorType.name}")
                false
            }
        }
    }
    
    // Flows compartidos usando shareIn - un único flow activo compartido entre todos
    // WhileSubscribed(0, Long.MAX_VALUE) mantiene el flow activo mientras haya al menos un suscriptor
    // El timeout de 0 significa que se activa inmediatamente cuando hay un suscriptor
    
    private val sharedCadenceFlow: Flow<CadenceReading> = createCadenceFlow().shareIn(
        scope = managerScope,
        started = SharingStarted.WhileSubscribed(0, Long.MAX_VALUE), // Activo inmediatamente cuando hay suscriptores
        replay = 1
    )
    
    
    // Flow compartido para segunda Cadencia - SOLO se activa cuando hay suscriptores (delay para no interferir con CAD1)
    private val sharedCadence2Flow: Flow<CadenceReading> = createCadenceFlow2().shareIn(
        scope = managerScope,
        started = SharingStarted.WhileSubscribed(7000, Long.MAX_VALUE), // Delay de 7 segundos para no interferir con HR1, HR2 y CAD1
        replay = 1
    )

    private fun createCadenceFlow(): Flow<CadenceReading> = callbackFlow {
        Timber.d("🚴 SensorManager: createCadenceFlow() iniciado")
        Timber.d("🚴 SensorManager: Context disponible: ${context != null}")
        Timber.d("🚴 SensorManager: Thread: ${Thread.currentThread().name}")
        
        Timber.d("🚴 SensorManager: Enviando Scanning...")
        try {
            trySend(CadenceReading.Scanning)
            Timber.d("🚴 SensorManager: Scanning enviado correctamente")
        } catch (e: Exception) {
            Timber.e(e, "❌ Error al enviar Scanning")
        }
        var isConnected = false

        // ========================================================================
        // FASE A: AUTOCONEXIÓN (Background)
        // ========================================================================
        Timber.d("🚴 Cadence: Iniciando Fase A - Autoconexión con priorización...")

        // Intentar autoconexión con dispositivos conocidos
        val autoConnected = attemptAutoConnect(SensorType.CADENCE) { device ->
            // Callback cuando se encuentra un dispositivo conocido
            Timber.d("🚴 Cadence: Dispositivo conocido encontrado en autoconexión: ${device.alias ?: device.deviceName}")
            // El dispositivo se conectará en el flujo normal ANT+/BLE
        }

        if (autoConnected) {
            Timber.d("🚴 Cadence: Autoconexión exitosa, continuando con flujo normal...")
        } else {
            Timber.d("🚴 Cadence: Autoconexión no exitosa, iniciando escaneo abierto...")
        }

        // IMPORTANTE: Esperar un delay antes de iniciar búsqueda ANT+ para evitar conflictos con HR
        // Esto permite que HR tenga tiempo de conectarse primero
        Timber.d("🚴 Cadence: Esperando ${AppConstants.CADENCE_ANT_PLUS_DELAY_MS}ms antes de iniciar búsqueda ANT+...")
        delay(AppConstants.CADENCE_ANT_PLUS_DELAY_MS)

        // ========================================================================
        // FLUJO NORMAL: ANT+ y BLE (con priorización mejorada)
        // ========================================================================

        // --- ANT+ Cadence ---
        Timber.d("🚴 ANT+ Cadence: Creando receiver...")
        val antReceiver = AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeCadencePcc> { result, resultCode, state ->
            try {
                Timber.d("🚴 ANT+ Cadence: Callback recibido - resultCode: $resultCode, isConnected: $isConnected")
                when (resultCode) {
                    RequestAccessResult.SUCCESS -> {
                        Timber.d("🚴 ANT+ Cadence: SUCCESS - Conectando... (isConnected: $isConnected)")
                        
                        // Verificar si este dispositivo es conocido y favorito
                        val deviceName = result.deviceName ?: "ANT+ Cadence"
                        val antDeviceNumber = try { result.antDeviceNumber } catch (e: Exception) { 0 }
                        val deviceId = if (antDeviceNumber > 0) "ANT+_CAD_$antDeviceNumber" else "ANT+_CAD_${deviceName.hashCode()}"
                        
                        val isKnownFavorite = runBlocking(Dispatchers.IO) {
                            val device = deviceProfileDao.getDeviceById(deviceId)
                            device?.isFavorite == true
                        }
                        
                        // Si es favorito, conectar inmediatamente (incluso si ya hay otra conexión)
                        if (isKnownFavorite && !isConnected) {
                            favoriteConnectedFlow.value = favoriteConnectedFlow.value.toMutableMap().apply {
                                put(SensorType.CADENCE, true)
                            }
                        }
                        
                        if (!isConnected || isKnownFavorite) {
                            if (isConnected && !isKnownFavorite) {
                                Timber.d("🚴 ANT+ Cadence: Ya conectado, pero este es favorito - priorizando")
                            }
                            
                            Timber.d("🚴 ANT+ Cadence: Marcando como conectado...")
                            isConnected = true

                            // Guardar el handle para poder cerrarlo después (thread-safe)
                            synchronized(this@SensorManager) {
                                val newHandle = state as? PccReleaseHandle<AntPlusBikeCadencePcc>
                                if (newHandle != null) {
                                    // Si ya hay un handle diferente, cerrarlo primero
                                    if (cadAntHandle != null && cadAntHandle != newHandle) {
                                        Timber.d("🚴 ANT+ Cadence: Cerrando handle anterior (nuevo handle recibido)")
                                        try {
                                            cadAntHandle?.close()
                                        } catch (e: Exception) {
                                            Timber.e(e, "Error al cerrar handle Cadence anterior")
                                        }
                                    }
                                    cadAntHandle = newHandle
                                    Timber.d("🚴 ANT+ Cadence: Handle guardado desde callback SUCCESS")
                                }
                            }

                            // Enviar estado de conexión
                            val deviceName = result.deviceName ?: "ANT+ Cadence"
                            // Intentar obtener antDeviceNumber del resultado
                            val antDeviceNumber = try { result.antDeviceNumber } catch (e: Exception) { 0 }
                            val deviceId = if (antDeviceNumber > 0) "ANT+_CAD_$antDeviceNumber" else "ANT+_CAD_${deviceName.hashCode()}"
                            
                            // Guardar nombre del primer dispositivo de cadencia
                            synchronized(this@SensorManager) {
                                firstCadenceDeviceName = deviceName
                                firstCadenceMacAddress = null // ANT+ no tiene MAC
                            }
                            
                            // Guardar dispositivo en base de datos
                            saveDeviceProfile(
                                deviceId = deviceId,
                                sensorType = SensorType.CADENCE,
                                protocolType = ProtocolType.ANT_PLUS,
                                deviceName = deviceName,
                                antDeviceNumber = if (antDeviceNumber > 0) antDeviceNumber else null
                            )
                            
                            try {
                                trySend(CadenceReading.Connected(deviceName, "ANT+"))
                                // Enviar un valor 0 inicial para despertar la UI
                                trySend(CadenceReading.Value(0))
                            } catch (e: Exception) {
                                Timber.e(e, "Error al enviar eventos de conexión Cadence")
                            }

                            // Suscripción al evento CALCULADO (más fiable)
                            try {
                                result.subscribeCalculatedCadenceEvent { estTimestamp, eventFlags, calculatedCadence ->
                                    try {
                                        // calculatedCadence suele ser BigDecimal, lo pasamos a Int
                                        val rpm = calculatedCadence.toInt()
                                        Timber.d("ANT+ Cadence: $rpm rpm (timestamp: $estTimestamp, flags: $eventFlags)")
                                        // Validación mínima: solo rango
                                        val validRpm = validateCadence(rpm)
                                        if (validRpm != null) {
                                            Timber.d("🚴 CAD1 ANT+: $validRpm rpm")
                                            trySend(CadenceReading.Value(validRpm))
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error en callback de datos Cadence")
                                    }
                                }
                                result.subscribeBatteryStatusEvent { _, _, voltage, status ->
                                    setCadenceBattery(0, SensorBattery.fromAntStatus(status?.name, voltage?.toFloat()))
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error al suscribirse a eventos Cadence")
                            }
                        } else {
                            // Si ya conectamos por BLE, cerramos este para ahorrar batería
                            try {
                                (state as? PccReleaseHandle<AntPlusBikeCadencePcc>)?.close()
                            } catch (e: Exception) {
                                Timber.e(e, "Error al cerrar handle Cadence")
                            }
                        }
                    }
                    RequestAccessResult.CHANNEL_NOT_AVAILABLE -> {
                        Timber.w("ANT+ Cadence: Canal no disponible")
                    }
                    RequestAccessResult.ADAPTER_NOT_DETECTED -> {
                        Timber.w("ANT+ Cadence: Adaptador ANT no detectado")
                    }
                    RequestAccessResult.DEPENDENCY_NOT_INSTALLED -> {
                        Timber.e("ANT+ Cadence: Servicio ANT+ Plugins no instalado")
                        try {
                            trySend(CadenceReading.Error("Servicio ANT+ no instalado. Instala ANT+ Plugins desde Play Store"))
                        } catch (e: Exception) {
                            Timber.e(e, "Error al enviar error de dependencia")
                        }
                    }
                    RequestAccessResult.USER_CANCELLED -> {
                        Timber.d("ANT+ Cadence: Usuario canceló la búsqueda")
                    }
                    else -> {
                        Timber.e("ANT+ Cadence: Error desconocido: $resultCode")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error en antReceiver Cadence")
            }
        }

        // Receiver para cambios de estado del dispositivo (desconexiones, etc.)
        val deviceStateReceiver = AntPluginPcc.IDeviceStateChangeReceiver { newDeviceState ->
            try {
                Timber.d("ANT+ Cadence: Estado del dispositivo cambió a: $newDeviceState")
                when (newDeviceState) {
                    DeviceState.DEAD -> {
                        // Dispositivo desconectado o perdido
                        if (isConnected) {
                            isConnected = false
                            setCadenceBattery(0, null)
                            try {
                                trySend(CadenceReading.Disconnected)
                            } catch (e: Exception) {
                                Timber.e(e, "Error al enviar evento Disconnected")
                            }
                            // No poner handle a null aquí, se cerrará en awaitClose
                        }
                    }
                    DeviceState.TRACKING -> {
                        // Dispositivo conectado y transmitiendo datos
                        Timber.d("ANT+ Cadence: Dispositivo en estado TRACKING")
                    }
                    else -> {
                        // Otros estados (SEARCHING, INITIALIZING, etc.)
                        Timber.d("ANT+ Cadence: Estado: $newDeviceState")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error en deviceStateReceiver Cadence")
            }
        }

        // Iniciar búsqueda ANT+ (el delay ya se aplicó antes)
        try {
            // Verificar que el contexto sea válido
            if (context == null) {
                Timber.e("ANT+ Cadence: Context es null")
                trySend(CadenceReading.Error("Context no disponible"))
                return@callbackFlow
            }
            
            Timber.d("ANT+ Cadence: Solicitando acceso ANT+...")
            // Cerrar handle anterior si existe
            synchronized(this@SensorManager) {
                if (cadAntHandle != null) {
                    Timber.d("ANT+ Cadence: Cerrando handle anterior antes de solicitar nuevo acceso...")
                    try {
                        cadAntHandle?.close()
                    } catch (e: Exception) {
                        Timber.e(e, "Error al cerrar handle Cadence anterior")
                    }
                    cadAntHandle = null
                }
            }
            
            // Parámetros: context, deviceNumber (0 = buscar cualquier), closeOnLost (0 = mantener conexión), 
            // isSpeedAndCadenceCombined (false = solo cadencia)
            // requestAccess debe ejecutarse en el hilo principal porque ANT+ necesita crear Handlers
            val releaseHandle = withContext(Dispatchers.Main) {
                try {
                AntPlusBikeCadencePcc.requestAccess(context, 0, 0, false, antReceiver, deviceStateReceiver)
                } catch (e: IllegalArgumentException) {
                    Timber.e(e, "❌ ANT+ CAD: Error de PendingIntent (Android 12+). Continuando solo con BLE...")
                    null
                } catch (e: Exception) {
                    Timber.e(e, "❌ ANT+ CAD: Error inesperado al solicitar acceso. Continuando solo con BLE...")
                    null
                }
            }
            synchronized(this@SensorManager) {
                if (releaseHandle != null) {
                    cadAntHandle = releaseHandle
                    Timber.d("ANT+ Cadence: Handle recibido directamente de requestAccess")
                }
            }
            Timber.d("ANT+ Cadence: requestAccess llamado, esperando callback...")
        } catch (e: Exception) {
            Timber.e(e, "ANT+ Cadence: Error al solicitar acceso")
            try {
                trySend(CadenceReading.Error("Error ANT+: ${e.message}"))
            } catch (ex: Exception) {
                Timber.e(ex, "Error al enviar error de acceso")
            }
        }
        
        // Corrutina para reiniciar búsqueda ANT+ periódicamente si no hay conexión
        val antRetryJobCad = CoroutineScope(Dispatchers.Default).launch {
            delay(30000) // Esperar 30 segundos después del inicio antes del primer reintento
            
            // Loop continuo de búsqueda ANT+ (cada 30 segundos si no hay conexión)
            while (true) {
                if (!isConnected) {
                    Timber.d("🔄 ANT+ CAD: Reiniciando búsqueda ANT+ (sin conexión detectada)...")
                    try {
                        if (context != null) {
                            synchronized(this@SensorManager) {
                                if (cadAntHandle != null) {
                                    try {
                                        cadAntHandle?.close()
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error al cerrar handle CAD anterior")
                                    }
                                    cadAntHandle = null
                                }
                            }
                            
                            val releaseHandle = withContext(Dispatchers.Main) {
                                try {
                                    AntPlusBikeCadencePcc.requestAccess(context, 0, 0, false, antReceiver, deviceStateReceiver)
                                } catch (e: IllegalArgumentException) {
                                    Timber.e(e, "❌ ANT+ CAD: Error de PendingIntent en reintento")
                                    null
                                } catch (e: Exception) {
                                    Timber.e(e, "❌ ANT+ CAD: Error inesperado en reintento")
                                    null
                                }
                            }
                            
                            synchronized(this@SensorManager) {
                                if (releaseHandle != null) {
                                    cadAntHandle = releaseHandle
                                    Timber.d("ANT+ CAD: Handle de reintento recibido")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error al reiniciar búsqueda ANT+ CAD")
                    }
                }
                // Esperar 30 segundos antes del siguiente intento
                delay(30000)
            }
        }

// Reemplazar desde "// --- BLE (Aquí añadimos la matemática) ---" hasta el final del bleCallback

// --- BLE Cadence ---
        var lastCrankRevs = -1
        var lastCrankTime = -1

        val bleCallback = object : ScanCallback() {
    @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
        val favoriteConnected = favoriteConnectedFlow.value[SensorType.CADENCE] ?: false
        
        if (isConnected && !favoriteConnected) {
            // Si ya estamos conectados y no es un favorito, ignorar
            return
        }

        val device = result.device
        Timber.d("🔍 CAD: Dispositivo encontrado: ${device.name ?: "Sin nombre"}, RSSI: ${result.rssi}")

        // Verificación completa de UUID (igual que HR)
        val hasCscService = result.scanRecord?.serviceUuids?.contains(ParcelUuid(CSC_SERVICE_UUID)) == true ||
                result.scanRecord?.serviceUuids?.any { it.uuid == CSC_SERVICE_UUID } == true

        if (hasCscService) {
            // Verificar si es un dispositivo conocido y favorito
            val deviceId = "BLE_CAD_${device.address}"
            val isKnownFavorite = runBlocking(Dispatchers.IO) {
                val deviceProfile = deviceProfileDao.getDeviceById(deviceId)
                deviceProfile?.isFavorite == true
            }
            
            // Si es favorito, conectar incluso si ya hay otra conexión
            if (isKnownFavorite) {
                favoriteConnectedFlow.value = favoriteConnectedFlow.value.toMutableMap().apply {
                    put(SensorType.CADENCE, true)
                }
                if (isConnected) {
                    Timber.d("⭐ Cadence: Favorito detectado, reconectando...")
                }
            }
            
            // Si ya estamos conectados y no es favorito, ignorar
            if (isConnected && !isKnownFavorite) {
                return
            }
            
            Timber.d("✅ CAD: Dispositivo Cadencia encontrado: ${device.name ?: device.address}")
                    isConnected = true
            
            try {
                    scanner?.stopScan(this)
            } catch (e: Exception) {
                Timber.e(e, "Error al detener escaneo CAD")
            }
            
            trySend(CadenceReading.Connecting(device.name ?: "CAD"))

            val scanCallback = this

            try {
                @SuppressLint("MissingPermission")
                val gattConnection = device.connectGatt(context, false, object : BluetoothGattCallback() {
                    
                    @SuppressLint("MissingPermission")
                        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        Timber.d("🔌 CAD: Estado conexión: $newState, status: $status")
                            if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Timber.d("✅ CAD: Conectado, descubriendo servicios...")
                                gatt.discoverServices()
                                
                            val deviceId = "BLE_CAD_${device.address}"
                            
                            // Guardar MAC del primer dispositivo de cadencia
                            synchronized(this@SensorManager) {
                                firstCadenceMacAddress = device.address
                                if (firstCadenceDeviceName == null) {
                                    firstCadenceDeviceName = device.name ?: "BLE CAD"
                                }
                            }
                            
                            saveDeviceProfile(
                                deviceId = deviceId,
                                sensorType = SensorType.CADENCE,
                                protocolType = ProtocolType.BLE,
                                deviceName = device.name,
                                macAddress = device.address
                            )
                                
                            trySend(CadenceReading.Connected(device.name ?: "BLE", "BLE"))
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            Timber.w("⚠️ CAD: Desconectado")
                                isConnected = false
                                setCadenceBattery(0, null)
                                lastCrankRevs = -1
                                lastCrankTime = -1
                            trySend(CadenceReading.Disconnected)
                            
                            // Reiniciar escaneo
                            try {
                                val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(CSC_SERVICE_UUID)).build())
                                val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
                                scanner?.startScan(filters, settings, scanCallback)
                                Timber.d("🔄 CAD: Reiniciando escaneo...")
                            } catch (e: Exception) {
                                Timber.e(e, "Error al reiniciar escaneo CAD")
                            }
                            }
                        }

                        @SuppressLint("MissingPermission")
                        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        Timber.d("🔍 CAD: Servicios descubiertos, status: $status")
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            val service = gatt.getService(CSC_SERVICE_UUID)
                            // CSC Measurement UUID: 0x2A5B
                            val charac = service?.characteristics?.find { 
                                it.uuid.toString().lowercase().contains("2a5b") 
                            }
                            if (charac != null) {
                                Timber.d("✅ CAD: Característica CSC encontrada, activando notificaciones...")
                                gatt.setCharacteristicNotification(charac, true)
                                val desc = charac.descriptors.firstOrNull()
                                desc?.let {
                                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    gatt.writeDescriptor(it)
                                    Timber.d("✅ CAD: Notificaciones activadas")
                            }
                            } else {
                                Timber.w("⚠️ CAD: Característica CSC (2A5B) no encontrada")
                                // Listar características disponibles para debug
                                service?.characteristics?.forEach { c ->
                                    Timber.d("   Característica disponible: ${c.uuid}")
                                }
                            }
                        } else {
                            Timber.e("❌ CAD: Error al descubrir servicios: $status")
                        }
                    }

                    @SuppressLint("MissingPermission")
                    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) readBleBatteryAfterCsc(gatt, descriptor)
                    }

                    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                        onBleBatteryRead(0, gatt, characteristic, value, status)
                    }

                    @Deprecated("API < 33")
                    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onBleBatteryRead(0, gatt, characteristic, characteristic.value, status)
                    }

                    @SuppressLint("MissingPermission")
                        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                            val data = characteristic.value
                            if (characteristic.uuid == BATTERY_LEVEL_UUID) {
                                setCadenceBattery(0, SensorBattery.parseBleBatteryLevel(data)); return
                            }
                            if (data != null && data.isNotEmpty()) {
                            val flags = data[0].toInt() and 0xFF
                                var offset = 1

                            Timber.d("🚴 CAD BLE Raw: flags=$flags, dataLen=${data.size}")

                            // Wheel Revolution Data (si presente, saltar)
                            if ((flags and 0x01) != 0) {
                                offset += 6 // 4 bytes cumulative + 2 bytes last event time
                                Timber.d("   Wheel data presente, saltando 6 bytes")
                            }

                            // Crank Revolution Data
                                if ((flags and 0x02) != 0 && data.size >= offset + 4) {
                                    val currentCrankRevs = (data[offset].toInt() and 0xFF) + ((data[offset + 1].toInt() and 0xFF) shl 8)
                                    val currentCrankTime = (data[offset + 2].toInt() and 0xFF) + ((data[offset + 3].toInt() and 0xFF) shl 8)

                                Timber.d("   Crank: revs=$currentCrankRevs, time=$currentCrankTime")

                                    if (lastCrankRevs != -1 && lastCrankTime != -1) {
                                        var diffRevs = currentCrankRevs - lastCrankRevs
                                        if (diffRevs < 0) diffRevs += 65536

                                        var diffTime = currentCrankTime - lastCrankTime
                                        if (diffTime < 0) diffTime += 65536

                                    if (diffTime > 0 && diffRevs >= 0 && diffTime >= MIN_VALID_DIFF_TIME && diffTime <= MAX_VALID_DIFF_TIME) {
                                            val rpm = (diffRevs * 1024 * 60) / diffTime
                                        // Validación mínima: solo rango
                                        val validRpm = validateCadence(rpm)
                                        if (validRpm != null) {
                                            Timber.d("🚴 CAD1 BLE: $validRpm rpm")
                                            trySend(CadenceReading.Value(validRpm))
                                        }
                                        }
                                    }

                                    lastCrankRevs = currentCrankRevs
                                    lastCrankTime = currentCrankTime
                            } else {
                                Timber.d("   No hay datos de Crank (flag 0x02 no presente o datos insuficientes)")
                                }
                            }
                        }
                    })
            } catch (e: Exception) {
                Timber.e(e, "Error al conectar GATT CAD")
                trySend(CadenceReading.Error("Error conexión: ${e.message}"))
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onScanFailed(errorCode: Int) {
        Timber.e("❌ CAD: Error escaneo BLE: $errorCode")
        if (errorCode == ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED && !isConnected) {
            val scanCallbackRef = this
            CoroutineScope(Dispatchers.Default).launch {
                delay(2000)
                try {
                    val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(CSC_SERVICE_UUID)).build())
                    val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
                    scanner?.startScan(filters, settings, scanCallbackRef)
                    Timber.d("🔄 CAD: Reiniciando escaneo después de fallo...")
                } catch (e: Exception) {
                    Timber.e(e, "Error al reiniciar escaneo CAD")
                }
            }
        }
    }
}

// PRIORIDAD ANT+: Esperar 5 segundos antes de BLE (igual que HR)
Timber.d("🚴 CAD: Esperando 5 segundos para dar prioridad a ANT+ antes de iniciar BLE...")
delay(5000)

if (!isConnected) {
    Timber.d("🚴 CAD: ANT+ no conectó en 5 segundos, iniciando BLE...")
} else {
    Timber.d("🚴 CAD: ANT+ conectado, omitiendo BLE")
}

        val bleEnabled = bluetoothAdapter?.isEnabled == true
        val hasBlePermissions = PermissionHelper.hasBleScanPermission(context)
Timber.d("🚴 BLE CAD: Bluetooth habilitado: $bleEnabled, Permisos: $hasBlePermissions")
        
if (!isConnected && bleEnabled && hasBlePermissions) {
    Timber.d("🚴 BLE CAD: Iniciando escaneo BLE...")
            val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(CSC_SERVICE_UUID)).build())
    val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .build()
    
            @SuppressLint("MissingPermission")
            val startScan = {
        try {
            scanner?.stopScan(bleCallback)
        } catch (e: Exception) {
            Timber.d("No había escaneo previo CAD para detener")
        }
                scanner?.startScan(filters, settings, bleCallback)
        Timber.d("🔍 CAD: Escaneo BLE iniciado")
            }
            startScan()
} else if (!isConnected) {
    Timber.d("⚠️ CAD: BLE no disponible - Continuando solo con ANT+")
}

// Corrutina de reintento continuo - INICIA INMEDIATAMENTE para mantener escaneo activo
val retryJob = CoroutineScope(Dispatchers.Default).launch {
    // Esperar un poco antes del primer reintento para dar tiempo al escaneo inicial
    delay(10000) // 10 segundos después del inicio
    
    // Loop continuo de escaneo (cada 20 segundos si no hay conexión)
    while (true) {
        if (!isConnected && bluetoothAdapter?.isEnabled == true && PermissionHelper.hasBleScanPermission(context)) {
            Timber.d("🔄 CAD: Reiniciando escaneo periódico (sin conexión detectada)...")
            @SuppressLint("MissingPermission")
            suspend fun restartScan() {
                try {
                    scanner?.stopScan(bleCallback)
                } catch (e: Exception) {
                    // Ignorar si no había escaneo activo
                }
                delay(500)
                val retryFilters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(CSC_SERVICE_UUID)).build())
                val retrySettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .build()
                scanner?.startScan(retryFilters, retrySettings, bleCallback)
                Timber.d("🔍 CAD: Escaneo reiniciado")
            }
            try {
                restartScan()
            } catch (e: Exception) {
                Timber.e(e, "Error al reiniciar escaneo CAD")
            }
        }
        // Esperar 20 segundos antes del siguiente intento
        delay(20000)
    }
        }
        
        // IMPORTANTE: Mantener el escaneo inicial activo si no hay conexión
        if (!isConnected && bleEnabled && hasBlePermissions) {
            Timber.d("🔍 CAD: Escaneo inicial activo, esperando sensores...")
        }

        awaitClose {
        retryJob.cancel()
        antRetryJobCad.cancel()
        Timber.d("🚴 ANT+ CAD: Flow interno cerrado (handle se mantiene)")
        @SuppressLint("MissingPermission")
        fun stopScanning() {
            scanner?.stopScan(bleCallback)
        }
        try { stopScanning() } catch (e: Exception) {}
        }
}.catch { emit(CadenceReading.Error(it.message ?: "Unknown Error")) }
    
    // ========================================================================
    // SEGUNDA CADENCIA - Flow para detectar un segundo sensor de cadencia
    // ========================================================================
    private fun createCadenceFlow2(): Flow<CadenceReading> = callbackFlow {
        Timber.d("🚴2 SensorManager: createCadenceFlow2() iniciado")
        
        Timber.d("🚴2 SensorManager: Enviando Scanning...")
        trySend(CadenceReading.Scanning)
        var isConnected = false
        var connectedDeviceName: String? = null
        
        // Variables para cálculo de RPM en BLE (igual que CAD1)
        var lastCrankRevs2 = -1
        var lastCrankTime2 = -1

        // Receiver para segunda Cadencia
        val antReceiver2 = AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeCadencePcc> { result, resultCode, state ->
            try {
                Timber.d("🚴2 ANT+ CAD2: Callback recibido - resultCode: $resultCode")
                when (resultCode) {
                    RequestAccessResult.SUCCESS -> {
                        Timber.d("🚴2 ANT+ CAD2: SUCCESS - Conectando...")
                        val deviceName = result.deviceName ?: "ANT+ Cadence2"
                        
                        // Solo conectar si es un dispositivo diferente al primero
                        val antDeviceNumber = try { result.antDeviceNumber } catch (e: Exception) { 0 }
                        synchronized(this@SensorManager) {
                            val firstDeviceName = firstCadenceDeviceName
                            
                            val isDifferentDevice = firstDeviceName == null || firstDeviceName != deviceName
                            
                            Timber.d("🚴2 CAD2: Verificando dispositivo ANT+: $deviceName (deviceNumber: $antDeviceNumber) vs CAD1: $firstDeviceName")
                            Timber.d("🚴2 CAD2: isDifferentDevice: $isDifferentDevice, isConnected: $isConnected")
                            
                            if (!isConnected && isDifferentDevice) {
                                Timber.d("🚴2 CAD2: ✅ Dispositivo diferente detectado, conectando CAD2...")
                                isConnected = true
                                connectedDeviceName = deviceName
                                
                                val newHandle = state as? PccReleaseHandle<AntPlusBikeCadencePcc>
                                if (newHandle != null) {
                                    if (cad2AntHandle != null && cad2AntHandle != newHandle) {
                                        Timber.d("🚴2 ANT+ CAD2: Cerrando handle anterior")
                                        try {
                                            cad2AntHandle?.close()
                                        } catch (e: Exception) {
                                            Timber.e(e, "Error al cerrar handle CAD2 anterior")
                                        }
                                    }
                                    cad2AntHandle = newHandle
                                    Timber.d("🚴2 ANT+ CAD2: Handle guardado")
                                }

                                try {
                                    trySend(CadenceReading.Connected(deviceName, "ANT+"))
                                    trySend(CadenceReading.Value(0))
                                } catch (e: Exception) {
                                    Timber.e(e, "Error al enviar eventos de conexión CAD2")
                                }

                                try {
                                    Timber.d("🚴2 ANT+ CAD2: Suscribiéndose a eventos de datos...")
                                    result.subscribeCalculatedCadenceEvent { estTimestamp, eventFlags, calculatedCadence ->
                                        try {
                                            val rpm = calculatedCadence.toInt()
                                            Timber.d("🚴2 ANT+ CAD2 Data recibido: $rpm rpm")
                                            // Validación mínima: solo rango
                                            val validRpm = validateCadence(rpm)
                                            if (validRpm != null) {
                                                Timber.d("🚴2 CAD2 ANT+: $validRpm rpm")
                                                trySend(CadenceReading.Value(validRpm))
                                            }
                                        } catch (e: Exception) {
                                            Timber.e(e, "Error en callback de datos CAD2")
                                        }
                                    }
                                    result.subscribeBatteryStatusEvent { _, _, voltage, status ->
                                        setCadenceBattery(1, SensorBattery.fromAntStatus(status?.name, voltage?.toFloat()))
                                    }
                                    Timber.d("🚴2 ANT+ CAD2: Suscripción a eventos completada")
                                } catch (e: Exception) {
                                    Timber.e(e, "❌ Error crítico al suscribirse a eventos CAD2: ${e.message}")
                                    try {
                                        trySend(CadenceReading.Error("Error al suscribirse a datos: ${e.message}"))
                                    } catch (ex: Exception) {
                                        Timber.e(ex, "Error al enviar error de suscripción")
                                    }
                                }
                            } else {
                                when {
                                    !isDifferentDevice -> {
                                        Timber.w("🚴2 ANT+ CAD2: Mismo dispositivo que CAD1 detectado ($deviceName), cerrando handle CAD2...")
                                        try {
                                            (state as? PccReleaseHandle<AntPlusBikeCadencePcc>)?.close()
                                        } catch (e: Exception) {
                                            Timber.e(e, "Error al cerrar handle CAD2 duplicado")
                                        }
                                    }
                                    isConnected -> {
                                        // Dispositivo diferente pero ya conectado
                                        Timber.d("🚴2 ANT+ CAD2: Dispositivo diferente pero ya conectado (ignorando)")
                                    }
                                    else -> {
                                        // Dispositivo diferente pero no se puede conectar
                                        Timber.d("🚴2 ANT+ CAD2: Dispositivo diferente pero no se puede conectar")
                                    }
                                }
                            }
                        }
                    }
                    RequestAccessResult.SEARCH_TIMEOUT -> {
                        Timber.w("🚴2 ANT+ CAD2: Timeout de búsqueda")
                        isConnected = false
                        try {
                            trySend(CadenceReading.Scanning)
                        } catch (e: Exception) {
                            Timber.e(e, "Error al enviar Scanning después de timeout")
                        }
                    }
                    RequestAccessResult.CHANNEL_NOT_AVAILABLE -> {
                        Timber.w("🚴2 ANT+ CAD2: Canal no disponible")
                    }
                    RequestAccessResult.ADAPTER_NOT_DETECTED -> {
                        Timber.w("🚴2 ANT+ CAD2: Adaptador ANT no detectado")
                    }
                    RequestAccessResult.DEPENDENCY_NOT_INSTALLED -> {
                        Timber.e("🚴2 ANT+ CAD2: Servicio ANT+ Plugins no instalado")
                        try {
                            trySend(CadenceReading.Error("Servicio ANT+ no instalado"))
                        } catch (e: Exception) {
                            Timber.e(e, "Error al enviar error de dependencia CAD2")
                        }
                    }
                    RequestAccessResult.USER_CANCELLED -> {
                        Timber.d("🚴2 ANT+ CAD2: Usuario canceló la búsqueda")
                    }
                    else -> {
                        Timber.d("🚴2 ANT+ CAD2: Resultado: $resultCode")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error en antReceiver2 CAD2")
            }
        }

        val deviceStateReceiver2 = AntPluginPcc.IDeviceStateChangeReceiver { newDeviceState ->
            try {
                Timber.d("🚴2 ANT+ CAD2: Estado del dispositivo cambió a: $newDeviceState")
                when (newDeviceState) {
                    DeviceState.DEAD -> {
                        if (isConnected) {
                            isConnected = false
                            setCadenceBattery(1, null)
                            try {
                                trySend(CadenceReading.Disconnected)
                            } catch (e: Exception) {
                                Timber.e(e, "Error al enviar evento Disconnected CAD2")
                            }
                        }
                    }
                    DeviceState.TRACKING -> {
                        Timber.d("🚴2 ANT+ CAD2: Dispositivo en estado TRACKING")
                        if (!isConnected) {
                            isConnected = true
                            try {
                                trySend(CadenceReading.Connected(connectedDeviceName ?: "ANT+ Cadence2", "ANT+"))
                            } catch (e: Exception) {
                                Timber.e(e, "Error al enviar evento Connected CAD2 desde TRACKING")
                            }
                        }
                    }
                    else -> {
                        Timber.d("🚴2 ANT+ CAD2: Estado: $newDeviceState")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error en deviceStateReceiver2 CAD2")
            }
        }

        try {
            if (context == null) {
                Timber.e("ANT+ CAD2: Context es null")
                trySend(CadenceReading.Error("Context no disponible"))
                return@callbackFlow
            }
            
            Timber.d("ANT+ CAD2: Solicitando acceso ANT+...")
            synchronized(this@SensorManager) {
                if (cad2AntHandle != null) {
                    Timber.d("ANT+ CAD2: Cerrando handle anterior...")
                    try {
                        cad2AntHandle?.close()
                    } catch (e: Exception) {
                        Timber.e(e, "Error al cerrar handle CAD2 anterior")
                    }
                    cad2AntHandle = null
                }
            }
            
            // Esperar a que CAD1 se conecte
            Timber.d("🚴2 ANT+ CAD2: Esperando a que CAD1 se conecte antes de iniciar CAD2...")
            var cad1Connected = false
            var waitTime = 0
            val maxWaitTime = 10000 // 10 segundos máximo
            
            while (waitTime < maxWaitTime && !cad1Connected) {
                delay(500)
                waitTime += 500
                synchronized(this@SensorManager) {
                    val cad1HasName = firstCadenceDeviceName != null
                    val cad1HasHandle = cadAntHandle != null
                    
                    if (cad1HasName || cad1HasHandle) {
                        Timber.d("🚴2 ANT+ CAD2: CAD1 detectado (nombre: $firstCadenceDeviceName, handle: ${cad1HasHandle}), procediendo con CAD2...")
                        cad1Connected = true
                    }
                }
            }
            
            suspend fun startAntSearchCad2() {
                if (context == null) return
                
                Timber.d("🚴2 ANT+ CAD2: Iniciando búsqueda ANT+...")
                synchronized(this@SensorManager) {
                    if (cad2AntHandle != null && !isConnected) {
                        try {
                            cad2AntHandle?.close()
                        } catch (e: Exception) {
                            Timber.e(e, "Error al cerrar handle CAD2 anterior")
                        }
                        cad2AntHandle = null
                    }
                }
                
                delay(1000)
                
                val releaseHandle = withContext(Dispatchers.Main) {
                    try {
                        AntPlusBikeCadencePcc.requestAccess(context, 0, 0, false, antReceiver2, deviceStateReceiver2)
                    } catch (e: Exception) {
                        Timber.e(e, "❌ Error crítico en requestAccess CAD2")
                        null
                    }
                }
                
                synchronized(this@SensorManager) {
                    if (releaseHandle != null) {
                        cad2AntHandle = releaseHandle
                        Timber.d("🚴2 ANT+ CAD2: Handle recibido y guardado correctamente")
                    }
                }
            }
            
            if (cad1Connected) {
                startAntSearchCad2()
            }
        } catch (e: Exception) {
            Timber.e(e, "ANT+ CAD2: Error al solicitar acceso")
            trySend(CadenceReading.Error("ANT+ Error: ${e.message}"))
        }

        // BLE para CAD2
        val bleCallback2 = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (isConnected) return

                val device = result.device
                val hasCscService = result.scanRecord?.serviceUuids?.contains(ParcelUuid(CSC_SERVICE_UUID)) == true ||
                        result.scanRecord?.serviceUuids?.any { it.uuid == CSC_SERVICE_UUID } == true

                if (hasCscService) {
                    synchronized(this@SensorManager) {
                        val firstMac = firstCadenceMacAddress
                        val firstName = firstCadenceDeviceName
                        val deviceName = device.name ?: "BLE Device"
                        val deviceMac = device.address
                        
                        val isDifferentDevice = (firstMac == null || firstMac != deviceMac) && 
                                               (firstName == null || firstName != deviceName)
                        
                        if (isDifferentDevice) {
                            Timber.d("✅ CAD2: Dispositivo Cadencia diferente encontrado: ${device.name ?: device.address}")
                            isConnected = true
                            try {
                                scanner?.stopScan(this)
                            } catch (e: Exception) {
                                Timber.e(e, "Error al detener escaneo CAD2")
                            }
                            trySend(CadenceReading.Connecting(device.name ?: "BLE Device"))

                            val scanCallback = this

                            try {
                                @SuppressLint("MissingPermission")
                                val gattConnection = device.connectGatt(context, false, object : BluetoothGattCallback() {
                                    @SuppressLint("MissingPermission")
                                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                                            gatt.discoverServices()
                                            trySend(CadenceReading.Connected(device.name ?: "BLE", "BLE"))
                                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                            trySend(CadenceReading.Disconnected)
                                            isConnected = false
                                            setCadenceBattery(1, null)
                                            lastCrankRevs2 = -1
                                            lastCrankTime2 = -1
                                        }
                                    }

                                    @SuppressLint("MissingPermission")
                                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                                        if (status == BluetoothGatt.GATT_SUCCESS) {
                                            val service = gatt.getService(CSC_SERVICE_UUID)
                                            val characteristic = service?.characteristics?.find { 
                                                it.uuid.toString().startsWith("00002a5b") 
                                            }
                                            if (characteristic != null) {
                                                gatt.setCharacteristicNotification(characteristic, true)
                                                val descriptor = characteristic.descriptors.firstOrNull()
                                                descriptor?.let {
                                                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                                    gatt.writeDescriptor(it)
                                                }
                                            }
                                        }
                                    }

                                    @SuppressLint("MissingPermission")
                                    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                                        if (status == BluetoothGatt.GATT_SUCCESS) readBleBatteryAfterCsc(gatt, descriptor)
                                    }

                                    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                                        onBleBatteryRead(1, gatt, characteristic, value, status)
                                    }

                                    @Deprecated("API < 33")
                                    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onBleBatteryRead(1, gatt, characteristic, characteristic.value, status)
                                    }

                                    @SuppressLint("MissingPermission")
                                    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                                        try {
                                            val data = characteristic.value
                                            if (characteristic.uuid == BATTERY_LEVEL_UUID) {
                                                setCadenceBattery(1, SensorBattery.parseBleBatteryLevel(data)); return
                                            }
                                            if (data != null && data.isNotEmpty()) {
                                                val flags = data[0].toInt() and 0xFF
                                                var offset = 1
                                                
                                                Timber.d("🚴2 CAD2 BLE Raw: flags=$flags, dataLen=${data.size}")
                                                
                                                // Wheel Revolution Data (si presente, saltar)
                                                if ((flags and 0x01) != 0) {
                                                    offset += 6 // 4 bytes cumulative + 2 bytes last event time
                                                    Timber.d("🚴2 CAD2: Wheel data presente, saltando 6 bytes")
                                                }
                                                
                                                // Crank Revolution Data
                                                if ((flags and 0x02) != 0 && data.size >= offset + 4) {
                                                    val currentCrankRevs = (data[offset].toInt() and 0xFF) + ((data[offset + 1].toInt() and 0xFF) shl 8)
                                                    val currentCrankTime = (data[offset + 2].toInt() and 0xFF) + ((data[offset + 3].toInt() and 0xFF) shl 8)
                                                    
                                                    Timber.d("🚴2 CAD2: Crank: revs=$currentCrankRevs, time=$currentCrankTime")
                                                    
                                                    if (lastCrankRevs2 != -1 && lastCrankTime2 != -1) {
                                                        var diffRevs = currentCrankRevs - lastCrankRevs2
                                                        if (diffRevs < 0) diffRevs += 65536
                                                        
                                                        var diffTime = currentCrankTime - lastCrankTime2
                                                        if (diffTime < 0) diffTime += 65536
                                                        
                                                        if (diffTime > 0 && diffRevs >= 0 && diffTime >= MIN_VALID_DIFF_TIME && diffTime <= MAX_VALID_DIFF_TIME) {
                                                            val rpm = (diffRevs * 1024 * 60) / diffTime
                                                            // Validación mínima: solo rango
                                                            val validRpm = validateCadence(rpm)
                                                            if (validRpm != null) {
                                                                Timber.d("🚴2 CAD2 BLE: $validRpm rpm")
                                                                trySend(CadenceReading.Value(validRpm))
                                                            }
                                                        }
                                                    }
                                                    
                                                    lastCrankRevs2 = currentCrankRevs
                                                    lastCrankTime2 = currentCrankTime
                                                } else {
                                                    Timber.d("🚴2 CAD2: No hay datos de Crank (flag 0x02 no presente o datos insuficientes)")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Timber.e(e, "Error al leer CAD2")
                                        }
                                    }
                                })
                            } catch (e: Exception) {
                                Timber.e(e, "Error al conectar GATT CAD2")
                                trySend(CadenceReading.Error("Error de conexión: ${e.message}"))
                            }
                        }
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onScanFailed(errorCode: Int) {
                Timber.e("❌ CAD2: Error en escaneo BLE: $errorCode")
            }
        }

        // Corrutina para reiniciar búsqueda ANT+ CAD2 periódicamente
        val antRetryJobCad2 = CoroutineScope(Dispatchers.Default).launch {
            delay(30000)
            
            while (true) {
                if (!isConnected) {
                    Timber.d("🔄 ANT+ CAD2: Reiniciando búsqueda ANT+...")
                    try {
                        if (context != null) {
                            synchronized(this@SensorManager) {
                                if (cad2AntHandle != null) {
                                    try {
                                        cad2AntHandle?.close()
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error al cerrar handle CAD2 anterior")
                                    }
                                    cad2AntHandle = null
                                }
                            }
                            
                            delay(1000)
                            
                            val releaseHandle = withContext(Dispatchers.Main) {
                                try {
                                    AntPlusBikeCadencePcc.requestAccess(context, 0, 0, false, antReceiver2, deviceStateReceiver2)
                                } catch (e: Exception) {
                                    Timber.e(e, "❌ ANT+ CAD2: Error en reintento")
                                    null
                                }
                            }
                            
                            synchronized(this@SensorManager) {
                                if (releaseHandle != null) {
                                    cad2AntHandle = releaseHandle
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error al reiniciar búsqueda ANT+ CAD2")
                    }
                }
                delay(30000)
            }
        }
        
        awaitClose {
            antRetryJobCad2.cancel()
            Timber.d("🚴2 ANT+ CAD2: Flow interno cerrado")
            @SuppressLint("MissingPermission")
            fun stopScanning() {
                scanner?.stopScan(bleCallback2)
            }
            try {
                stopScanning()
            } catch (e: Exception) {
                // Ignorar error al detener escaneo
            }
        }
    }.catch { emit(CadenceReading.Error(it.message ?: "Unknown Error")) }
    
    // ========================================================================
    // MÉTODOS PÚBLICOS - Exponer flows compartidos
    // ========================================================================
    
    /**
     * Observa el flujo de frecuencia cardíaca (HR1)
     * Retorna un Flow que emite eventos de conexión y valores de HR
     */
    fun observeHeartRate(): Flow<HeartRateReading> {
        Timber.d("❤️ SensorManager: observeHeartRate() llamado - usando flow compartido")
        Timber.d("❤️ SensorManager: Context disponible: ${context != null}")
        // Todos los pulsómetros (ANT+ y BLE) los gestiona HeartRateHub; HR1 = hueco 0
        return heartRateHub.slotFlow(0)
    }
    
    /**
     * Observa el flujo de cadencia
     * Retorna un Flow que emite eventos de conexión y valores de cadencia
     */
    fun observeCadence(): Flow<CadenceReading> {
        Timber.d("🚴 SensorManager: observeCadence() llamado - usando flow compartido")
        Timber.d("🚴 SensorManager: Context disponible: ${context != null}")
        return sharedCadenceFlow.onStart { 
            Timber.d("🚴 SensorManager: Suscriptor conectado a sharedCadenceFlow - iniciando...")
        }
    }
    
    /**
     * Observa el flujo de segunda frecuencia cardíaca (HR2)
     * Retorna un Flow que emite eventos de conexión y valores de HR2
     */
    fun observeHeartRate2(): Flow<HeartRateReading> {
        Timber.d("❤️2 SensorManager: observeHeartRate2() llamado - usando flow compartido")
        Timber.d("❤️2 SensorManager: Context disponible: ${context != null}")
        return heartRateHub.slotFlow(1)
    }
    
    /**
     * Observa el flujo de segunda cadencia (CAD2)
     * Retorna un Flow que emite eventos de conexión y valores de CAD2
     */
    fun observeCadence2(): Flow<CadenceReading> {
        Timber.d("🚴2 SensorManager: observeCadence2() llamado - usando flow compartido")
        Timber.d("🚴2 SensorManager: Context disponible: ${context != null}")
        return sharedCadence2Flow
    }
    
    /**
     * Reinicia los flows compartidos (útil para forzar reconexión)
     */
    fun resetFlows() {
        Timber.d("🔄 SensorManager: Reiniciando flows compartidos...")
        heartRateHub.restart()
        // Los flows compartidos se reiniciarán automáticamente cuando se cancelen todos los suscriptores
        // y se vuelvan a suscribir
        synchronized(this@SensorManager) {
            // Cerrar handles ANT+ para forzar reconexión
            try {
                hrAntHandle?.close()
                hr2AntHandle?.close()
                cadAntHandle?.close()
                cad2AntHandle?.close()
            } catch (e: Exception) {
                Timber.e(e, "Error al cerrar handles ANT+")
            }
            hrAntHandle = null
            hr2AntHandle = null
            cadAntHandle = null
            cad2AntHandle = null
            Timber.d("🔄 SensorManager: Handles cerrados, los flows compartidos se reiniciarán automáticamente")
        }
    }
}