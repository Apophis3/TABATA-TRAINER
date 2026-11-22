package com.tuapp.tabatatrainer.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.tabatatrainer.sensor.NewDeviceAsk
import com.tuapp.tabatatrainer.sensor.SensorKind
import com.tuapp.tabatatrainer.sensor.SensorManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

/** Texto de la señal del aparato nuevo: "ANT+ 12345" o "BLE XOSS X2" */
private fun askSignal(a: NewDeviceAsk): String =
    if (a.isAnt) "ANT+ ${a.id.removePrefix("ANT:")}" else "BLE ${a.name.ifBlank { a.id.removePrefix("BLE:") }}"

/** Etiqueta para "Ignorados" (como la de la hoja: "nombre · señal") */
private fun askLabel(a: NewDeviceAsk): String = askSignal(a).let { s -> if (a.name == s || a.name.isBlank()) s else "${a.name} · $s" }

/**
 * Aviso "¿Es tuyo?" de un aparato nuevo (spec 007, R11): un pulsómetro o cadenciómetro sin guardar no se usa hasta
 * que el usuario lo aprueba. Uno cada vez, pulso primero. Observarlo no arranca los sensores.
 */
@HiltViewModel
class NewDevicePromptViewModel @Inject constructor(
    private val sensorManager: SensorManager
) : ViewModel() {

    val ask: StateFlow<NewDeviceAsk?> = combine(sensorManager.hrToAsk(), sensorManager.cadenceToAsk()) { hr, cad ->
        hr.firstOrNull() ?: cad.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun approve(a: NewDeviceAsk) {
        Timber.d("📋 Aparato nuevo: sí, usar ${a.id} (${a.name})")
        sensorManager.approveSignal(a.kind, a.id, a.name.ifBlank { askSignal(a) })
    }

    fun reject(a: NewDeviceAsk) {
        Timber.d("📋 Aparato nuevo: no es mío ${a.id}")
        sensorManager.ignoreSignals(a.kind, mapOf(a.id to askLabel(a)))
    }
}

/** Franja del aviso; no ocupa nada si no hay aparato por el que preguntar */
@Composable
fun NewDevicePrompt(
    modifier: Modifier = Modifier,
    viewModel: NewDevicePromptViewModel = hiltViewModel()
) {
    val ask by viewModel.ask.collectAsState()
    val a = ask ?: return
    val what = if (a.kind == SensorKind.HR) "Pulsómetro nuevo" else "Cadenciómetro nuevo"
    val unit = if (a.kind == SensorKind.HR) "bpm" else "rpm"
    Surface(
        color = Color(0xFF2D2D2D),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("$what · ¿Es tuyo?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "${a.name.ifBlank { askSignal(a) }} (${a.protocol}) · ${a.value} $unit",
                color = Color(0xFFAAAAAA), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = { viewModel.reject(a) }) { Text("No es mío", color = Color(0xFFE53935)) }
                Button(
                    onClick = { viewModel.approve(a) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))
                ) { Text("Sí, usar", color = Color.White) }
            }
        }
    }
}
