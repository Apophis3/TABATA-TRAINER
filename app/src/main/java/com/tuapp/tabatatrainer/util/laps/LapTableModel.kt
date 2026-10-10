package com.tuapp.tabatatrainer.util.laps

import com.tuapp.tabatatrainer.service.LapData
import java.util.Locale

/**
 * Fila de la tabla de la ventana 4 (spec 006, R8). [order] es la columna fija (nº global) y [cells] las 9 columnas
 * que se desplazan en horizontal, en el orden de [LapTableModel.SCROLL_HEADERS].
 */
data class LapTableRow(
    val order: Int,
    val style: LapRowStyle,
    val cells: List<String>
)

/**
 * Qué muestra cada celda de la tabla de vueltas. Sin Android: lo que se pinta se decide aquí.
 * - 10 columnas: nº (fija) · tipo · km · tiempo · ritmo · FC · FC máx · pasos · cadencia · desnivel (RF-8.1).
 * - Dato ausente → "--", nunca 0 (RF-7.6). Ritmo con la misma regla que la lista ([LapRowFormatter]).
 * - Cadencia: pasos ÷ minutos de ESA vuelta (ppm); con sensor de bici (rpm ≠ ppm) se muestra la cadencia del sensor
 *   con su unidad, nunca ppm (RF-7.5).
 * - Estilo AUTO tenue / MANUAL negrita sale del tipo (RF-5.2), igual que en la lista.
 */
object LapTableModel {

    const val NO_DATA = "--"
    const val FIXED_HEADER = "#"

    /** Cabeceras de las columnas que se desplazan en horizontal (la columna nº va aparte, fija) */
    val SCROLL_HEADERS = listOf("Tipo", "Km", "Tiempo", "Ritmo", "FC", "FC máx", "Pasos", "Cadencia", "Desnivel +")

    /** Total de columnas incluida la fija */
    val COLUMN_COUNT: Int get() = 1 + SCROLL_HEADERS.size

    /** La más reciente primero (igual que la lista de la ventana 3) */
    fun rows(laps: List<LapData>, locale: Locale = Locale.getDefault()): List<LapTableRow> =
        laps.sortedByDescending { it.number }.map { rowOf(it, locale) }

    private fun rowOf(lap: LapData, locale: Locale): LapTableRow {
        val duration = lap.endTimeSeconds - lap.startTimeSeconds
        val base = LapRowFormatter.format(lap.type, lap.typeNumber, lap.distanceMeters, duration, locale)
        return LapTableRow(
            order = lap.number,
            style = base.style,
            cells = listOf(
                base.label,
                base.distanceKm,
                base.time,
                base.pace,
                if (lap.avgHeartRate > 0) "${lap.avgHeartRate}" else NO_DATA,
                if (lap.maxHeartRate > 0) "${lap.maxHeartRate}" else NO_DATA,
                lap.steps?.toString() ?: NO_DATA,
                cadenceCell(lap, duration),
                lap.elevationGain?.let { String.format(locale, "%.0f", it) } ?: NO_DATA
            )
        )
    }

    private fun cadenceCell(lap: LapData, duration: Int): String =
        if (lap.cadenceSource == CadenceSource.BIKE_SENSOR) {
            if (lap.avgCadence > 0f) "${Math.round(lap.avgCadence)} rpm" else NO_DATA
        } else {
            LapBlockMetrics.cadencePpm(lap.steps, duration, lap.cadenceSource)?.let { "$it ppm" } ?: NO_DATA
        }
}
