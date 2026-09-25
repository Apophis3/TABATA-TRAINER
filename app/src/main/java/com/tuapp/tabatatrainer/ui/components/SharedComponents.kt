package com.tuapp.tabatatrainer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tuapp.tabatatrainer.ui.theme.TabataColors
import com.tuapp.tabatatrainer.ui.theme.TabataSizes

// ============================================================================
// 1. SENSORES COMPACTOS (1 línea)
// ============================================================================

/**
 * Fila compacta de sensores con iconos de estado.
 * Ocupa una sola línea horizontal.
 */
@Composable
fun CompactSensorRow(
    hrConnected: Boolean,
    hrScanning: Boolean,
    cadenceConnected: Boolean,
    cadenceScanning: Boolean,
    gpsEnabled: Boolean,
    gpsAvailable: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TabataColors.CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TabataSizes.PaddingMedium, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // HR
                SensorIcon(
                    icon = Icons.Default.Favorite,
                    isConnected = hrConnected,
                    isScanning = hrScanning,
                    label = "HR"
                )

                // Cadencia
                SensorIcon(
                    icon = Icons.Default.DirectionsBike,
                    isConnected = cadenceConnected,
                    isScanning = cadenceScanning,
                    label = "CAD"
                )

                // GPS
                SensorIcon(
                    icon = Icons.Default.LocationOn,
                    isConnected = gpsEnabled && gpsAvailable,
                    isScanning = false,
                    label = "GPS",
                    showAsToggle = true,
                    isEnabled = gpsAvailable
                )
            }

            // Botón refresh
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refrescar",
                    tint = TabataColors.VibrantOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SensorIcon(
    icon: ImageVector,
    isConnected: Boolean,
    isScanning: Boolean,
    label: String,
    showAsToggle: Boolean = false,
    isEnabled: Boolean = true
) {
    val color = when {
        !isEnabled -> TabataColors.Disconnected.copy(alpha = 0.5f)
        isConnected -> TabataColors.Connected
        isScanning -> TabataColors.Scanning
        else -> TabataColors.Disconnected
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(TabataSizes.IconMedium)
            )

            // Indicador de estado (punto)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(8.dp)
                    .background(color, CircleShape)
            )
        }

        // Spinner de scanning
        if (isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = TabataColors.Scanning
            )
        }
    }
}

// ============================================================================
// 2. PRESETS RÁPIDOS
// ============================================================================

data class WorkoutPreset(
    val name: String,
    val warmup: Int,
    val work: Int,
    val rest: Int,
    val rounds: Int
)

val DefaultPresets = listOf(
    WorkoutPreset("Tabata", 10, 20, 10, 8),
    WorkoutPreset("HIIT", 30, 40, 20, 8),
    WorkoutPreset("Custom", 0, 30, 15, 10)
)

