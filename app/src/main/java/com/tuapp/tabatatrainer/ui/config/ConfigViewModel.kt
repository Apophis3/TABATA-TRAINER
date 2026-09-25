package com.tuapp.tabatatrainer.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.tabatatrainer.sensor.CadenceReading
import com.tuapp.tabatatrainer.sensor.GpsManager
import com.tuapp.tabatatrainer.sensor.HeartRateReading
import com.tuapp.tabatatrainer.sensor.SensorManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// Modelo de estado para la UI
data class BleScanStatus(
    val hrScanning: Boolean = false,
    val hrConnected: Boolean = false,
    val hrDeviceName: String? = null,
    val hrValue: Int = 0,
    val hrError: String? = null,

    val cadenceScanning: Boolean = false,
    val cadenceConnected: Boolean = false,
    val cadenceDeviceName: String? = null,
    val cadenceValue: Int = 0,
    val cadenceError: String? = null,
    
    val gpsEnabled: Boolean = false,
    val gpsAvailable: Boolean = false
)

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val sensorManager: SensorManager,
    private val gpsManager: GpsManager
) : ViewModel() {

    private val _bleStatus = MutableStateFlow(BleScanStatus())
    val bleStatus = _bleStatus.asStateFlow()

    private var hrScanJob: Job? = null
    private var cadenceScanJob: Job? = null

    init {
        refreshGpsStatus()
        // NO iniciar scanning automáticamente - se iniciará cuando se observe por primera vez
        // startScanning() se llama desde ConfigScreen con LaunchedEffect
    }
    
    private fun refreshGpsStatus() {
        val available = gpsManager.hasLocationPermission() && gpsManager.isGpsEnabled()
        _bleStatus.update { it.copy(gpsAvailable = available) }
    }

    fun startScanning() {
        // Los sensores se inician automáticamente cuando se observan por primera vez
        // Solo observamos el estado compartido
        // --- 1. RITMO CARDÍACO (ANT+ y BLE) ---
        hrScanJob?.cancel()
        hrScanJob = viewModelScope.launch {
            try {
                _bleStatus.update { it.copy(hrScanning = true, hrError = null) }
                
                // Forzar inicio del flow compartido suscribiéndonos
                sensorManager.observeHeartRate().collect { reading ->
                        try {
                            when (reading) {
                                is HeartRateReading.Scanning -> {
                                    _bleStatus.update { it.copy(hrScanning = true, hrError = "Buscando...") }
                                }
                                is HeartRateReading.Connecting -> {
                                    _bleStatus.update { it.copy(hrScanning = true, hrError = "Conectando...") }
                                }
                                is HeartRateReading.Connected -> {
                                    _bleStatus.update {
                                        it.copy(
                                            hrScanning = false,
                                            hrConnected = true,
                                            hrDeviceName = "${reading.deviceName} (${reading.protocol})",
                                            hrError = null
                                        )
                                    }
                                }
                                is HeartRateReading.Value -> {
                                    _bleStatus.update { it.copy(hrConnected = true, hrScanning = false, hrValue = reading.bpm) }
                                }
                                is HeartRateReading.Disconnected -> {
                                    _bleStatus.update { it.copy(hrConnected = false, hrScanning = false) }
                                }
                                is HeartRateReading.Error -> {
                                    _bleStatus.update { it.copy(hrScanning = false, hrError = reading.message) }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error procesando lectura de HR en ConfigViewModel")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error crítico en collector de HR")
                _bleStatus.update { 
                    it.copy(
                        hrScanning = false, 
                        hrConnected = false,
                        hrError = "Error crítico: ${e.message ?: "Desconocido"}"
                    ) 
                }
            }
        }

        // --- 2. CADENCIA (ANT+ y BLE) ---
        cadenceScanJob?.cancel()
        cadenceScanJob = viewModelScope.launch {
            try {
                delay(1000) // Delay reducido de 1500ms a 1000ms para acelerar detección
                _bleStatus.update { it.copy(cadenceScanning = true, cadenceError = null) }
                
                // Forzar inicio del flow compartido suscribiéndonos
                sensorManager.observeCadence().collect { reading ->
                        try {
                            when (reading) {
                                is CadenceReading.Scanning -> {
                                    _bleStatus.update { it.copy(cadenceScanning = true, cadenceError = "Buscando...") }
                                }
                                is CadenceReading.Connecting -> {
                                    _bleStatus.update { it.copy(cadenceScanning = true, cadenceError = "Conectando...") }
                                }
                                is CadenceReading.Connected -> {
                                    _bleStatus.update {
                                        it.copy(
                                            cadenceScanning = false,
                                            cadenceConnected = true,
                                            cadenceDeviceName = "${reading.deviceName} (${reading.protocol})",
                                            cadenceError = null
                                        )
                                    }
                                }
                                is CadenceReading.Value -> {
                                    _bleStatus.update { it.copy(cadenceConnected = true, cadenceScanning = false, cadenceValue = reading.rpm) }
                                }
                                is CadenceReading.Disconnected -> {
                                    _bleStatus.update { it.copy(cadenceConnected = false, cadenceScanning = false) }
                                }
                                is CadenceReading.Error -> {
                                    _bleStatus.update { it.copy(cadenceScanning = false, cadenceError = reading.message) }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error procesando lectura de Cadence en ConfigViewModel")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error crítico en collector de Cadence")
                _bleStatus.update { 
                    it.copy(
                        cadenceScanning = false, 
                        cadenceConnected = false,
                        cadenceError = "Error crítico: ${e.message ?: "Desconocido"}"
                    ) 
                }
            }
        }
        
        refreshGpsStatus()
    }

    fun stopScanning() {
        // NO cancelar los flows - se mantienen activos en SensorManager
        // Solo cancelamos nuestros observers
        hrScanJob?.cancel()
        cadenceScanJob?.cancel()
        hrScanJob = null
        cadenceScanJob = null
    }
    
    fun refreshSensors() {
        // Método específico para refresh - reinicia los flows
        sensorManager.resetFlows()
        // Reiniciar observadores
        startScanning()
    }

    fun setGpsEnabled(enabled: Boolean) {
        _bleStatus.update { it.copy(gpsEnabled = enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
    }
}