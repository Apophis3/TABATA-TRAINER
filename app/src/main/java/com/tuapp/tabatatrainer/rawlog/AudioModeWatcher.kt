package com.tuapp.tabatatrainer.rawlog

/**
 * Estado de llamada sin permisos (spec 008, T-08): el agente mira `AudioManager.getMode()` cada 5 s y le pasa el
 * valor a [observe]. Solo devuelve el detalle de un evento `audio_mode` la primera vez (con `,initial`) y cuando
 * el modo cambia. Sin Android, para probarlo en la JVM.
 */
class AudioModeWatcher {

    private var last: Int? = null

    /** Eventos `audio_mode` devueltos (incluido el inicial). */
    var changes = 0
        private set

    /** @return el detalle del evento (`mode=in_call`) o `null` si el modo es el mismo que la vez anterior. */
    fun observe(mode: Int): String? {
        val previous = last
        if (previous == mode) return null
        last = mode
        changes++
        val detail = "mode=${name(mode)}"
        return if (previous == null) RawSessionCore.initial(detail) else detail
    }

    companion object {
        /** Cada cuánto se mira `getMode()`. */
        const val POLL_MS = 5_000L

        /** Nombres de los valores de `AudioManager.MODE_*` (copiados para no depender de Android en los tests). */
        fun name(mode: Int): String = when (mode) {
            0 -> "normal"
            1 -> "ringtone"
            2 -> "in_call"
            3 -> "in_communication"
            4 -> "call_screening"
            5 -> "call_redirect"
            6 -> "communication_redirect"
            else -> "mode_$mode"
        }
    }
}
