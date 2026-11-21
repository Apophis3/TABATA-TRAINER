package com.tuapp.tabatatrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.tabatatrainer.sensor.SensorManager
import com.tuapp.tabatatrainer.sensor.SlotStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Etiquetas de los huecos, en el mismo orden que [rememberSensorSlots] */
val SENSOR_SLOT_LABELS = listOf("HR1", "HR2", "C1", "C2")

/** Estado de un hueco para pintar su punto: con señal, buscando (hueco libre) o aparato del hueco sin señal */
enum class SlotSignalState { SIGNAL, SEARCHING, LOST }

val SlotStatus.signalState: SlotSignalState
    get() = when {
        connected -> SlotSignalState.SIGNAL
        deviceKey == null -> SlotSignalState.SEARCHING
        else -> SlotSignalState.LOST
    }

/** Estado de HR1, HR2, C1, C2 (spec 007, T-04). Mismo ViewModel en toda la pantalla (por destino de navegación) */
@Composable
fun rememberSensorSlots(): List<SlotStatus> {
    val slots by hiltViewModel<SensorSlotsViewModel>().slots.collectAsState()
    return slots
}

/**
 * Los 4 [SlotStatus] (HR1, HR2, C1, C2). Solo lee: no arranca sensores ni toca servicios (los arrancan
 * quienes observan `observeHeartRate*` / `observeCadence*`).
 */
@HiltViewModel
class SensorSlotsViewModel @Inject constructor(sensorManager: SensorManager) : ViewModel() {
    val slots: StateFlow<List<SlotStatus>> = combine(
        sensorManager.hrSlotStatus(0), sensorManager.hrSlotStatus(1),
        sensorManager.cadenceSlotStatus(0), sensorManager.cadenceSlotStatus(1)
    ) { h1, h2, c1, c2 -> listOf(h1, h2, c1, c2) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(4) { SlotStatus(it % 2) })
}

/** Color de la señal: ANT+ verde, BLE azul; sin señal, null */
fun protocolColor(protocol: String?): Color? = when {
    protocol == null -> null
    protocol.startsWith("ANT") -> Color(0xFF66BB6A)
    else -> Color(0xFF42A5F5)
}

/**
 * Hueco + señal + batería (R6, R6.1): el nombre del hueco va dentro de un recuadro verde (ANT+) o azul (BLE);
 * sin señal, sin recuadro (borde transparente con el mismo hueco, para que nada salte al conectar).
 * [stacked] pone la batería debajo (ahorra ancho en Tabata). La batería solo sale si el hueco tiene fuente
 * (ver `DualSlotAllocator`).
 */
@Composable
fun SensorSlotBadge(
    label: String,
    status: SlotStatus?,
    modifier: Modifier = Modifier,
    labelColor: Color = Color.White.copy(alpha = 0.6f),
    fontSize: TextUnit = 11.sp,
    stacked: Boolean = false
) {
    val protocol = status?.protocol
    val signal = protocolColor(protocol)
    val shape = RoundedCornerShape(4.dp)
    val description = "$label, ${protocol ?: "sin señal"}"
    val labelRow: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                fontSize = fontSize,
                color = labelColor,
                maxLines = 1,
                modifier = Modifier
                    .semantics { contentDescription = description }
                    .background(signal?.copy(alpha = 0.15f) ?: Color.Transparent, shape)
                    .border(1.5.dp, signal ?: Color.Transparent, shape)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
            if (!stacked) BatteryBadge(status?.battery)
        }
    }
    if (stacked) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            labelRow()
            BatteryBadge(status?.battery, modifier = Modifier.padding(top = 2.dp))
        }
    } else {
        Row(modifier = modifier) { labelRow() }
    }
}
