package com.tuapp.tabatatrainer.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.tabatatrainer.sensor.SensorBattery
import com.tuapp.tabatatrainer.sensor.SensorManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Etiquetas de los huecos, en el mismo orden que [SensorBatteryViewModel.batteries] */
val BATTERY_SLOT_LABELS = listOf("HR1", "HR2", "C1", "C2")

/** Batería de HR1, HR2, C1, C2. Mismo ViewModel en toda la pantalla (se comparte por destino de navegación) */
@Composable
fun rememberSensorBatteries(): List<SensorBattery?> {
    val batteries by hiltViewModel<SensorBatteryViewModel>().batteries.collectAsState()
    return batteries
}

/** Muestra "Batería baja en HRx" una sola vez por sensor y sesión (pantallas de entrenamiento) */
@Composable
fun BatteryLowWarnings() {
    val vm = hiltViewModel<SensorBatteryViewModel>()
    val context = LocalContext.current
    LaunchedEffect(vm) {
        vm.lowWarnings.collect { Toast.makeText(context, "🔋 $it", Toast.LENGTH_LONG).show() }
    }
}

/**
 * Batería de los 4 huecos de sensor (spec 005, T-05). Solo lee flows: no toca servicios ni conexiones.
 * El aviso de batería baja sale una sola vez por sensor mientras viva el ViewModel (= la sesión en pantalla).
 */
@HiltViewModel
class SensorBatteryViewModel @Inject constructor(sensorManager: SensorManager) : ViewModel() {

    private val warned = mutableSetOf<Int>()
    private val _lowWarnings = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val lowWarnings: SharedFlow<String> = _lowWarnings.asSharedFlow()

    val batteries: StateFlow<List<SensorBattery?>> = combine(
        sensorManager.hrBattery(0), sensorManager.hrBattery(1),
        sensorManager.cadenceBattery(0), sensorManager.cadenceBattery(1)
    ) { h1, h2, c1, c2 -> listOf(h1, h2, c1, c2) }
        .onEach { list ->
            list.forEachIndexed { i, b ->
                if (b?.isLow == true && warned.add(i)) _lowWarnings.tryEmit("Batería baja en ${BATTERY_SLOT_LABELS[i]}")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(4) { null })
}
