// WorkoutScreen.kt - SOLO CAMBIOS ESTÉTICOS
package com.tuapp.tabatatrainer.ui.workout

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.IBinder
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuapp.tabatatrainer.data.local.WorkoutConfig
import com.tuapp.tabatatrainer.data.local.WorkoutPhase
import com.tuapp.tabatatrainer.data.local.WorkoutState
import com.tuapp.tabatatrainer.data.local.SensorState
import com.tuapp.tabatatrainer.data.local.SessionStats
import com.tuapp.tabatatrainer.service.WorkoutService

// ============================================================================
// PALETA NIKE - SOLO CAMBIO DE COLORES
// ============================================================================
private object TabataColors {
    val VibrantOrange = Color(0xFFFF6B35)
    val CoolGray = Color(0xFF1A1A1A)
    val WarmupOrange = Color(0xFFFF9800)
    val WorkGreen = Color(0xFF4CAF50)
    val RestRed = Color(0xFFE53935)
    val RoundsBlue = Color(0xFF2196F3)
    val Connected = Color(0xFF4CAF50)
    val HrPink = Color(0xFFF1DE33)
    val HrCyan = Color(0xFF00BCD4)
}

// ============================================================================
// PANTALLA PRINCIPAL DE ENTRENAMIENTO
// ============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutScreen(
    warmupSeconds: Int,
    workSeconds: Int,
    restSeconds: Int,
    rounds: Int,
    gpsEnabled: Boolean,
    onFinish: () -> Unit,
    onViewSession: (String) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var service by remember { mutableStateOf<WorkoutService?>(null) }
    var isBound by remember { mutableStateOf(false) }

    val workoutState by service?.workoutState?.collectAsState() ?: remember { mutableStateOf(WorkoutState()) }
    val sensorState by service?.sensorState?.collectAsState() ?: remember { mutableStateOf(SensorState()) }
    val sessionStats by service?.sessionStats?.collectAsState() ?: remember { mutableStateOf(SessionStats()) }

    val pageCount = if (gpsEnabled) 2 else 1
    val pagerState = rememberPagerState(pageCount = { pageCount })

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val localBinder = binder as WorkoutService.LocalBinder
                service = localBinder.getService()
                isBound = true
                // Solo configurar si el workout no está en curso
                // Esto permite que el workout siga corriendo aunque navegues a otra pantalla
                val currentState = service?.workoutState?.value
                if (currentState?.phase == WorkoutPhase.IDLE || currentState?.phase == WorkoutPhase.FINISHED) {
                    service?.configureWorkout(
                        WorkoutConfig(
                            warmupSeconds = warmupSeconds,
                            workSeconds = workSeconds,
                            restSeconds = restSeconds,
                            rounds = rounds,
                            gpsEnabled = gpsEnabled
                        )
                    )
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                isBound = false
            }
        }
    }

    DisposableEffect(Unit) {
        val intent = Intent(context, WorkoutService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose {
            if (isBound) context.unbindService(connection)
        }
    }

    val backgroundColor = phaseGradient(workoutState.phase)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(backgroundColor))
    ) {
        if (pageCount > 1) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> WorkoutMainPage(
                        workoutState = workoutState,
                        sensorState = sensorState,
                        sessionStats = sessionStats,
                        gpsEnabled = gpsEnabled,
                        isLandscape = isLandscape,
                        onStart = { service?.startWorkout() },
                        onPause = { service?.pauseWorkout() },
                        onResume = { service?.resumeWorkout() },
                        onStop = { service?.stopWorkout() },
                        onRefreshSensors = { service?.refreshSensors() }
                    )
                    1 -> GpsMetricsPage(
                        workoutState = workoutState,
                        sensorState = sensorState,
                        sessionStats = sessionStats,
                        onPause = { service?.pauseWorkout() },
                        onResume = { service?.resumeWorkout() },
                        onRefreshSensors = { service?.refreshSensors() }
                    )
                }
            }

            if (workoutState.phase != WorkoutPhase.FINISHED) {
                PageIndicators(
                    pageCount = pageCount,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                )

                if (pagerState.currentPage == 0 && workoutState.isRunning) {
                    SwipeHint(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                    )
                }
            }
        } else {
            WorkoutMainPage(
                workoutState = workoutState,
                sensorState = sensorState,
                sessionStats = sessionStats,
                gpsEnabled = gpsEnabled,
                isLandscape = isLandscape,
                onStart = { service?.startWorkout() },
                onPause = { service?.pauseWorkout() },
                onResume = { service?.resumeWorkout() },
                onStop = { service?.stopWorkout() },
                onRefreshSensors = { service?.refreshSensors() }
            )
        }

        if (workoutState.phase != WorkoutPhase.FINISHED) {
            IconButton(
                onClick = {
                    // NO pausar el workout al navegar - permitir que siga corriendo en background
                    // El usuario puede volver a esta pantalla y el workout seguirá activo
                    onFinish()
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }
            }
        }

        val showFinishedDialog = workoutState.phase == WorkoutPhase.FINISHED

        BackHandler(enabled = showFinishedDialog) {
            service?.resetToIdle()
            onFinish()
        }

        if (showFinishedDialog) {
            FinishedDialog(
                stats = sessionStats,
                gpsEnabled = gpsEnabled,
                onDismiss = {
                    service?.resetToIdle()
                    onFinish()
                }
            )
        }
    }
}

