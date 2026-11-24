package com.tuapp.tabatatrainer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.tuapp.tabatatrainer.rawlog.RawLogSettings
import com.tuapp.tabatatrainer.ui.theme.TabataColors
import com.tuapp.tabatatrainer.ui.theme.TabataSizes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Lee y guarda el interruptor del registro en bruto (spec 008, R1). Solo preferencias: no toca servicios. */
@HiltViewModel
class RawLogToggleViewModel @Inject constructor(private val settings: RawLogSettings) : ViewModel() {
    val enabled: StateFlow<Boolean> = settings.enabled
    fun setEnabled(value: Boolean) = settings.setEnabled(value)
}

/**
 * Interruptor «Registro bruto» (spec 008, R1). El mismo ajuste en Ruta Libre (versión [compact], solo antes de
 * Iniciar) y en Config del Tabata (tarjeta). Se aplica al empezar la siguiente Ruta Libre o Tabata con GPS.
 */
@Composable
fun RawLogToggle(modifier: Modifier = Modifier, compact: Boolean = false) {
    val vm = hiltViewModel<RawLogToggleViewModel>()
    val enabled by vm.enabled.collectAsState()
    if (compact) RawLogPill(enabled, vm::setEnabled, modifier) else RawLogCard(enabled, vm::setEnabled, modifier)
}

@Composable
private fun RawLogPill(enabled: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier) {
    Surface(
        color = Color.Black.copy(alpha = 0.3f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.clickable { onToggle(!enabled) }
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.Storage,
                contentDescription = null,
                tint = if (enabled) TabataColors.Connected else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
            Text(
                "Registro bruto",
                fontSize = 13.sp,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.6f)
            )
            RawLogSwitch(enabled, onToggle, Modifier.scale(0.7f))
        }
    }
}

@Composable
private fun RawLogCard(enabled: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TabataColors.CardBackground),
        shape = RoundedCornerShape(TabataSizes.CardCornerRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = if (enabled) TabataColors.Connected else TabataColors.Disconnected,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        "Registro bruto (pruebas)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Text(
                        if (enabled) "Activo: Ruta Libre y Tabata con GPS" else "Desactivado",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) TabataColors.Connected else Color.Gray
                    )
                }
            }
            RawLogSwitch(enabled, onToggle, Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RawLogSwitch(enabled: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier) {
    Switch(
        checked = enabled,
        onCheckedChange = onToggle,
        colors = SwitchDefaults.colors(
            checkedThumbColor = TabataColors.Connected,
            checkedTrackColor = TabataColors.Connected.copy(alpha = 0.5f)
        ),
        modifier = modifier
    )
}
