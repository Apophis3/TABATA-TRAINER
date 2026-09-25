package com.tuapp.tabatatrainer.ui.session

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.tabatatrainer.data.local.*
import com.tuapp.tabatatrainer.util.ExportUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val sensorReadingDao: SensorReadingDao,
    private val gpsDao: GpsDao
) : ViewModel() {

    private val _session = MutableStateFlow<WorkoutSessionEntity?>(null)
    val session: StateFlow<WorkoutSessionEntity?> = _session.asStateFlow()

    private val _readings = MutableStateFlow<List<SensorReadingEntity>>(emptyList())
    val readings: StateFlow<List<SensorReadingEntity>> = _readings.asStateFlow()

    private val _gpsPoints = MutableStateFlow<List<GpsPointEntity>>(emptyList())
    val gpsPoints: StateFlow<List<GpsPointEntity>> = _gpsPoints.asStateFlow()

    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            _session.value = sessionDao.getSessionById(sessionId)
            _readings.value = sensorReadingDao.getReadingsForSessionSync(sessionId)
            _gpsPoints.value = gpsDao.getPointsForSessionSync(sessionId)
        }
    }

    fun deleteSession(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _session.value?.let { session ->
                gpsDao.deletePointsForSession(session.id)
                sensorReadingDao.deleteReadingsForSession(session.id)
                sessionDao.deleteSession(session)
                onDeleted()
            }
        }
    }

    fun updateNotes(notes: String) {
        viewModelScope.launch {
            _session.value?.let { session ->
                val updated = session.copy(notes = notes)
                sessionDao.updateSession(updated)
                _session.value = updated
            }
        }
    }

    fun updateRating(rating: Int) {
        viewModelScope.launch {
            _session.value?.let { session ->
                val updated = session.copy(rating = rating)
                sessionDao.updateSession(updated)
                _session.value = updated
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val coroutineScope = rememberCoroutineScope()
    val session by viewModel.session.collectAsState()
    val readings by viewModel.readings.collectAsState()
    val gpsPoints by viewModel.gpsPoints.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Sesión") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showNotesDialog = true }) {
                        Icon(Icons.Default.Edit, "Editar notas")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Eliminar")
                    }
                }
            )
        }
    ) { padding ->
        session?.let { sess ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Cabecera con fecha y duración
                SessionHeader(session = sess)

                Spacer(modifier = Modifier.height(16.dp))

                // Métricas principales (HR + Cadencia)
                MetricsGrid(session = sess, readings = readings)

                // Métricas GPS (si hay datos)
                val distance = sess.totalDistanceMeters
                if (sess.gpsEnabled && distance != null && distance > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    GpsMetricsGrid(session = sess)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Gráfico de HR
                if (readings.isNotEmpty()) {
                    HeartRateChart(
                        session = sess,
                        readings = readings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Configuración del entrenamiento
                WorkoutConfigCard(session = sess)

                Spacer(modifier = Modifier.height(16.dp))

                // === BOTONES DE EXPORTACIÓN ===
                ExportCard(
                    session = sess,
                    readings = readings,
                    gpsPoints = gpsPoints,
                    isExporting = isExporting,
                    onExportExcel = {
                        // Ejecutar en coroutine para evitar bloqueos y manejar errores mejor
                        coroutineScope.launch {
                            try {
                                isExporting = true
                                val file = ExportUtils.exportToExcel(applicationContext, sess, readings, gpsPoints)
                                isExporting = false
                                if (file != null) {
                                    // Usar context de actividad para el share (necesita UI)
                                    ExportUtils.shareFile(context, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                } else {
                                    Toast.makeText(context, "Error al exportar Excel", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                isExporting = false
                                Log.e("SessionDetail", "Error exportando Excel: ${e.message}", e)
                                Toast.makeText(context, "Error al exportar: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onExportGpx = {
                        if (gpsPoints.isNotEmpty()) {
                            // Ejecutar en coroutine para evitar bloqueos y manejar errores mejor
                            coroutineScope.launch {
                                try {
                                    isExporting = true
                                    val file = ExportUtils.exportToGpx(applicationContext, sess, gpsPoints)
                                    isExporting = false
                                    if (file != null) {
                                        // Usar context de actividad para el share (necesita UI)
                                        ExportUtils.shareFile(context, file, "application/gpx+xml")
                                    } else {
                                        Toast.makeText(context, "Error al exportar GPX", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    isExporting = false
                                    Log.e("SessionDetail", "Error exportando GPX: ${e.message}", e)
                                    Toast.makeText(context, "Error al exportar: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "No hay datos GPS para exportar", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Rating
                RatingCard(
                    currentRating = sess.rating,
                    onRatingChange = { viewModel.updateRating(it) }
                )
                
                // Notas
                sess.notes?.let { notes ->
                    if (notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        NotesCard(notes = notes)
                    }
                }
            }
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    // Diálogo de eliminar
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Eliminar sesión?") },
            text = { Text("Esta acción no se puede deshacer. Se eliminarán todos los datos incluyendo GPS.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession { onBack() }
                        showDeleteDialog = false
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de notas
    if (showNotesDialog) {
        var notesText by remember { mutableStateOf(session?.notes ?: "") }

        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            title = { Text("Notas") },
            text = {
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Añade notas sobre el entrenamiento...") },
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateNotes(notesText)
                        showNotesDialog = false
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotesDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun SessionHeader(session: WorkoutSessionEntity) {
    val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("es"))
    val timeFormat = SimpleDateFormat("HH:mm", Locale("es"))

    val durationMinutes = session.endTime?.let { end ->
        ((end - session.startTime) / 1000 / 60).toInt()
    } ?: 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = dateFormat.format(Date(session.startTime)).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Inicio: ${timeFormat.format(Date(session.startTime))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )

                Text(
                    text = "Duración: $durationMinutes min",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Estado completado
                if (session.isCompleted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${session.completedRounds}/${session.totalRounds} rondas",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
                
                // Indicador GPS
                if (session.gpsEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "GPS",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2196F3)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricsGrid(
    session: WorkoutSessionEntity,
    readings: List<SensorReadingEntity>
) {
    // Calcular métricas solo desde el primer dato válido de cada sensor
    val hr1ValidReadings = readings.mapNotNull { it.heartRate?.takeIf { it > 0 } }
    val hr2ValidReadings = readings.mapNotNull { it.heartRate2?.takeIf { it > 0 } }
    val cadenceValidReadings = readings.mapNotNull { it.cadence?.takeIf { it > 0f } }
    
    // Calcular promedios y máximos desde los datos válidos.
    // Si la sesión no tiene lecturas por segundo (p.ej. rutas antiguas), usar los valores guardados en la sesión.
    val avgHr1 = if (hr1ValidReadings.isNotEmpty()) hr1ValidReadings.average().toInt() else session.avgHeartRate
    val maxHr1 = if (hr1ValidReadings.isNotEmpty()) hr1ValidReadings.max() else session.maxHeartRate
    val avgHr2 = if (hr2ValidReadings.isNotEmpty()) hr2ValidReadings.average().toInt() else session.avgHeartRate2
    val maxHr2 = if (hr2ValidReadings.isNotEmpty()) hr2ValidReadings.max() else session.maxHeartRate2
    val avgCadence = if (cadenceValidReadings.isNotEmpty()) cadenceValidReadings.average().toFloat() else session.avgCadence
    
    // Primera fila: HR1, HR1 Máx, Cadencia
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricCard(
            icon = Icons.Default.Favorite,
            label = "HR1",
            value = avgHr1?.toString() ?: "--",
            unit = "bpm",
            color = Color(0xFFE91E63),
            modifier = Modifier.weight(1f)
        )

        MetricCard(
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            label = "HR1 Máx",
            value = maxHr1?.toString() ?: "--",
            unit = "bpm",
            color = Color(0xFFE91E63),
            modifier = Modifier.weight(1f)
        )

        MetricCard(
            icon = Icons.AutoMirrored.Filled.DirectionsBike,
            label = "Cadencia",
            value = avgCadence?.let { String.format("%.0f", it) } ?: "--",
            unit = "rpm",
            color = Color(0xFF9C27B0),
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Segunda fila: HR2, HR2 Máx
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricCard(
            icon = Icons.Default.Favorite,
            label = "HR2",
            value = avgHr2?.toString() ?: "--",
            unit = "bpm",
            color = Color(0xFF00BCD4),
            modifier = Modifier.weight(1f)
        )

        MetricCard(
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            label = "HR2 Máx",
            value = maxHr2?.toString() ?: "--",
            unit = "bpm",
            color = Color(0xFF00BCD4),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GpsMetricsGrid(session: WorkoutSessionEntity) {
    val distance = session.totalDistanceMeters
    val avgSpeed = session.avgSpeedKmh
    val maxSpeed = session.maxSpeedKmh
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricCard(
            icon = Icons.Default.Route,
            label = "Distancia",
            value = distance?.let { String.format("%.2f", it / 1000f) } ?: "--",
            unit = "km",
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(1f)
        )

        MetricCard(
            icon = Icons.Default.Speed,
            label = "Vel. Media",
            value = avgSpeed?.let { String.format("%.1f", it) } ?: "--",
            unit = "km/h",
            color = Color(0xFFFF9800),
            modifier = Modifier.weight(1f)
        )

        MetricCard(
            icon = Icons.Default.RocketLaunch,
            label = "Vel. Máx",
            value = maxSpeed?.let { String.format("%.1f", it) } ?: "--",
            unit = "km/h",
            color = Color(0xFFFF5722),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ExportCard(
    session: WorkoutSessionEntity,
    readings: List<SensorReadingEntity>,
    gpsPoints: List<GpsPointEntity>,
    isExporting: Boolean,
    onExportExcel: () -> Unit,
    onExportGpx: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Exportar",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Botón Excel (habilitado si hay lecturas O si es FreeRide con GPS)
                Button(
                    onClick = onExportExcel,
                    enabled = !isExporting && (readings.isNotEmpty() || (session.activityType == "FREE_RIDE" && gpsPoints.isNotEmpty())),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.TableChart, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Excel")
                    }
                }

                // Botón GPX (solo si hay GPS)
                Button(
                    onClick = onExportGpx,
                    enabled = !isExporting && gpsPoints.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Icon(Icons.Default.Map, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GPX")
                }
            }

            if (gpsPoints.isEmpty() && session.gpsEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No hay datos GPS grabados",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = " $unit",
                    fontSize = 12.sp,
                    color = color.copy(alpha = 0.7f)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeartRateChart(
    session: WorkoutSessionEntity,
    readings: List<SensorReadingEntity>,
    modifier: Modifier = Modifier
) {
    val sessionStartTime = session.startTime
    val sessionEndTime = session.endTime ?: (readings.maxOfOrNull { it.timestamp } ?: sessionStartTime)
    val totalTimeRange = (sessionEndTime - sessionStartTime).coerceAtLeast(1000L)
    
    // Crear lista completa de todos los readings con valores (0 si no hay dato)
    val allReadingsWithTime = readings.map { reading ->
        Triple(
            reading.timestamp,
            reading.heartRate?.takeIf { it > 0 } ?: 0,
            reading.heartRate2?.takeIf { it > 0 } ?: 0
        )
    }
    
    // Encontrar el primer timestamp válido para cada sensor
    val hr1FirstValidTime = readings.firstOrNull { it.heartRate != null && it.heartRate > 0 }?.timestamp
    val hr2FirstValidTime = readings.firstOrNull { it.heartRate2 != null && it.heartRate2 > 0 }?.timestamp
    
    // Encontrar el rango de valores HR (solo de valores válidos > 0)
    val allHr1Values = readings.mapNotNull { it.heartRate?.takeIf { it > 0 } }
    val allHr2Values = readings.mapNotNull { it.heartRate2?.takeIf { it > 0 } }
    val allHrValues = (allHr1Values + allHr2Values)
    val maxHr = if (allHrValues.isNotEmpty()) allHrValues.max() else 180
    val minHr = if (allHrValues.isNotEmpty()) allHrValues.min() else 60
    val hrRange = (maxHr - minHr).coerceAtLeast(20)

    if (allReadingsWithTime.isEmpty()) return

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Frecuencia Cardíaca",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                // Dibujar HR1 - desde inicio de sesión, 0 hasta primer dato válido
                val path1 = Path()
                var path1Started = false
                
                allReadingsWithTime.forEach { (timestamp, hr1, _) ->
                    val normalizedTime = ((timestamp - sessionStartTime).toFloat() / totalTimeRange) * size.width
                    // Si hay HR1 válido y el timestamp es >= al primer válido, usar el valor real, sino 0
                    val hrValue = if (hr1FirstValidTime != null && timestamp >= hr1FirstValidTime) hr1 else 0
                    val y = size.height - ((hrValue - minHr).toFloat() / hrRange * size.height).coerceIn(0f, size.height)
                    
                    if (!path1Started) {
                        path1.moveTo(normalizedTime, y)
                        path1Started = true
                    } else {
                        path1.lineTo(normalizedTime, y)
                    }
                }
                
                if (path1Started) {
                    drawPath(
                        path = path1,
                        color = Color(0xFFE91E63),
                        style = Stroke(width = 3f)
                    )
                }

                // Dibujar HR2 - desde inicio de sesión, 0 hasta primer dato válido
                val path2 = Path()
                var path2Started = false
                
                allReadingsWithTime.forEach { (timestamp, _, hr2) ->
                    val normalizedTime = ((timestamp - sessionStartTime).toFloat() / totalTimeRange) * size.width
                    // Si hay HR2 válido y el timestamp es >= al primer válido, usar el valor real, sino 0
                    val hrValue = if (hr2FirstValidTime != null && timestamp >= hr2FirstValidTime) hr2 else 0
                    val y = size.height - ((hrValue - minHr).toFloat() / hrRange * size.height).coerceIn(0f, size.height)
                    
                    if (!path2Started) {
                        path2.moveTo(normalizedTime, y)
                        path2Started = true
                    } else {
                        path2.lineTo(normalizedTime, y)
                    }
                }
                
                if (path2Started) {
                    drawPath(
                        path = path2,
                        color = Color(0xFF00BCD4),
                        style = Stroke(width = 3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutConfigCard(session: WorkoutSessionEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Configuración",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ConfigItem("Calent.", "${session.warmupSeconds}s", Color(0xFFFFA726))
                ConfigItem("Trabajo", "${session.workSeconds}s", Color(0xFF4CAF50))
                ConfigItem("Descanso", "${session.restSeconds}s", Color(0xFFF44336))
                ConfigItem("Rondas", "${session.totalRounds}", Color(0xFF2196F3))
            }
        }
    }
}

@Composable
private fun ConfigItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RatingCard(
    currentRating: Int,
    onRatingChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Calificación del Esfuerzo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                (1..5).forEach { rating ->
                    Icon(
                        imageVector = if (rating <= currentRating) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Rating $rating",
                        tint = if (rating <= currentRating) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onRatingChange(rating) }
                    )
                }
            }
        }
    }
}

@Composable
fun NotesCard(notes: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Notas",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = notes, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
