package com.tuapp.tabatatrainer.ui.freeride

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.*
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import com.tuapp.tabatatrainer.sensor.SlotStatus
import com.tuapp.tabatatrainer.ui.components.BatteryLowWarnings
import com.tuapp.tabatatrainer.ui.components.SENSOR_SLOT_LABELS
import com.tuapp.tabatatrainer.ui.components.SensorSlotBadge
import com.tuapp.tabatatrainer.ui.components.SlotSignalState
import com.tuapp.tabatatrainer.ui.components.rememberSensorSlots
import com.tuapp.tabatatrainer.ui.components.signalState
import com.tuapp.tabatatrainer.ui.theme.TabataColors
import com.tuapp.tabatatrainer.ui.theme.TabataSizes
import com.tuapp.tabatatrainer.util.laps.CadenceDisplayPolicy
import com.tuapp.tabatatrainer.util.laps.CurrentLapModel
import com.tuapp.tabatatrainer.util.laps.HeartRateCardLayout
import com.tuapp.tabatatrainer.util.laps.HeartRateCardPolicy
import com.tuapp.tabatatrainer.util.laps.KmAlert
import com.tuapp.tabatatrainer.util.laps.LastManualLap
import com.tuapp.tabatatrainer.util.map.MapFollowPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Ventana tras pulsar Iniciar en la que se ignora el toque de Vuelta (rebote del dedo, R2) */
private const val LAP_TAP_GUARD_AFTER_START_MS = 1000L

/** Tiempo que el banner de km completado permanece en pantalla (R3) */
private const val KM_BANNER_MILLIS = 4000L

/** Ventanas del pager (R8): 1 Métricas · 2 Mapa · 3 Vueltas resumida · 4 Tabla de vueltas */
private const val PAGE_METRICS = 0
private const val PAGE_MAP = 1
private const val PAGE_LAPS = 2
private const val PAGE_TABLE = 3
private const val PAGE_COUNT = 4
// Alto que ocupa el indicador de páginas sobre el borde inferior (40 dp mínimo + 16 dp de margen)
private val PAGE_INDICATOR_CLEARANCE = 56.dp

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

    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val pagerScope = rememberCoroutineScope()

    // R3: banner "KM 3 · 5:42/km" al completar cada kilómetro (el sonido y la vibración los pone el servicio)
    var kmBanner by remember { mutableStateOf<KmAlert?>(null) }
    LaunchedEffect(service) {
        service?.kmAlerts?.collectLatest { alert ->
            kmBanner = alert
            delay(KM_BANNER_MILLIS)
            kmBanner = null
        }
    }

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
    // R2: el botón Vuelta aparece bajo el dedo justo donde estaba Iniciar; un toque
    // en el primer segundo tras iniciar es el rebote del mismo dedo, no una vuelta.
    val lastStartAtMs = remember { longArrayOf(0L) }
    val onStart: () -> Unit = {
        lastStartAtMs[0] = android.os.SystemClock.elapsedRealtime()
        service?.startRide()
    }
    val onPause: () -> Unit = { service?.pauseRide() }
    val onResume: () -> Unit = { service?.resumeRide() }
    val onStop: () -> Unit = {
        val id = stats.sessionId
        service?.stopRide(onSaved = { id?.let { onViewSession(it) } })
    }
    val onLap: () -> Unit = onLap@{
        if (android.os.SystemClock.elapsedRealtime() - lastStartAtMs[0] < LAP_TAP_GUARD_AFTER_START_MS) return@onLap
        if (service?.addLap() == false) {
            android.widget.Toast.makeText(context, "Vuelta ignorada: dura menos de 5 s", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val onReconnect: () -> Unit = { service?.reconnectSensors() }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(TabataColors.CoolGray, Color(0xFF2D2D2D), TabataColors.CoolGray)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        // Tanto en Portrait como Landscape: 4 páginas con swipe
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                PAGE_METRICS -> {
                    if (isLandscape) {
                        LandscapeMetricsPage(stats, laps, onStart, onPause, onResume, onStop, onLap, onReconnect)
                    } else {
                        MetricsPage(stats, onStart, onPause, onResume, onStop, onLap, onReconnect)
                    }
                }
                PAGE_MAP -> MapPage(stats, mapPoints, onPause, onResume)
                PAGE_LAPS -> {
                    if (isLandscape) {
                        LandscapeLapsPage(stats, laps, onStart, onPause, onResume, onStop, onLap)
                    } else {
                        LapsPage(stats, laps, onLap)
                    }
                }
                PAGE_TABLE -> LapTablePage(laps)
            }
        }

        // Indicadores de página (pulsables: salto a la ventana en un toque)
        PageIndicatorBar(
            currentPage = pagerState.currentPage,
            onSelect = { index -> pagerScope.launch { pagerState.animateScrollToPage(index) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )

        // R8 (RF-8.5): botón de vuelta manual siempre al alcance. Las ventanas 1 y 3 ya traen el suyo;
        // el flotante cubre las ventanas Mapa y Tabla, que no tienen botón de vuelta propio.
        val showFloatingLap = stats.isRunning && !stats.isPaused &&
            (pagerState.currentPage == PAGE_MAP || pagerState.currentPage == PAGE_TABLE)
        if (showFloatingLap) {
            FloatingActionButton(
                onClick = onLap,
                containerColor = Color(0xFFFFD700),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = if (pagerState.currentPage == PAGE_MAP) 100.dp else 12.dp)
            ) {
                Icon(Icons.Default.Flag, "Vuelta", tint = Color.Black)
            }
        }

        // Banner de km completado
        kmBanner?.let { alert ->
            Surface(
                color = Color(0xFFFFD700),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = alert.bannerText(),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }
        }

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
            // Las tarjetas ocupan el alto que deja libre la franja de pasos y ajustan el número a ese alto:
            // así la franja ya no queda recortada y sus etiquetas se leen enteras.
            MetricsGrid(stats, Modifier.weight(1f).fillMaxWidth())
            // Podómetro del móvil: solo si hay pasos (franja compacta para que quepa)
            if (stats.steps > 0) StepsStrip(stats)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Controles (MÁS ABAJO)
        FreeRideControls(stats, onStart, onPause, onResume, onStop, onLap)

        Spacer(modifier = Modifier.height(60.dp))
    }
}

