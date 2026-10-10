package com.tuapp.tabatatrainer.util.laps

import java.util.Locale

/**
 * Estilo visual de una fila de vuelta. Sale SOLO del [LapType] (RF-5.2), nunca de la posición ni de la distancia.
 * Los colores son ARGB (Long) para poder verificar el contraste en tests JVM sin Compose.
 */
data class LapRowStyle(
    val bold: Boolean,
    val textColorArgb: Long,
    val rowBackgroundArgb: Long,   // 0x00000000 = sin fondo
    val showFlag: Boolean          // refuerzo no cromático: bandera en las manuales
)

/** Fila lista para pintar: `etiqueta+nº · distancia · tiempo · ritmo` (R6) */
data class LapRowModel(
    val tag: String,          // "A" | "M"
    val label: String,        // "A3", "M2" (etiqueta + número dentro de su serie)
    val distanceKm: String,   // "1.00"
    val time: String,         // "05:30" | "1:02:03"
    val pace: String,         // "5:30" | "--"
    val style: LapRowStyle
)

/**
 * Formatea una vuelta para la lista (portrait y landscape comparten esto).
 *
 * - AUTO: tenue, color secundario, peso normal, etiqueta "A".
 * - MANUAL: negrita, color de acento, etiqueta "M", bandera y fondo tintado.
 * - Ritmo = tiempo de ESA vuelta / distancia de ESA vuelta; distancia 0 → "--" (nunca "0:00").
 */
object LapRowFormatter {

    /** Contra el fondo más claro de la pantalla (CoolGray 0xFF36454F) ambos colores superan 4.5:1 */
    const val AUTO_COLOR = 0xFFB0BEC5L
    const val MANUAL_COLOR = 0xFFFFB74DL
    const val MANUAL_ROW_BACKGROUND = 0x1FFFB74DL

    /** Por encima de 60:00 /km la vuelta es en la práctica "parado": se muestra "--" */
    const val MAX_LAP_PACE_SEC_PER_KM = 3600

    val AUTO_STYLE = LapRowStyle(bold = false, textColorArgb = AUTO_COLOR, rowBackgroundArgb = 0x00000000L, showFlag = false)
    val MANUAL_STYLE = LapRowStyle(bold = true, textColorArgb = MANUAL_COLOR, rowBackgroundArgb = MANUAL_ROW_BACKGROUND, showFlag = true)

    fun styleOf(type: LapType): LapRowStyle = if (type == LapType.AUTO) AUTO_STYLE else MANUAL_STYLE

    fun tagOf(type: LapType): String = if (type == LapType.AUTO) "A" else "M"

    fun format(
        type: LapType,
        typeNumber: Int,
        distanceMeters: Float,
        durationSeconds: Int,
        locale: Locale = Locale.getDefault()
    ): LapRowModel {
        val tag = tagOf(type)
        val paceSec = PaceCalculator.secPerKm(durationSeconds, distanceMeters)
            ?.takeIf { it <= MAX_LAP_PACE_SEC_PER_KM }
        return LapRowModel(
            tag = tag,
            label = "$tag$typeNumber",
            distanceKm = String.format(locale, "%.2f", distanceMeters.coerceAtLeast(0f) / 1000f),
            time = formatTime(durationSeconds),
            pace = PaceCalculator.format(paceSec),
            style = styleOf(type)
        )
    }

    fun formatTime(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    /** Contraste WCAG entre dos colores ARGB opacos (se ignora el alfa) */
    fun contrastRatio(foregroundArgb: Long, backgroundArgb: Long): Double {
        val l1 = relativeLuminance(foregroundArgb)
        val l2 = relativeLuminance(backgroundArgb)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun relativeLuminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
