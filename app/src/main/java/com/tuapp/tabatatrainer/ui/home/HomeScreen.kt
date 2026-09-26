// HomeScreen.kt - REEMPLAZO COMPLETO
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tuapp.tabatatrainer.ui.config.ConfigViewModel
import android.app.Activity
import android.content.Intent
import com.tuapp.tabatatrainer.service.FreeRideService
import com.tuapp.tabatatrainer.service.WorkoutService

// Paleta Nike
private val NikeOrange = Color(0xFFFF6B35)
private val NikeGray = Color(0xFF1A1A1A)
private val NikeGrayLight = Color(0xFF2D2D2D)
private val SurfaceCard = Color(0xFF252525)
private val TextSecondary = Color(0xFFAAAAAA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartFreeRide: () -> Unit,
    onStartTabata: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ConfigViewModel = hiltViewModel()
) {
    val bleStatus by viewModel.bleStatus.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > 600

    LaunchedEffect(Unit) { viewModel.startScanning() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NikeGray)
    ) {
        val context = LocalContext.current
        
        val onCloseApp: () -> Unit = {
            // Detener servicios antes de cerrar
            context.stopService(Intent(context, WorkoutService::class.java))
            context.stopService(Intent(context, FreeRideService::class.java))
            // Cerrar la app
            (context as? Activity)?.finishAffinity()
        }
        
        if (isLandscape) {
            LandscapeLayout(
                bleStatus = bleStatus,
                onRefresh = { viewModel.refreshSensors() },
                onStartFreeRide = onStartFreeRide,
                onStartTabata = onStartTabata,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToSettings = onNavigateToSettings,
                onCloseApp = onCloseApp
            )
        } else {
            PortraitLayout(
                bleStatus = bleStatus,
                onRefresh = { viewModel.refreshSensors() },
                onStartFreeRide = onStartFreeRide,
                onStartTabata = onStartTabata,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToSettings = onNavigateToSettings,
                onCloseApp = onCloseApp
            )
        }
    }
}

@Composable
private fun PortraitLayout(
    bleStatus: com.tuapp.tabatatrainer.ui.config.BleScanStatus,
    onRefresh: () -> Unit,
    onStartFreeRide: () -> Unit,
    onStartTabata: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onCloseApp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // Header
        HeaderRow(onNavigateToHistory, onNavigateToSettings)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Sensores
        SensorBar(bleStatus, onRefresh)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Título
        Text(
            "Elige tu actividad",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Cards principales
        ActivityCardModern(
            title = "RUTA LIBRE",
            subtitle = "GPS • Sensores • Sin límites",
            icon = Icons.Outlined.Explore,
            accentColor = Color(0xFF4CAF50),
            onClick = onStartFreeRide
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ActivityCardModern(
            title = "TABATA",
            subtitle = "Intervalos • HIIT • Indoor/Outdoor",
            icon = Icons.Outlined.FitnessCenter,
            accentColor = NikeOrange,
            onClick = onStartTabata
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Accesos rápidos
        QuickAccessRow(onNavigateToHistory, onNavigateToSettings)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Última actividad
        LastActivityCardModern()
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Botón cerrar app
        CloseAppButton(onClick = onCloseApp)
    }
}

@Composable
private fun LandscapeLayout(
    bleStatus: com.tuapp.tabatatrainer.ui.config.BleScanStatus,
    onRefresh: () -> Unit,
    onStartFreeRide: () -> Unit,
    onStartTabata: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onCloseApp: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Columna izquierda
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                HeaderRow(onNavigateToHistory, onNavigateToSettings)
                Spacer(modifier = Modifier.height(24.dp))
                SensorBar(bleStatus, onRefresh)
            }
            
            Column {
                QuickAccessRow(onNavigateToHistory, onNavigateToSettings)
                Spacer(modifier = Modifier.height(16.dp))
                LastActivityCardModern()
                Spacer(modifier = Modifier.height(16.dp))
                CloseAppButton(onClick = onCloseApp)
            }
        }
        
        // Columna derecha - Cards
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Elige tu actividad",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            ActivityCardModern(
                title = "RUTA LIBRE",
                subtitle = "GPS • Sensores • Sin límites",
                icon = Icons.Outlined.Explore,
                accentColor = Color(0xFF4CAF50),
                onClick = onStartFreeRide
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            ActivityCardModern(
                title = "TABATA",
                subtitle = "Intervalos • HIIT • Indoor/Outdoor",
                icon = Icons.Outlined.FitnessCenter,
                accentColor = NikeOrange,
                onClick = onStartTabata
            )
        }
    }
}

@Composable
private fun HeaderRow(
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "TABATA TRAINER",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = NikeOrange,
                letterSpacing = 3.sp
            )
            Text(
                "S. CELIS",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "v${appVersionName()}",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButtonMinimal(Icons.Outlined.History, "Historial", onNavigateToHistory)
            IconButtonMinimal(Icons.Outlined.Settings, "Ajustes", onNavigateToSettings)
        }
    }
}

/** versionName del APK instalado (lo pone Gradle desde version.properties) */
@Composable
private fun appVersionName(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }
}

@Composable
private fun IconButtonMinimal(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(SurfaceCard)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SensorBar(
    bleStatus: com.tuapp.tabatatrainer.ui.config.BleScanStatus,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SensorDot("HR", bleStatus.hrConnected, Color(0xFFE91E63))
            SensorDot("CAD", bleStatus.cadenceConnected, Color(0xFF2196F3))
            SensorDot("GPS", bleStatus.gpsAvailable, Color(0xFF4CAF50))
        }
        
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

@Composable
private fun ActivityCardModern(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "scale"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
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
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(SurfaceCard, NikeGrayLight)
                    )
                )
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        subtitle,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Botón START
                    Box(
                        modifier = Modifier
                            .background(accentColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PlayArrow,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "INICIAR",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                
                // Icono grande
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        null,
                        tint = accentColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
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
private fun QuickAccessRow(
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickButton(Icons.Outlined.History, "Historial", Modifier.weight(1f), onNavigateToHistory)
        QuickButton(Icons.Outlined.BarChart, "Stats", Modifier.weight(1f)) { }
        QuickButton(Icons.Outlined.Person, "Perfil", Modifier.weight(1f), onNavigateToSettings)
    }
}

@Composable
private fun QuickButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun LastActivityCardModern() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NikeOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Timer,
                    null,
                    tint = NikeOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text("Última actividad", fontSize = 14.sp, color = Color.White)
                Text("Sin actividades recientes", fontSize = 12.sp, color = TextSecondary)
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CloseAppButton(onClick: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .clickable { showDialog = true }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ExitToApp,
                "Cerrar app",
                tint = Color(0xFFE53935),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Cerrar aplicación",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE53935)
            )
        }
    }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
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
                        showDialog = false
                        onClick()
                    }
                ) {
                    Text(
                        "Cerrar",
                        color = Color(0xFFE53935),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(20.dp)
        )
    }
}