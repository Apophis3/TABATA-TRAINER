package com.tuapp.tabatatrainer.util.laps

/** Cruce de un múltiplo de 1000 m: [km] es el nº de kilómetro (1, 2, 3…) y [timeSeconds] el instante interpolado */
data class AutoLapCrossing(val km: Int, val timeSeconds: Double) {
    val distanceMeters: Float get() = km * AutoLapTracker.LAP_METERS
}

/**
 * Detecta los cruces de cada kilómetro sobre la distancia total acumulada.
 *
 * - Cada autolap mide exactamente 1000 m: el instante del cruce se interpola entre la muestra
 *   anterior y la actual, así que el tiempo de la vuelta no depende de cuándo llegó el fix GPS.
 * - Si una muestra salta varios km (hueco de GPS), devuelve todos los cruces intermedios.
 * - No hay vuelta parcial: solo existen cruces completos.
 * - Sin reloj propio: el tiempo entra por parámetro (testeable con JUnit puro).
 */
class AutoLapTracker {

    companion object {
        const val LAP_METERS = 1000f
    }

    private var lastDistance = 0f
    private var lastTime = 0.0
    private var nextKm = 1

    fun reset() {
        lastDistance = 0f
        lastTime = 0.0
        nextKm = 1
    }

    /** @return los cruces ocurridos entre la muestra anterior y ésta (vacío casi siempre) */
    fun onSample(timeSeconds: Double, distanceMeters: Float): List<AutoLapCrossing> {
        if (distanceMeters <= lastDistance) {
            // Sin avance (parado, ruido): solo se adelanta el reloj para interpolar bien después
            if (timeSeconds > lastTime) lastTime = timeSeconds
            return emptyList()
        }

        val crossings = mutableListOf<AutoLapCrossing>()
        while (nextKm * LAP_METERS <= distanceMeters) {
            val target = nextKm * LAP_METERS
            val fraction = (target - lastDistance) / (distanceMeters - lastDistance)
            val crossTime = lastTime + (timeSeconds - lastTime).coerceAtLeast(0.0) * fraction
            crossings += AutoLapCrossing(nextKm, crossTime)
            nextKm++
        }

        lastDistance = distanceMeters
        if (timeSeconds > lastTime) lastTime = timeSeconds
        return crossings
    }
}