@Composable
fun PresetButtonRow(
    selectedPreset: String?,
    onPresetSelected: (WorkoutPreset) -> Unit,
    presets: List<WorkoutPreset> = DefaultPresets,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { preset ->
            val isSelected = selectedPreset == preset.name

            FilterChip(
                selected = isSelected,
                onClick = { onPresetSelected(preset) },
                label = {
                    Text(
                        preset.name,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TabataColors.VibrantOrange,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ============================================================================
// 3. SLIDER COMPACTO (sin botones -5/-1/+1/+5)
// ============================================================================

/**
 * Slider compacto con botones de ajuste rápido a los lados del valor.
 * Formato: [-1/-5] [VALOR] [+1/+5]
 */
@Composable
fun CompactTimeSlider(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    icon: ImageVector,
    color: Color,
    unit: String = "seg",
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }

    // Funciones para ajustar el valor
    fun adjustValue(delta: Int) {
        val newValue = (value + delta).coerceIn(valueRange.first, valueRange.last)
        onValueChange(newValue)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TabataSizes.PaddingMedium, vertical = 12.dp)
        ) {
            // Fila superior: icono + título
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fila de controles: Botones -1/-5 | Valor | Botones +1/+5
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botones izquierda: -1 y -5
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Botón -5
                    IconButton(
                        onClick = { adjustValue(-5) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text(
                            text = "-5",
                            color = color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Botón -1
                    IconButton(
                        onClick = { adjustValue(-1) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text(
                            text = "-1",
                            color = color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Valor central (tocable para edición directa)
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showEditDialog = true },
                    color = color.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (unit.isNotEmpty()) "$value $unit" else "$value",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Botones derecha: +1 y +5
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Botón +1
                    IconButton(
                        onClick = { adjustValue(1) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text(
                            text = "+1",
                            color = color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Botón +5
                    IconButton(
                        onClick = { adjustValue(5) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text(
                            text = "+5",
                            color = color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Slider
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = color,
                    inactiveTrackColor = color.copy(alpha = 0.3f)
                ),
                modifier = Modifier.height(TabataSizes.SliderHeight)
            )
        }
    }

    // Diálogo de edición numérica
    if (showEditDialog) {
        NumberInputDialog(
            title = title,
            currentValue = value,
            valueRange = valueRange,
            unit = unit,
            onValueConfirmed = {
                onValueChange(it)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }
}

@Composable
private fun NumberInputDialog(
    title: String,
    currentValue: Int,
    valueRange: IntRange,
    unit: String,
    onValueConfirmed: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf(currentValue.toString()) }
    val focusManager = LocalFocusManager.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TabataColors.CardBackground)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        if (it.isEmpty() || it.all { c -> c.isDigit() }) {
                            textValue = it
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            val newValue = textValue.toIntOrNull() ?: currentValue
                            onValueConfirmed(newValue.coerceIn(valueRange))
                        }
                    ),
                    suffix = { if (unit.isNotEmpty()) Text(unit) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.width(150.dp)
                )

                Text(
                    text = "Rango: ${valueRange.first} - ${valueRange.last}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            val newValue = textValue.toIntOrNull() ?: currentValue
                            onValueConfirmed(newValue.coerceIn(valueRange))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TabataColors.VibrantOrange
                        )
                    ) {
                        Text("Aceptar")
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. TIMER HERO (Display gigante)
// ============================================================================

/**
 * Display de timer gigante para pantalla de entrenamiento.
 */
@Composable
fun TimerHeroDisplay(
    seconds: Int,
    phaseTitle: String,
    currentRound: Int,
    totalRounds: Int,
    modifier: Modifier = Modifier,
    timerSize: TextUnit = TabataSizes.TimerGiant,
    showRoundIndicators: Boolean = true
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Título de fase
        Text(
            text = phaseTitle,
            fontSize = TabataSizes.PhaseTitle,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Timer gigante
        Text(
            text = String.format("%02d", seconds),
            fontSize = timerSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Ronda
        Text(
            text = "Ronda",
            fontSize = TabataSizes.LabelMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
        Text(
            text = "$currentRound / $totalRounds",
            fontSize = TabataSizes.SensorValue,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Indicadores de ronda (puntos)
        if (showRoundIndicators && totalRounds <= 12) {
            Spacer(modifier = Modifier.height(12.dp))
            RoundIndicatorDots(
                currentRound = currentRound,
                totalRounds = totalRounds
            )
        }
    }
}

@Composable
private fun RoundIndicatorDots(
    currentRound: Int,
    totalRounds: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(totalRounds) { index ->
            val isCompleted = index < currentRound
            val isCurrent = index == currentRound - 1

            Box(
                modifier = Modifier
                    .size(if (isCurrent) 10.dp else 8.dp)
                    .background(
                        color = when {
                            isCompleted -> Color.White
                            isCurrent -> Color.White
                            else -> Color.White.copy(alpha = 0.3f)
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

// ============================================================================
// 5. PANEL DE DATOS DE SENSORES (durante entrenamiento)
// ============================================================================

/**
 * Panel de sensores con fondo semi-transparente.
 * Muestra HR, Cadencia, y estadísticas.
 */
@Composable
fun SensorDataPanel(
    heartRate: Int?,
    cadence: Int?,
    avgHeartRate: Int?,
    maxHeartRate: Int?,
    hrConnected: Boolean,
    cadenceConnected: Boolean,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (isCompact) {
            // Versión compacta: 1 fila
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SensorValueCompact(
                    icon = Icons.Default.Favorite,
                    value = heartRate?.toString() ?: "--",
                    unit = "bpm",
                    isConnected = hrConnected
                )
                SensorValueCompact(
                    icon = Icons.Default.DirectionsBike,
                    value = cadence?.toString() ?: "--",
                    unit = "rpm",
                    isConnected = cadenceConnected
                )
                if (avgHeartRate != null) {
                    SensorValueCompact(
                        icon = Icons.Default.FavoriteBorder,
                        value = avgHeartRate.toString(),
                        unit = "avg",
                        isConnected = hrConnected
                    )
                }
                if (maxHeartRate != null) {
                    SensorValueCompact(
                        icon = Icons.Default.TrendingUp,
                        value = maxHeartRate.toString(),
                        unit = "max",
                        isConnected = hrConnected
                    )
                }
            }
        } else {
            // Versión expandida: 2 filas
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(TabataSizes.PaddingMedium)
            ) {
                // Mensaje de espera si no hay HR
                if (!hrConnected || heartRate == null) {
                    Text(
                        text = "Esperando datos HR...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // Fila de valores principales
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SensorValueDisplay(
                        icon = Icons.Default.Favorite,
                        value = heartRate?.toString() ?: "--",
                        unit = "bpm",
                        label = "HR",
                        isConnected = hrConnected
                    )
                    SensorValueDisplay(
                        icon = Icons.Default.DirectionsBike,
                        value = cadence?.toString() ?: "--",
                        unit = "rpm",
                        label = "Cad",
                        isConnected = cadenceConnected
                    )
                    SensorValueDisplay(
                        icon = Icons.Default.FavoriteBorder,
                        value = avgHeartRate?.toString() ?: "--",
                        unit = "bpm",
                        label = "Media",
                        isConnected = hrConnected
                    )
                    SensorValueDisplay(
                        icon = Icons.Default.TrendingUp,
                        value = maxHeartRate?.toString() ?: "--",
                        unit = "bpm",
                        label = "Máx",
                        isConnected = hrConnected
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorValueDisplay(
    icon: ImageVector,
    value: String,
    unit: String,
    label: String,
    isConnected: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isConnected) Color.White else Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(TabataSizes.IconSmall)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = value,
                fontSize = TabataSizes.SensorValue,
                fontWeight = FontWeight.Bold,
                color = if (isConnected) Color.White else Color.White.copy(alpha = 0.5f)
            )
            Text(
                text = unit,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SensorValueCompact(
    icon: ImageVector,
    value: String,
    unit: String,
    isConnected: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isConnected) Color.White else Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (isConnected) Color.White else Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = unit,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

// ============================================================================
// 6. BARRA DE CONTROLES (Stop/Pausa)
// ============================================================================

@Composable
fun ControlButtonsBar(
    onStop: () -> Unit,
    onPauseResume: () -> Unit,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    showElapsedTime: Boolean = false,
    elapsedSeconds: Int = 0
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TabataSizes.PaddingLarge),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Botón Stop
        FloatingActionButton(
            onClick = onStop,
            containerColor = TabataColors.RestRed,
            contentColor = Color.White,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                Icons.Default.Stop,
                contentDescription = "Detener",
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.width(32.dp))

        // Tiempo transcurrido (opcional)
        if (showElapsedTime) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = formatTime(elapsedSeconds),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(32.dp))
        }

        // Botón Pausa/Reanudar
        FloatingActionButton(
            onClick = onPauseResume,
            containerColor = Color.White,
            contentColor = TabataColors.WarmupOrange,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isPaused) "Reanudar" else "Pausar",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// ============================================================================
// 7. FONDO DE FASE (Background dinámico)
// ============================================================================

enum class WorkoutPhaseType {
    WARMUP,
    WORK,
    REST,
    IDLE,
    FINISHED
}

@Composable
fun PhaseBackground(
    phase: WorkoutPhaseType,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = when (phase) {
            WorkoutPhaseType.WARMUP -> TabataColors.WarmupOrange
            WorkoutPhaseType.WORK -> TabataColors.WorkGreen
            WorkoutPhaseType.REST -> TabataColors.RestRed
            else -> TabataColors.CoolGray
        },
        animationSpec = tween(durationMillis = 300),
        label = "phase_background"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
        content = content
    )
}

// ============================================================================
// 8. RESUMEN DE TIEMPO (compacto)
// ============================================================================

@Composable
fun TimeSummaryBar(
    warmup: Int,
    work: Int,
    rest: Int,
    rounds: Int,
    totalTimeSeconds: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TabataSizes.PaddingMedium),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeSummaryItem(
                label = "Calent.",
                value = "${warmup}s",
                color = TabataColors.WarmupOrange
            )
            TimeSummaryItem(
                label = "Trabajo",
                value = "${work}s",
                color = TabataColors.WorkGreen
            )
            TimeSummaryItem(
                label = "Descanso",
                value = "${rest}s",
                color = TabataColors.RestRed
            )
            TimeSummaryItem(
                label = "Rondas",
                value = "$rounds",
                color = TabataColors.RoundsBlue
            )

            Divider(
                modifier = Modifier
                    .height(32.dp)
                    .width(1.dp),
                color = Color.White.copy(alpha = 0.3f)
            )

            // Tiempo total
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatTime(totalTimeSeconds),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Total",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun TimeSummaryItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

// ============================================================================
// 9. INDICADORES DE CONEXIÓN PARA PANTALLA ACTIVA
// ============================================================================

@Composable
fun ActiveSensorIndicators(
    hrConnected: Boolean,
    cadenceConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (hrConnected) {
            ActiveSensorChip(
                icon = Icons.Default.Favorite,
                label = "HR",
                isConnected = true
            )
        }
        if (cadenceConnected) {
            ActiveSensorChip(
                icon = Icons.Default.DirectionsBike,
                label = "Cadencia",
                isConnected = true
            )
        }
    }
}

@Composable
private fun ActiveSensorChip(
    icon: ImageVector,
    label: String,
    isConnected: Boolean
) {
    Surface(
        color = Color.Black.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (isConnected) TabataColors.Connected else TabataColors.Disconnected,
                        CircleShape
                    )
            )
        }
    }
}

// ============================================================================
// 10. BOTÓN CTA PRINCIPAL
// ============================================================================

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = Icons.Default.PlayArrow
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(TabataSizes.ButtonHeight),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = TabataColors.WorkGreen,
            contentColor = Color.White,
            disabledContainerColor = TabataColors.WorkGreen.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}