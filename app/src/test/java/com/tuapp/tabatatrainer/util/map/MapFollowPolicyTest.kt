package com.tuapp.tabatatrainer.util.map

import com.tuapp.tabatatrainer.util.map.MapFollowPolicy.Action
import com.tuapp.tabatatrainer.util.map.MapFollowPolicy.FOLLOW_ZOOM
import com.tuapp.tabatatrainer.util.map.MapFollowPolicy.USER_IDLE_MS
import org.junit.Assert.assertEquals
import org.junit.Test

class MapFollowPolicyTest {

    private val now = 100_000L

    private fun decide(
        isTracking: Boolean = true,
        hasLocation: Boolean = true,
        gesture: Boolean = false,
        lastTouch: Long? = null,
        zoom: Float = FOLLOW_ZOOM,
        userZoom: Float? = null
    ) = MapFollowPolicy.decide(isTracking, hasLocation, gesture, lastTouch, now, zoom, userZoom)

    @Test
    fun not_tracking_never_moves_camera() {
        assertEquals(Action.NONE, decide(isTracking = false))
        assertEquals(Action.NONE, decide(isTracking = false, zoom = 3f))
    }

    @Test
    fun without_location_does_nothing() {
        assertEquals(Action.NONE, decide(hasLocation = false))
    }

    @Test
    fun never_touched_at_street_zoom_only_centers() {
        assertEquals(Action.CENTER, decide(zoom = 16f))
        assertEquals(Action.CENTER, decide(zoom = 16.05f))
    }

    @Test
    fun never_touched_world_zoom_is_restored_to_street_zoom() {
        // Regresión de la captura 1: cámara a escala mundial durante la actividad
        assertEquals(Action.CENTER_AND_ZOOM, decide(zoom = 3f))
        assertEquals(FOLLOW_ZOOM, MapFollowPolicy.targetZoom(null))
    }

    @Test
    fun active_gesture_is_never_interrupted() {
        assertEquals(Action.NONE, decide(gesture = true))
        assertEquals(Action.NONE, decide(gesture = true, zoom = 3f))
    }

    @Test
    fun no_follow_during_idle_window_after_touch() {
        assertEquals(Action.NONE, decide(lastTouch = now, userZoom = 18f, zoom = 18f))
        assertEquals(Action.NONE, decide(lastTouch = now - USER_IDLE_MS + 1, userZoom = 18f, zoom = 18f))
    }

    @Test
    fun follow_resumes_after_idle_window_keeping_user_zoom() {
        assertEquals(Action.CENTER, decide(lastTouch = now - USER_IDLE_MS, userZoom = 18f, zoom = 18f))
    }

    @Test
    fun deliberate_wide_user_zoom_is_kept() {
        // El usuario se alejó a propósito: no se le fuerza el zoom de calle
        assertEquals(Action.CENTER, decide(lastTouch = now - USER_IDLE_MS, userZoom = 5f, zoom = 5f))
        assertEquals(5f, MapFollowPolicy.targetZoom(5f))
    }

    @Test
    fun zoom_drift_not_made_by_user_restores_user_zoom() {
        // Eligió 18 y la cámara acabó en 3 sin gesto suyo (mapa recreado, etc.) → vuelve a 18
        assertEquals(Action.CENTER_AND_ZOOM, decide(lastTouch = now - USER_IDLE_MS, userZoom = 18f, zoom = 3f))
        assertEquals(18f, MapFollowPolicy.targetZoom(18f))
    }
}
