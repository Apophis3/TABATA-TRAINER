package com.tuapp.tabatatrainer.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.tabatatrainer.sensor.SensorDeviceRegistry
import com.tuapp.tabatatrainer.sensor.SensorKind
import com.tuapp.tabatatrainer.sensor.SensorManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Lo que muestra la hoja para un tipo de sensor */
data class DevicesKindState(
    val cards: List<DeviceCard> = emptyList(),
    val guided: GuidedStep = GuidedStep.WaitingAnt
)

data class DevicesState(
    val hr: DevicesKindState = DevicesKindState(),
    val cadence: DevicesKindState = DevicesKindState()
) {
    fun of(kind: SensorKind) = if (kind == SensorKind.HR) hr else cadence
}

/**
 * Hoja "Dispositivos" (spec 007, T-06): aparatos guardados + señales vistas y los cambios manuales (unir, separar,
 * renombrar, hueco fijo, olvidar). Los cambios van al registro y al asignador, y se aplican en vivo en el siguiente
 * segundo, también con Tabata o Ruta en marcha (no toca servicios). Reconectar no está aquí: cada pantalla pasa su
 * propio callback (el mismo que su flecha).
 */
@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val sensorManager: SensorManager,
    private val registry: SensorDeviceRegistry
) : ViewModel() {

    val state: StateFlow<DevicesState> = combine(
        registry.devices, sensorManager.hrSignals(), sensorManager.cadenceSignals()
    ) { saved, hr, cadence ->
        DevicesState(
            hr = DevicesKindState(buildDeviceCards(SensorKind.HR, saved, hr), guidedPairing(SensorKind.HR, saved, hr)),
            cadence = DevicesKindState(
                buildDeviceCards(SensorKind.CADENCE, saved, cadence), guidedPairing(SensorKind.CADENCE, saved, cadence)
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesState())

    // Con la hoja abierta se observan HR1 y C1 (y se descartan los datos) para que los sensores sigan en marcha aunque
    // la pantalla no los esté usando: así se ven las señales para emparejar
    private var keepAlive: Job? = null

    fun onSheetOpened(screen: String) {
        Timber.d("📋 Dispositivos: hoja abierta ($screen)")
        keepAlive?.cancel()
        keepAlive = viewModelScope.launch {
            launch { sensorManager.observeHeartRate().collect { } }
            launch { sensorManager.observeCadence().collect { } }
        }
    }

    fun onSheetClosed(screen: String) {
        keepAlive?.cancel()
        keepAlive = null
        Timber.d("📋 Dispositivos: hoja cerrada ($screen)")
    }

    /** Ficha del aparato; si aún no está guardado, se guarda primero (con su señal ANT+ si la tiene) */
    private fun ensureSaved(card: DeviceCard, alias: String = card.title): String =
        card.device?.key ?: registry.remember(card.kind, card.primaryId, alias)

    fun setSlot(card: DeviceCard, slot: Int?) {
        val key = ensureSaved(card)
        registry.setSlot(card.kind, key, slot)
    }

    fun rename(card: DeviceCard, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        if (card.device == null) ensureSaved(card, clean) else registry.rename(card.kind, card.device.key, clean)
    }

    /** "Unir con…": la señal de [card] y la de [other] (una ANT+ y otra BLE) pasan a ser un aparato, a mano */
    fun link(card: DeviceCard, other: DeviceCard) {
        val ids = listOfNotNull(card.loneId, other.loneId)
        val ant = ids.firstOrNull { it.startsWith("ANT:") } ?: return
        val ble = ids.firstOrNull { it.startsWith("BLE:") } ?: return
        val bleCard = if (card.loneId == ble) card else other
        val alias = card.device?.alias ?: other.device?.alias ?: bleCard.title
        Timber.d("📋 Dispositivos: unir $ant + $ble")
        registry.link(card.kind, ant, ble, alias, manual = true)
    }

    /** "Guardar como un aparato": el agrupado solo de sesión pasa a vínculo manual guardado */
    fun saveAsOne(card: DeviceCard) {
        val (ant, ble) = card.sessionLinks.singleOrNull() ?: return
        Timber.d("📋 Dispositivos: guardar $ant + $ble como un aparato")
        registry.link(card.kind, ant, ble, card.title, manual = true)
    }

    /** "Separar": dos aparatos de una señal; la pareja queda bloqueada y deja de agruparse también en la sesión */
    fun split(card: DeviceCard) {
        val d = card.device?.takeIf { it.isPaired } ?: return
        registry.split(card.kind, d.key)
        sensorManager.unlinkSession(card.kind, d.antId!!, d.bleId!!)
    }

    /** "No son el mismo aparato": deshace el agrupado solo de sesión y bloquea la pareja */
    fun unlinkSession(card: DeviceCard) {
        card.sessionLinks.forEach { (ant, ble) -> sensorManager.unlinkSession(card.kind, ant, ble) }
    }

    fun forget(card: DeviceCard) {
        val d = card.device ?: return
        registry.forget(card.kind, d.key)
    }

    /** Modo guiado: une la ANT+ y la BLE vistas, con el nombre elegido y, si se pide, un hueco fijo */
    fun guidedLink(kind: SensorKind, step: GuidedStep.Ready, name: String, slot: Int?) {
        val ant = step.ant.id
        val ble = step.ble.id
        Timber.d("📋 Dispositivos: modo guiado, unir $ant + $ble")
        registry.link(kind, ant, ble, name, manual = true)
        name.trim().takeIf { it.isNotEmpty() }?.let { registry.rename(kind, ant, it) }
        if (slot != null) registry.setSlot(kind, ant, slot)
    }
}
