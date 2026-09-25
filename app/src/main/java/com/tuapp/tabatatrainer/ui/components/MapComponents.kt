package com.tuapp.tabatatrainer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.tuapp.tabatatrainer.data.local.GpsPointEntity
import com.tuapp.tabatatrainer.data.local.PoiEntity

// Colores
private val NikeOrange = Color(0xFFFF6B35)
private val WorkGreen = Color(0xFF4CAF50)

// ============================================================================
// COMPONENTE DE MAPA REUTILIZABLE
// ============================================================================

/**
 * Mapa que muestra una ruta con puntos GPS
 * Puede usarse tanto para ver rutas guardadas como en tiempo real
 *
 * @param gpsPoints Lista de puntos GPS de la ruta
 * @param pois Lista opcional de POIs a mostrar
 * @param currentLocation Ubicación actual (para modo tiempo real)
 * @param isLive Si es true, sigue la ubicación actual
 * @param showStartEndMarkers Mostrar marcadores de inicio/fin
 * @param routeColor Color de la línea de la ruta
 * @param modifier Modifier para el composable
 */
@Composable
fun WorkoutMap(
    gpsPoints: List<GpsPointEntity>,
    pois: List<PoiEntity> = emptyList(),
    currentLocation: LatLng? = null,
    isLive: Boolean = false,
    showStartEndMarkers: Boolean = true,
    routeColor: Color = Color(0xFF2196F3),  // Azul
    routeWidth: Float = 8f,
    modifier: Modifier = Modifier
) {
    // Si no hay puntos y no hay ubicación actual, mostrar placeholder
    if (gpsPoints.isEmpty() && currentLocation == null) {
        MapPlaceholder(modifier = modifier)
        return
    }

    // Convertir puntos GPS a LatLng
    val routeCoordinates = remember(gpsPoints) {
        gpsPoints.map { LatLng(it.latitude, it.longitude) }
    }

    // Determinar centro inicial del mapa
    val initialPosition = remember(gpsPoints, currentLocation) {
        when {
            currentLocation != null -> currentLocation
            routeCoordinates.isNotEmpty() -> routeCoordinates.first()
            else -> LatLng(0.0, 0.0)
        }
    }

    // Estado de la cámara
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPosition, 16f)
    }

    // Seguir ubicación actual si es modo live
    LaunchedEffect(currentLocation, isLive) {
        if (isLive && currentLocation != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(currentLocation),
                durationMs = 500
            )
        }
    }

    // Ajustar cámara para mostrar toda la ruta cuando no es live
    LaunchedEffect(gpsPoints) {
        if (!isLive && routeCoordinates.size >= 2) {
            val boundsBuilder = LatLngBounds.Builder()
            routeCoordinates.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()

            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(bounds, 50),
                durationMs = 1000
            )
        }
    }

    // Propiedades del mapa
    val mapProperties = remember {
        MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = false,  // Usamos nuestro propio marcador
            isBuildingEnabled = true,
            isTrafficEnabled = false
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = true,
            compassEnabled = true,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false
        )
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = mapUiSettings
    ) {
        // Dibujar ruta
        if (routeCoordinates.size >= 2) {
            Polyline(
                points = routeCoordinates,
                color = routeColor,
                width = routeWidth
            )
        }

        // Marcador de inicio
        if (showStartEndMarkers && routeCoordinates.isNotEmpty()) {
            Marker(
                state = MarkerState(position = routeCoordinates.first()),
                title = "Inicio",
                snippet = "Punto de partida",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
            )
        }

        // Marcador de fin (si hay más de un punto y no es live)
        if (showStartEndMarkers && routeCoordinates.size > 1 && !isLive) {
            Marker(
                state = MarkerState(position = routeCoordinates.last()),
                title = "Fin",
                snippet = "Punto final",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )
        }

        // Marcador de ubicación actual (modo live)
        if (isLive && currentLocation != null) {
            Marker(
                state = MarkerState(position = currentLocation),
                title = "Tu posición",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            )
        }

        // Marcadores de POIs
        pois.forEach { poi ->
            Marker(
                state = MarkerState(position = LatLng(poi.latitude, poi.longitude)),
                title = poi.name ?: "POI ${poi.id}",
                snippet = poi.description ?: "HR: ${poi.heartRate ?: "--"} bpm",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
            )
        }
    }
}

/**
 * Placeholder cuando no hay datos de GPS
 */
