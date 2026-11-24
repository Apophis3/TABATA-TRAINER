package com.tuapp.tabatatrainer.rawlog

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class RawCsvFormatTest {

    private lateinit var savedLocale: Locale

    @Before
    fun spanishPhone() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("es-ES"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(savedLocale)
    }

    @Test
    fun decimals_use_a_dot_even_on_a_spanish_phone() {
        assertEquals("40.4168", RawCsvFormat.num(40.4168))
        assertEquals("-3.7038", RawCsvFormat.num(-3.7038))
        assertEquals("1013.25", RawCsvFormat.num(1013.25f))
        assertEquals("3.0", RawCsvFormat.num(3f))
    }

    @Test
    fun small_and_large_numbers_never_use_scientific_notation() {
        // 1.0E-5 → 0.000010: se conservan las cifras que da Java, solo se quita la «E»
        assertEquals("0.000010", RawCsvFormat.num(0.00001))
        assertEquals("0.000010", RawCsvFormat.num(1.0E-5f))
        assertEquals("12345678900000", RawCsvFormat.num(1.23456789E13))
    }

    @Test
    fun missing_values_are_empty_never_zero() {
        assertEquals("", RawCsvFormat.num(null as Double?))
        assertEquals("", RawCsvFormat.num(null as Float?))
        assertEquals("", RawCsvFormat.num(null as Long?))
        assertEquals("", RawCsvFormat.num(null as Int?))
        assertEquals("", RawCsvFormat.bool(null))
        assertEquals("", RawCsvFormat.text(null))
        assertEquals("", RawCsvFormat.num(Double.NaN))
        assertEquals("", RawCsvFormat.num(Float.POSITIVE_INFINITY))
    }

    @Test
    fun zero_stays_zero() {
        assertEquals("0.0", RawCsvFormat.num(0.0))
        assertEquals("0", RawCsvFormat.num(0L))
        assertEquals("false", RawCsvFormat.bool(false))
    }

    @Test
    fun plain_text_is_not_quoted() {
        assertEquals("fused", RawCsvFormat.text("fused"))
        assertEquals("interactive=true", RawCsvFormat.text("interactive=true"))
    }

    @Test
    fun text_with_separator_quotes_or_newlines_is_quoted_without_losing_anything() {
        assertEquals("\"fine=true;coarse=true\"", RawCsvFormat.text("fine=true;coarse=true"))
        assertEquals("\"dijo \"\"hola\"\"\"", RawCsvFormat.text("dijo \"hola\""))
        assertEquals("\"línea 1\nlínea 2\"", RawCsvFormat.text("línea 1\nlínea 2"))
        assertEquals("\"a\r\nb\"", RawCsvFormat.text("a\r\nb"))
    }

    @Test
    fun row_and_header_use_semicolon() {
        assertEquals("a;b;;d", RawCsvFormat.row("a", "b", "", "d"))
        assertEquals("epoch_ms;elapsed_ns", RawCsvFormat.header("epoch_ms", "elapsed_ns"))
    }
}
