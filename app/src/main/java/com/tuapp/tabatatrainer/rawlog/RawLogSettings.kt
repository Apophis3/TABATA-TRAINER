package com.tuapp.tabatatrainer.rawlog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interruptor «Registro bruto (pruebas)» (spec 008, R1). Apagado por defecto. Se guarda en preferencias
 * propias (`raw_log`), sin tocar Room. Solo se mira al empezar una sesión: cambiarlo a mitad no la corta.
 */
@Singleton
class RawLogSettings @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun isEnabled(): Boolean = _enabled.value

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    private companion object {
        const val PREFS = "raw_log"
        const val KEY_ENABLED = "enabled"
    }
}
