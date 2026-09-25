package com.tuapp.tabatatrainer.sensor

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// NOTA: Las definiciones de HeartRateReading y CadenceReading están en SensorManager.kt
// ============================================================================
// Manager de Frecuencia Cardíaca BLE - MEJORADO
// ============================================================================

@Singleton
class BleHeartRateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BleHRManager"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val SCAN_TIMEOUT_MS = 30000L
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val handler = Handler(Looper.getMainLooper())

    // Guardar último dispositivo para reconexión rápida
    private var lastConnectedDeviceAddress: String? = null
    private var lastConnectedDeviceName: String? = null

    // Estado actual de conexión
    @Volatile private var currentGatt: BluetoothGatt? = null
    @Volatile private var isScanning = false

    /**
     * Resetea el estado del manager y cierra conexiones
     * Llamar entre entrenamientos para limpiar el estado
     */
    @SuppressLint("MissingPermission")
    fun reset() {
        Log.d(TAG, "🔄 Reseteando HR Manager...")
        handler.removeCallbacksAndMessages(null)

        try {
            bluetoothAdapter?.bluetoothLeScanner?.let { scanner ->
                if (isScanning) {
                    isScanning = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parando escaneo: ${e.message}")
        }

        currentGatt?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error cerrando GATT: ${e.message}")
            }
        }
        currentGatt = null
        Log.d(TAG, "✅ HR Manager reseteado")
    }

    /**
     * Intenta reconectar al último dispositivo conocido
     */
    @SuppressLint("MissingPermission")
    fun tryReconnectToLastDevice(): Boolean {
        val address = lastConnectedDeviceAddress ?: return false
        val name = lastConnectedDeviceName ?: "HR Sensor"

        Log.d(TAG, "🔄 Intentando reconectar a $name ($address)")

        try {
            val device = bluetoothAdapter?.getRemoteDevice(address)
            if (device != null) {
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo dispositivo: ${e.message}")
        }
        return false
    }

    @SuppressLint("MissingPermission")
    fun heartRateFlow(): Flow<HeartRateReading> = callbackFlow {
        var gatt: BluetoothGatt? = null
        var currentScanCallback: ScanCallback? = null
        var isFlowActive = true
        var lastConnectedDevice: BluetoothDevice? = null
        val scanner = bluetoothAdapter?.bluetoothLeScanner

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            trySend(HeartRateReading.Error("Bluetooth no disponible"))
            close()
            return@callbackFlow
        }

        // ========== FUNCIONES AUXILIARES (ORDEN CORRECTO) ==========

        // 1. Función para configurar notificaciones HR
        fun setupHrNotifications(g: BluetoothGatt) {
            val hrService = g.getService(HR_SERVICE_UUID)
            val hrCharacteristic = hrService?.getCharacteristic(HR_MEASUREMENT_UUID)

            hrCharacteristic?.let { char ->
                g.setCharacteristicNotification(char, true)

                val descriptor = char.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                descriptor?.let { desc ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(desc)
                    }
                }
            }
        }

        // 2. Función para parsear HR
        fun parseHr(data: ByteArray): Int {
            if (data.isEmpty()) return 0
            val flags = data[0].toInt()
            return if (flags and 0x01 != 0) {
                if (data.size >= 3) {
                    (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
                } else 0
            } else {
                if (data.size >= 2) data[1].toInt() and 0xFF else 0
            }
        }

        // 3. Crear GattCallback (ANTES de scheduleReconnect)
        fun createGattCallback(
            onSetupNotifications: (BluetoothGatt) -> Unit,
            onParseHr: (ByteArray) -> Int,
            onSend: (HeartRateReading) -> Unit,
            onDeviceConnected: (BluetoothDevice?) -> Unit,
            onGattReady: (BluetoothGatt?) -> Unit,
            onReconnect: () -> Unit,
            isActive: () -> Boolean
        ): BluetoothGattCallback {
            return object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    Log.d(TAG, "Estado conexión HR: status=$status, newState=$newState")
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.d(TAG, "✅ Conectado a ${g.device.name}")
                            onDeviceConnected(g.device)
                            onGattReady(g)
                            onSend(HeartRateReading.Connected(g.device.name ?: "HR Sensor", "BLE"))
                            g.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Log.d(TAG, "❌ Desconectado HR (status=$status)")
                            onSend(HeartRateReading.Disconnected)
                            g.close()
                            onGattReady(null)

                            // Reconexión automática
                            if (isActive()) {
                                onReconnect()
                            }
                        }
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    Log.d(TAG, "Servicios descubiertos HR: status=$status")
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        onSetupNotifications(g)
                    }
                }

                override fun onCharacteristicChanged(
                    gattParam: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray
                ) {
                    if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                        val bpm = onParseHr(value)
                        onSend(HeartRateReading.Value(bpm))
                    }
                }

                @Deprecated("Deprecated in API 33")
                override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                            @Suppress("DEPRECATION")
                            val bpm = onParseHr(characteristic.value)
                            onSend(HeartRateReading.Value(bpm))
                        }
                    }
                }
            }
        }

        // 4. Función para iniciar escaneo (ANTES de scheduleReconnect)
        fun startScan(scannerParam: android.bluetooth.le.BluetoothLeScanner?, callback: ScanCallback?) {
            if (scannerParam == null || callback == null) return
            isScanning = true

            try {
                val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setReportDelay(0)
                    .build()

                scannerParam.startScan(null, scanSettings, callback)

                // Timeout del escaneo
                handler.postDelayed({
                    if (isFlowActive && gatt == null && isScanning) {
                        Log.d(TAG, "⏱️ Timeout de escaneo HR")
                        try { scannerParam.stopScan(callback) } catch (e: Exception) {}
                        isScanning = false
                        trySend(HeartRateReading.Error("No se encontró sensor HR"))
                        // Reintentar después de un tiempo
                        handler.postDelayed({
                            if (isFlowActive && gatt == null) {
                                trySend(HeartRateReading.Scanning)
                                startScan(scannerParam, callback)
                            }
                        }, 5000)
                    }
                }, SCAN_TIMEOUT_MS)

            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando escaneo: ${e.message}")
                isScanning = false
                trySend(HeartRateReading.Error("Error: ${e.message}"))
            }
        }

        // 5. Función para reconexión (DESPUÉS de createGattCallback y startScan)
        fun scheduleReconnect() {
            if (!isFlowActive) return

            handler.postDelayed({
                if (isFlowActive && lastConnectedDevice != null) {
                    Log.d(TAG, "🔄 Reconectando a ${lastConnectedDevice?.name}...")
                    trySend(HeartRateReading.Connecting(lastConnectedDevice?.name ?: ""))
                    gatt = lastConnectedDevice?.connectGatt(
                        context,
                        true,
                        createGattCallback(
                            { setupHrNotifications(it) },
                            { parseHr(it) },
                            { trySend(it) },
                            { lastConnectedDevice = it; lastConnectedDeviceAddress = it?.address; lastConnectedDeviceName = it?.name },
                            { gatt = it; currentGatt = it },
                            { scheduleReconnect() },
                            { isFlowActive }
                        ),
                        BluetoothDevice.TRANSPORT_LE
                    )
                    currentGatt = gatt
                } else if (isFlowActive) {
                    Log.d(TAG, "🔍 Reiniciando escaneo...")
                    trySend(HeartRateReading.Scanning)
                    startScan(scanner, currentScanCallback)
                }
            }, RECONNECT_DELAY_MS)
        }

        // ========== SCAN CALLBACK ==========

        currentScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val deviceName = result.device.name ?: ""
                val serviceUuids = result.scanRecord?.serviceUuids

                val isHrDevice = deviceName.contains("HR", ignoreCase = true) ||
                        deviceName.contains("Heart", ignoreCase = true) ||
                        deviceName.contains("Decathlon", ignoreCase = true) ||
                        deviceName.contains("Polar", ignoreCase = true) ||
                        deviceName.contains("Garmin", ignoreCase = true) ||
                        deviceName.contains("Wahoo", ignoreCase = true) ||
                        deviceName.contains("Coospo", ignoreCase = true) ||
                        deviceName.contains("Magene", ignoreCase = true) ||
                        serviceUuids?.any { it.uuid == HR_SERVICE_UUID } == true

                if (isHrDevice) {
                    Log.d(TAG, "📡 Dispositivo HR encontrado: $deviceName")
                    try { scanner?.stopScan(this) } catch (e: Exception) {}
                    isScanning = false
                    trySend(HeartRateReading.Connecting(deviceName))
                    lastConnectedDevice = result.device
                    lastConnectedDeviceAddress = result.device.address
                    lastConnectedDeviceName = deviceName

                    gatt = result.device.connectGatt(
                        context,
                        false,
                        createGattCallback(
                            { setupHrNotifications(it) },
                            { parseHr(it) },
                            { trySend(it) },
                            { lastConnectedDevice = it; lastConnectedDeviceAddress = it?.address; lastConnectedDeviceName = it?.name },
                            { gatt = it; currentGatt = it },
                            { scheduleReconnect() },
                            { isFlowActive }
                        ),
                        BluetoothDevice.TRANSPORT_LE
                    )
                    currentGatt = gatt
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Escaneo HR fallido: $errorCode")
                isScanning = false
                if (isFlowActive && errorCode != 1) {
                    trySend(HeartRateReading.Error("Error escaneo: $errorCode"))
                }
            }
        }

        // ========== INICIO ==========

        if (lastConnectedDeviceAddress != null) {
            Log.d(TAG, "🔄 Reconectando a dispositivo conocido: $lastConnectedDeviceName")
            trySend(HeartRateReading.Connecting(lastConnectedDeviceName ?: ""))

            try {
                val device = bluetoothAdapter.getRemoteDevice(lastConnectedDeviceAddress)
                lastConnectedDevice = device
                gatt = device.connectGatt(
                    context,
                    true,
                    createGattCallback(
                        { setupHrNotifications(it) },
                        { parseHr(it) },
                        { trySend(it) },
                        { lastConnectedDevice = it; lastConnectedDeviceAddress = it?.address; lastConnectedDeviceName = it?.name },
                        { gatt = it; currentGatt = it },
                        { scheduleReconnect() },
                        { isFlowActive }
                    ),
                    BluetoothDevice.TRANSPORT_LE
                )
                currentGatt = gatt

                handler.postDelayed({
                    if (isFlowActive && gatt == null) {
                        Log.d(TAG, "🔍 Reconexión falló, iniciando escaneo...")
                        trySend(HeartRateReading.Scanning)
                        startScan(scanner, currentScanCallback)
                    }
                }, 5000)

            } catch (e: Exception) {
                Log.e(TAG, "Error reconectando: ${e.message}")
                trySend(HeartRateReading.Scanning)
                startScan(scanner, currentScanCallback)
            }
        } else {
            Log.d(TAG, "🔍 Iniciando escaneo HR...")
            trySend(HeartRateReading.Scanning)
            startScan(scanner, currentScanCallback)
        }

        awaitClose {
            Log.d(TAG, "Cerrando flow HR")
            isFlowActive = false
            isScanning = false
            handler.removeCallbacksAndMessages(null)
            currentScanCallback?.let {
                try { scanner?.stopScan(it) } catch (e: Exception) { }
            }
        }
    }.catch { e ->
        Log.e(TAG, "Error en flow HR: ${e.message}")
        emit(HeartRateReading.Error(e.message ?: "Error desconocido"))
    }
}