/**
 * Velocidad y distancia arriba, pulso y cadencia abajo. Las tarjetas ocupan el alto que les da [modifier]
 * (la franja de pasos se mide antes, al no llevar weight) y el tamaño del número se ajusta a ese alto, en
 * vertical y en horizontal (spec 007, plan §8: en horizontal la franja quedaba bajo el indicador de páginas).
 */
@Composable
private fun MetricsGrid(stats: FreeRideStats, modifier: Modifier, cardPadding: Dp = 16.dp) {
    val slots = rememberSensorSlots()
    val hrLayout = rememberHeartRateCardLayout(slots)
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val rowHeight = (maxHeight - 12.dp) / 2
        // Alto de tarjeta = 2·padding + número + 4 dp + etiqueta (interlineado M3 ~24 dp)
        val valueLine = rowHeight - cardPadding * 2 - 4.dp - 24.dp
        val valueFontSize = with(density) { (valueLine / 1.3f).toSp() }
            .value.coerceIn(28f, 55f).sp
        // Fila de pulso y cadencia: cada mitad lleva debajo hueco + señal + batería (~18 dp más)
        val splitFontSize = with(density) { ((valueLine - 18.dp) / 1.3f).toSp() }
            .value.coerceIn(24f, 55f).sp
        // Tarjeta de pulso única (R7.1): la insignia va en una línea en lugar de la etiqueta y el número usa todo
        // el alto, sin el tope de 55 sp; el ancho lo ajusta el autoajuste del texto
        val singleFontSize = with(density) { (valueLine / 1.3f).toSp() }
            .value.coerceIn(28f, 96f).sp
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LapMetricCardNoIcon(
                    value = if (stats.currentSpeedKmh > 0.5f) String.format("%.1f", stats.currentSpeedKmh) else "--",
                    unit = "km/h",
                    label = "Velocidad",
                    valueFontSize = valueFontSize,
                    contentPadding = cardPadding,
                    modifier = Modifier.weight(1f)
                )
                LapMetricCardNoIcon(
                    value = String.format("%.2f", stats.totalDistanceMeters / 1000f),
                    unit = "km",
                    label = "Distancia",
                    valueFontSize = valueFontSize,
                    contentPadding = cardPadding,
                    modifier = Modifier.weight(1f)
                )
            }
            // Alto fijo de fila: las dos tarjetas igual de altas aunque una vaya partida y la otra no
            Row(
                Modifier.fillMaxWidth().height(rowHeight),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val hrModifier = Modifier.weight(1f).fillMaxHeight()
                when (hrLayout) {
                    HeartRateCardLayout.SPLIT ->
                        HeartRateSplitCard(stats, slots, hrModifier, splitFontSize, cardPadding)
                    HeartRateCardLayout.SINGLE_HR1 -> SingleSlotCard(
                        half = SlotHalf(
                            value = if (stats.heartRate > 0) "${stats.heartRate}" else "--",
                            unit = "bpm",
                            label = SENSOR_SLOT_LABELS[0],
                            color = if (stats.isHrConnected) HR1_COLOR else Color.Gray,
                            status = slots.getOrNull(0)
                        ),
                        valueFontSize = singleFontSize,
                        contentPadding = cardPadding,
                        modifier = hrModifier
                    )
                    HeartRateCardLayout.SINGLE_HR2 -> SingleSlotCard(
                        half = SlotHalf(
                            value = if (stats.heartRate2 > 0) "${stats.heartRate2}" else "--",
                            unit = "bpm",
                            label = SENSOR_SLOT_LABELS[1],
                            color = if (stats.isHr2Connected) HR2_COLOR else Color.Gray,
                            status = slots.getOrNull(1)
                        ),
                        valueFontSize = singleFontSize,
                        contentPadding = cardPadding,
                        modifier = hrModifier
                    )
                }
                CadencePaceCard(stats, slots, Modifier.weight(1f).fillMaxHeight(), splitFontSize, cardPadding)
            }
        }
    }
}

