package com.tuapp.tabatatrainer.ui.config

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.IBinder
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tuapp.tabatatrainer.data.local.WorkoutPhase
import com.tuapp.tabatatrainer.sensor.*
import com.tuapp.tabatatrainer.service.WorkoutService
import com.tuapp.tabatatrainer.ui.components.*
import com.tuapp.tabatatrainer.ui.theme.TabataColors
import com.tuapp.tabatatrainer.ui.theme.TabataSizes
import com.tuapp.tabatatrainer.util.CustomConfigPreferences

// ============================================================================
// DATOS DEL ENTRENAMIENTO EN CURSO
// ============================================================================

data class ActiveWorkoutInfo(
    val isActive: Boolean = false,
    val phase: WorkoutPhase = WorkoutPhase.IDLE,
    val currentRound: Int = 0,
    val totalRounds: Int = 0,
    val remainingSeconds: Int = 0,
    val totalElapsedSeconds: Int = 0,
    val gpsEnabled: Boolean = false
)

// ============================================================================
// PANTALLA PRINCIPAL DE CONFIGURACIÓN
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onStartWorkout: (warmup: Int, work: Int, rest: Int, rounds: Int, gpsEnabled: Boolean) -> Unit,
    onNavigateToHistory: () -> Unit,
    onResumeWorkout: ((warmup: Int, work: Int, rest: Int, rounds: Int, gpsEnabled: Boolean) -> Unit)? = null,
    viewModel: ConfigViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Usar screenWidthDp para detectar orientación de forma más confiable
    // ya que configChanges maneja los cambios sin recrear la Activity
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    // Estados de configuración
    var warmupTime by remember { mutableIntStateOf(10) }
    var workTime by remember { mutableIntStateOf(20) }
    var restTime by remember { mutableIntStateOf(10) }
    var rounds by remember { mutableIntStateOf(8) }
    var selectedPreset by remember { mutableStateOf<String?>("Tabata") }

    // Cargar configuración CUSTOM guardada al iniciar
    LaunchedEffect(Unit) {
        CustomConfigPreferences.loadCustomConfig(context)?.let { customConfig ->
            warmupTime = customConfig.warmup
            workTime = customConfig.work
            restTime = customConfig.rest
            rounds = customConfig.rounds
        }
    }

    val bleStatus by viewModel.bleStatus.collectAsState()

    // Detección de entrenamiento en curso
    var activeWorkout by remember { mutableStateOf(ActiveWorkoutInfo()) }
    var service by remember { mutableStateOf<WorkoutService?>(null) }
    var isBound by remember { mutableStateOf(false) }

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val localBinder = binder as WorkoutService.LocalBinder
                service = localBinder.getService()
                isBound = true
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                isBound = false
            }
        }
    }

    DisposableEffect(Unit) {
        val intent = Intent(context, WorkoutService::class.java)
        try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) { }

        onDispose {
            if (isBound) {
                context.unbindService(connection)
            }
        }
    }

    LaunchedEffect(service) {
        service?.let { svc ->
            svc.workoutState.collect { state ->
                activeWorkout = ActiveWorkoutInfo(
                    isActive = state.isRunning || state.isPaused,
                    phase = state.phase,
                    currentRound = state.currentRound,
                    totalRounds = state.totalRounds,
                    remainingSeconds = state.remainingSeconds,
                    totalElapsedSeconds = state.totalElapsedSeconds,
                    gpsEnabled = svc.sensorState.value.isGpsEnabled
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startScanning()
    }

    // Cálculo de tiempo total
    val totalSeconds = warmupTime + (rounds * workTime) + ((rounds - 1) * restTime)

    // Callback para aplicar preset
    val applyPreset: (WorkoutPreset) -> Unit = { preset ->
        selectedPreset = preset.name
        if (preset.name != "Custom") {
            warmupTime = preset.warmup
            workTime = preset.work
            restTime = preset.rest
            rounds = preset.rounds
        } else {
            // Si se selecciona Custom, cargar la configuración guardada
            CustomConfigPreferences.loadCustomConfig(context)?.let { customConfig ->
                warmupTime = customConfig.warmup
                workTime = customConfig.work
                restTime = customConfig.rest
                rounds = customConfig.rounds
            }
        }
    }

    // Detectar cambio manual (desseleccionar preset)
    val onManualChange: () -> Unit = {
        if (selectedPreset != "Custom") {
            selectedPreset = "Custom"
        }
    }

    val canStartWorkout = !activeWorkout.isActive ||
            activeWorkout.phase == WorkoutPhase.IDLE ||
            activeWorkout.phase == WorkoutPhase.FINISHED

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "TABATA TRAINER",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TabataColors.CoolGray,
                    titleContentColor = TabataColors.VibrantOrange
                ),
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.Default.History,
                            "Historial",
                            tint = TabataColors.VibrantOrange
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (isLandscape) {
            // ============================================================
            // LAYOUT HORIZONTAL (2 columnas)
            // ============================================================
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(TabataSizes.PaddingMedium),
                horizontalArrangement = Arrangement.spacedBy(TabataSizes.PaddingMedium)
            ) {
                // Columna izquierda: Configuración
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Banner de entrenamiento en curso
                    ActiveWorkoutBannerAnimated(
                        activeWorkout = activeWorkout,
                        onResume = {
                            // Obtener configuración del servicio activo y navegar
                            service?.getCurrentConfig()?.let { config ->
                                onResumeWorkout?.invoke(
                                    config.warmupSeconds,
                                    config.workSeconds,
                                    config.restSeconds,
                                    config.rounds,
                                    config.gpsEnabled
                                )
                            }
                        }
                    )

                    // Presets
                    PresetButtonRow(
                        selectedPreset = selectedPreset,
                        onPresetSelected = applyPreset
                    )

                    // Sliders compactos
                    CompactTimeSlider(
                        title = "Calentamiento",
                        value = warmupTime,
                        onValueChange = { warmupTime = it; onManualChange() },
                        valueRange = 0..60,
                        icon = Icons.Default.Timer,
                        color = TabataColors.WarmupOrange
                    )
                    CompactTimeSlider(
                        title = "Trabajo",
                        value = workTime,
                        onValueChange = { workTime = it; onManualChange() },
                        valueRange = 5..360,
                        icon = Icons.Default.FitnessCenter,
                        color = TabataColors.WorkGreen
                    )
                    CompactTimeSlider(
                        title = "Descanso",
                        value = restTime,
                        onValueChange = { restTime = it; onManualChange() },
                        valueRange = 5..420,
                        icon = Icons.Default.Pause,
                        color = TabataColors.RestRed
                    )
                    CompactTimeSlider(
                        title = "Rondas",
                        value = rounds,
                        onValueChange = { rounds = it; onManualChange() },
                        valueRange = 1..50,
                        icon = Icons.Default.Repeat,
                        color = TabataColors.RoundsBlue,
                        unit = ""
                    )
                }

                // Columna derecha: Resumen y acción
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Sensores compactos
                        CompactSensorRowWithGps(
                            bleStatus = bleStatus,
                            onRefresh = { viewModel.startScanning() },
                            onGpsToggle = { viewModel.setGpsEnabled(it) }
                        )

                        // Tiempo total
                        TotalTimeCard(totalSeconds = totalSeconds)
                    }

                    // Botón iniciar
                    PrimaryActionButton(
                        text = "INICIAR",
                        onClick = {
                            viewModel.stopScanning()
                            // Guardar configuración si está en modo Custom
                            if (selectedPreset == "Custom") {
                                CustomConfigPreferences.saveCustomConfig(
                                    context,
                                    warmupTime,
                                    workTime,
                                    restTime,
                                    rounds
                                )
                            }
                            onStartWorkout(warmupTime, workTime, restTime, rounds, bleStatus.gpsEnabled)
                        },
                        enabled = canStartWorkout,
                        modifier = Modifier.padding(bottom = TabataSizes.PaddingSmall)
                    )
                }
            }
        } else {
            // ============================================================
            // LAYOUT VERTICAL (1 columna)
            // ============================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(TabataSizes.PaddingMedium),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Banner de entrenamiento en curso
                ActiveWorkoutBannerAnimated(
                    activeWorkout = activeWorkout,
                    onResume = {
                        // Obtener configuración del servicio activo y navegar
                        service?.getCurrentConfig()?.let { config ->
                            onResumeWorkout?.invoke(
                                config.warmupSeconds,
                                config.workSeconds,
                                config.restSeconds,
                                config.rounds,
                                config.gpsEnabled
                            )
                        }
                    }
                )

                // Sensores compactos con GPS
                CompactSensorRowWithGps(
                    bleStatus = bleStatus,
                    onRefresh = { viewModel.startScanning() },
                    onGpsToggle = { viewModel.setGpsEnabled(it) }
                )

                // Tiempo total
                TotalTimeCard(totalSeconds = totalSeconds)

                // Presets
                PresetButtonRow(
                    selectedPreset = selectedPreset,
                    onPresetSelected = applyPreset
                )

                // Sliders compactos
                CompactTimeSlider(
                    title = "Calentamiento",
                    value = warmupTime,
                    onValueChange = { warmupTime = it; onManualChange() },
                    valueRange = 0..60,
                    icon = Icons.Default.Timer,
                    color = TabataColors.WarmupOrange
                )
                CompactTimeSlider(
                    title = "Trabajo",
                    value = workTime,
                    onValueChange = { workTime = it; onManualChange() },
                    valueRange = 5..300,
                    icon = Icons.Default.FitnessCenter,
                    color = TabataColors.WorkGreen
                )
                CompactTimeSlider(
                    title = "Descanso",
                    value = restTime,
                    onValueChange = { restTime = it; onManualChange() },
                    valueRange = 5..420,
                    icon = Icons.Default.Pause,
                    color = TabataColors.RestRed
                )
                CompactTimeSlider(
                    title = "Rondas",
                    value = rounds,
                    onValueChange = { rounds = it; onManualChange() },
                    valueRange = 1..50,
                    icon = Icons.Default.Repeat,
                    color = TabataColors.RoundsBlue,
                    unit = ""
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Botón iniciar
                PrimaryActionButton(
                    text = "INICIAR ENTRENAMIENTO",
                    onClick = {
                        viewModel.stopScanning()
                        // Guardar configuración si está en modo Custom
                        if (selectedPreset == "Custom") {
                            CustomConfigPreferences.saveCustomConfig(
                                context,
                                warmupTime,
                                workTime,
                                restTime,
                                rounds
                            )
                        }
                        onStartWorkout(warmupTime, workTime, restTime, rounds, bleStatus.gpsEnabled)
                    },
                    enabled = canStartWorkout
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ============================================================================
// COMPONENTES ESPECÍFICOS DE CONFIGSCREEN
// ============================================================================

/**
 * Card de tiempo total con diseño prominente
 */
@Composable
private fun TotalTimeCard(totalSeconds: Int) {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(TabataSizes.CardCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Tiempo Total",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                String.format("%02d:%02d", minutes, seconds),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Fila de sensores compacta con toggle de GPS integrado
 */
@Composable
private fun CompactSensorRowWithGps(
    bleStatus: BleScanStatus,
    onRefresh: () -> Unit,
    onGpsToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = TabataColors.CardBackground
        ),
        shape = RoundedCornerShape(TabataSizes.CardCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Fila de sensores
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // HR
                    SensorStatusChip(
                        icon = Icons.Default.Favorite,
                        label = "HR",
                        isConnected = bleStatus.hrConnected,
                        isScanning = bleStatus.hrScanning,
                        value = if (bleStatus.hrValue > 0) "${bleStatus.hrValue}" else null
                    )

                    // Cadencia
                    SensorStatusChip(
                        icon = Icons.AutoMirrored.Filled.DirectionsBike,
                        label = "CAD",
                        isConnected = bleStatus.cadenceConnected,
                        isScanning = bleStatus.cadenceScanning,
                        value = if (bleStatus.cadenceValue > 0) "${bleStatus.cadenceValue}" else null
                    )
                }

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

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.1f)
            )

            // Toggle GPS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = if (bleStatus.gpsEnabled) TabataColors.Connected else TabataColors.Disconnected,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            "GPS / Track",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            when {
                                !bleStatus.gpsAvailable -> "No disponible"
                                bleStatus.gpsEnabled -> "Grabará ruta"
                                else -> "Desactivado"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (bleStatus.gpsEnabled) TabataColors.Connected else Color.Gray
                        )
                    }
                }

                Switch(
                    checked = bleStatus.gpsEnabled,
                    onCheckedChange = onGpsToggle,
                    enabled = bleStatus.gpsAvailable,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TabataColors.Connected,
                        checkedTrackColor = TabataColors.Connected.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }

            // Mensaje de GPS no disponible
            if (!bleStatus.gpsAvailable) {
                Text(
                    "⚠️ Activa GPS y concede permisos",
                    style = MaterialTheme.typography.bodySmall,
                    color = TabataColors.WarmupOrange,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Chip de estado de sensor individual
 */
@Composable
private fun SensorStatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isConnected: Boolean,
    isScanning: Boolean,
    value: String? = null
) {
    val color = when {
        isConnected -> TabataColors.Connected
        isScanning -> TabataColors.Scanning
        else -> TabataColors.Disconnected
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(20.dp)
        )

        if (value != null && isConnected) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        } else {
            Text(
                text = label,
                fontSize = 12.sp,
                color = color
            )
        }

        // Indicador de estado
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape)
        )

        // Spinner si está buscando
        if (isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = TabataColors.Scanning
            )
        }
    }
}