@Composable
fun MapPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Map,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Sin datos GPS",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 16.sp
            )
            Text(
                "Inicia una ruta para ver el mapa",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Placeholder con animación de búsqueda GPS
 */
@Composable
fun MapPlaceholderSearching(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val infiniteTransition = rememberInfiniteTransition(label = "gps")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing)
                ),
                label = "rotation"
            )

            Icon(
                Icons.Default.GpsFixed,
                contentDescription = null,
                tint = NikeOrange,
                modifier = Modifier
                    .size(80.dp)
                    .rotate(rotation)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Buscando señal GPS...",
                color = Color.White,
                fontSize = 18.sp
            )
            Text(
                "El mapa se mostrará cuando\nobtengas ubicación",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================================================
// MAPA CON OVERLAY DE MÉTRICAS (para FreeRide)
// ============================================================================

/**
 * Mapa con overlay de métricas en tiempo real
 * Usado en la página 2 de FreeRideScreen
 */
@Composable
fun LiveMapWithMetrics(
    gpsPoints: List<GpsPointEntity>,
    currentLatitude: Double,
    currentLongitude: Double,
    currentSpeedKmh: Float,
    totalDistanceMeters: Float,
    totalTimeSeconds: Int,
    gpsAccuracy: Float?,
    isGpsConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Mapa
        WorkoutMap(
            gpsPoints = gpsPoints,
            currentLocation = if (currentLatitude != 0.0) LatLng(currentLatitude, currentLongitude) else null,
            isLive = true,
            showStartEndMarkers = true,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay superior - Estado GPS
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.GpsFixed,
                contentDescription = null,
                tint = if (isGpsConnected) WorkGreen else Color.Yellow,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    if (isGpsConnected) "GPS Conectado" else "Buscando GPS...",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                gpsAccuracy?.let {
                    Text(
                        "Precisión: ±${String.format("%.0f", it)}m",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Overlay inferior - Métricas
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricOverlayItem(
                value = if (currentSpeedKmh > 0.5f) String.format("%.1f", currentSpeedKmh) else "--",
                unit = "km/h",
                label = "Velocidad"
            )
            MetricOverlayItem(
                value = formatDuration(totalTimeSeconds),
                unit = "",
                label = "Tiempo"
            )
            MetricOverlayItem(
                value = String.format("%.2f", totalDistanceMeters / 1000f),
                unit = "km",
                label = "Distancia"
            )
        }

        // Coordenadas actuales (esquina superior derecha)
        if (currentLatitude != 0.0) {
            Text(
                "${String.format("%.5f", currentLatitude)}, ${String.format("%.5f", currentLongitude)}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun MetricOverlayItem(value: String, unit: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (unit.isNotEmpty()) {
                Text(
                    " $unit",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
        Text(
            label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

// ============================================================================
// MAPA PARA DETALLE DE SESIÓN (con estadísticas)
// ============================================================================

/**
 * Card con mapa y estadísticas de la ruta
 * Usado en SessionDetailScreen
 */
@Composable
fun SessionMapCard(
    gpsPoints: List<GpsPointEntity>,
    pois: List<PoiEntity> = emptyList(),
    totalDistanceMeters: Float?,
    avgSpeedKmh: Float?,
    maxSpeedKmh: Float?,
    elevationGain: Float? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            // Mapa
            WorkoutMap(
                gpsPoints = gpsPoints,
                pois = pois,
                isLive = false,
                showStartEndMarkers = true,
                routeColor = WorkGreen,  // Verde para rutas completadas
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            )

            // Estadísticas debajo del mapa
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.Route,
                    value = totalDistanceMeters?.let { String.format("%.2f", it / 1000f) } ?: "--",
                    unit = "km",
                    label = "Distancia"
                )
                StatItem(
                    icon = Icons.Default.Speed,
                    value = avgSpeedKmh?.let { String.format("%.1f", it) } ?: "--",
                    unit = "km/h",
                    label = "Vel. Media"
                )
                StatItem(
                    icon = Icons.Default.Bolt,
                    value = maxSpeedKmh?.let { String.format("%.1f", it) } ?: "--",
                    unit = "km/h",
                    label = "Vel. Máx"
                )
                if (elevationGain != null && elevationGain > 0) {
                    StatItem(
                        icon = Icons.Default.TrendingUp,
                        value = String.format("%.0f", elevationGain),
                        unit = "m",
                        label = "Desnivel"
                    )
                }
            }

            // Número de POIs si hay
            if (pois.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF9C27B0),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${pois.size} puntos de interés marcados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    unit: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                " $unit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================================
// COMPONENTES DE OVERLAY PARA GPS
// ============================================================================

/**
 * Overlay de estado GPS para mostrar en cualquier pantalla
 */
@Composable
fun GpsStatusOverlay(
    isConnected: Boolean,
    isScanning: Boolean,
    accuracy: Float?,
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                Icon(
                    Icons.Default.GpsFixed,
                    contentDescription = null,
                    tint = when {
                        isConnected -> WorkGreen
                        isScanning -> Color.Yellow
                        else -> Color.Gray
                    },
                    modifier = Modifier.size(20.dp)
                )
                if (isScanning) {
                    val infiniteTransition = rememberInfiniteTransition(label = "scan")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.Yellow.copy(alpha = alpha * 0.3f), CircleShape)
                    )
                }
            }
            Column {
                Text(
                    when {
                        isConnected -> "GPS Conectado"
                        isScanning -> "Buscando GPS..."
                        else -> "GPS Desconectado"
                    },
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
                    Text(
                        "Precisión: ±${String.format("%.0f", it)}m",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * Indicador compacto de GPS para barras de sensores
 */
@Composable
fun GpsIndicator(
    isConnected: Boolean,
    isScanning: Boolean,
    accuracy: Float?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box {
            Icon(
                Icons.Default.GpsFixed,
                contentDescription = "GPS",
                tint = when {
                    isConnected -> WorkGreen
                    isScanning -> Color.Yellow
                    else -> Color.Gray
                },
                modifier = Modifier.size(20.dp)
            )
            if (isScanning) {
                val infiniteTransition = rememberInfiniteTransition(label = "gps_scan")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "gps_alpha"
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.Yellow.copy(alpha = alpha * 0.3f), CircleShape)
                )
            }
        }

        Column {
            Text(
                "GPS",
                fontSize = 10.sp,
                color = when {
                    isConnected -> WorkGreen
                    isScanning -> Color.Yellow
                    else -> Color.Gray
                }
            )
            accuracy?.let {
                Text(
                    "±${String.format("%.0f", it)}m",
                    fontSize = 8.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
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

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}