// ============================================================================
// Manager de Cadencia BLE - MEJORADO
// ============================================================================

@Singleton
class BleCadenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BleCadenceManager"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val SCAN_TIMEOUT_MS = 30000L
        val CSC_SERVICE_UUID: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
        val CSC_MEASUREMENT_UUID: UUID = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val handler = Handler(Looper.getMainLooper())

    private var lastConnectedDeviceAddress: String? = null
    private var lastConnectedDeviceName: String? = null

    @Volatile private var currentGatt: BluetoothGatt? = null
    @Volatile private var isScanning = false

    private var lastCrankRevolutions: Int? = null
    private var lastCrankEventTime: Int? = null

    @SuppressLint("MissingPermission")
    fun reset() {
        Log.d(TAG, "🔄 Reseteando Cadence Manager...")
        handler.removeCallbacksAndMessages(null)

        try {
            if (isScanning) isScanning = false
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
        }

        currentGatt?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error cerrando GATT: ${e.message}")
            }
        }
        currentGatt = null
        lastCrankRevolutions = null
        lastCrankEventTime = null
        Log.d(TAG, "✅ Cadence Manager reseteado")
    }

    @SuppressLint("MissingPermission")
    fun cadenceFlow(): Flow<CadenceReading> = callbackFlow {
        var gatt: BluetoothGatt? = null
        var currentScanCallback: ScanCallback? = null
        var isFlowActive = true
        var lastConnectedDevice: BluetoothDevice? = null
        val scanner = bluetoothAdapter?.bluetoothLeScanner

        lastCrankRevolutions = null
        lastCrankEventTime = null

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            trySend(CadenceReading.Error("Bluetooth no disponible"))
            close()
            return@callbackFlow
        }

        // ========== FUNCIONES AUXILIARES (ORDEN CORRECTO) ==========

        // 1. Configurar notificaciones
        fun setupCscNotifications(g: BluetoothGatt) {
            val cscService = g.getService(CSC_SERVICE_UUID)
            val cscCharacteristic = cscService?.getCharacteristic(CSC_MEASUREMENT_UUID)

            cscCharacteristic?.let { char ->
                g.setCharacteristicNotification(char, true)
                val descriptor = char.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                descriptor?.let { desc ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(desc)
                    }
                }
            }
        }

        // 2. Parsear cadencia
        fun parseCadence(data: ByteArray): Float? {
            if (data.isEmpty()) return null
            val flags = data[0].toInt()
            if (flags and 0x02 == 0) return null

            var offset = 1
            if (flags and 0x01 != 0) offset += 6
            if (data.size < offset + 4) return null

            val crankRevolutions = (data[offset].toInt() and 0xFF) or
                    ((data[offset + 1].toInt() and 0xFF) shl 8)
            val crankEventTime = (data[offset + 2].toInt() and 0xFF) or
                    ((data[offset + 3].toInt() and 0xFF) shl 8)

            val rpm = if (lastCrankRevolutions != null && lastCrankEventTime != null) {
                var timeDiff = crankEventTime - lastCrankEventTime!!
                if (timeDiff < 0) timeDiff += 65536
                val revDiff = crankRevolutions - lastCrankRevolutions!!

                if (timeDiff > 0 && revDiff >= 0 && revDiff < 10) {
                    val timeSeconds = timeDiff / 1024.0f
                    if (timeSeconds > 0) (revDiff / timeSeconds) * 60 else null
                } else null
            } else null

            lastCrankRevolutions = crankRevolutions
            lastCrankEventTime = crankEventTime

            return rpm?.takeIf { it in 0f..250f }
        }

        // 3. Crear GattCallback (ANTES de scheduleReconnect)
        fun createGattCallback(
            onSetupNotifications: (BluetoothGatt) -> Unit,
            onParseCadence: (ByteArray) -> Float?,
            onSend: (CadenceReading) -> Unit,
            onDeviceConnected: (BluetoothDevice?) -> Unit,
            onGattReady: (BluetoothGatt?) -> Unit,
            onReconnect: () -> Unit,
            isActive: () -> Boolean
        ): BluetoothGattCallback {
            return object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.d(TAG, "✅ Cadencia conectada: ${g.device.name}")
                            onDeviceConnected(g.device)
                            onGattReady(g)
                            onSend(CadenceReading.Connected(g.device.name ?: "Cadence Sensor", "BLE"))
                            g.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Log.d(TAG, "❌ Cadencia desconectada")
                            onSend(CadenceReading.Disconnected)
                            g.close()
                            onGattReady(null)
                            if (isActive()) onReconnect()
                        }
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        onSetupNotifications(g)
                    }
                }

                override fun onCharacteristicChanged(
                    gattParam: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray
                ) {
                    if (characteristic.uuid == CSC_MEASUREMENT_UUID) {
                        onParseCadence(value)?.let { rpm ->
                            onSend(CadenceReading.Value(rpm.toInt()))
                        }
                    }
                }

                @Deprecated("Deprecated in API 33")
                override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        if (characteristic.uuid == CSC_MEASUREMENT_UUID) {
                            @Suppress("DEPRECATION")
                                parseCadence(characteristic.value)?.let { rpm ->
                                onSend(CadenceReading.Value(rpm.toInt()))
                            }
                        }
                    }
                }
            }
        }

        // 4. startScan (ANTES de scheduleReconnect)
        fun startScan(scannerParam: android.bluetooth.le.BluetoothLeScanner?, callback: ScanCallback?) {
            if (scannerParam == null || callback == null) return
            isScanning = true

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .build()

            try {
                scannerParam.startScan(null, scanSettings, callback)

                handler.postDelayed({
                    if (isFlowActive && gatt == null && isScanning) {
                        Log.d(TAG, "⏱️ Timeout escaneo cadencia")
                        try { scannerParam.stopScan(callback) } catch (e: Exception) {}
                        isScanning = false
                        handler.postDelayed({
                            if (isFlowActive && gatt == null) {
                                trySend(CadenceReading.Scanning)
                                startScan(scannerParam, callback)
                            }
                        }, 5000)
                    }
                }, SCAN_TIMEOUT_MS)
            } catch (e: Exception) {
                Log.e(TAG, "Error escaneo: ${e.message}")
                isScanning = false
            }
        }

        // 5. scheduleReconnect (DESPUÉS de createGattCallback y startScan)
        fun scheduleReconnect() {
            if (!isFlowActive) return

            handler.postDelayed({
                if (isFlowActive && lastConnectedDevice != null) {
                    Log.d(TAG, "🔄 Reconectando cadencia...")
                    trySend(CadenceReading.Connecting(lastConnectedDevice?.name ?: ""))
                    gatt = lastConnectedDevice?.connectGatt(context, true, createGattCallback(
                        { setupCscNotifications(it) },
                        { parseCadence(it) },
                        { trySend(it) },
                        { lastConnectedDevice = it },
                        { gatt = it; currentGatt = it },
                        { scheduleReconnect() },
                        { isFlowActive }
                    ), BluetoothDevice.TRANSPORT_LE)
                    currentGatt = gatt
                }
            }, RECONNECT_DELAY_MS)
        }

        // ========== SCAN CALLBACK ==========

        currentScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val deviceName = result.device.name ?: ""
                val serviceUuids = result.scanRecord?.serviceUuids

                val isCadenceDevice = deviceName.contains("CAD", ignoreCase = true) ||
                        deviceName.contains("Cadence", ignoreCase = true) ||
                        deviceName.contains("Speed", ignoreCase = true) ||
                        deviceName.contains("CSC", ignoreCase = true) ||
                        deviceName.contains("Wahoo", ignoreCase = true) ||
                        deviceName.contains("Garmin", ignoreCase = true) ||
                        deviceName.contains("Coospo", ignoreCase = true) ||
                        deviceName.contains("Magene", ignoreCase = true) ||
                        serviceUuids?.any { it.uuid == CSC_SERVICE_UUID } == true

                if (isCadenceDevice) {
                    Log.d(TAG, "📡 Cadencia encontrada: $deviceName")
                    try { scanner?.stopScan(this) } catch (e: Exception) {}
                    isScanning = false
                    trySend(CadenceReading.Connecting(deviceName))
                    lastConnectedDevice = result.device
                    lastConnectedDeviceAddress = result.device.address
                    lastConnectedDeviceName = deviceName

                    gatt = result.device.connectGatt(context, false, createGattCallback(
                        { setupCscNotifications(it) },
                        { parseCadence(it) },
                        { trySend(it) },
                        { lastConnectedDevice = it },
                        { gatt = it; currentGatt = it },
                        { scheduleReconnect() },
                        { isFlowActive }
                    ), BluetoothDevice.TRANSPORT_LE)
                    currentGatt = gatt
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Escaneo cadencia fallido: $errorCode")
                isScanning = false
            }
        }

        // ========== INICIO ==========

        if (lastConnectedDeviceAddress != null) {
            Log.d(TAG, "🔄 Reconectando cadencia conocida: $lastConnectedDeviceName")
            trySend(CadenceReading.Connecting(lastConnectedDeviceName ?: ""))

            try {
                val device = bluetoothAdapter.getRemoteDevice(lastConnectedDeviceAddress)
                lastConnectedDevice = device
                gatt = device.connectGatt(context, true, createGattCallback(
                    { setupCscNotifications(it) },
                    { parseCadence(it) },
                    { trySend(it) },
                    { lastConnectedDevice = it },
                    { gatt = it; currentGatt = it },
                    { scheduleReconnect() },
                    { isFlowActive }
                ), BluetoothDevice.TRANSPORT_LE)
                currentGatt = gatt

                handler.postDelayed({
                    if (isFlowActive && gatt == null) {
                        trySend(CadenceReading.Scanning)
                        startScan(scanner, currentScanCallback)
                    }
                }, 5000)
            } catch (e: Exception) {
                Log.e(TAG, "Error reconectando: ${e.message}")
                trySend(CadenceReading.Scanning)
                startScan(scanner, currentScanCallback)
            }
        } else {
            Log.d(TAG, "🔍 Iniciando escaneo cadencia...")
            trySend(CadenceReading.Scanning)
            startScan(scanner, currentScanCallback)
        }

        awaitClose {
            Log.d(TAG, "Cerrando flow cadencia")
            isFlowActive = false
            isScanning = false
            handler.removeCallbacksAndMessages(null)
            currentScanCallback?.let {
                try { scanner?.stopScan(it) } catch (e: Exception) { }
            }
        }
    }.catch { e ->
        Log.e(TAG, "Error en flow cadencia: ${e.message}")
        emit(CadenceReading.Error(e.message ?: "Error desconocido"))
    }
}