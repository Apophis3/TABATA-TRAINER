package com.tuapp.tabatatrainer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.tuapp.tabatatrainer.data.local.GpsPointEntity
import com.tuapp.tabatatrainer.data.local.SensorReadingEntity
import com.tuapp.tabatatrainer.data.local.WorkoutSessionEntity
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object ExportUtils {
    private const val TAG = "ExportUtils"

    /**
     * Exporta la sesión a Excel (.xlsx) con 2-3 hojas: Resumen, Datos y GPS (si hay)
     */
    fun exportToExcel(
        context: Context,
        session: WorkoutSessionEntity,
        readings: List<SensorReadingEntity>,
        gpsPoints: List<GpsPointEntity> = emptyList()
    ): File? {
        return try {
            val workbook = XSSFWorkbook()

            // === HOJA 1: RESUMEN ===
            createSummarySheet(workbook, session, gpsPoints.isNotEmpty())

            // === HOJA 2: DATOS (solo si hay lecturas) ===
            if (readings.isNotEmpty()) {
                createDataSheet(workbook, session, readings)
            }

            // === HOJA 3: GPS (si hay datos) ===
            if (gpsPoints.isNotEmpty()) {
                createGpsSheet(workbook, gpsPoints, session)
            }
            
            // === HOJA 4: LAPS (solo para FreeRide) ===
            if (session.activityType == "FREE_RIDE" && gpsPoints.isNotEmpty()) {
                createLapsSheet(workbook, gpsPoints, session)
            }
            
            // === HOJA 5: SENSORES (solo para FreeRide, estadísticas agregadas) ===
            if (session.activityType == "FREE_RIDE") {
                createSensorsSheet(workbook, session)
            }

            // Guardar archivo
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val activityPrefix = if (session.activityType == "FREE_RIDE") "FreeRide" else "Tabata"
            val fileName = "${activityPrefix}_${dateFormat.format(Date(session.startTime))}.xlsx"
            val file = File(context.getExternalFilesDir(null), fileName)

            FileOutputStream(file).use { outputStream ->
                workbook.write(outputStream)
            }
            workbook.close()

            Log.d(TAG, "Excel exportado: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error exportando Excel: ${e.message}", e)
            null
        }
    }

    /**
     * Exporta la ruta GPS a formato GPX
     */
    fun exportToGpx(
        context: Context,
        session: WorkoutSessionEntity,
        gpsPoints: List<GpsPointEntity>
    ): File? {
        if (gpsPoints.isEmpty()) {
            Log.w(TAG, "No hay puntos GPS para exportar")
            return null
        }

        return try {
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "Tabata_${dateFormat.format(Date(session.startTime))}.gpx"
            val file = File(context.getExternalFilesDir(null), fileName)

            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            FileWriter(file).use { writer ->
                writer.write("""<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="TabataTrainer"
     xmlns="http://www.topografix.com/GPX/1/1"
     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
     xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
  <metadata>
    <name>Tabata Training - ${dateFormat.format(Date(session.startTime))}</name>
    <desc>Entrenamiento Tabata: ${session.completedRounds}/${session.totalRounds} rondas</desc>
    <time>${isoFormat.format(Date(session.startTime))}</time>
  </metadata>
  <trk>
    <name>Tabata Workout</name>
    <type>training</type>
    <trkseg>
""")

                gpsPoints.forEach { point ->
                    writer.write("""      <trkpt lat="${point.latitude}" lon="${point.longitude}">
        <ele>${point.altitude ?: 0.0}</ele>
        <time>${isoFormat.format(Date(point.timestamp))}</time>
        <speed>${point.speed ?: 0.0f}</speed>
        <extensions>
          <phase>${point.phase}</phase>
          <round>${point.round}</round>
        </extensions>
      </trkpt>
""")
                }

                writer.write("""    </trkseg>
  </trk>
</gpx>
""")
            }

            Log.d(TAG, "GPX exportado: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error exportando GPX: ${e.message}", e)
            null
        }
    }

    /**
     * Comparte archivo usando Intent
     * Maneja errores del servicio ANT+ que puede activarse durante el share
     */
    fun shareFile(context: Context, file: File, mimeType: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Crear chooser con flags seguros para evitar conflictos con servicios en background
            val chooserIntent = Intent.createChooser(shareIntent, "Compartir entrenamiento").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(chooserIntent)
        } catch (e: IllegalStateException) {
            // Error común cuando la actividad no está en el stack correcto
            Log.e(TAG, "Error de estado al compartir archivo: ${e.message}", e)
            // Intentar de nuevo con flags adicionales
            try {
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir entrenamiento"))
            } catch (e2: Exception) {
                Log.e(TAG, "Error definitivo compartiendo archivo: ${e2.message}", e2)
                throw e2
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error compartiendo archivo: ${e.message}", e)
            throw e // Re-lanzar para que el UI pueda mostrar el error
        }
    }

    // ========================================================================
    // FUNCIONES PRIVADAS PARA CREAR HOJAS EXCEL
    // ========================================================================

    private fun createSummarySheet(
        workbook: XSSFWorkbook,
        session: WorkoutSessionEntity,
        hasGps: Boolean
    ) {
        val sheet = workbook.createSheet("Resumen")
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        // Estilos
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.ORANGE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont()
            font.bold = true
            font.color = IndexedColors.WHITE.index
            setFont(font)
        }

        val labelStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }

        var rowNum = 0

        // Título
        val titleRow = sheet.createRow(rowNum++)
        titleRow.createCell(0).apply {
            setCellValue("RESUMEN DEL ENTRENAMIENTO")
            cellStyle = headerStyle
        }
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 1))

        rowNum++ // Línea vacía

        // Información general
        addSummaryRow(sheet, rowNum++, "Fecha", dateFormat.format(Date(session.startTime)), labelStyle)
        addSummaryRow(sheet, rowNum++, "Duración (seg)", session.totalTimeSeconds.toString(), labelStyle)
        addSummaryRow(sheet, rowNum++, "Estado", if (session.isCompleted) "Completado" else "Detenido", labelStyle)

        rowNum++

        // Configuración
        val configRow = sheet.createRow(rowNum++)
        configRow.createCell(0).apply {
            setCellValue("CONFIGURACIÓN")
            cellStyle = headerStyle
        }
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1))

        addSummaryRow(sheet, rowNum++, "Calentamiento (seg)", session.warmupSeconds.toString(), labelStyle)
        addSummaryRow(sheet, rowNum++, "Trabajo (seg)", session.workSeconds.toString(), labelStyle)
        addSummaryRow(sheet, rowNum++, "Descanso (seg)", session.restSeconds.toString(), labelStyle)
        addSummaryRow(sheet, rowNum++, "Rondas", "${session.completedRounds}/${session.totalRounds}", labelStyle)

        rowNum++

        // Tiempos desglosados
        val timesRow = sheet.createRow(rowNum++)
        timesRow.createCell(0).apply {
            setCellValue("TIEMPOS")
            cellStyle = headerStyle
        }
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1))

        addSummaryRow(sheet, rowNum++, "Tiempo total (seg)", session.totalTimeSeconds.toString(), labelStyle)
        addSummaryRow(sheet, rowNum++, "Tiempo trabajo (seg)", session.workTimeSeconds.toString(), labelStyle)
        addSummaryRow(sheet, rowNum++, "Tiempo descanso (seg)", session.restTimeSeconds.toString(), labelStyle)
        addSummaryRow(sheet, rowNum++, "Tiempo calentamiento (seg)", session.warmupTimeSeconds.toString(), labelStyle)

        rowNum++

        // Frecuencia cardíaca
        val hrRow = sheet.createRow(rowNum++)
        hrRow.createCell(0).apply {
            setCellValue("FRECUENCIA CARDÍACA")
            cellStyle = headerStyle
        }
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1))

        addSummaryRow(sheet, rowNum++, "HR1 Media (bpm)", session.avgHeartRate?.toString() ?: "--", labelStyle)
        addSummaryRow(sheet, rowNum++, "HR1 Máxima (bpm)", session.maxHeartRate?.toString() ?: "--", labelStyle)
        addSummaryRow(sheet, rowNum++, "HR1 Mínima (bpm)", session.minHeartRate?.toString() ?: "--", labelStyle)

        rowNum++

        // HR2
        val hr2Row = sheet.createRow(rowNum++)
        hr2Row.createCell(0).apply {
            setCellValue("FRECUENCIA CARDÍACA 2")
            cellStyle = headerStyle
        }
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1))

        addSummaryRow(sheet, rowNum++, "HR2 Media (bpm)", session.avgHeartRate2?.toString() ?: "--", labelStyle)
        addSummaryRow(sheet, rowNum++, "HR2 Máxima (bpm)", session.maxHeartRate2?.toString() ?: "--", labelStyle)
        addSummaryRow(sheet, rowNum++, "HR2 Mínima (bpm)", session.minHeartRate2?.toString() ?: "--", labelStyle)

        rowNum++

        // Cadencia
        val cadRow = sheet.createRow(rowNum++)
        cadRow.createCell(0).apply {
            setCellValue("CADENCIA")
            cellStyle = headerStyle
        }
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1))

        addSummaryRow(sheet, rowNum++, "Cadencia Media (rpm)", session.avgCadence?.let { String.format("%.1f", it) } ?: "--", labelStyle)
        addSummaryRow(sheet, rowNum++, "Cadencia Máxima (rpm)", session.maxCadence?.let { String.format("%.1f", it) } ?: "--", labelStyle)

        // GPS (si está habilitado)
        if (hasGps && session.gpsEnabled) {
            rowNum++

            val gpsRow = sheet.createRow(rowNum++)
            gpsRow.createCell(0).apply {
                setCellValue("GPS / DISTANCIA")
                cellStyle = headerStyle
            }
            sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1))

            addSummaryRow(sheet, rowNum++, "Distancia (km)", session.totalDistanceMeters?.let { String.format("%.2f", it / 1000f) } ?: "--", labelStyle)
            addSummaryRow(sheet, rowNum++, "Velocidad Media (km/h)", session.avgSpeedKmh?.let { String.format("%.1f", it) } ?: "--", labelStyle)
            addSummaryRow(sheet, rowNum++, "Velocidad Máxima (km/h)", session.maxSpeedKmh?.let { String.format("%.1f", it) } ?: "--", labelStyle)
        }

        // ✅ CORREGIDO: Usar ancho fijo en lugar de autoSizeColumn
        // (autoSizeColumn usa java.awt que no existe en Android)
        sheet.setColumnWidth(0, 30 * 256)  // ~30 caracteres
        sheet.setColumnWidth(1, 20 * 256)  // ~20 caracteres
    }

    private fun addSummaryRow(sheet: Sheet, rowNum: Int, label: String, value: String, labelStyle: CellStyle) {
        val row = sheet.createRow(rowNum)
        row.createCell(0).apply {
            setCellValue(label)
            cellStyle = labelStyle
        }
        row.createCell(1).setCellValue(value)
    }

    private fun createDataSheet(
        workbook: XSSFWorkbook,
        session: WorkoutSessionEntity,
        readings: List<SensorReadingEntity>
    ) {
        val sheet = workbook.createSheet("Datos")

        // Estilos
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }

        val workStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        val restStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.CORAL.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        val warmupStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_ORANGE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        // Cabeceras
        val headerRow = sheet.createRow(0)
        val headers = listOf("Tiempo (s)", "Fase", "Ronda", "HR1 (bpm)", "HR2 (bpm)", "Cadencia (rpm)", "Timestamp")
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }

        // Formateador de fechas (definido UNA vez, fuera del bucle)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        // Datos
        val startTime = session.startTime

        readings.forEachIndexed { index, reading ->
            val row = sheet.createRow(index + 1)
            val elapsedSeconds = ((reading.timestamp - startTime) / 1000).toInt()

            // Determinar estilo según fase
            val phaseStyle = when (reading.phase) {
                "WORK" -> workStyle
                "REST" -> restStyle
                "WARMUP" -> warmupStyle
                else -> null
            }

            row.createCell(0).apply {
                setCellValue(elapsedSeconds.toDouble())
                phaseStyle?.let { cellStyle = it }
            }
            row.createCell(1).apply {
                setCellValue(reading.phase)
                phaseStyle?.let { cellStyle = it }
            }
            row.createCell(2).apply {
                setCellValue(reading.round.toDouble())
                phaseStyle?.let { cellStyle = it }
            }
            row.createCell(3).apply {
                reading.heartRate?.let { setCellValue(it.toDouble()) }
                phaseStyle?.let { cellStyle = it }
            }
            row.createCell(4).apply {
                reading.heartRate2?.let { setCellValue(it.toDouble()) }
                phaseStyle?.let { cellStyle = it }
            }
            row.createCell(5).apply {
                reading.cadence?.let { setCellValue(it.toDouble()) }
                phaseStyle?.let { cellStyle = it }
            }

            // ✅ CORREGIDO: Convertir timestamp a fecha legible
            row.createCell(6).apply {
                val dateString = dateFormat.format(Date(reading.timestamp))
                setCellValue(dateString)
                phaseStyle?.let { cellStyle = it }
            }
        }

        // ✅ CORREGIDO: Usar ancho fijo en lugar de autoSizeColumn
        for (i in 0..5) {
            sheet.setColumnWidth(i, 20 * 256)  // ~20 caracteres
        }
    }

    private fun createGpsSheet(
        workbook: XSSFWorkbook,
        gpsPoints: List<GpsPointEntity>,
        session: WorkoutSessionEntity
    ) {
        val sheet = workbook.createSheet("GPS")

        // Estilos
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }

        // Cabeceras (añadir Distancia acumulada)
        val headerRow = sheet.createRow(0)
        val headers = listOf("Timestamp", "Latitud", "Longitud", "Altitud (m)", "Velocidad (m/s)", "Velocidad (km/h)", "Distancia Acumulada (m)", "Precisión (m)", "Fase", "Ronda")
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }

        // Formateador de fechas
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        // Calcular distancia acumulada desde los puntos GPS
        var accumulatedDistance = 0.0
        var previousLat: Double? = null
        var previousLon: Double? = null

        // Función para calcular distancia entre dos puntos (Haversine)
        fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val earthRadius = 6371000.0 // metros
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return earthRadius * c
        }

        // Datos
        gpsPoints.forEachIndexed { index, point ->
            val row = sheet.createRow(index + 1)

            // Calcular distancia acumulada
            if (previousLat != null && previousLon != null) {
                val distance = calculateDistance(previousLat!!, previousLon!!, point.latitude, point.longitude)
                accumulatedDistance += distance
            }
            previousLat = point.latitude
            previousLon = point.longitude

            // ✅ CORREGIDO: Convertir timestamp a fecha legible
            val dateString = dateFormat.format(Date(point.timestamp))
            row.createCell(0).setCellValue(dateString)

            row.createCell(1).setCellValue(point.latitude)
            row.createCell(2).setCellValue(point.longitude)
            row.createCell(3).setCellValue(point.altitude ?: 0.0)
            row.createCell(4).setCellValue(point.speed?.toDouble() ?: 0.0)
            row.createCell(5).setCellValue((point.speed?.toDouble() ?: 0.0) * 3.6) // m/s a km/h
            row.createCell(6).setCellValue(accumulatedDistance)
            row.createCell(7).setCellValue(point.accuracy?.toDouble() ?: 0.0)
            row.createCell(8).setCellValue(point.phase)
            row.createCell(9).setCellValue(point.round.toDouble())
        }

        // ✅ CORREGIDO: Usar ancho fijo en lugar de autoSizeColumn
        for (i in 0..9) {
            sheet.setColumnWidth(i, 20 * 256)  // ~20 caracteres
        }
    }
    
    /**
     * Crea hoja de Laps para FreeRide basada en puntos GPS agrupados por round
     */
    private fun createLapsSheet(
        workbook: XSSFWorkbook,
        gpsPoints: List<GpsPointEntity>,
        session: WorkoutSessionEntity
    ) {
        val sheet = workbook.createSheet("Laps")

        // Estilos
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }

        // Cabeceras
        val headerRow = sheet.createRow(0)
        val headers = listOf("Lap #", "Tipo", "Tiempo (s)", "Distancia (m)", "Distancia (km)", "Vel. Media (km/h)", "Vel. Máx (km/h)", "HR Media", "HR Máx", "Cadencia Media")
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }

        // Agrupar puntos por round (lap)
        val lapsByRound = gpsPoints.groupBy { it.round }
        
        // Calcular estadísticas por lap
        var rowNum = 1
        lapsByRound.forEach { (round, points) ->
            if (points.isEmpty()) return@forEach
            
            val sortedPoints = points.sortedBy { it.timestamp }
            val startTime = sortedPoints.first().timestamp
            val endTime = sortedPoints.last().timestamp
            val lapTimeSeconds = ((endTime - startTime) / 1000).toInt()
            
            // Calcular distancia del lap
            var lapDistance = 0.0
            for (i in 1 until sortedPoints.size) {
                val p1 = sortedPoints[i - 1]
                val p2 = sortedPoints[i]
                val distance = calculateHaversineDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                lapDistance += distance
            }
            
            // Calcular velocidades
            val speeds = sortedPoints.mapNotNull { it.speed?.toDouble()?.times(3.6) } // km/h
            val avgSpeed = if (speeds.isNotEmpty()) speeds.average() else 0.0
            val maxSpeed = speeds.maxOrNull() ?: 0.0
            
            // Determinar tipo de lap (auto-lap si es múltiplo de 1km aproximadamente)
            val isAutoLap = lapDistance >= 900 && lapDistance <= 1100 // Entre 900m y 1.1km
            
            val row = sheet.createRow(rowNum++)
            row.createCell(0).setCellValue(round.toDouble())
            row.createCell(1).setCellValue(if (isAutoLap) "Auto (1km)" else "Manual")
            row.createCell(2).setCellValue(lapTimeSeconds.toDouble())
            row.createCell(3).setCellValue(lapDistance)
            row.createCell(4).setCellValue(lapDistance / 1000.0)
            row.createCell(5).setCellValue(avgSpeed)
            row.createCell(6).setCellValue(maxSpeed)
            // HR y Cadencia no están en GPS points, se dejan vacíos
            row.createCell(7).setCellValue("--")
            row.createCell(8).setCellValue("--")
            row.createCell(9).setCellValue("--")
        }

        // Ajustar anchos
        for (i in 0..9) {
            sheet.setColumnWidth(i, 18 * 256)
        }
    }
    
    /**
     * Crea hoja de Sensores con estadísticas agregadas para FreeRide
     */
    private fun createSensorsSheet(
        workbook: XSSFWorkbook,
        session: WorkoutSessionEntity
    ) {
        val sheet = workbook.createSheet("Sensores")

        // Estilos
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }
        
        val labelStyle = workbook.createCellStyle()

        var rowNum = 0

        // Título
        val titleRow = sheet.createRow(rowNum++)
        titleRow.createCell(0).apply {
            setCellValue("ESTADÍSTICAS DE SENSORES")
            cellStyle = headerStyle
        }
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1))

        rowNum++

        // Frecuencia Cardíaca
        val hrRow = sheet.createRow(rowNum++)
        hrRow.createCell(0).apply {
            setCellValue("FRECUENCIA CARDÍACA")
            cellStyle = headerStyle
        }
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1))

        addSummaryRow(sheet, rowNum++, "HR Media (bpm)", session.avgHeartRate?.toString() ?: "--", labelStyle)
        addSummaryRow(sheet, rowNum++, "HR Máxima (bpm)", session.maxHeartRate?.toString() ?: "--", labelStyle)
        addSummaryRow(sheet, rowNum++, "HR Mínima (bpm)", session.minHeartRate?.toString() ?: "--", labelStyle)

        rowNum++

        // Cadencia
        val cadRow = sheet.createRow(rowNum++)
        cadRow.createCell(0).apply {
            setCellValue("CADENCIA")
            cellStyle = headerStyle
        }
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1))

        addSummaryRow(sheet, rowNum++, "Cadencia Media (rpm)", session.avgCadence?.toString() ?: "--", labelStyle)
        addSummaryRow(sheet, rowNum++, "Cadencia Máxima (rpm)", session.maxCadence?.toString() ?: "--", labelStyle)

        // Ajustar anchos
        sheet.setColumnWidth(0, 30 * 256)
        sheet.setColumnWidth(1, 20 * 256)
    }
    
    /**
     * Calcula distancia entre dos puntos usando fórmula de Haversine
     */
    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }
}
