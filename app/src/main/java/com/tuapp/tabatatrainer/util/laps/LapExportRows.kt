package com.tuapp.tabatatrainer.util.laps

import com.tuapp.tabatatrainer.data.local.LapEntity

/** Fila de la hoja "Laps" del Excel, ya calculada a partir de una vuelta persistida (spec 006, RF-P2) */
data class LapExportRow(
    val order: Int,                 // orden global (AUTO y MANUAL mezcladas)
    val type: LapType,
    val typeLabel: String,          // "Auto" | "Manual"
    val typeNumber: Int,            // número dentro de su serie
    val durationSeconds: Int,
    val distanceMeters: Double,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val avgCadence: Float?,
    val steps: Int?
)

/**
 * Convierte las vueltas guardadas en filas de exportación. El tipo sale SIEMPRE del `type` persistido:
 * ya no se deduce de la distancia (antes: 900–1100 m = auto).
 */
object LapExportRows {

    fun from(laps: List<LapEntity>): List<LapExportRow> =
        laps.sortedBy { it.lapNumber }.map { lap ->
            val type = typeOf(lap.type)
            LapExportRow(
                order = lap.lapNumber,
                type = type,
                typeLabel = if (type == LapType.AUTO) "Auto" else "Manual",
                typeNumber = lap.typeNumber,
                durationSeconds = LapDuration.secondsOf(lap),
                distanceMeters = (lap.endDistanceMeters - lap.startDistanceMeters).coerceAtLeast(0f).toDouble(),
                avgSpeedKmh = lap.avgSpeedKmh.toDouble(),
                maxSpeedKmh = lap.maxSpeedKmh.toDouble(),
                avgHeartRate = lap.avgHeartRate,
                maxHeartRate = lap.maxHeartRate,
                avgCadence = lap.avgCadence,
                steps = lap.steps
            )
        }

    /** Valor desconocido (fila de una versión futura o corrupta) → MANUAL, que es el valor por defecto de la columna */
    fun typeOf(raw: String): LapType = LapType.values().firstOrNull { it.name == raw } ?: LapType.MANUAL
}
