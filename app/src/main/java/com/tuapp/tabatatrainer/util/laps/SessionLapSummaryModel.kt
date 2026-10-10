package com.tuapp.tabatatrainer.util.laps

import com.tuapp.tabatatrainer.data.local.LapEntity
import java.util.Locale

data class LapSummaryItem(val label: String, val value: String)

/** Resumen de UNA vuelta manual para el detalle de sesión (spec 006, R7) */
data class ManualLapSummary(val title: String, val items: List<LapSummaryItem>)

/**
 * Qué se muestra por cada vuelta manual al terminar la sesión. Sin Android: lo que se pinta se decide aquí.
 * - Solo vueltas MANUAL (las AUTO no llevan resumen).
 * - Dato ausente → "--", nunca 0 (RF-7.6).
 * - La cadencia ppm solo aparece si la fuente no es el sensor de bici (RF-7.5): en ese caso el ítem se omite.
 */
object SessionLapSummaryModel {

    const val NO_DATA = "--"

    fun from(laps: List<LapEntity>, locale: Locale = Locale.getDefault()): List<ManualLapSummary> =
        laps.filter { LapExportRows.typeOf(it.type) == LapType.MANUAL }
            .sortedBy { it.lapNumber }
            .map { summaryOf(it, locale) }

    private fun summaryOf(lap: LapEntity, locale: Locale): ManualLapSummary {
        val duration = LapDuration.secondsOf(lap)
        val meters = (lap.endDistanceMeters - lap.startDistanceMeters).coerceAtLeast(0f)
        val source = lap.cadenceSource?.let { name -> CadenceSource.values().firstOrNull { it.name == name } }

        val pace = PaceCalculator.secPerKm(duration, meters)
            ?.takeIf { it <= LapRowFormatter.MAX_LAP_PACE_SEC_PER_KM }
        val items = mutableListOf(
            LapSummaryItem("Tiempo", LapRowFormatter.formatTime(duration)),
            LapSummaryItem("Distancia", String.format(locale, "%.2f km", meters / 1000f)),
            LapSummaryItem("Ritmo medio", if (pace != null) PaceCalculator.format(pace) + " /km" else NO_DATA),
            LapSummaryItem(
                "Vel. media",
                if (meters > 0f && lap.avgSpeedKmh > 0f) String.format(locale, "%.1f km/h", lap.avgSpeedKmh) else NO_DATA
            ),
            LapSummaryItem("FC media", lap.avgHeartRate?.toString() ?: NO_DATA),
            LapSummaryItem("FC máx", lap.maxHeartRate?.toString() ?: NO_DATA),
            LapSummaryItem("Pasos", lap.steps?.toString() ?: NO_DATA)
        )
        if (source != CadenceSource.BIKE_SENSOR) {
            val ppm = LapBlockMetrics.cadencePpm(lap.steps, duration, source)
            items += LapSummaryItem("Cadencia", if (ppm != null) "$ppm ppm" else NO_DATA)
        }
        items += LapSummaryItem(
            "Desnivel +",
            lap.elevationGain?.let { String.format(locale, "%.0f m", it) } ?: NO_DATA
        )

        val n = if (lap.typeNumber > 0) lap.typeNumber else lap.lapNumber
        return ManualLapSummary(title = "M$n", items = items)
    }
}