// ============================================================================
// PÁGINA PRINCIPAL DE ENTRENAMIENTO
// ============================================================================

@Composable
private fun WorkoutMainPage(
    workoutState: WorkoutState,
    sensorState: SensorState,
    sessionStats: SessionStats,
    gpsEnabled: Boolean,
    isLandscape: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRefreshSensors: () -> Unit
) {
    if (isLandscape) {
        LandscapeWorkoutLayout(
            workoutState = workoutState,
            sensorState = sensorState,
            sessionStats = sessionStats,
            gpsEnabled = gpsEnabled,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop,
            onRefreshSensors = onRefreshSensors
        )
    } else {
        PortraitWorkoutLayout(
            workoutState = workoutState,
            sensorState = sensorState,
            sessionStats = sessionStats,
            gpsEnabled = gpsEnabled,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop,
            onRefreshSensors = onRefreshSensors
        )
    }
}

// ============================================================================
// LAYOUT VERTICAL (PORTRAIT)
// ============================================================================

@Composable
private fun PortraitWorkoutLayout(
    workoutState: WorkoutState,
    sensorState: SensorState,
    sessionStats: SessionStats,
    gpsEnabled: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRefreshSensors: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SensorStatusChips(
                sensorState = sensorState,
                gpsEnabled = gpsEnabled
            )
            if (workoutState.phase == WorkoutPhase.IDLE || workoutState.isRunning) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onRefreshSensors,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refrescar sensores",
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PhaseTitle(phase = workoutState.phase)

        Spacer(modifier = Modifier.height(8.dp))

        AnimatedTimer(
            seconds = workoutState.remainingSeconds,
            isRunning = workoutState.isRunning && !workoutState.isPaused,
            fontSize = 100
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (workoutState.phase.isActive) {
            RoundDisplay(
                currentRound = workoutState.currentRound,
                totalRounds = workoutState.totalRounds,
                showDots = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        HeartRateGraph(
            readings = sessionStats.hrReadings,
            readings2 = sessionStats.hr2Readings,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        MetricsPanel(
            heartRate = sensorState.heartRate,
            heartRate2 = sensorState.heartRate2,
            cadence = sensorState.cadence,
            cadence2 = sensorState.cadence2,
            avgHeartRate = sessionStats.avgHeartRate,
            maxHeartRate = sessionStats.maxHeartRate,
            avgHeartRate2 = sessionStats.avgHeartRate2,
            maxHeartRate2 = sessionStats.maxHeartRate2,
            gpsEnabled = gpsEnabled,
            speedKmh = sensorState.currentSpeedKmh,
            distanceKm = sensorState.totalDistanceKm,
            isHrConnected = sensorState.isHrConnected,
            isHr2Connected = sensorState.isHr2Connected,
            isCadenceConnected = sensorState.isCadenceConnected,
            isCadence2Connected = sensorState.isCadence2Connected
        )

        Spacer(modifier = Modifier.height(12.dp))

        TotalTimeDisplay(
            totalSeconds = workoutState.totalElapsedSeconds,
            isLandscape = false
        )

        Spacer(modifier = Modifier.weight(1f))

        WorkoutControls(
            workoutState = workoutState,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop,
            isCompact = false
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ============================================================================
// LAYOUT HORIZONTAL (LANDSCAPE)
// ============================================================================

@Composable
private fun LandscapeWorkoutLayout(
    workoutState: WorkoutState,
    sensorState: SensorState,
    sessionStats: SessionStats,
    gpsEnabled: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRefreshSensors: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // === COLUMNA IZQUIERDA (40%) - Timer y controles ===
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SensorStatusChipsCompact(
                    sensorState = sensorState,
                    gpsEnabled = gpsEnabled
                )
                if (workoutState.phase == WorkoutPhase.IDLE || workoutState.isRunning) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onRefreshSensors,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refrescar sensores",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            PhaseTitle(phase = workoutState.phase, fontSize = 28)

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedTimer(
                seconds = workoutState.remainingSeconds,
                isRunning = workoutState.isRunning && !workoutState.isPaused,
                fontSize = 100
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (workoutState.phase.isActive) {
                    RoundDisplay(
                        currentRound = workoutState.currentRound,
                        totalRounds = workoutState.totalRounds,
                        showDots = false,
                        compact = true
                    )
                }

                TotalTimeDisplay(
                    totalSeconds = workoutState.totalElapsedSeconds,
                    isLandscape = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            WorkoutControls(
                workoutState = workoutState,
                onStart = onStart,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
                isCompact = true
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // === COLUMNA DERECHA (60%) - Gráfica y métricas ===
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Gráfica grande - ocupa la mayor parte del espacio
            HeartRateGraph(
                readings = sessionStats.hrReadings,
                readings2 = sessionStats.hr2Readings,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.65f)
            )

            // Métricas compactas abajo
            MetricsPanelCompact(
                heartRate = sensorState.heartRate,
                heartRate2 = sensorState.heartRate2,
                cadence = sensorState.cadence,
                cadence2 = sensorState.cadence2,
                avgHeartRate = sessionStats.avgHeartRate,
                maxHeartRate = sessionStats.maxHeartRate,
                avgHeartRate2 = sessionStats.avgHeartRate2,
                maxHeartRate2 = sessionStats.maxHeartRate2,
                gpsEnabled = gpsEnabled,
                speedKmh = sensorState.currentSpeedKmh,
                distanceKm = sensorState.totalDistanceKm,
                isHrConnected = sensorState.isHrConnected,
                isHr2Connected = sensorState.isHr2Connected,
                isCadenceConnected = sensorState.isCadenceConnected,
                isCadence2Connected = sensorState.isCadence2Connected
            )
        }
    }
}

// ============================================================================
// PÁGINA GPS / MÉTRICAS DETALLADAS
// ============================================================================

@Composable
private fun GpsMetricsPage(
    workoutState: WorkoutState,
    sensorState: SensorState,
    sessionStats: SessionStats,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRefreshSensors: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    workoutState.phase.displayName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "Ronda ${workoutState.currentRound}/${workoutState.totalRounds}",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    formatTime(workoutState.remainingSeconds),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        GpsMetricCard(
            title = "VELOCIDAD",
            value = if (sensorState.currentSpeedKmh > 0)
                String.format("%.1f", sensorState.currentSpeedKmh) else "--",
            unit = "km/h",
            fontSize = 72
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GpsMetricCardSmall(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Route,
                title = "DISTANCIA",
                value = String.format("%.2f", sensorState.totalDistanceKm),
                unit = "km",
                color = TabataColors.RoundsBlue
            )
            GpsMetricCardSmall(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Speed,
                title = "VEL. MEDIA",
                value = String.format("%.1f", sessionStats.avgSpeedKmh),
                unit = "km/h",
                color = TabataColors.WorkGreen
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GpsMetricCardSmall(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                title = "VEL. MÁX",
                value = String.format("%.1f", sessionStats.maxSpeedKmh),
                unit = "km/h",
                color = TabataColors.VibrantOrange
            )
            GpsMetricCardSmall(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Favorite,
                title = "HR ACTUAL",
                value = if (sensorState.heartRate > 0) "${sensorState.heartRate}" else "--",
                unit = "bpm",
                color = TabataColors.RestRed
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.GpsFixed,
                contentDescription = null,
                tint = if (sensorState.isGpsTracking) TabataColors.Connected else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Text(
                if (sensorState.isGpsTracking) "GPS Activo" else "Buscando GPS...",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ============================================================================
// COMPONENTES DE UI
// ============================================================================

@Composable
private fun PhaseTitle(phase: WorkoutPhase, fontSize: Int = 28) {
    Text(
        text = phase.displayName,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        letterSpacing = 3.sp
    )
}

@Composable
private fun AnimatedTimer(seconds: Int, isRunning: Boolean, fontSize: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (seconds <= 3 && isRunning) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val timeText = formatTime(seconds)

    Text(
        text = timeText,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = Color.White,
        modifier = Modifier.scale(scale)
    )
}

@Composable
private fun RoundDisplay(
    currentRound: Int,
    totalRounds: Int,
    showDots: Boolean,
    compact: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Ronda",
            fontSize = if (compact) 14.sp else 16.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
        Text(
            "$currentRound / $totalRounds",
            fontSize = if (compact) 22.sp else 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        if (showDots && totalRounds <= 15) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                repeat(totalRounds) { index ->
                    val isCompleted = index < currentRound
                    val isCurrent = index == currentRound - 1
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isCurrent) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCompleted || isCurrent -> Color.White
                                    else -> Color.White.copy(alpha = 0.3f)
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalTimeDisplay(totalSeconds: Int, isLandscape: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Tiempo Total",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium
        )
        if (!isLandscape) Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatTime(totalSeconds),
            fontSize = if (isLandscape) 22.sp else 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SensorStatusChips(sensorState: SensorState, gpsEnabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SensorChip(
            icon = Icons.Default.Favorite,
            label = "HR1",
            isConnected = sensorState.isHrConnected,
            isScanning = sensorState.isHrScanning
        )
        Spacer(modifier = Modifier.width(12.dp))
        SensorChip(
            icon = Icons.Default.Favorite,
            label = "HR2",
            isConnected = sensorState.isHr2Connected,
            isScanning = sensorState.isHr2Scanning
        )
        Spacer(modifier = Modifier.width(12.dp))
        SensorChip(
            icon = Icons.AutoMirrored.Filled.DirectionsBike,
            label = "C1",
            isConnected = sensorState.isCadenceConnected,
            isScanning = sensorState.isCadenceScanning
        )
        Spacer(modifier = Modifier.width(12.dp))
        SensorChip(
            icon = Icons.AutoMirrored.Filled.DirectionsBike,
            label = "C2",
            isConnected = sensorState.isCadence2Connected,
            isScanning = sensorState.isCadence2Scanning
        )
        if (gpsEnabled) {
            Spacer(modifier = Modifier.width(12.dp))
            SensorChip(
                icon = Icons.Default.LocationOn,
                label = "GPS",
                isConnected = sensorState.isGpsTracking,
                isScanning = false
            )
        }
    }
}

@Composable
private fun SensorStatusChipsCompact(sensorState: SensorState, gpsEnabled: Boolean) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SensorChipCompact(Icons.Default.Favorite, sensorState.isHrConnected, sensorState.isHrScanning)
        Spacer(modifier = Modifier.width(8.dp))
        SensorChipCompact(Icons.Default.Favorite, sensorState.isHr2Connected, sensorState.isHr2Scanning)
        Spacer(modifier = Modifier.width(8.dp))
        SensorChipCompact(Icons.AutoMirrored.Filled.DirectionsBike, sensorState.isCadenceConnected, sensorState.isCadenceScanning)
        Spacer(modifier = Modifier.width(8.dp))
        SensorChipCompact(Icons.AutoMirrored.Filled.DirectionsBike, sensorState.isCadence2Connected, sensorState.isCadence2Scanning)
        if (gpsEnabled) {
            Spacer(modifier = Modifier.width(8.dp))
            SensorChipCompact(Icons.Default.LocationOn, sensorState.isGpsTracking, false)
        }
    }
}

@Composable
private fun SensorChip(icon: ImageVector, label: String, isConnected: Boolean, isScanning: Boolean = false) {
    val dotColor = when {
        isConnected -> TabataColors.Connected
        isScanning -> Color(0xFFFFA500)
        else -> TabataColors.RestRed
    }
    
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}

@Composable
private fun SensorChipCompact(icon: ImageVector, isConnected: Boolean, isScanning: Boolean = false) {
    val dotColor = when {
        isConnected -> TabataColors.Connected
        isScanning -> Color(0xFFFFA500)
        else -> TabataColors.RestRed
    }
    
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}

@Composable
private fun MetricsPanel(
    heartRate: Int,
    heartRate2: Int = 0,
    cadence: Float,
    cadence2: Float = 0f,
    avgHeartRate: Int,
    maxHeartRate: Int,
    avgHeartRate2: Int = 0,
    maxHeartRate2: Int = 0,
    gpsEnabled: Boolean,
    speedKmh: Float,
    distanceKm: Float,
    isHrConnected: Boolean = false,
    isHr2Connected: Boolean = false,
    isCadenceConnected: Boolean = false,
    isCadence2Connected: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // HR1
        MetricItem(
            icon = Icons.Default.Favorite,
            value = if (isHrConnected || heartRate > 0) "$heartRate" else "--",
            unit = "bpm",
            label = "HR1",
            isPrimary = true
        )
        // HR1 Máx
        MetricItem(
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            value = if (maxHeartRate > 0) "$maxHeartRate" else "--",
            unit = "bpm",
            label = "HR1 Máx"
        )
        // Cadencia 1
        MetricItem(
            icon = Icons.AutoMirrored.Filled.DirectionsBike,
            value = if (isCadenceConnected || cadence > 0) "${cadence.toInt()}" else "--",
            unit = "rpm",
            label = "C1",
            isPrimary = true
        )
        // HR2
        MetricItem(
            icon = Icons.Default.Favorite,
            value = if (isHr2Connected || heartRate2 > 0) "$heartRate2" else "--",
            unit = "bpm",
            label = "HR2",
            isPrimary = true,
            color = TabataColors.HrCyan
        )
        // HR2 Máx
        MetricItem(
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            value = if (maxHeartRate2 > 0) "$maxHeartRate2" else "--",
            unit = "bpm",
            label = "HR2 Máx",
            color = TabataColors.HrCyan
        )
        // Cadencia 2
        MetricItem(
            icon = Icons.AutoMirrored.Filled.DirectionsBike,
            value = if (isCadence2Connected || cadence2 > 0) "${cadence2.toInt()}" else "--",
            unit = "rpm",
            label = "C2",
            isPrimary = true
        )
    }
}

@Composable
private fun MetricsPanelCompact(
    heartRate: Int,
    heartRate2: Int = 0,
    cadence: Float,
    cadence2: Float = 0f,
    avgHeartRate: Int,
    maxHeartRate: Int,
    avgHeartRate2: Int = 0,
    maxHeartRate2: Int = 0,
    gpsEnabled: Boolean,
    speedKmh: Float,
    distanceKm: Float,
    isHrConnected: Boolean = false,
    isHr2Connected: Boolean = false,
    isCadenceConnected: Boolean = false,
    isCadence2Connected: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Primera fila: HR1, HR1 Máx, Cad1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricItemCompact(Icons.Default.Favorite, if (isHrConnected || heartRate > 0) "$heartRate" else "--", "HR1", TabataColors.HrPink)
            MetricItemCompact(Icons.AutoMirrored.Filled.TrendingUp, if (maxHeartRate > 0) "$maxHeartRate" else "--", "HR1 Máx", TabataColors.HrPink)
            MetricItemCompact(Icons.AutoMirrored.Filled.DirectionsBike, if (isCadenceConnected || cadence > 0) "${cadence.toInt()}" else "--", "C1", Color.White)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Segunda fila: HR2, HR2 Máx, Cad2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricItemCompact(Icons.Default.Favorite, if (isHr2Connected || heartRate2 > 0) "$heartRate2" else "--", "HR2", TabataColors.HrCyan)
            MetricItemCompact(Icons.AutoMirrored.Filled.TrendingUp, if (maxHeartRate2 > 0) "$maxHeartRate2" else "--", "HR2 Máx", TabataColors.HrCyan)
            MetricItemCompact(Icons.AutoMirrored.Filled.DirectionsBike, if (isCadence2Connected || cadence2 > 0) "${cadence2.toInt()}" else "--", "C2", Color.White)
        }
    }
}

@Composable
private fun MetricItem(icon: ImageVector, value: String, unit: String, label: String, isPrimary: Boolean = false, color: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(if (isPrimary) 22.dp else 18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = if (isPrimary) 32.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = unit,
                fontSize = if (isPrimary) 14.sp else 10.sp,
                color = color.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
            )
        }
        Text(label, fontSize = 11.sp, color = color.copy(alpha = 0.6f))
    }
}

@Composable
private fun MetricItemCompact(icon: ImageVector, value: String, unit: String, color: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = color)
            Text(unit, fontSize = 14.sp, color = color.copy(alpha = 0.7f), modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
    }
}

@Composable
private fun WorkoutControls(
    workoutState: WorkoutState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    isCompact: Boolean
) {
    val mainSize = if (isCompact) 56.dp else 72.dp
    val secondarySize = if (isCompact) 48.dp else 60.dp
    val iconSize = if (isCompact) 28.dp else 36.dp
    val secondaryIconSize = if (isCompact) 24.dp else 28.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            workoutState.phase == WorkoutPhase.IDLE -> {
                FloatingActionButton(
                    onClick = onStart,
                    containerColor = Color.White,
                    modifier = Modifier.size(mainSize)
                ) {
                    Icon(Icons.Default.PlayArrow, "Iniciar", tint = TabataColors.WorkGreen, modifier = Modifier.size(iconSize))
                }
            }
            workoutState.isPaused -> {
                FloatingActionButton(
                    onClick = onStop,
                    containerColor = TabataColors.RestRed,
                    modifier = Modifier.size(secondarySize)
                ) {
                    Icon(Icons.Default.Stop, "Detener", tint = Color.White, modifier = Modifier.size(secondaryIconSize))
                }
                Spacer(modifier = Modifier.width(24.dp))
                FloatingActionButton(
                    onClick = onResume,
                    containerColor = Color.White,
                    modifier = Modifier.size(mainSize)
                ) {
                    Icon(Icons.Default.PlayArrow, "Reanudar", tint = TabataColors.WorkGreen, modifier = Modifier.size(iconSize))
                }
            }
            workoutState.isRunning -> {
                FloatingActionButton(
                    onClick = onStop,
                    containerColor = TabataColors.RestRed,
                    modifier = Modifier.size(secondarySize)
                ) {
                    Icon(Icons.Default.Stop, "Detener", tint = Color.White, modifier = Modifier.size(secondaryIconSize))
                }
                Spacer(modifier = Modifier.width(24.dp))
                FloatingActionButton(
                    onClick = onPause,
                    containerColor = Color.White,
                    modifier = Modifier.size(mainSize)
                ) {
                    Icon(Icons.Default.Pause, "Pausar", tint = TabataColors.WarmupOrange, modifier = Modifier.size(iconSize))
                }
            }
        }
    }
}

@Composable
private fun GpsMetricCard(title: String, value: String, unit: String, fontSize: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f), letterSpacing = 2.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = fontSize.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(" $unit", fontSize = 24.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun GpsMetricCardSmall(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    unit: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(title, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f), letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(" $unit", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun HeartRateGraph(
    readings: List<Int>,
    readings2: List<Int> = emptyList(),
    modifier: Modifier = Modifier
) {
    // Cada lista tiene 1 muestra por segundo de entrenamiento (0 = sin dato)
    if (readings.none { it > 0 } && readings2.none { it > 0 }) {
        Box(
            modifier = modifier.background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Esperando datos HR...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        }
        return
    }

    val totalSeconds = maxOf(readings.size, readings2.size).coerceAtLeast(2)
    val valid = readings.filter { it > 0 } + readings2.filter { it > 0 }
    val minHr = (valid.minOrNull() ?: 60) - 10
    val maxHr = (valid.maxOrNull() ?: 180) + 10
    val range = (maxHr - minHr).coerceAtLeast(1)

    Canvas(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        val labelSpace = 14.dp.toPx()
        val width = size.width
        val height = size.height - labelSpace
        fun yOf(hr: Float) = height - ((hr - minHr) / range * height)

        for (i in 0..5) {
            val y = height * i / 5
            drawLine(
                color = Color.White.copy(alpha = if (i == 0 || i == 5) 0.3f else 0.15f),
                start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1f
            )
        }

        // Marcas de tiempo en el eje X (cada 1/2/5/10/15/30/60 min según duración)
        val totalMin = totalSeconds / 60f
        val stepMin = listOf(1, 2, 5, 10, 15, 30, 60).firstOrNull { totalMin / it <= 6 } ?: 120
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(140, 255, 255, 255)
            textSize = 10.sp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        var m = stepMin
        while (m * 60 < totalSeconds) {
            val x = m * 60f / (totalSeconds - 1) * width
            drawLine(Color.White.copy(alpha = 0.1f), Offset(x, 0f), Offset(x, height), 1f)
            val label = if (m >= 60) "${m / 60}h${if (m % 60 > 0) "%02d".format(m % 60) else ""}" else "${m}'"
            drawContext.canvas.nativeCanvas.drawText(label, x, size.height, paint)
            m += stepMin
        }

        // Reduce a 1 columna cada ~2 px: línea con la media y banda con mín/máx del tramo,
        // así 2 h de sesión (7200 muestras) se dibujan igual de fluidas que 5 min
        fun drawSeries(data: List<Int>, color: Color) {
            if (data.none { it > 0 }) return
            val buckets = (width / 2f).toInt().coerceAtLeast(1)
            val perBucket = (totalSeconds.toFloat() / buckets).coerceAtLeast(1f)
            val path = Path()
            var started = false
            var b = 0
            while (b * perBucket < data.size) {
                val from = (b * perBucket).toInt()
                val to = minOf(data.size, ((b + 1) * perBucket).toInt().coerceAtLeast(from + 1))
                var sum = 0; var n = 0; var lo = Int.MAX_VALUE; var hi = 0
                for (i in from until to) {
                    val v = data[i]
                    if (v > 0) { sum += v; n++; if (v < lo) lo = v; if (v > hi) hi = v }
                }
                val x = (from + to - 1) / 2f / (totalSeconds - 1) * width
                if (n > 0) {
                    if (hi > lo) drawLine(color.copy(alpha = 0.35f), Offset(x, yOf(hi.toFloat())), Offset(x, yOf(lo.toFloat())), 2f)
                    val y = yOf(sum.toFloat() / n)
                    if (started) path.lineTo(x, y) else { path.moveTo(x, y); started = true }
                } else {
                    started = false // hueco sin datos: cortar la línea
                }
                b++
            }
            drawPath(path = path, color = color, style = Stroke(width = 4f))

            val last = data.indexOfLast { it > 0 }
            val center = Offset(last.toFloat() / (totalSeconds - 1) * width, yOf(data[last].toFloat()))
            drawCircle(color = color, radius = 10f, center = center)
            drawCircle(color = Color.White, radius = 5f, center = center)
        }

        drawSeries(readings, TabataColors.HrPink)
        drawSeries(readings2, TabataColors.HrCyan)
    }
}

@Composable
private fun PageIndicators(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (index == currentPage) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (index == currentPage) Color.White else Color.White.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
private fun SwipeHint(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.ChevronLeft, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Text("GPS", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
    }
}

@Composable
private fun FinishedDialog(stats: SessionStats, gpsEnabled: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF252525),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFD700), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("¡Entrenamiento Completado!", textAlign = TextAlign.Center, color = Color.White)
            }
        },
        text = {
            Column {
                Text("Resumen:", fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("⏱ Tiempo: ${stats.totalTimeSeconds / 60}:${String.format("%02d", stats.totalTimeSeconds % 60)}", color = Color.White.copy(alpha = 0.9f))
                Text("🔄 Rondas: ${stats.completedRounds}/${stats.totalRounds}", color = Color.White.copy(alpha = 0.9f))
                if (stats.avgHeartRate > 0) {
                    Text("❤️ HR Media: ${stats.avgHeartRate} bpm", color = Color.White.copy(alpha = 0.9f))
                    Text("📈 HR Máx: ${stats.maxHeartRate} bpm", color = Color.White.copy(alpha = 0.9f))
                }
                if (stats.avgCadence > 0) {
                    Text("🚴 Cadencia: ${String.format("%.0f", stats.avgCadence)} rpm", color = Color.White.copy(alpha = 0.9f))
                }
                if (gpsEnabled && stats.totalDistanceMeters > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("📍 GPS:", fontWeight = FontWeight.Bold, color = Color.White)
                    Text("🛣️ Distancia: ${String.format("%.2f", stats.totalDistanceMeters / 1000f)} km", color = Color.White.copy(alpha = 0.9f))
                    Text("⚡ Vel. Media: ${String.format("%.1f", stats.avgSpeedKmh)} km/h", color = Color.White.copy(alpha = 0.9f))
                    Text("🚀 Vel. Máx: ${String.format("%.1f", stats.maxSpeedKmh)} km/h", color = Color.White.copy(alpha = 0.9f))
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = TabataColors.WorkGreen)) {
                Text("Aceptar", fontWeight = FontWeight.Bold)
            }
        }
    )
}

// ============================================================================
// UTILIDADES
// ============================================================================

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) String.format("%d:%02d", mins, secs) else String.format("%02d", secs)
}

private fun phaseGradient(phase: WorkoutPhase): List<Color> = when (phase) {
    WorkoutPhase.WARMUP -> listOf(TabataColors.WarmupOrange, TabataColors.WarmupOrange.copy(alpha = 0.85f))
    WorkoutPhase.WORK -> listOf(TabataColors.WorkGreen, TabataColors.WorkGreen.copy(alpha = 0.85f))
    WorkoutPhase.REST -> listOf(TabataColors.RestRed, TabataColors.RestRed.copy(alpha = 0.85f))
    WorkoutPhase.FINISHED -> listOf(TabataColors.RoundsBlue, TabataColors.RoundsBlue.copy(alpha = 0.85f))
    else -> listOf(TabataColors.CoolGray, TabataColors.CoolGray.copy(alpha = 0.85f))
}

private val WorkoutPhase.isActive: Boolean
    get() = this != WorkoutPhase.IDLE && this != WorkoutPhase.FINISHED