package com.tuapp.tabatatrainer.ui.freeride

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.IBinder
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.tuapp.tabatatrainer.service.FreeRideService
import com.tuapp.tabatatrainer.service.FreeRideStats
import com.tuapp.tabatatrainer.service.LapData
import com.tuapp.tabatatrainer.service.MapPoint
import com.tuapp.tabatatrainer.ui.components.BatteryBadge
import com.tuapp.tabatatrainer.ui.components.BatteryLowWarnings
import com.tuapp.tabatatrainer.ui.components.rememberSensorBatteries
import com.tuapp.tabatatrainer.ui.theme.TabataColors
import com.tuapp.tabatatrainer.ui.theme.TabataSizes

// ============================================================================
// PANTALLA PRINCIPAL FREE RIDE
// ============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FreeRideScreen(
    onFinish: () -> Unit,
    onViewSession: (String) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Usar screenWidthDp para detectar orientación de forma más confiable
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    BatteryLowWarnings()

    var service by remember { mutableStateOf<FreeRideService?>(null) }
    var isBound by remember { mutableStateOf(false) }

    val stats by service?.stats?.collectAsState() ?: remember { mutableStateOf(FreeRideStats()) }
    val laps by service?.laps?.collectAsState() ?: remember { mutableStateOf(emptyList<LapData>()) }
    val mapPoints by service?.mapPoints?.collectAsState() ?: remember { mutableStateOf(emptyList<MapPoint>()) }

    val pagerState = rememberPagerState(pageCount = { 3 })

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val localBinder = binder as FreeRideService.LocalBinder
                service = localBinder.getService()
                isBound = true
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                isBound = false
            }
        }
    }

    LaunchedEffect(Unit) {
        Intent(context, FreeRideService::class.java).also { intent ->
            context.startForegroundService(intent)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isBound) context.unbindService(connection)
        }
    }

    // Callbacks
    val onStart: () -> Unit = { service?.startRide() }
    val onPause: () -> Unit = { service?.pauseRide() }
    val onResume: () -> Unit = { service?.resumeRide() }
    val onStop: () -> Unit = {
        val id = stats.sessionId
        service?.stopRide(onSaved = { id?.let { onViewSession(it) } })
    }
    val onLap: () -> Unit = { service?.addLap() }
    val onReconnect: () -> Unit = { service?.reconnectSensors() }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(TabataColors.CoolGray, Color(0xFF2D2D2D), TabataColors.CoolGray)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        // Tanto en Portrait como Landscape: 3 páginas con swipe
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    if (isLandscape) {
                        LandscapeMetricsPage(stats, laps, onStart, onPause, onResume, onStop, onLap, onReconnect)
                    } else {
                        MetricsPage(stats, onStart, onPause, onResume, onStop, onLap, onReconnect)
                    }
                }
                1 -> MapPage(stats, mapPoints, onPause, onResume)
                2 -> {
                    if (isLandscape) {
                        LandscapeLapsPage(stats, laps, onStart, onPause, onResume, onStop, onLap)
                    } else {
                        LapsPage(stats, laps, onLap)
                    }
                }
            }
        }

        // Indicadores de página
        PageIndicatorBar(
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )

        // Botón volver
        IconButton(
            onClick = onFinish,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
        }
    }
}

// ============================================================================
// PÁGINA 1: MÉTRICAS
// ============================================================================

