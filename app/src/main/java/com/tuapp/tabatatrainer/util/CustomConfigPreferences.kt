package com.tuapp.tabatatrainer.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Utilidad para guardar y cargar la última configuración CUSTOM usada
 */
object CustomConfigPreferences {
    private const val PREFS_NAME = "custom_workout_config"
    private const val KEY_WARMUP = "warmup_seconds"
    private const val KEY_WORK = "work_seconds"
    private const val KEY_REST = "rest_seconds"
    private const val KEY_ROUNDS = "rounds"

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Guarda la configuración CUSTOM
     */
    fun saveCustomConfig(
        context: Context,
        warmup: Int,
        work: Int,
        rest: Int,
        rounds: Int
    ) {
        getSharedPreferences(context).edit().apply {
            putInt(KEY_WARMUP, warmup)
            putInt(KEY_WORK, work)
            putInt(KEY_REST, rest)
            putInt(KEY_ROUNDS, rounds)
            apply()
        }
    }

    /**
     * Carga la configuración CUSTOM guardada
     * Retorna null si no hay configuración guardada
     */
    fun loadCustomConfig(context: Context): CustomWorkoutConfig? {
        val prefs = getSharedPreferences(context)
        val warmup = prefs.getInt(KEY_WARMUP, -1)
        val work = prefs.getInt(KEY_WORK, -1)
        val rest = prefs.getInt(KEY_REST, -1)
        val rounds = prefs.getInt(KEY_ROUNDS, -1)

        return if (warmup >= 0 && work >= 0 && rest >= 0 && rounds >= 0) {
            CustomWorkoutConfig(warmup, work, rest, rounds)
        } else {
            null
        }
    }
}

data class CustomWorkoutConfig(
    val warmup: Int,
    val work: Int,
    val rest: Int,
    val rounds: Int
)
