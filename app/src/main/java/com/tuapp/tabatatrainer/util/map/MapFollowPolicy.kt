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
     * @param isOverview el usuario pulsó «Ver todo» (spec 009, R2): no se sigue.
     */
    fun decide(
        isTracking: Boolean,
        hasLocation: Boolean,
        isUserGestureActive: Boolean,
        lastUserInteractionMs: Long?,
        nowMs: Long,
        currentZoom: Float,
        userZoom: Float?,
        isOverview: Boolean = false
    ): Action {
        // R2 (spec 009): en «Ver todo» la cámara no sigue la posición hasta que se sale del modo
        if (isOverview) return Action.NONE
        if (!isTracking || !hasLocation || isUserGestureActive) return Action.NONE
        if (lastUserInteractionMs != null && nowMs - lastUserInteractionMs < USER_IDLE_MS) return Action.NONE
        return if (abs(currentZoom - targetZoom(userZoom)) > ZOOM_TOLERANCE) Action.CENTER_AND_ZOOM else Action.CENTER
    }

    /** RF-2.3: el zoom de «Ver todo» no cuenta como zoom elegido por el usuario. */
    fun shouldRecordUserZoom(isOverview: Boolean): Boolean = !isOverview

    /** RF-2.4: «Ver todo» necesita al menos 2 puntos distintos para encuadrar algo. */
    fun canShowOverview(points: List<Pair<Double, Double>>): Boolean {
        val first = points.firstOrNull() ?: return false
        return points.any { it.first != first.first || it.second != first.second }
    }
}
