package com.tuapp.tabatatrainer.util.laps

import com.tuapp.tabatatrainer.service.LapData
import java.util.Locale

/** Textos ya formateados de la vuelta manual en curso (ventana 3, spec 006 RF-8.7) */
data class CurrentLapView(
    val pace: String,            // "5:30" | "--"
    val steps: String,           // "412" | "--"
    val cadenceValue: String,    // "171" | "--"
    val cadenceUnit: String,     // "ppm" | "rpm"
    val elevation: String        // "8" | "--"
)

/** Resumen de la última vuelta manual cerrada */
data class LastManualLap(
    val label: String,   // "M2"
    val time: String,
    val pace: String,
    val heartRate: String,
    val steps: String
)

/**
 * Qué muestra la ventana 3 de la vuelta en curso. Sin Android: los números entran por parámetro.
 * Sin dato → "--", nunca 0 (RF-7.6). La cadencia en ppm es la de ESTA vuelta (pasos ÷ minutos de la vuelta);
 * si el sensor de bici está emitiendo se muestra su cadencia en rpm, y nunca se mezclan las dos (RF-7.5).
 */
object CurrentLapModel {

    const val NO_DATA = "--"

    /** Antes de estos mínimos el ritmo y la cadencia de la vuelta bailan demasiado para ser útiles */
    const val MIN_METERS_FOR_PACE = 10f
    const val MIN_SECONDS_FOR_CADENCE = 10

    fun of(
        lapSeconds: Int,
        lapMeters: Float,
        lapSteps: Int?,
        lapElevationGainM: Float?,
        cadenceActive: Boolean,
        cadenceRpm: Float,
        locale: Locale = Locale.getDefault()
    ): CurrentLapView {
        val paceSec = if (lapMeters >= MIN_METERS_FOR_PACE) {
            PaceCalculator.secPerKm(lapSeconds, lapMeters)?.takeIf { it <= LapRowFormatter.MAX_LAP_PACE_SEC_PER_KM }
        } else {
            null
        }
        val ppm = if (lapSeconds >= MIN_SECONDS_FOR_CADENCE) {
            LapBlockMetrics.cadencePpm(lapSteps, lapSeconds, CadenceSource.PEDOMETER)
        } else {
            null
        }
        return CurrentLapView(
            pace = PaceCalculator.format(paceSec),
            steps = lapSteps?.toString() ?: NO_DATA,
            cadenceValue = if (cadenceActive) String.format(locale, "%.0f", cadenceRpm) else ppm?.toString() ?: NO_DATA,
            cadenceUnit = if (cadenceActive) "rpm" else "ppm",
            elevation = lapElevationGainM?.let { String.format(locale, "%.0f", it) } ?: NO_DATA
        )
    }

    /** Última vuelta MANUAL cerrada (la de mayor orden global), o null si aún no hay ninguna */
    fun lastManual(laps: List<LapData>, locale: Locale = Locale.getDefault()): LastManualLap? {
        val lap = laps.filter { it.type == LapType.MANUAL }.maxByOrNull { it.number } ?: return null
        val cells = LapTableModel.rows(listOf(lap), locale).single().cells
        fun at(header: String) = cells[LapTableModel.SCROLL_HEADERS.indexOf(header)]
        return LastManualLap(
            label = at("Tipo"),
            time = at("Tiempo"),
            pace = at("Ritmo"),
            heartRate = at("FC"),
            steps = at("Pasos")
        )
    }
}
