package com.tuapp.tabatatrainer.util.laps

enum class CadenceSlotMode { CADENCE, PACE }

/** Lo que se pinta en el hueco "Cadencia / Ritmo" de la pantalla */
data class CadenceSlot(val mode: CadenceSlotMode, val label: String, val value: String, val unit: String)

/**
 * Decide si el hueco muestra cadencia de sensor (bici, rpm) o ritmo (min/km).
 * Lógica pura: el instante actual entra por parámetro.
 */
object CadenceDisplayPolicy {

    /** Sin datos del sensor durante este tiempo se considera que no está emitiendo */
    const val CADENCE_STALE_MS = 5_000L

    fun mode(
        paired: Boolean,
        lastSampleAtMs: Long?,
        nowMs: Long,
        staleMs: Long = CADENCE_STALE_MS
    ): CadenceSlotMode =
        if (paired && lastSampleAtMs != null && nowMs - lastSampleAtMs <= staleMs) {
            CadenceSlotMode.CADENCE
        } else {
            CadenceSlotMode.PACE
        }

    fun slot(cadenceActive: Boolean, cadenceRpm: Float, paceSecPerKm: Int?): CadenceSlot =
        if (cadenceActive) {
            CadenceSlot(CadenceSlotMode.CADENCE, "Cadencia", "%.0f".format(cadenceRpm), "rpm")
        } else {
            CadenceSlot(CadenceSlotMode.PACE, "Ritmo", PaceCalculator.format(paceSecPerKm), "/km")
        }
}
