package com.tuapp.tabatatrainer.util.laps

/**
 * Lecturas de un tramo (ruta, vuelta manual o vuelta AUTO) separadas por hueco: 0 = HR1/C1, 1 = HR2/C2
 * (spec 007, T-05). Antes las dos bandas se mezclaban en una sola media.
 *
 * Sincronizado: los colectores de cada hueco escriben desde hilos distintos.
 */
class DualSlotSamples {

    private val samples = arrayOf(mutableListOf<Float>(), mutableListOf<Float>())

    @Synchronized
    fun add(slot: Int, value: Float) {
        samples[slot].add(value)
    }

    @Synchronized
    fun count(slot: Int): Int = samples[slot].size

    @Synchronized
    fun isEmpty(): Boolean = samples.all { it.isEmpty() }

    /** Media del hueco; null sin lecturas */
    @Synchronized
    fun avg(slot: Int): Float? = samples[slot].takeIf { it.isNotEmpty() }?.average()?.toFloat()

    /** Máximo del hueco; null sin lecturas */
    @Synchronized
    fun max(slot: Int): Float? = samples[slot].maxOrNull()

    /** Mínimo del hueco sin contar ceros; null si no hay ninguno mayor que 0 */
    @Synchronized
    fun minPositive(slot: Int): Float? = samples[slot].filter { it > 0f }.minOrNull()

    /**
     * Hueco que representa el tramo cuando solo cabe un valor (vuelta, Room): HR1/C1 si tiene lecturas, si no
     * HR2/C2; null si ninguno tiene.
     */
    @Synchronized
    fun primarySlot(): Int? = when {
        samples[0].isNotEmpty() -> 0
        samples[1].isNotEmpty() -> 1
        else -> null
    }

    /** Media del hueco que representa el tramo ([primarySlot]); null sin lecturas */
    @Synchronized
    fun primaryAvg(): Float? = primarySlot()?.let { avg(it) }

    /** Máximo del hueco que representa el tramo ([primarySlot]); null sin lecturas */
    @Synchronized
    fun primaryMax(): Float? = primarySlot()?.let { max(it) }

    @Synchronized
    fun clear() {
        samples.forEach { it.clear() }
    }
}
