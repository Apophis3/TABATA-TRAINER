package com.tuapp.tabatatrainer.util.laps

import com.tuapp.tabatatrainer.data.local.LapEntity

/**
 * Duración de una vuelta guardada. `lap_records` solo guarda reloj de pared (inicio/fin) y la velocidad media,
 * que el servicio calculó con el tiempo de ruta (sin pausas). Si hay distancia y velocidad se recupera ese
 * tiempo (así una pausa dentro de la ventana no estira la vuelta); en una serie en el sitio (0 m) se usa el
 * reloj de pared, que ahí no puede incluir un desplazamiento.
 */
object LapDuration {
    fun secondsOf(lap: LapEntity): Int {
        val meters = (lap.endDistanceMeters - lap.startDistanceMeters).coerceAtLeast(0f)
        if (meters > 0f && lap.avgSpeedKmh > 0f) {
            return Math.round(meters / (lap.avgSpeedKmh / 3.6f)).coerceAtLeast(0)
        }
        return ((lap.endTimestamp - lap.startTimestamp) / 1000L).toInt().coerceAtLeast(0)
    }
}
