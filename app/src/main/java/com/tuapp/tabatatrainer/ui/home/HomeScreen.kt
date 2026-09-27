// HomeScreen.kt - Layout responsive (spec 004): móvil/tablet × vertical/horizontal
// Fila 1: RUTA LIBRE | TABATA · Fila 2: Última actividad | Esta semana · Fila 3: Historial | Perfil | Cerrar
package com.tuapp.tabatatrainer.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tuapp.tabatatrainer.ui.config.BleScanStatus
import com.tuapp.tabatatrainer.sensor.SensorBattery
import com.tuapp.tabatatrainer.ui.components.BatteryBadge
import com.tuapp.tabatatrainer.ui.components.rememberSensorBatteries
import com.tuapp.tabatatrainer.ui.config.ConfigViewModel
import android.app.Activity
import android.content.Intent
import com.tuapp.tabatatrainer.service.FreeRideService
import com.tuapp.tabatatrainer.service.WorkoutService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Paleta Nike
private val NikeOrange = Color(0xFFFF6B35)
private val NikeGray = Color(0xFF1A1A1A)
private val NikeGrayLight = Color(0xFF2D2D2D)
private val SurfaceCard = Color(0xFF252525)
private val TextSecondary = Color(0xFFAAAAAA)
private val FreeRideGreen = Color(0xFF4CAF50)
private val HeartPink = Color(0xFFE91E63)
private val StatsBlue = Color(0xFF2196F3)
private val DangerRed = Color(0xFFE53935)

/** Medidas que cambian entre móvil y tablet */
private data class HomeDims(
    val pad: Dp,
    val gap: Dp,
    val titleSize: TextUnit,
    val cardTitle: TextUnit,
    val cardSubtitle: TextUnit,
    val cardIcon: Dp,
    val statValue: TextUnit,
    val isTablet: Boolean
)

private val PhoneDims = HomeDims(16.dp, 12.dp, 22.sp, 20.sp, 12.sp, 52.dp, 16.sp, false)
private val TabletDims = HomeDims(32.dp, 20.dp, 32.sp, 32.sp, 17.sp, 88.dp, 24.sp, true)

/** Callbacks agrupados para no arrastrarlos uno a uno por cada layout */
private class HomeActions(
    val onRefresh: () -> Unit,
    val onStartFreeRide: () -> Unit,
    val onStartTabata: () -> Unit,
    val onNavigateToHistory: () -> Unit,
    val onNavigateToSettings: () -> Unit,
    val onCloseApp: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartFreeRide: () -> Unit,
    onStartTabata: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ConfigViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val bleStatus by viewModel.bleStatus.collectAsState()
    val summary by homeViewModel.summary.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.startScanning() }

    val actions = HomeActions(
        onRefresh = { viewModel.refreshSensors() },
        onStartFreeRide = onStartFreeRide,
        onStartTabata = onStartTabata,
        onNavigateToHistory = onNavigateToHistory,
        onNavigateToSettings = onNavigateToSettings,
        onCloseApp = {
            // Detener servicios antes de cerrar
            context.stopService(Intent(context, WorkoutService::class.java))
            context.stopService(Intent(context, FreeRideService::class.java))
            // Cerrar la app
            (context as? Activity)?.finishAffinity()
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NikeGray)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            val isLandscape = maxWidth > maxHeight
            // Tablet = el lado corto del área útil mide >= 600dp
            val isTablet = minOf(maxWidth, maxHeight) >= 600.dp
            val dims = if (isTablet) TabletDims else PhoneDims

            if (isLandscape) {
                LandscapeLayout(bleStatus, summary, actions, dims, compactHeight = maxHeight < 480.dp)
            } else {
                // Con poco alto no se pueden estirar las tarjetas: se usa scroll
                PortraitLayout(bleStatus, summary, actions, dims, fillHeight = maxHeight >= 620.dp)
            }
        }
    }
}

