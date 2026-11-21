package com.tuapp.tabatatrainer.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
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

/** Etiqueta pequeña con la señal del sensor: ANT+ en verde, BLE en azul. Sin señal no pinta nada */
@Composable
fun ProtocolBadge(protocol: String?, fontSize: TextUnit = 9.sp) {
    if (protocol == null) return
    val c = if (protocol.startsWith("ANT")) Color(0xFF66BB6A) else Color(0xFF42A5F5)
    Text(
        text = protocol,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = c,
        maxLines = 1,
        modifier = Modifier
            .background(c.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

/**
 * Hueco + señal + batería (R6): "HR1 [ANT+] 🔋". [stacked] pone la batería debajo (ahorra ancho en Tabata).
 * Sin señal solo se ve el hueco; la batería solo sale si el hueco tiene fuente (ver `DualSlotAllocator`).
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
    val labelRow: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = fontSize, color = labelColor, maxLines = 1)
            ProtocolBadge(status?.protocol)
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
