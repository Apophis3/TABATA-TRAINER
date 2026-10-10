package com.tuapp.tabatatrainer.util.laps

/**
 * Valida una vuelta manual candidata antes de crearla.
 *
 * Solo cuenta el TIEMPO: en series explosivas o ejercicios en el sitio (tabatas al aire libre)
 * el GPS apenas se mueve, y una vuelta de 10 o 20 s con 0 m es perfectamente válida.
 * Una vuelta de < 5 s es un toque accidental. Lógica pura, sin Android.
 */
object LapGuard {

    const val MIN_DURATION_SECONDS = 5

    enum class Verdict { OK, TOO_SHORT_TIME }

    fun check(durationSeconds: Int): Verdict =
        if (durationSeconds < MIN_DURATION_SECONDS) Verdict.TOO_SHORT_TIME else Verdict.OK
}
