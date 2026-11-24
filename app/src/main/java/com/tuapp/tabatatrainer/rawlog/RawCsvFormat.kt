package com.tuapp.tabatatrainer.rawlog

import java.math.BigDecimal

/**
 * Formato común de los CSV del registro en bruto (spec 008, R5).
 *
 * - Separador `;`, una fila por línea (`\n`).
 * - Números siempre con punto decimal y sin notación científica, sea cual sea el idioma del móvil
 *   (`toString` de Kotlin no depende del idioma; `BigDecimal.toPlainString` quita el `E`).
 * - Un dato ausente es un campo vacío, nunca 0.
 * - Textos con `;`, comillas o saltos de línea van entre comillas dobles y las comillas internas se duplican.
 */
object RawCsvFormat {

    const val SEPARATOR = ";"
    const val LINE_END = "\n"

    fun num(value: Double?): String =
        if (value == null || value.isNaN() || value.isInfinite()) "" else BigDecimal(value.toString()).toPlainString()

    fun num(value: Float?): String =
        if (value == null || value.isNaN() || value.isInfinite()) "" else BigDecimal(value.toString()).toPlainString()

    fun num(value: Long?): String = value?.toString() ?: ""

    fun num(value: Int?): String = value?.toString() ?: ""

    fun bool(value: Boolean?): String = value?.toString() ?: ""

    fun text(value: String?): String {
        if (value == null) return ""
        val needsQuotes = value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    fun row(vararg fields: String): String = fields.joinToString(SEPARATOR)

    fun header(vararg columns: String): String = columns.joinToString(SEPARATOR)
}
