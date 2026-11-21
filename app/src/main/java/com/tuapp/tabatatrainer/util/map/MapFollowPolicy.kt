package com.tuapp.tabatatrainer.util.map

import kotlin.math.abs

/**
 * R9 (spec 006): decide qué hace la cámara del mapa en vivo con cada fix GPS.
 *
 * - RF-9.1: mientras el usuario toca el mapa y durante [USER_IDLE_MS] después, no se sigue.
 * - RF-9.2: pasado ese tiempo se sigue con el zoom que eligió el usuario.
 * - RF-9.3: si la cámara está en otro zoom que no eligió el usuario (p. ej. escala mundial),
 *   se restaura su zoom elegido o, si nunca eligió, [FOLLOW_ZOOM].
 */
object MapFollowPolicy {

    const val USER_IDLE_MS = 10_000L
    const val FOLLOW_ZOOM = 16f
    const val ZOOM_TOLERANCE = 0.1f

    enum class Action { NONE, CENTER, CENTER_AND_ZOOM }

    /** Zoom al que debe seguir la cámara: el elegido por el usuario o el de calle. */
    fun targetZoom(userZoom: Float?): Float = userZoom ?: FOLLOW_ZOOM

    /**
     * @param lastUserInteractionMs instante (reloj monótono) del último gesto del usuario; null = nunca.
     * @param isUserGestureActive el usuario está moviendo el mapa ahora mismo.
     * @param userZoom último zoom en que el usuario dejó el mapa; null = nunca lo tocó.
     */
    fun decide(
        isTracking: Boolean,
        hasLocation: Boolean,
        isUserGestureActive: Boolean,
        lastUserInteractionMs: Long?,
        nowMs: Long,
        currentZoom: Float,
        userZoom: Float?
    ): Action {
        if (!isTracking || !hasLocation || isUserGestureActive) return Action.NONE
        if (lastUserInteractionMs != null && nowMs - lastUserInteractionMs < USER_IDLE_MS) return Action.NONE
        return if (abs(currentZoom - targetZoom(userZoom)) > ZOOM_TOLERANCE) Action.CENTER_AND_ZOOM else Action.CENTER
    }
}