/**
 * Disposición de la tarjeta de pulso (spec 007, R7.1, plan §9): única con un solo hueco con dato reciente,
 * partida con los dos. Arranca en la deseada (sin espera al girar o volver a la página) y cada cambio debe
 * mantenerse [HeartRateCardPolicy.holdMs] para que un hueco que va y viene no la haga parpadear.
 */
@Composable
private fun rememberHeartRateCardLayout(slots: List<SlotStatus>): HeartRateCardLayout {
    val hr1Fresh = slots.getOrNull(0)?.fresh == true
    val hr2Fresh = slots.getOrNull(1)?.fresh == true
    var layout by remember { mutableStateOf(HeartRateCardPolicy.desired(hr1Fresh, hr2Fresh, HeartRateCardLayout.SPLIT)) }
    val desired = HeartRateCardPolicy.desired(hr1Fresh, hr2Fresh, layout)
    LaunchedEffect(desired) {
        if (desired == layout) return@LaunchedEffect
        delay(HeartRateCardPolicy.holdMs(layout, desired))
        layout = desired
        Log.i(
            "FreeRideScreen",
            when (desired) {
                HeartRateCardLayout.SPLIT -> "Ruta: tarjeta de pulso partida HR1 | HR2"
                HeartRateCardLayout.SINGLE_HR1 -> "Ruta: tarjeta de pulso única HR1"
                HeartRateCardLayout.SINGLE_HR2 -> "Ruta: tarjeta de pulso única HR2"
            }
        )
    }
    return layout
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

    // rememberCameraPositionState ya conserva zoom y posición al rotar (rememberSaveable)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            currentLocation ?: LatLng(43.3, -2.98),
            MapFollowPolicy.FOLLOW_ZOOM
        )
    }

    // R9: último toque del usuario en el mapa (pellizco, arrastre o botones +/-) y el zoom en que lo dejó
    var lastUserInteractionMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var userZoom by rememberSaveable { mutableStateOf<Float?>(null) }
    val reason = cameraPositionState.cameraMoveStartedReason
    val isUserMove = reason == CameraMoveStartedReason.GESTURE || reason == CameraMoveStartedReason.API_ANIMATION

    // TEMPORAL (T-11): zoom con el que (re)aparece el mapa → distingue "recreado alejado" de "alejado por gesto"
    LaunchedEffect(Unit) {
        Log.d("MapDiag", "mapa compuesto zoom=${cameraPositionState.position.zoom} userZoom=$userZoom tracking=$isTracking")
    }

    // Se marca al empezar y al terminar el gesto: los 10 s de margen cuentan desde que suelta
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!isUserMove) return@LaunchedEffect
        lastUserInteractionMs = SystemClock.elapsedRealtime()
        if (!cameraPositionState.isMoving) {
            userZoom = cameraPositionState.position.zoom
            // TEMPORAL (T-11): solo movimientos del usuario, no los del seguimiento (1 por segundo)
            Log.d("MapDiag", "usuario deja zoom=$userZoom reason=$reason tracking=$isTracking")
        }
    }

    // R9: seguir la posición sin cortar gestos; movimiento instantáneo (sin animación que se pise)
    LaunchedEffect(currentLocation, isTracking) {
        val loc = currentLocation ?: return@LaunchedEffect
        val action = MapFollowPolicy.decide(
            isTracking = isTracking,
            hasLocation = true,
            isUserGestureActive = cameraPositionState.isMoving && isUserMove,
            lastUserInteractionMs = lastUserInteractionMs,
            nowMs = SystemClock.elapsedRealtime(),
            currentZoom = cameraPositionState.position.zoom,
            userZoom = userZoom
        )
        when (action) {
            MapFollowPolicy.Action.NONE -> Unit
            MapFollowPolicy.Action.CENTER -> cameraPositionState.move(CameraUpdateFactory.newLatLng(loc))
            MapFollowPolicy.Action.CENTER_AND_ZOOM -> {
                val target = MapFollowPolicy.targetZoom(userZoom)
                Log.d("MapDiag", "zoom restaurado ${cameraPositionState.position.zoom} -> $target")
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(loc, target))
            }
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
        uiSettings = mapUiSettings,
        // RF-9.4: +/- y logo por encima de la franja de métricas (~100 dp) y del FAB de pausa (56 dp)
        contentPadding = PaddingValues(bottom = 172.dp)
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
    val lastManual = remember(laps) { CurrentLapModel.lastManual(laps) }

    // Panel de la vuelta manual en curso (RF-8.7). El historial completo vive en la ventana 4.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(TabataSizes.PaddingMedium),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Flag, null, tint = TabataColors.VibrantOrange, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Vuelta manual M${stats.manualLapsCount + 1}",
                color = TabataColors.VibrantOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        LapStatGrid(
            stats = currentLapStats(stats),
            columns = 2,
            valueFontSize = 36.sp,
            cardPadding = 10.dp,
            spacing = 10.dp
        )

        // Botón NUEVA VUELTA
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

        LastLapStrip(lastManual, compact = false)
    }
}

