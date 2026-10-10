package com.tuapp.tabatatrainer.util.laps

/** Aviso de kilómetro completado: [paceSecPerKm] es el ritmo de ese km (null si no hay dato) */
data class KmAlert(val km: Int, val paceSecPerKm: Int?) {
    /** Texto del banner, p. ej. "KM 3 · 5:42/km" */
    fun bannerText(): String =
        if (paceSecPerKm == null) "KM $km" else "KM $km · ${PaceCalculator.format(paceSecPerKm)}/km"
}

/**
 * Decide si hay que avisar: un aviso por km, una sola vez, nunca hacia atrás.
 * Si una muestra completa varios km a la vez, se avisa solo del último (un único pitido).
 */
class KmAlertGate {

    private var lastAlertedKm = 0

    fun reset() {
        lastAlertedKm = 0
    }

    /** @return el km del que avisar, o null si ya estaba avisado */
    fun alertFor(crossedKms: List<Int>): Int? {
        val highest = crossedKms.maxOrNull() ?: return null
        if (highest <= lastAlertedKm) return null
        lastAlertedKm = highest
        return highest
    }
}
