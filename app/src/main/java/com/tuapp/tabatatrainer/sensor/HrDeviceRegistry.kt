package com.tuapp.tabatatrainer.sensor

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Un pulsómetro físico con sus dos identidades: número ANT+ y dirección BLE */
data class PhysicalHr(
    val key: String,        // id estable del aparato (= su id ANT+ al vincularse)
    val alias: String,
    val antId: String,      // "ANT:12345"
    val bleId: String       // "BLE:AA:BB:..."
)

/** Libreta de vínculos ANT+ ↔ BLE. Lógica pura (testeable sin Android). */
class HrDeviceBook(initial: Collection<PhysicalHr> = emptyList()) {
    private val byKey = LinkedHashMap<String, PhysicalHr>()

    init { initial.forEach { byKey[it.key] = it } }

    @Synchronized fun all(): List<PhysicalHr> = byKey.values.toList()

    /** Clave del aparato físico al que pertenece la identidad, o null si no está vinculada */
    @Synchronized fun keyOf(id: String): String? =
        byKey.values.firstOrNull { it.antId == id || it.bleId == id }?.key

    /** La otra identidad del mismo aparato */
    @Synchronized fun partnerOf(id: String): String? = byKey.values.firstNotNullOfOrNull {
        when (id) { it.antId -> it.bleId; it.bleId -> it.antId; else -> null }
    }

    /** Une ANT+ y BLE como un solo aparato. Rompe vínculos previos de cualquiera de los dos. Devuelve true si cambió algo. */
    @Synchronized fun link(antId: String, bleId: String, alias: String): Boolean {
        if (byKey.values.any { it.antId == antId && it.bleId == bleId }) return false
        byKey.values.removeAll { it.antId == antId || it.bleId == bleId }
        byKey[antId] = PhysicalHr(antId, alias, antId, bleId)
        return true
    }

    @Synchronized fun unlink(key: String): Boolean = byKey.remove(key) != null

    @Synchronized fun serialize(): String = byKey.values.joinToString("\n") {
        listOf(it.key, it.antId, it.bleId, it.alias.replace('\t', ' ').replace('\n', ' ')).joinToString("\t")
    }

    companion object {
        fun parse(text: String?): HrDeviceBook = HrDeviceBook(
            text.orEmpty().lines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size >= 4 && p[1].startsWith("ANT:") && p[2].startsWith("BLE:")) PhysicalHr(p[0], p[3], p[1], p[2]) else null
            }
        )
    }
}

/**
 * Mínima diferencia media de bpm entre dos series (muestreadas a 1 Hz) probando desfases de
 * -maxLag..maxLag muestras. ANT+ y BLE del mismo aparato llegan desfasados unos segundos.
 */
fun minLaggedAvgDiff(a: List<Int>, b: List<Int>, maxLag: Int): Double {
    val n = minOf(a.size, b.size)
    if (n == 0) return Double.MAX_VALUE
    val x = a.takeLast(n); val y = b.takeLast(n)
    var best = Double.MAX_VALUE
    for (lag in -maxLag..maxLag) {
        var sum = 0; var count = 0
        for (i in 0 until n) {
            val j = i + lag
            if (j in 0 until n) { sum += kotlin.math.abs(x[i] - y[j]); count++ }
        }
        if (count >= n - maxLag && count > 0) best = minOf(best, sum.toDouble() / count)
    }
    return best
}

/** Registro persistente (SharedPreferences) de pulsómetros físicos */
@Singleton
class HrDeviceRegistry @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("hr_device_registry", Context.MODE_PRIVATE)
    private val book = HrDeviceBook.parse(prefs.getString(KEY, null))

    fun all() = book.all()
    fun keyOf(id: String) = book.keyOf(id)
    fun partnerOf(id: String) = book.partnerOf(id)

    fun link(antId: String, bleId: String, alias: String) {
        if (book.link(antId, bleId, alias)) {
            save()
            Timber.d("💓 Registro: $alias vinculado ($antId ↔ $bleId)")
        }
    }

    fun unlink(key: String) { if (book.unlink(key)) save() }

    private fun save() = prefs.edit().putString(KEY, book.serialize()).apply()

    private companion object { const val KEY = "devices" }
}
