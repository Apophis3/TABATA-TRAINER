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

    // Estado de cada hueco: aparato, señal (ANT+/BLE), valor (1 Hz) y batería (spec 007, T-04).
    // Solo lectura: observarlo NO arranca los sensores (eso lo hacen observeHeartRate* / observeCadence*)
    fun hrSlotStatus(slot: Int): StateFlow<SlotStatus> = heartRateHub.slotStatus(slot)
    fun cadenceSlotStatus(slot: Int): StateFlow<SlotStatus> = cadenceHub.slotStatus(slot)

    // Hoja "Dispositivos" (spec 007, T-06): señales vistas con su aparato y su hueco (1 Hz). Solo lectura: no arranca
    // los sensores. Y "no son el mismo aparato" para un agrupado solo de sesión (los aparatos guardados se cambian en
    // SensorDeviceRegistry)
    fun hrSignals(): StateFlow<List<VisibleSignal>> = heartRateHub.signals
    fun cadenceSignals(): StateFlow<List<VisibleSignal>> = cadenceHub.signals
    fun unlinkSession(kind: SensorKind, antId: String, bleId: String) = when (kind) {
        SensorKind.HR -> heartRateHub.unlinkSession(antId, bleId)
        SensorKind.CADENCE -> cadenceHub.unlinkSession(antId, bleId)
    }

    // "No es mío" (spec 007, R10): señales ignoradas en esta sesión de la app (id -> etiqueta); sueltan su hueco y
    // no se conectan ni se graban hasta "Volver a usar" o cerrar la app
    fun hrIgnored(): StateFlow<Map<String, String>> = heartRateHub.ignored
    fun cadenceIgnored(): StateFlow<Map<String, String>> = cadenceHub.ignored
    fun ignoreSignals(kind: SensorKind, ids: Map<String, String>) = when (kind) {
        SensorKind.HR -> heartRateHub.ignore(ids)
        SensorKind.CADENCE -> cadenceHub.ignore(ids)
    }
    fun unignoreSignal(kind: SensorKind, id: String) = when (kind) {
        SensorKind.HR -> heartRateHub.unignore(id)
        SensorKind.CADENCE -> cadenceHub.unignore(id)
    }

    // Aparato nuevo (spec 007, R11): no se usa hasta que el usuario lo aprueba; "Sí, usar" lo guarda en el registro
    fun hrToAsk(): StateFlow<List<NewDeviceAsk>> = heartRateHub.toAsk
    fun cadenceToAsk(): StateFlow<List<NewDeviceAsk>> = cadenceHub.toAsk
    fun approveSignal(kind: SensorKind, id: String, name: String) = when (kind) {
        SensorKind.HR -> heartRateHub.approve(id, name)
        SensorKind.CADENCE -> cadenceHub.approve(id, name)
    }

    /** Reconexión de pulso y cadencia (botón "reconectar"): reinicia las radios; conserva huecos e ignoradas */
    fun resetFlows() {
        Timber.d("🔄 SensorManager: reconexión de pulso y cadencia")
        heartRateHub.restart()
        cadenceHub.restart()
    }
}