@Composable
private fun PortraitLayout(
    bleStatus: BleScanStatus,
    summary: HomeSummary,
    actions: HomeActions,
    dims: HomeDims,
    fillHeight: Boolean
) {
    val base = Modifier.fillMaxSize()
    Column(
        modifier = (if (fillHeight) base else base.verticalScroll(rememberScrollState()))
            .padding(dims.pad),
        verticalArrangement = Arrangement.spacedBy(dims.gap)
    ) {
        HeaderRow(dims)
        SensorBar(bleStatus, actions.onRefresh)
        SectionTitle("Elige tu actividad", dims)

        // Fila 1: las actividades se reparten el alto sobrante (sin huecos abajo)
        ActivityRow(
            dims, actions,
            if (fillHeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(240.dp)
        )
        // Fila 2: resumen
        SummaryRow(summary, dims, actions.onNavigateToHistory)
        // Fila 3: acciones
        ActionBar(actions, dims)
    }
}

@Composable
private fun LandscapeLayout(
    bleStatus: BleScanStatus,
    summary: HomeSummary,
    actions: HomeActions,
    dims: HomeDims,
    compactHeight: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(dims.pad),
        horizontalArrangement = Arrangement.spacedBy(dims.pad)
    ) {
        // Panel lateral: cabecera y sensores arriba, resumen y acciones abajo.
        // En móvil horizontal (poco alto) hace scroll.
        val sideBase = Modifier.weight(if (dims.isTablet) 0.42f else 0.46f).fillMaxHeight()
        Column(
            modifier = if (compactHeight) sideBase.verticalScroll(rememberScrollState()) else sideBase,
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 8.dp else dims.gap)
        ) {
            HeaderRow(dims)
            SensorBar(bleStatus, actions.onRefresh)
            if (!compactHeight) Spacer(Modifier.weight(1f))
            SummaryRow(summary, dims, actions.onNavigateToHistory)
            ActionBar(actions, dims)
        }

        // Actividades lado a lado ocupando todo el alto disponible
        Column(
            modifier = Modifier.weight(if (dims.isTablet) 0.58f else 0.54f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(dims.gap)
        ) {
            if (!compactHeight) SectionTitle("Elige tu actividad", dims)
            ActivityRow(dims, actions, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun SectionTitle(text: String, dims: HomeDims) {
    Text(text, fontSize = dims.titleSize, fontWeight = FontWeight.Bold, color = Color.White)
}

@Composable
private fun ActivityRow(dims: HomeDims, actions: HomeActions, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(dims.gap)) {
        val cardMod = Modifier.weight(1f).fillMaxHeight()
        ActivityCard(
            title = "RUTA LIBRE",
            subtitle = "GPS • Sensores • Sin límites",
            icon = Icons.Outlined.Explore,
            accentColor = FreeRideGreen,
            dims = dims,
            modifier = cardMod,
            onClick = actions.onStartFreeRide
        )
        ActivityCard(
            title = "TABATA",
            subtitle = "Intervalos • HIIT • Indoor/Outdoor",
            icon = Icons.Outlined.FitnessCenter,
            accentColor = NikeOrange,
            dims = dims,
            modifier = cardMod,
            onClick = actions.onStartTabata
        )
    }
}

@Composable
private fun HeaderRow(dims: HomeDims) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "TABATA TRAINER",
            fontSize = if (dims.isTablet) 14.sp else 12.sp,
            fontWeight = FontWeight.Medium,
            color = NikeOrange,
            letterSpacing = 3.sp
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "S. CELIS",
                fontSize = if (dims.isTablet) 28.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "v${appVersionName()}",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

/** versionName del APK instalado (lo pone Gradle desde version.properties) */
@Composable
private fun appVersionName(): String {
    val context = LocalContext.current
    return remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }
}

@Composable
private fun SensorBar(
    bleStatus: BleScanStatus,
    onRefresh: () -> Unit
) {
    val batteries = rememberSensorBatteries()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // HR1 / C1 arriba, HR2 / C2 abajo, cada uno con su batería (spec 005 T-05)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                SensorWithBattery("HR1", bleStatus.hrConnected, HeartPink, batteries[0])
                SensorWithBattery("C1", bleStatus.cadenceConnected, StatsBlue, batteries[2])
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                // Sin flujo propio de HR2/C2 en la Home: se marca conectado si ya hay dato de batería
                SensorWithBattery("HR2", batteries[1] != null, HeartPink, batteries[1])
                SensorWithBattery("C2", batteries[3] != null, StatsBlue, batteries[3])
            }
        }

        SensorDot("GPS", bleStatus.gpsAvailable, FreeRideGreen)

        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Refresh,
                "Refrescar",
                tint = NikeOrange,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SensorWithBattery(label: String, connected: Boolean, color: Color, battery: SensorBattery?) {
    Row(
        modifier = Modifier.widthIn(min = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SensorDot(label, connected, color)
        BatteryBadge(battery, fontSize = 11.sp)
    }
}

@Composable
private fun SensorDot(label: String, connected: Boolean, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (connected) color else Color.Gray.copy(alpha = 0.4f))
        )
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (connected) Color.White else TextSecondary
        )
    }
}

