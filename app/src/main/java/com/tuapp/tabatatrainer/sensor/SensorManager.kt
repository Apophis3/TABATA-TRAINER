package com.tuapp.tabatatrainer.sensor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
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

/**
 * Fachada de sensores para servicios y pantallas. Pulso y cadencia los gestionan sus hubs
 * ([HeartRateHub], [CadenceHub]): ANT+ y BLE, aparatos de doble señal y reparto de huecos (spec 007).
 * Hueco 0 = HR1 / C1, hueco 1 = HR2 / C2.
 */
@Singleton
class SensorManager @Inject constructor(
    private val heartRateHub: HeartRateHub,
    private val cadenceHub: CadenceHub
) {
    /** HR1 */
    fun observeHeartRate(): Flow<HeartRateReading> = heartRateHub.slotFlow(0)

    /** HR2 */
    fun observeHeartRate2(): Flow<HeartRateReading> = heartRateHub.slotFlow(1)

    /** C1 */
    fun observeCadence(): Flow<CadenceReading> = cadenceHub.slotFlow(0)

    /** C2 */
    fun observeCadence2(): Flow<CadenceReading> = cadenceHub.slotFlow(1)

    // Batería del aparato que alimenta cada hueco (spec 005). Solo informativa
    fun hrBattery(slot: Int): StateFlow<SensorBattery?> = heartRateHub.slotBattery(slot)
    fun cadenceBattery(slot: Int): StateFlow<SensorBattery?> = cadenceHub.slotBattery(slot)

    /** Fuerza una reconexión completa de pulso y cadencia (botón "reconectar") */
    fun resetFlows() {
        Timber.d("🔄 SensorManager: reconexión de pulso y cadencia")
        heartRateHub.restart()
        cadenceHub.restart()
    }
}