/** Un dato de la rejilla de la vuelta en curso */
private data class LapStat(val value: String, val unit: String, val label: String, val color: Color = Color.White)

/**
 * Datos de la vuelta manual en curso, en el orden de lectura: tiempo, distancia, ritmo, velocidad, HR1, HR2, pasos,
 * cadencia o pasos/min, desnivel+. Con cadenciómetro, "Cadencia" en rpm (C1 y, si C1 no emite, C2; spec 007,
 * T-05); sin él, "Pasos/min" de la vuelta (R7.2). Nunca las dos a la vez.
 */
private fun currentLapStats(stats: FreeRideStats): List<LapStat> {
    val lap = CurrentLapModel.of(
        lapSeconds = stats.currentLapTimeSeconds,
        lapMeters = stats.currentLapDistanceMeters,
        lapSteps = stats.currentLapSteps,
        lapElevationGainM = stats.currentLapElevationGainM,
        cadenceActive = stats.anyCadenceActive,
        cadenceRpm = stats.primaryCadence
    )
    return listOf(
        LapStat(formatDuration(stats.currentLapTimeSeconds), "", "Tiempo"),
        LapStat(String.format("%.2f", stats.currentLapDistanceMeters / 1000f), "km", "Distancia"),
        LapStat(lap.pace, "min/km", "Ritmo vuelta"),
        LapStat(if (stats.currentSpeedKmh > 0.5f) String.format("%.1f", stats.currentSpeedKmh) else "--", "km/h", "Velocidad"),
        LapStat(
            if (stats.heartRate > 0) "${stats.heartRate}" else "--", "bpm", "HR1",
            if (stats.isHrConnected) HR1_COLOR else Color.Gray
        ),
        LapStat(
            if (stats.heartRate2 > 0) "${stats.heartRate2}" else "--", "bpm", "HR2",
            if (stats.isHr2Connected) HR2_COLOR else Color.Gray
        ),
        LapStat(lap.steps, "", "Pasos"),
        LapStat(lap.cadenceValue, lap.cadenceUnit, lap.cadenceLabel),
        LapStat(lap.elevation, "m", "Desnivel +")
    )
}