/** Tarjeta de actividad: ocupa todo el espacio de [modifier]; icono arriba, botón abajo */
@Composable
private fun ActivityCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    dims: HomeDims,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "scale"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                pressed = true
                onClick()
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(accentColor.copy(alpha = 0.18f), SurfaceCard, NikeGrayLight)
                    )
                )
                .padding(if (dims.isTablet) 24.dp else 14.dp)
        ) {
            // Icono de fondo grande en la esquina: rellena la tarjeta sin recargarla
            Icon(
                icon,
                null,
                tint = accentColor.copy(alpha = 0.10f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxHeight(0.6f)
                    .aspectRatio(1f)
            )
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .size(dims.cardIcon)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(dims.cardIcon / 2))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    title,
                    fontSize = dims.cardTitle,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    subtitle,
                    fontSize = dims.cardSubtitle,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(if (dims.isTablet) 16.dp else 10.dp))
                StartButton(accentColor, dims)
            }
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(100)
            pressed = false
        }
    }
}

@Composable
private fun StartButton(accentColor: Color, dims: HomeDims) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accentColor, RoundedCornerShape(12.dp))
            .padding(vertical = if (dims.isTablet) 16.dp else 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "INICIAR",
            fontSize = if (dims.isTablet) 16.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// ---------- Fila 2: resumen ----------

@Composable
private fun SummaryRow(summary: HomeSummary, dims: HomeDims, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(dims.gap)
    ) {
        val mod = Modifier.weight(1f).fillMaxHeight()
        LastActivityCard(summary.last, dims, mod, onClick)
        WeeklyStatsCard(summary.week, dims, mod, onClick)
    }
}

@Composable
private fun SummaryCard(
    title: String,
    icon: ImageVector,
    tint: Color,
    dims: HomeDims,
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .clickable(onClick = onClick)
            .padding(if (dims.isTablet) 16.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
                letterSpacing = 1.sp,
                maxLines = 1
            )
        }
        content()
    }
}

@Composable
private fun LastActivityCard(
    last: LastActivitySummary?,
    dims: HomeDims,
    modifier: Modifier,
    onClick: () -> Unit
) {
    SummaryCard("Última actividad", Icons.Outlined.Timer, NikeOrange, dims, modifier, onClick) {
        if (last == null) {
            Text("Sin actividades todavía", fontSize = 13.sp, color = TextSecondary)
            return@SummaryCard
        }
        Text(
            if (last.isFreeRide) "Ruta libre" else "Tabata",
            fontSize = dims.statValue,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
        Text(formatWhen(last.startTime), fontSize = 12.sp, color = TextSecondary, maxLines = 1)
        val detail = buildList {
            add(formatDuration(last.durationSeconds))
            if (last.isFreeRide) last.distanceMeters?.takeIf { it > 0f }?.let { add(formatKm(it)) }
            else add("${last.completedRounds}/${last.totalRounds} rondas")
            last.avgHeartRate?.let { add("♥ $it") }
        }.joinToString(" · ")
        Text(detail, fontSize = 12.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WeeklyStatsCard(
    week: WeeklySummary,
    dims: HomeDims,
    modifier: Modifier,
    onClick: () -> Unit
) {
    SummaryCard("Esta semana", Icons.Outlined.BarChart, StatsBlue, dims, modifier, onClick) {
        Row(Modifier.fillMaxWidth()) {
            StatCell("${week.sessions}", "sesiones", dims, Modifier.weight(1f))
            StatCell(formatDuration(week.totalSeconds), "tiempo", dims, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            StatCell(formatKm(week.distanceMeters), "distancia", dims, Modifier.weight(1f))
            StatCell(week.avgHeartRate?.let { "$it" } ?: "--", "FC media", dims, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, dims: HomeDims, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontSize = dims.statValue, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        Text(label, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "${h}h ${"%02d".format(m)}m" else "%d:%02d".format(m, s)
}

private fun formatKm(meters: Float): String = "%.1f km".format(meters / 1000f)

private val TimeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val DateFmt = DateTimeFormatter.ofPattern("dd/MM")

private fun formatWhen(millis: Long): String {
    val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val day = when (dt.toLocalDate()) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> dt.format(DateFmt)
    }
    return "$day · ${dt.format(TimeFmt)}"
}

// ---------- Fila 3: acciones ----------

@Composable
private fun ActionBar(actions: HomeActions, dims: HomeDims) {
    var showCloseDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(dims.gap)
    ) {
        val mod = Modifier.weight(1f).fillMaxHeight()
        ActionButton(Icons.Outlined.History, "Historial", Color.White, mod, actions.onNavigateToHistory)
        ActionButton(Icons.Outlined.Person, "Perfil", Color.White, mod, actions.onNavigateToSettings)
        ActionButton(Icons.AutoMirrored.Filled.ExitToApp, "Cerrar", DangerRed, mod) { showCloseDialog = true }
    }

    if (showCloseDialog) {
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = {
                Text(
                    "Cerrar aplicación",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    "¿Estás seguro de que deseas cerrar la aplicación? Se detendrán todos los servicios activos.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloseDialog = false
                        actions.onCloseApp()
                    }
                ) {
                    Text(
                        "Cerrar",
                        color = DangerRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tint, maxLines = 1)
    }
}