@Composable
private fun MetricsPage(
    stats: FreeRideStats,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onLap: () -> Unit,
    onReconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(TabataSizes.PaddingMedium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Barra de sensores
        FreeRideSensorBar(stats = stats, onReconnect = onReconnect)

        Spacer(modifier = Modifier.height(16.dp))

        // Estado
        StatusLabel(stats = stats)

        // Timer grande
        Text(
            text = formatDuration(stats.totalTimeSeconds),
            fontSize = 90.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Grid de métricas (SIN ICONOS, DATOS MÁS GRANDES)
        // ⚡ SOLO: Velocidad, Distancia, HR, Cadencia (SIN Altitud ni Desnivel)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LapMetricCardNoIcon(
                    value = if (stats.currentSpeedKmh > 0.5f) String.format("%.1f", stats.currentSpeedKmh) else "--",
                    unit = "km/h",
                    label = "Velocidad",
                    modifier = Modifier.weight(1f)
                )
                LapMetricCardNoIcon(
                    value = String.format("%.2f", stats.totalDistanceMeters / 1000f),
                    unit = "km",
                    label = "Distancia",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LapMetricCardNoIcon(
                    value = if (stats.heartRate > 0) "${stats.heartRate}" else "--",
                    unit = "bpm",
                    label = "HR",
                    valueColor = if (stats.isHrConnected) Color(0xFFE91E63) else Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                LapMetricCardNoIcon(
                    value = if (stats.cadence > 0) String.format("%.0f", stats.cadence) else "--",
                    unit = "rpm",
                    label = "Cadencia",
                    modifier = Modifier.weight(1f)
                )
            }
            // Podómetro del móvil: solo si hay pasos (franja compacta para que quepa)
            if (stats.steps > 0) StepsStrip(stats)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Controles (MÁS ABAJO)
        FreeRideControls(stats, onStart, onPause, onResume, onStop, onLap)

        Spacer(modifier = Modifier.height(60.dp))
    }
}

// ============================================================================
// PÁGINA 2: MAPA
// ============================================================================

@Composable
private fun MapPage(
    stats: FreeRideStats,
    mapPoints: List<MapPoint>,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        if (mapPoints.isEmpty() && stats.latitude == 0.0) {
            MapPlaceholder()
        } else {
            RealTimeGoogleMap(
                mapPoints = mapPoints,
                currentLatitude = stats.latitude,
                currentLongitude = stats.longitude,
                isTracking = stats.isRunning && !stats.isPaused
            )
        }

        // Overlay GPS status
        GpsStatusChip(
            isConnected = stats.isGpsConnected,
            accuracy = stats.gpsAccuracy,
            latitude = stats.latitude,
            longitude = stats.longitude,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        // Overlay métricas inferior
        MapMetricsOverlay(
            speed = stats.currentSpeedKmh,
            time = stats.totalTimeSeconds,
            distance = stats.totalDistanceMeters,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )

        // FAB pausa/resume
        if (stats.isRunning) {
            FloatingActionButton(
                onClick = { if (stats.isPaused) onResume() else onPause() },
                containerColor = if (stats.isPaused) TabataColors.WorkGreen else TabataColors.VibrantOrange,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 100.dp)
            ) {
                Icon(
                    if (stats.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun RealTimeGoogleMap(
    mapPoints: List<MapPoint>,
    currentLatitude: Double,
    currentLongitude: Double,
    isTracking: Boolean
) {
    val routeCoordinates = remember(mapPoints) {
        mapPoints.map { LatLng(it.latitude, it.longitude) }
    }

    val currentLocation = remember(currentLatitude, currentLongitude) {
        if (currentLatitude != 0.0) LatLng(currentLatitude, currentLongitude) else null
    }

    // ⚡ Preservar zoom y posición al cambiar de orientación
    var savedZoom by rememberSaveable { mutableStateOf(16f) }
    var savedPosition by rememberSaveable { mutableStateOf<LatLng?>(null) }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            savedPosition ?: currentLocation ?: LatLng(43.3, -2.98),
            savedZoom
        )
    }

    // ⚡ Seguir ubicación actual si está en tracking
    LaunchedEffect(currentLocation, isTracking) {
        if (isTracking && currentLocation != null) {
            savedPosition = currentLocation
            cameraPositionState.animate(CameraUpdateFactory.newLatLng(currentLocation), durationMs = 500)
        }
    }
    
    // ⚡ Guardar zoom cuando cambia (solo si no está en tracking para no interferir)
    LaunchedEffect(cameraPositionState.position.zoom) {
        if (!isTracking) {
            savedZoom = cameraPositionState.position.zoom
        }
    }

    val mapProperties = remember {
        MapProperties(mapType = MapType.NORMAL, isMyLocationEnabled = false, isBuildingEnabled = true)
    }

    val mapUiSettings = remember {
        MapUiSettings(zoomControlsEnabled = true, compassEnabled = true, myLocationButtonEnabled = false, mapToolbarEnabled = false)
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = mapUiSettings
    ) {
        if (routeCoordinates.size >= 2) {
            Polyline(points = routeCoordinates, color = TabataColors.RoundsBlue, width = 10f)
        }

        if (routeCoordinates.isNotEmpty()) {
            Marker(
                state = MarkerState(position = routeCoordinates.first()),
                title = "Inicio",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
            )
        }

        currentLocation?.let { loc ->
            Marker(
                state = MarkerState(position = loc),
                title = "Tu posición",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            )
        }
    }
}

@Composable
private fun MapPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TabataColors.CoolGray),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val infiniteTransition = rememberInfiniteTransition(label = "gps")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing)),
                label = "rotation"
            )

            Icon(
                Icons.Default.Explore,
                contentDescription = null,
                tint = TabataColors.VibrantOrange,
                modifier = Modifier
                    .size(64.dp)
                    .rotate(rotation)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Buscando señal GPS...", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text("Inicia la actividad para ver el mapa", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun GpsStatusChip(
    isConnected: Boolean,
    accuracy: Float?,
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.GpsFixed,
            contentDescription = null,
            tint = if (isConnected) TabataColors.Connected else TabataColors.Scanning,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                if (isConnected) "GPS Conectado" else "Buscando GPS...",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            if (isConnected && latitude != 0.0) {
                Text(
                    "${String.format("%.5f", latitude)}, ${String.format("%.5f", longitude)}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
            accuracy?.let {
                Text("Precisión: ±${String.format("%.0f", it)}m", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun MapMetricsOverlay(speed: Float, time: Int, distance: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        OverlayMetric(if (speed > 0.5f) String.format("%.1f", speed) else "--", "km/h", "Velocidad")
        OverlayMetric(formatDuration(time), "", "Tiempo")
        OverlayMetric(String.format("%.2f", distance / 1000f), "km", "Distancia")
    }
}

@Composable
private fun OverlayMetric(value: String, unit: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (unit.isNotEmpty()) {
                Text(" $unit", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

// ============================================================================
// PÁGINA 3: VUELTAS/LAPS
// ============================================================================

@Composable
private fun LapsPage(stats: FreeRideStats, laps: List<LapData>, onLap: () -> Unit) {
    var isLapHistoryExpanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(TabataSizes.PaddingMedium)
    ) {
        // ============================================================
        // 1. DATOS SIEMPRE VISIBLES (SIN ICONOS, FORMATO PÁGINA 1)
        // ============================================================
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Fila 1: Nº Vuelta y HR (intercambiado: HR ahora en top-right)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LapMetricCardNoIcon(
                    value = "${laps.size + 1}",
                    unit = "",
                    label = "Vuelta",
                    valueColor = TabataColors.VibrantOrange,
                    modifier = Modifier.weight(1f)
                )
                LapMetricCardNoIcon(
                    value = if (stats.heartRate > 0) "${stats.heartRate}" else "--",
                    unit = "bpm",
                    label = "HR",
                    valueColor = if (stats.isHrConnected) Color(0xFFE91E63) else Color.Gray,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Fila 2: Distancia y Velocidad
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LapMetricCardNoIcon(
                    value = String.format("%.2f", stats.currentLapDistanceMeters / 1000f),
                    unit = "km",
                    label = "Distancia",
                    modifier = Modifier.weight(1f)
                )
                LapMetricCardNoIcon(
                    value = if (stats.currentSpeedKmh > 0.5f) String.format("%.1f", stats.currentSpeedKmh) else "--",
                    unit = "km/h",
                    label = "Velocidad",
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Fila 3: Tiempo (OCUPA TODA LA FILA - ancho completo)
            LapMetricCardNoIcon(
                value = formatDuration(stats.currentLapTimeSeconds),
                unit = "",
                label = "Tiempo",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // ============================================================
        // 2. BOTÓN NUEVA VUELTA
        // ============================================================
        if (stats.isRunning && !stats.isPaused) {
            Button(
                onClick = onLap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Flag, null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("NUEVA VUELTA", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============================================================
        // 3. DESPLEGABLE CON HISTORIAL DE VUELTAS (SCROLLABLE)
        // ============================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                // Header clickeable
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isLapHistoryExpanded = !isLapHistoryExpanded }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Historial de Vueltas",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Icon(
                        if (isLapHistoryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isLapHistoryExpanded) "Contraer" else "Expandir",
                        tint = Color.White
                    )
                }

                // Contenido expandible con scroll
                androidx.compose.animation.AnimatedVisibility(
                    visible = isLapHistoryExpanded,
                    enter = expandVertically(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        if (laps.isNotEmpty()) {
                            // Header de la tabla
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("#", Modifier.width(30.dp), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                Text("Tiempo", Modifier.weight(1f), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                Text("Dist.", Modifier.weight(1f), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                Text("Vel.", Modifier.weight(1f), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                Text("HR", Modifier.weight(1f), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            }

                            // Lista scrollable de vueltas
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 350.dp)
                            ) {
                                items(laps.reversed()) { lap ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (lap.number % 2 == 0) Color.Black.copy(alpha = 0.2f) else Color.Transparent)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.width(50.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "${lap.number}",
                                                color = if (lap.isAutoLap) TabataColors.WorkGreen else TabataColors.VibrantOrange,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            if (lap.isAutoLap) {
                                                Text(
                                                    text = "⚡",
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }

                                        Text(
                                            text = formatDuration((lap.endTimeSeconds - lap.startTimeSeconds)),
                                            modifier = Modifier.weight(1f),
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )

                                        Text(
                                            text = String.format("%.2f", lap.distanceMeters / 1000f),
                                            modifier = Modifier.weight(1f),
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            String.format("%.1f", lap.avgSpeedKmh),
                                            Modifier.weight(1f),
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            if (lap.avgHeartRate > 0) "${lap.avgHeartRate}" else "--",
                                            Modifier.weight(1f),
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                "No hay vueltas registradas",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }

    }
}

/** Pasos, pasos/min y zancada en una sola tarjeta baja */
@Composable
private fun StepsStrip(stats: FreeRideStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            StepsStripItem("${stats.steps}", "", "Pasos", Modifier.weight(1f))
            StepsStripItem(if (stats.stepCadenceSpm > 0) "${stats.stepCadenceSpm}" else "--", "ppm", "Pasos/min", Modifier.weight(1f))
            StepsStripItem(if (stats.avgStrideM > 0f) String.format("%.2f", stats.avgStrideM) else "--", "m", "Zancada", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StepsStripItem(value: String, unit: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            if (unit.isNotEmpty()) Text(" $unit", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        }
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun LapMetricCardNoIcon(
    value: String,
    unit: String,
    label: String,
    valueColor: Color = Color.White,
    valueFontSize: TextUnit = 55.sp,  // ⚡ Tamaño de fuente configurable
    modifier: Modifier = Modifier
) {
    // ⚡ SIN ICONOS - DATOS MÁS GRANDES (formato página 1 sin iconos)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),  // ⚡ Más padding para datos más grandes
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ⚡ SIN ICONO - Eliminado completamente para hacer datos más grandes
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontSize = valueFontSize,  // ⚡ Tamaño configurable
                    fontWeight = FontWeight.Bold,
                    color = valueColor
                )
                if (unit.isNotEmpty()) {
                    Text(
                        " $unit",
                        fontSize = 16.sp,  // ⚡ Unidad también más grande
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                label,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun LapMetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun TotalItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
    }
}

// ============================================================================
// LANDSCAPE LAYOUTS
// ============================================================================

@Composable
private fun LandscapeMetricsPage(
    stats: FreeRideStats,
    laps: List<LapData>,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onLap: () -> Unit,
    onReconnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(8.dp)
    ) {
        // Izquierda: Timer y controles (35%)
        Column(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Sensores compactos
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SensorDot(Icons.Default.GpsFixed, stats.isGpsConnected, stats.isGpsScanning, TabataColors.WorkGreen)
                SensorDot(Icons.Default.Favorite, stats.isHrConnected, stats.isHrScanning, Color(0xFFE91E63))
                SensorDot(Icons.AutoMirrored.Filled.DirectionsBike, stats.isCadenceConnected, stats.isCadenceScanning, TabataColors.RoundsBlue)
            }

            // Timer (MÁS GRANDE en horizontal)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StatusLabel(stats = stats, fontSize = 12)
                Text(formatDuration(stats.totalTimeSeconds), fontSize = 70.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            // Controles compactos
            FreeRideControlsCompact(stats, onStart, onPause, onResume, onStop, onLap)
        }

        // Derecha: Métricas (65%) - SIN ICONOS, DATOS MÁS GRANDES
        // ⚡ SOLO: Velocidad, Distancia, HR, Cadencia (SIN Altitud, Desnivel, avg, laps)
        Column(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LapMetricCardNoIcon(
                    value = if (stats.currentSpeedKmh > 0.5f) String.format("%.1f", stats.currentSpeedKmh) else "--",
                    unit = "km/h",
                    label = "Velocidad",
                    modifier = Modifier.weight(1f)
                )
                LapMetricCardNoIcon(
                    value = String.format("%.2f", stats.totalDistanceMeters / 1000f),
                    unit = "km",
                    label = "Distancia",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LapMetricCardNoIcon(
                    value = if (stats.heartRate > 0) "${stats.heartRate}" else "--",
                    unit = "bpm",
                    label = "HR",
                    valueColor = if (stats.isHrConnected) Color(0xFFE91E63) else Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                LapMetricCardNoIcon(
                    value = if (stats.cadence > 0) String.format("%.0f", stats.cadence) else "--",
                    unit = "rpm",
                    label = "Cadencia",
                    modifier = Modifier.weight(1f)
                )
            }
            if (stats.steps > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                StepsStrip(stats)
            }
            // ⚡ ELIMINADAS: Altitud, Desnivel+, avg, laps (no deben aparecer)
        }
    }
}

@Composable
private fun LandscapeLapsPage(
    stats: FreeRideStats,
    laps: List<LapData>,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onLap: () -> Unit
) {
    var isLapHistoryExpanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(8.dp)
    ) {
        // ============================================================
        // IZQUIERDA: Timer y Historial (35%)
        // ============================================================
        Column(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sensores compactos
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SensorDot(Icons.Default.GpsFixed, stats.isGpsConnected, stats.isGpsScanning, TabataColors.WorkGreen)
                SensorDot(Icons.Default.Favorite, stats.isHrConnected, stats.isHrScanning, Color(0xFFE91E63))
                SensorDot(Icons.AutoMirrored.Filled.DirectionsBike, stats.isCadenceConnected, stats.isCadenceScanning, TabataColors.RoundsBlue)
            }

            // Estado "EN RUTA"
            StatusLabel(stats = stats, fontSize = 14)

            // Timer grande (tiempo total)
            Text(
                formatDuration(stats.totalTimeSeconds),
                fontSize = 70.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Desplegable Historial de Vueltas
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    // Header clickeable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isLapHistoryExpanded = !isLapHistoryExpanded }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Historial de Vueltas",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Icon(
                            if (isLapHistoryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Contenido expandible con scroll
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isLapHistoryExpanded,
                        enter = expandVertically(animationSpec = tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            if (laps.isNotEmpty()) {
                                // Header de la tabla
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text("#", Modifier.width(25.dp), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                    Text("Tiempo", Modifier.weight(1f), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                    Text("Dist.", Modifier.weight(1f), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                    Text("Vel.", Modifier.weight(1f), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                    Text("HR", Modifier.weight(1f), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                }

                                // Lista scrollable de vueltas
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    items(laps.reversed()) { lap ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (lap.number % 2 == 0) Color.Black.copy(alpha = 0.2f) else Color.Transparent)
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.width(40.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    text = "${lap.number}",
                                                    color = if (lap.isAutoLap) TabataColors.WorkGreen else TabataColors.VibrantOrange,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                if (lap.isAutoLap) {
                                                    Text(
                                                        text = "⚡",
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            }
                                            Text(
                                                text = formatDuration((lap.endTimeSeconds - lap.startTimeSeconds)),
                                                modifier = Modifier.weight(1f),
                                                color = Color.White,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = String.format("%.2f", lap.distanceMeters / 1000f),
                                                modifier = Modifier.weight(1f),
                                                color = Color.White,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                String.format("%.1f", lap.avgSpeedKmh),
                                                Modifier.weight(1f),
                                                color = Color.White,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                if (lap.avgHeartRate > 0) "${lap.avgHeartRate}" else "--",
                                                Modifier.weight(1f),
                                                color = Color.White,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    "No hay vueltas",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ============================================================
        // DERECHA: Métricas de vuelta actual y controles (65%)
        // ============================================================
        Column(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Fila superior: HR, Cadencia, Vuelta (3 tarjetas) - INTERCAMBIADAS
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LapMetricCardNoIcon(
                    value = if (stats.heartRate > 0) "${stats.heartRate}" else "--",
                    unit = "bpm",
                    label = "HR",
                    valueColor = if (stats.isHrConnected) Color(0xFFE91E63) else Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                LapMetricCardNoIcon(
                    value = if (stats.cadence > 0) String.format("%.0f", stats.cadence) else "--",
                    unit = "rpm",
                    label = "Cadencia",
                    modifier = Modifier.weight(1f)
                )
                LapMetricCardNoIcon(
                    value = "${laps.size + 1}",
                    unit = "",
                    label = "Vuelta",
                    valueColor = TabataColors.VibrantOrange,
                    valueFontSize = 40.sp,  // ⚡ Tamaño más pequeño para "Vuelta"
                    modifier = Modifier.weight(1f)
                )
            }

            // Fila media: Velocidad, Distancia (2 tarjetas) - INTERCAMBIADAS
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LapMetricCardNoIcon(
                    value = if (stats.currentSpeedKmh > 0.5f) String.format("%.1f", stats.currentSpeedKmh) else "--",
                    unit = "km/h",
                    label = "Velocidad",
                    modifier = Modifier.weight(1f)
                )
                LapMetricCardNoIcon(
                    value = String.format("%.2f", stats.currentLapDistanceMeters / 1000f),
                    unit = "km",
                    label = "Distancia",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Botón Nueva Vuelta
            if (stats.isRunning && !stats.isPaused) {
                Button(
                    onClick = onLap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Flag, null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("NUEVA VUELTA", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            // Controles compactos
            FreeRideControlsCompact(stats, onStart, onPause, onResume, onStop, onLap)
        }
    }
}

// ============================================================================
// COMPONENTES COMUNES
// ============================================================================

@Composable
private fun StatusLabel(stats: FreeRideStats, fontSize: Int = 16) {
    Text(
        text = when {
            !stats.isRunning -> "PREPARADO"
            stats.isPaused -> "PAUSADO"
            else -> "EN RUTA"
        },
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold,
        color = when {
            !stats.isRunning -> Color.White.copy(alpha = 0.6f)
            stats.isPaused -> Color.Yellow
            else -> TabataColors.WorkGreen
        },
        letterSpacing = 2.sp
    )
}

@Composable
private fun FreeRideSensorBar(stats: FreeRideStats, onReconnect: () -> Unit) {
    val batteries = rememberSensorBatteries()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SensorChipAnimated(Icons.Default.GpsFixed, "GPS", stats.isGpsConnected, stats.isGpsScanning, TabataColors.WorkGreen)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SensorChipAnimated(Icons.Default.Favorite, "HR", stats.isHrConnected, stats.isHrScanning, Color(0xFFE91E63))
            BatteryBadge(batteries[0])
            BatteryBadge(batteries[1])
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SensorChipAnimated(Icons.AutoMirrored.Filled.DirectionsBike, "CAD", stats.isCadenceConnected, stats.isCadenceScanning, TabataColors.RoundsBlue)
            BatteryBadge(batteries[2])
            BatteryBadge(batteries[3])
        }
        IconButton(onClick = onReconnect, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Refresh, "Reconectar", tint = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun SensorChipAnimated(icon: ImageVector, label: String, isConnected: Boolean, isScanning: Boolean, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            Icon(
                icon,
                contentDescription = label,
                tint = when { isConnected -> color; isScanning -> Color.Yellow; else -> Color.Gray },
                modifier = Modifier.size(20.dp)
            )
            if (isScanning) {
                val infiniteTransition = rememberInfiniteTransition(label = "scan")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse),
                    label = "alpha"
                )
                Box(modifier = Modifier
                    .size(20.dp)
                    .background(Color.Yellow.copy(alpha = alpha * 0.3f), CircleShape))
            }
        }
        Text(label, fontSize = 10.sp, color = when { isConnected -> color; isScanning -> Color.Yellow; else -> Color.Gray })
    }
}

@Composable
private fun SensorDot(icon: ImageVector, isConnected: Boolean, isScanning: Boolean, color: Color) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(
                when { isConnected -> color.copy(alpha = 0.3f); isScanning -> Color.Yellow.copy(alpha = 0.3f); else -> Color.Gray.copy(alpha = 0.2f) },
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when { isConnected -> color; isScanning -> Color.Yellow; else -> Color.Gray },
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun FreeRideMetricCard(
    icon: ImageVector,
    value: String,
    unit: String,
    label: String,
    valueColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = valueColor)
                Text(" $unit", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            }
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun CompactMetricCard(icon: ImageVector, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f))) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(" $unit", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun FreeRideControls(
    stats: FreeRideStats,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onLap: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            !stats.isRunning -> {
                FloatingActionButton(onClick = onStart, containerColor = Color.White, modifier = Modifier.size(72.dp)) {
                    Icon(Icons.Default.PlayArrow, "Iniciar", tint = TabataColors.WorkGreen, modifier = Modifier.size(40.dp))
                }
            }
            stats.isPaused -> {
                FloatingActionButton(onClick = onStop, containerColor = TabataColors.RestRed, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Stop, "Terminar", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                FloatingActionButton(onClick = onResume, containerColor = Color.White, modifier = Modifier.size(72.dp)) {
                    Icon(Icons.Default.PlayArrow, "Reanudar", tint = TabataColors.WorkGreen, modifier = Modifier.size(40.dp))
                }
            }
            else -> {
                FloatingActionButton(onClick = onStop, containerColor = TabataColors.RestRed, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.Stop, "Terminar", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                FloatingActionButton(onClick = onLap, containerColor = Color(0xFFFFD700), modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Default.Flag, "Vuelta", tint = Color.Black, modifier = Modifier.size(28.dp))
                }
                FloatingActionButton(onClick = onPause, containerColor = Color.White, modifier = Modifier.size(72.dp)) {
                    Icon(Icons.Default.Pause, "Pausar", tint = TabataColors.VibrantOrange, modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

@Composable
private fun FreeRideControlsCompact(
    stats: FreeRideStats,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onLap: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            !stats.isRunning -> {
                FloatingActionButton(onClick = onStart, containerColor = Color.White, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.PlayArrow, null, tint = TabataColors.WorkGreen, modifier = Modifier.size(32.dp))
                }
            }
            stats.isPaused -> {
                FloatingActionButton(onClick = onStop, containerColor = TabataColors.RestRed, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                FloatingActionButton(onClick = onResume, containerColor = Color.White, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.PlayArrow, null, tint = TabataColors.WorkGreen, modifier = Modifier.size(32.dp))
                }
            }
            else -> {
                FloatingActionButton(onClick = onStop, containerColor = TabataColors.RestRed, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                FloatingActionButton(onClick = onLap, containerColor = Color(0xFFFFD700), modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Flag, null, tint = Color.Black, modifier = Modifier.size(24.dp))
                }
                FloatingActionButton(onClick = onPause, containerColor = Color.White, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Pause, null, tint = TabataColors.VibrantOrange, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun PageIndicatorBar(currentPage: Int, modifier: Modifier = Modifier) {
    val labels = listOf("Métricas", "Mapa", "Vueltas")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { index ->
            val isSelected = currentPage == index
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 10.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) TabataColors.VibrantOrange else Color.White.copy(alpha = 0.4f))
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(labels[index], fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// ============================================================================
// UTILIDADES
// ============================================================================

private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}