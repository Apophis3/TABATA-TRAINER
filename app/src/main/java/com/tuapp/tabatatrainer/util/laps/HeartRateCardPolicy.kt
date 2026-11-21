package com.tuapp.tabatatrainer.util.laps

import com.tuapp.tabatatrainer.sensor.HeartRateHub

/** Cómo se pinta la tarjeta de pulso de la página 1 de Ruta (spec 007, R7.1) */
enum class HeartRateCardLayout { SPLIT, SINGLE_HR1, SINGLE_HR2 }

/**
 * Tarjeta de pulso de Ruta (spec 007, R7.1, plan §9). Sin Android.
 * - Un solo hueco con señal reciente → tarjeta única de ese hueco; los dos → partida HR1 | HR2.
 * - Ninguno → se queda como estaba: una caída corta de la única banda no cambia la tarjeta.
 * - Un cambio solo se aplica si se mantiene [holdMs]: pasar de única a partida exige más que [STALE_MS] para que
 *   un dato suelto de la banda quitada (al reconectarse sola) no parta la tarjeta un momento.
 */
object HeartRateCardPolicy {

    /** Lo que tarda una señal en dejar de ser reciente (`HeartRateHub.STALE_MS`) */
    const val STALE_MS = HeartRateHub.STALE_MS
    const val SINGLE_TO_SPLIT_HOLD_MS = 8_000L
    const val DEFAULT_HOLD_MS = 2_000L

    fun desired(hr1Fresh: Boolean, hr2Fresh: Boolean, current: HeartRateCardLayout): HeartRateCardLayout = when {
        hr1Fresh && hr2Fresh -> HeartRateCardLayout.SPLIT
        hr1Fresh -> HeartRateCardLayout.SINGLE_HR1
        hr2Fresh -> HeartRateCardLayout.SINGLE_HR2
        else -> current
    }

    /** Tiempo que [to] debe seguir siendo la deseada antes de sustituir a [from] */
    fun holdMs(from: HeartRateCardLayout, to: HeartRateCardLayout): Long = when {
        from == to -> 0L
        from != HeartRateCardLayout.SPLIT && to == HeartRateCardLayout.SPLIT -> SINGLE_TO_SPLIT_HOLD_MS
        else -> DEFAULT_HOLD_MS
    }
}
