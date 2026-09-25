package com.tuapp.tabatatrainer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ============================================================================
// COLORES BASE (compatibilidad con código existente)
// ============================================================================

val Green500 = Color(0xFF4CAF50)
val Green700 = Color(0xFF388E3C)
val Orange500 = Color(0xFFFFA726)
val Red500 = Color(0xFFF44336)
val Blue500 = Color(0xFF2196F3)

// ============================================================================
// TABATA COLORS - Paleta centralizada de la app
// ============================================================================

object TabataColors {
    // === Colores principales (Pantone Nike) ===
    val VibrantOrange = Color(0xFFFF5F1F)    // Pantone 165 C aproximado
    val CoolGray = Color(0xFF36454F)          // Cool Gray 11 C aproximado

    // === Colores de fase (entrenamiento) ===
    val WarmupOrange = Color(0xFFFFA726)      // Calentamiento
    val WorkGreen = Color(0xFF4CAF50)         // Trabajo
    val RestRed = Color(0xFFF44336)           // Descanso
    val RoundsBlue = Color(0xFF2196F3)        // Rondas/info

    // === Estados de sensores ===
    val Connected = Color(0xFF4CAF50)         // Verde - conectado
    val Scanning = Color(0xFFFFA726)          // Naranja - buscando
    val Disconnected = Color(0xFF9E9E9E)      // Gris - desconectado
    val Error = Color(0xFFF44336)             // Rojo - error

    // === Fondos y superficies ===
    val CardBackground = Color(0xFF1E1E1E)
    val CardBackgroundLight = Color(0xFF2D2D2D)
    val SurfaceDark = Color(0xFF121212)

    // === Transparencias ===
    val SemiTransparentDark = Color(0x40000000)    // 25% negro
    val SemiTransparentLight = Color(0x1AFFFFFF)   // 10% blanco
    val OverlayDark = Color(0x80000000)            // 50% negro

    // === Helpers para fases ===
    fun phaseColor(phase: String): Color = when (phase.lowercase()) {
        "warmup", "calentamiento" -> WarmupOrange
        "work", "trabajo" -> WorkGreen
        "rest", "descanso" -> RestRed
        else -> CoolGray
    }
}

// ============================================================================
// TABATA SIZES - Dimensiones centralizadas
// ============================================================================

object TabataSizes {
    // === Tamaños de Timer ===
    val TimerGiant = 120.sp      // Pantalla activa - principal
    val TimerLarge = 80.sp       // Pantalla activa - horizontal/compacto
    val TimerMedium = 48.sp      // Banners, resúmenes
    val TimerSmall = 32.sp       // Widgets pequeños

    // === Tamaños de texto ===
    val PhaseTitle = 28.sp       // "TRABAJO", "DESCANSO"
    val SectionTitle = 20.sp     // Títulos de sección
    val SensorValue = 24.sp      // Valores de HR, Cadencia
    val LabelLarge = 18.sp       // Labels prominentes
    val LabelMedium = 14.sp      // Labels normales
    val LabelSmall = 11.sp       // Labels secundarios

    // === Espaciados ===
    val PaddingTiny = 4.dp
    val PaddingSmall = 8.dp
    val PaddingMedium = 16.dp
    val PaddingLarge = 24.dp
    val PaddingXLarge = 32.dp

    // === Tamaños de iconos ===
    val IconTiny = 16.dp
    val IconSmall = 20.dp
    val IconMedium = 28.dp
    val IconLarge = 48.dp
    val IconXLarge = 64.dp

    // === Componentes ===
    val ButtonHeight = 56.dp
    val ButtonHeightSmall = 44.dp
    val SliderHeight = 48.dp
    val CardCornerRadius = 12.dp
    val ChipCornerRadius = 16.dp

    // === FAB ===
    val FabSize = 64.dp
    val FabIconSize = 32.dp

    // === Indicadores ===
    val StatusDotSmall = 6.dp
    val StatusDotMedium = 8.dp
    val StatusDotLarge = 10.dp
}

// ============================================================================
// MATERIAL THEME SCHEMES
// ============================================================================

private val DarkColorScheme = darkColorScheme(
    primary = Green500,
    secondary = Orange500,
    tertiary = Blue500,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    primaryContainer = Color(0xFF1B3D1E),      // Verde oscuro para containers
    secondaryContainer = Color(0xFF2D2D2D),    // Gris para cards
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onPrimaryContainer = Color.White,
    onSecondaryContainer = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = Green700,
    secondary = Orange500,
    tertiary = Blue500,
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    primaryContainer = Color(0xFFB8E6B9),
    secondaryContainer = Color(0xFFFFE0B2),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    onPrimaryContainer = Color(0xFF1C1B1F),
    onSecondaryContainer = Color(0xFF1C1B1F),
)

// ============================================================================
// THEME COMPOSABLE
// ============================================================================

@Composable
fun TabataTrainerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,  // Cambiado a false para usar nuestros colores
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}