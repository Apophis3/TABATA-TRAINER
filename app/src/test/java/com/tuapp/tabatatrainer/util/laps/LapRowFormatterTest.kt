package com.tuapp.tabatatrainer.util.laps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LapRowFormatterTest {

    private fun row(type: LapType, n: Int, meters: Float, seconds: Int) =
        LapRowFormatter.format(type, n, meters, seconds, Locale.US)

    @Test
    fun auto_km_at_5_30_shows_pace_5_30() {
        val r = row(LapType.AUTO, 3, 1000f, 330)
        assertEquals("A3", r.label)
        assertEquals("1.00", r.distanceKm)
        assertEquals("05:30", r.time)
        assertEquals("5:30", r.pace)
    }

    @Test
    fun manual_lap_at_6_00_shows_pace_6_00_using_its_own_distance() {
        val r = row(LapType.MANUAL, 2, 500f, 180)    // 3:00 por 500 m = 6:00/km
        assertEquals("M2", r.label)
        assertEquals("0.50", r.distanceKm)
        assertEquals("6:00", r.pace)
    }

    @Test
    fun zero_distance_shows_double_dash_never_zero_pace() {
        val r = row(LapType.MANUAL, 1, 0f, 15)       // serie explosiva en el sitio
        assertEquals("--", r.pace)
        assertEquals("0.00", r.distanceKm)
        assertEquals("00:15", r.time)
    }

    @Test
    fun practically_standing_still_shows_double_dash() {
        assertEquals("--", row(LapType.MANUAL, 1, 3f, 20).pace)    // 111:07 /km
    }

    @Test
    fun style_depends_only_on_type() {
        // Misma geometría que un AUTO típico, pero MANUAL: el estilo sigue al tipo, no a la distancia
        val manualOf1000 = row(LapType.MANUAL, 1, 1000f, 330)
        val autoOf200 = row(LapType.AUTO, 1, 200f, 60)
        assertTrue(manualOf1000.style.bold)
        assertTrue(manualOf1000.style.showFlag)
        assertFalse(autoOf200.style.bold)
        assertFalse(autoOf200.style.showFlag)
        assertEquals("M", manualOf1000.tag)
        assertEquals("A", autoOf200.tag)
        // y no cambia con el número ni con la posición
        assertEquals(row(LapType.MANUAL, 1, 10f, 10).style, row(LapType.MANUAL, 99, 5000f, 1800).style)
        assertEquals(row(LapType.AUTO, 1, 10f, 10).style, row(LapType.AUTO, 99, 1000f, 330).style)
    }

    @Test
    fun auto_and_manual_look_different_in_color_weight_and_background() {
        val a = LapRowFormatter.AUTO_STYLE
        val m = LapRowFormatter.MANUAL_STYLE
        assertNotEquals(a.textColorArgb, m.textColorArgb)
        assertNotEquals(a.bold, m.bold)
        assertNotEquals(a.rowBackgroundArgb, m.rowBackgroundArgb)
    }

    @Test
    fun text_colors_have_contrast_of_4_5_or_more_on_the_screen_background() {
        // Fondo de la pantalla: degradado CoolGray 0xFF36454F ↔ 0xFF2D2D2D (el más claro es el caso peor)
        val lightestBackground = 0xFF36454FL
        val darkestBackground = 0xFF2D2D2DL
        listOf(LapRowFormatter.AUTO_COLOR, LapRowFormatter.MANUAL_COLOR).forEach { c ->
            assertTrue(LapRowFormatter.contrastRatio(c, lightestBackground) >= 4.5)
            assertTrue(LapRowFormatter.contrastRatio(c, darkestBackground) >= 4.5)
        }
    }

    @Test
    fun contrast_helper_matches_known_wcag_values() {
        assertEquals(21.0, LapRowFormatter.contrastRatio(0xFFFFFFFFL, 0xFF000000L), 0.01)
        assertEquals(1.0, LapRowFormatter.contrastRatio(0xFF777777L, 0xFF777777L), 0.001)
    }

    @Test
    fun time_over_an_hour_uses_h_mm_ss() {
        assertEquals("1:02:03", LapRowFormatter.formatTime(3723))
        assertEquals("00:00", LapRowFormatter.formatTime(-4))
    }
}