@Composable
private fun LapStatGrid(
    stats: List<LapStat>,
    columns: Int,
    valueFontSize: TextUnit,
    cardPadding: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        stats.chunked(columns).forEach { rowStats ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                rowStats.forEach { s ->
                    LapMetricCardNoIcon(
                        value = s.value,
                        unit = s.unit,
                        label = s.label,
                        valueColor = s.color,
                        valueFontSize = valueFontSize,
                        contentPadding = cardPadding,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - rowStats.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Franja con el resumen de la última vuelta manual cerrada; sin ninguna, un aviso */
@Composable
private fun LastLapStrip(last: LastManualLap?, compact: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp)) {
            if (last == null) {
                Text(
                    "Última vuelta: aún no has cerrado ninguna vuelta manual",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = if (compact) 12.sp else 14.sp
                )
                return@Column
            }
            Text(
                "Última vuelta · ${last.label}",
                color = Color(0xFFFFB74D),
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 12.sp else 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            val items = listOf(
                Triple(last.time, "", "Tiempo"),
                Triple(last.pace, "", "Ritmo"),
                Triple(last.heartRate, "", "FC"),
                Triple(last.steps, "", "Pasos")
            )
            items.chunked(if (compact) 2 else 4).forEach { rowItems ->
                Row(Modifier.fillMaxWidth()) {
                    rowItems.forEach { (v, u, l) -> StepsStripItem(v, u, l, Modifier.weight(1f)) }
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
            if (unit.isNotEmpty()) Text(" $unit", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
        }
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
    }
}

private val HR1_COLOR = Color(0xFFE91E63)
private val HR2_COLOR = Color(0xFF00BCD4) // mismo cian que HR2 en Tabata

/** Tarjeta de pulso partida HR1 | HR2 (spec 007, T-05): cada banda con su valor, su señal y su batería */
@Composable
private fun HeartRateSplitCard(
    stats: FreeRideStats,
    slots: List<SlotStatus>,
    modifier: Modifier = Modifier,
    valueFontSize: TextUnit = 55.sp,
    contentPadding: Dp = 16.dp
) {
    SplitMetricCard(
        left = SlotHalf(
            value = if (stats.heartRate > 0) "${stats.heartRate}" else "--",
            unit = "bpm",
            label = SENSOR_SLOT_LABELS[0],
            color = if (stats.isHrConnected) HR1_COLOR else Color.Gray,
            status = slots.getOrNull(0)
        ),
        right = SlotHalf(
            value = if (stats.heartRate2 > 0) "${stats.heartRate2}" else "--",
            unit = "bpm",
            label = SENSOR_SLOT_LABELS[1],
            color = if (stats.isHr2Connected) HR2_COLOR else Color.Gray,
            status = slots.getOrNull(1)
        ),
        valueFontSize = valueFontSize,
        contentPadding = contentPadding,
        modifier = modifier
    )
}

/**
 * Hueco "Cadencia / Ritmo": si C1 o C2 emiten, tarjeta partida C1 | C2 en rpm (spec 007, T-05); si ninguno
 * emite, ritmo en min/km. La conmutación sale de [FreeRideStats.cadenceActive] / [FreeRideStats.cadence2Active].
 */
@Composable
private fun CadencePaceCard(
    stats: FreeRideStats,
    slots: List<SlotStatus>,
    modifier: Modifier = Modifier,
    valueFontSize: TextUnit = 55.sp,
    contentPadding: Dp = 16.dp
) {
    if (stats.anyCadenceActive) {
        SplitMetricCard(
            left = SlotHalf(
                value = if (stats.cadenceActive) "%.0f".format(stats.cadence) else "--",
                unit = "rpm",
                label = SENSOR_SLOT_LABELS[2],
                color = if (stats.cadenceActive) Color.White else Color.Gray,
                status = slots.getOrNull(2)
            ),
            right = SlotHalf(
                value = if (stats.cadence2Active) "%.0f".format(stats.cadence2) else "--",
                unit = "rpm",
                label = SENSOR_SLOT_LABELS[3],
                color = if (stats.cadence2Active) Color.White else Color.Gray,
                status = slots.getOrNull(3)
            ),
            valueFontSize = valueFontSize,
            contentPadding = contentPadding,
            modifier = modifier
        )
        return
    }
    val slot = CadenceDisplayPolicy.slot(false, 0f, stats.paceSecPerKm)
    LapMetricCardNoIcon(
        value = slot.value,
        unit = slot.unit,
        label = slot.label,
        valueFontSize = valueFontSize,
        contentPadding = contentPadding,
        modifier = modifier
    )
}

/** Mitad de una tarjeta partida: valor de un hueco con su etiqueta (HR1, C2…), señal y batería */
private data class SlotHalf(
    val value: String,
    val unit: String,
    val label: String,
    val color: Color,
    val status: SlotStatus?
)

/** Tarjeta con dos huecos lado a lado (HR1 | HR2, C1 | C2), mismo estilo que [LapMetricCardNoIcon] */
@Composable
private fun SplitMetricCard(
    left: SlotHalf,
    right: SlotHalf,
    valueFontSize: TextUnit,
    contentPadding: Dp,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding / 2, vertical = contentPadding)
        ) {
            SplitMetricHalf(left, valueFontSize, Modifier.weight(1f))
            Box(
                Modifier
                    .align(Alignment.CenterVertically)
                    .padding(horizontal = 4.dp)
                    .width(1.dp)
                    .height(valueFontSize.value.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )
            SplitMetricHalf(right, valueFontSize, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SplitMetricHalf(half: SlotHalf, valueFontSize: TextUnit, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            BasicText(
                text = half.value,
                style = TextStyle(fontWeight = FontWeight.Bold, color = half.color),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 14.sp,
                    maxFontSize = valueFontSize,
                    stepSize = 1.sp
                ),
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                " ${half.unit}",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        SensorSlotBadge(
            label = half.label,
            status = half.status,
            labelColor = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            stacked = true
        )
    }
}

/**
 * Tarjeta de un solo hueco a tamaño completo (R7.1): mismo estilo que [LapMetricCardNoIcon], con hueco + señal +
 * batería en una línea (R6) en lugar de la etiqueta.
 */
@Composable
private fun SingleSlotCard(
    half: SlotHalf,
    valueFontSize: TextUnit,
    contentPadding: Dp,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                BasicText(
                    text = half.value,
                    style = TextStyle(fontWeight = FontWeight.Bold, color = half.color),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 16.sp,
                        maxFontSize = valueFontSize,
                        stepSize = 1.sp
                    ),
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    " ${half.unit}",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            SensorSlotBadge(
                label = half.label,
                status = half.status,
                labelColor = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
    }
}

/** Color del punto o etiqueta de un hueco: verde con señal, naranja buscando, rojo con su aparato sin señal */
private fun slotSignalColor(status: SlotStatus?): Color = when (status?.signalState) {
    SlotSignalState.SIGNAL -> TabataColors.Connected
    SlotSignalState.LOST -> TabataColors.RestRed
    else -> Color(0xFFFFA500)
}

/** Fila compacta para apaisado: GPS y un punto por hueco (HR1, HR2, C1, C2) */
@Composable
private fun CompactSensorRow(stats: FreeRideStats, slots: List<SlotStatus>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SensorDot(Icons.Default.GpsFixed, stats.isGpsConnected, stats.isGpsScanning, TabataColors.WorkGreen)
        SENSOR_SLOT_LABELS.forEachIndexed { i, label ->
            val c = slotSignalColor(slots.getOrNull(i))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.size(8.dp).background(c, CircleShape))
                Text(label, fontSize = 10.sp, color = c, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LapMetricCardNoIcon(
    value: String,
    unit: String,
    label: String,
    valueColor: Color = Color.White,
    valueFontSize: TextUnit = 55.sp,  // ⚡ Tamaño de fuente configurable
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
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
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ⚡ SIN ICONO - Eliminado completamente para hacer datos más grandes
            // Valor en una sola línea que encoge hasta caber; la unidad se mide primero
            // y nunca se parte (antes "10:15 /km" se solapaba y la unidad salía en vertical).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                BasicText(
                    text = value,
                    style = TextStyle(fontWeight = FontWeight.Bold, color = valueColor),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 16.sp,
                        maxFontSize = valueFontSize,
                        stepSize = 1.sp
                    ),
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (unit.isNotEmpty()) {
                    Text(
                        " $unit",
                        fontSize = 16.sp,  // ⚡ Unidad también más grande
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        softWrap = false,
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
    val slots = rememberSensorSlots()
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
            // Sensores compactos: GPS y un punto por hueco (HR1, HR2, C1, C2)
            CompactSensorRow(stats, slots)

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
        // Sin desplazamiento y con hueco abajo para el indicador de páginas (antes la franja de pasos quedaba
        // debajo): las tarjetas ajustan el número al alto que queda (plan §8).
        Column(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
                .padding(start = 8.dp, bottom = PAGE_INDICATOR_CLEARANCE),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricsGrid(stats, Modifier.weight(1f).fillMaxWidth(), cardPadding = 10.dp)
            if (stats.steps > 0) StepsStrip(stats)
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
    val lastManual = remember(laps) { CurrentLapModel.lastManual(laps) }
    val slots = rememberSensorSlots()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(8.dp)
    ) {
        // ============================================================
        // IZQUIERDA: Timer total y última vuelta manual (35%)
        // ============================================================
        Column(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Sensores compactos: GPS y un punto por hueco (HR1, HR2, C1, C2)
            CompactSensorRow(stats, slots)

            // Estado "EN RUTA"
            StatusLabel(stats = stats, fontSize = 14)

            // Timer grande (tiempo total)
            Text(
                formatDuration(stats.totalTimeSeconds),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Resumen de la última vuelta manual cerrada (el historial completo está en la ventana 4)
            LastLapStrip(lastManual, compact = true)
        }

        // ============================================================
        // DERECHA: Panel de la vuelta manual en curso y controles (65%)
        // ============================================================
        Column(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Vuelta manual M${stats.manualLapsCount + 1}",
                color = TabataColors.VibrantOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            // Rejilla 3x3; si no cupiera (pantallas bajas) se desplaza en vertical
            Box(modifier = Modifier.weight(1f)) {
                LapStatGrid(
                    stats = currentLapStats(stats),
                    columns = 3,
                    valueFontSize = 26.sp,
                    cardPadding = 6.dp,
                    spacing = 6.dp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }

            // Botón Nueva Vuelta
            if (stats.isRunning && !stats.isPaused) {
                Button(
                    onClick = onLap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
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
    // GPS y los 4 huecos (HR1, HR2, C1, C2), cada uno en su recuadro de señal (ANT+ verde / BLE azul, R6.1) y su batería (spec 007)
    val slots = rememberSensorSlots()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SensorChipAnimated(Icons.Default.GpsFixed, "GPS", stats.isGpsConnected, stats.isGpsScanning, TabataColors.WorkGreen)
        SENSOR_SLOT_LABELS.forEachIndexed { i, label ->
            val status = slots.getOrNull(i)
            SensorSlotBadge(
                label = label,
                status = status,
                labelColor = slotSignalColor(status),
                fontSize = 11.sp,
                stacked = true
            )
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

/**
 * PÁGINA 4: TABLA DE VUELTAS (R8). Misma composición en portrait y landscape; el hueco inferior deja libres la barra
 * de pestañas y el botón de vuelta flotante, y el superior la flecha de volver.
 */
@Composable
private fun LapTablePage(laps: List<LapData>) {
    val autos = laps.count { it.isAutoLap }
    val manuals = laps.size - autos
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 48.dp, bottom = 76.dp)
    ) {
        Text(
            text = "Vueltas · ${laps.size} (A $autos · M $manuals)",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
        LapTable(laps = laps, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun PageIndicatorBar(currentPage: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val labels = listOf("Métricas", "Mapa", "Vueltas", "Tabla")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(PAGE_COUNT) { index ->
            val isSelected = currentPage == index
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable(onClickLabel = labels[index]) { onSelect(index) }
                    .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
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