/**
 * Banner animado de entrenamiento en curso
 */
@Composable
private fun ActiveWorkoutBannerAnimated(
    activeWorkout: ActiveWorkoutInfo,
    onResume: () -> Unit
) {
    AnimatedVisibility(
        visible = activeWorkout.isActive &&
                activeWorkout.phase != WorkoutPhase.IDLE &&
                activeWorkout.phase != WorkoutPhase.FINISHED,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        ActiveWorkoutBanner(
            workoutInfo = activeWorkout,
            onResume = onResume
        )
    }
}

/**
 * Banner de entrenamiento en curso
 */
@Composable
private fun ActiveWorkoutBanner(
    workoutInfo: ActiveWorkoutInfo,
    onResume: () -> Unit
) {
    val backgroundColor = when (workoutInfo.phase) {
        WorkoutPhase.WARMUP -> listOf(TabataColors.WarmupOrange, TabataColors.WarmupOrange.copy(alpha = 0.8f))
        WorkoutPhase.WORK -> listOf(TabataColors.WorkGreen, TabataColors.WorkGreen.copy(alpha = 0.8f))
        WorkoutPhase.REST -> listOf(TabataColors.RestRed, TabataColors.RestRed.copy(alpha = 0.8f))
        else -> listOf(TabataColors.CoolGray, TabataColors.CoolGray.copy(alpha = 0.8f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onResume() },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(TabataSizes.CardCornerRadius)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(backgroundColor))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "⚡ EN CURSO",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            workoutInfo.phase.displayName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "R${workoutInfo.currentRound}/${workoutInfo.totalRounds}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Timer
                    val mins = workoutInfo.remainingSeconds / 60
                    val secs = workoutInfo.remainingSeconds % 60
                    Text(
                        if (mins > 0) String.format("%d:%02d", mins, secs) else String.format("%02d", secs),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Botón volver
                    FilledTonalButton(
                        onClick = onResume,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.9f)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = backgroundColor[0],
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "IR",
                            color = backgroundColor[0],
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}