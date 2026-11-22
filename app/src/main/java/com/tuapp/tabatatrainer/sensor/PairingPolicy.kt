package com.tuapp.tabatatrainer.sensor

/** Umbrales para decidir si una señal ANT+ y una BLE son el mismo aparato */
data class PairingThresholds(
    val minSamples: Int,       // muestras (1 Hz) mínimas en ambas series antes de comparar
    val maxLag: Int,           // desfase ANT+/BLE máximo probado (muestras)
    val maxAvgDiff: Double,    // diferencia media (con desfase) para considerarlas el mismo aparato
    val minRange: Int,         // variación mínima de cada serie para poder guardarlo para siempre
    val tieMargin: Double = 1.0, // el segundo mejor candidato debe quedar al menos esto peor
    val safeTicks: Int = 30     // segundos seguidos viendo solo 1 ANT+ y 1 BLE sin pareja para guardarlo (> 1 ciclo de búsqueda ANT+)
) {
    companion object {
        val HR = PairingThresholds(minSamples = 15, maxLag = 5, maxAvgDiff = 4.0, minRange = 4)
        val CADENCE = PairingThresholds(minSamples = 15, maxLag = 5, maxAvgDiff = 4.0, minRange = 4)
    }
}

/** Una señal conectada vista en este segundo */
data class SignalSnapshot(
    val id: String,            // "ANT:..." o "BLE:..."
    val name: String,
    val fresh: Boolean,        // con dato reciente
    val history: List<Int>     // valores a 1 Hz (vacía si no hay datos recientes)
) {
    val isAnt: Boolean get() = id.startsWith("ANT:")
    val isBle: Boolean get() = id.startsWith("BLE:")
}

/** Decisión: estas dos señales son el mismo aparato; guardarlo (persistent) o solo en esta sesión */
data class PairDecision(val antId: String, val bleId: String, val persistent: Boolean, val reason: String)

/**
 * Reglas de emparejado automático ANT+ ↔ BLE (spec 007, R3). Lógica pura; se llama una vez por segundo.
 *
 * Persistente solo si es seguro (y la pareja no está bloqueada por el usuario):
 * - el nº ANT+ aparece al final de los dígitos del nombre BLE (Garmin y otros: "HRM-Pro:12345"), o
 * - durante toda la ventana de comparación solo hay **una** señal ANT+ y **una** BLE sin emparejar, sus valores
 *   coinciden con desfase y ambas varían lo suficiente.
 * En otro caso, solo en la sesión: pareja mutuamente mejor, sin empate, también con valores planos.
 */
class PairingPolicy(
    private val kind: SensorKind,
    private val t: PairingThresholds
) {
    // Segundos seguidos en los que solo se ve esta pareja (1 ANT+ + 1 BLE sin emparejar)
    private var safePair: Pair<String, String>? = null
    private var safeStreak = 0

    fun reset() { safePair = null; safeStreak = 0 }

    /**
     * [signals]: señales conectadas de este tipo (frescas o no).
     * [sessionPartners]: señal agrupada solo en la sesión → señales del otro protocolo de su grupo (R3.1). En la
     * comparación por valores solo se compara con ellas: no hace de rival de las demás (dos bandas en el mismo pecho
     * darían empate y la 2.ª esperaría 30 s). El recuento para guardar para siempre no cambia.
     */
    fun evaluate(
        signals: List<SignalSnapshot>,
        lookup: PairingLookup,
        sessionPartners: Map<String, Set<String>> = emptyMap()
    ): List<PairDecision> {
        val unpaired = signals.filter { lookup.partnerOf(kind, it.id) == null }
        val ants = unpaired.filter { it.isAnt }
        val bles = unpaired.filter { it.isBle }

        if (ants.size == 1 && bles.size == 1) {
            val p = ants[0].id to bles[0].id
            if (p == safePair) safeStreak++ else { safePair = p; safeStreak = 1 }
        } else reset()

        val freshAnts = ants.filter { it.fresh }
        val freshBles = bles.filter { it.fresh }
        val decisions = ArrayList<PairDecision>()
        val decided = HashSet<String>()

        // 1) Coincidencia de nombre
        for (a in freshAnts) for (b in freshBles) {
            if (a.id in decided || b.id in decided || lookup.isBlocked(kind, a.id, b.id)) continue
            if (nameMatch(a.id, b.name)) {
                decisions += PairDecision(a.id, b.id, persistent = true, reason = "nombre")
                decided += a.id; decided += b.id
            }
        }

        // 2) Parecido de valores (con desfase)
        // Una serie solo de ceros (cadenciómetro parado) no identifica nada: dos aparatos distintos parados son iguales
        val ready = { s: SignalSnapshot -> s.id !in decided && s.history.size >= t.minSamples && s.history.any { it > 0 } }
        val diffs = HashMap<Pair<String, String>, Double>()
        for (a in freshAnts.filter(ready)) for (b in freshBles.filter(ready)) {
            if (lookup.isBlocked(kind, a.id, b.id)) continue
            if (crossesSessionPair(a.id, b.id, sessionPartners)) continue
            diffs[a.id to b.id] = minLaggedAvgDiff(a.history, b.history, t.maxLag)
        }
        for ((pair, d) in diffs) {
            if (d > t.maxAvgDiff || !isUnambiguous(pair, d, diffs)) continue
            val a = freshAnts.first { it.id == pair.first }
            val b = freshBles.first { it.id == pair.second }
            val safe = pair == safePair && safeStreak >= t.safeTicks && varies(a.history) && varies(b.history)
            decisions += PairDecision(a.id, b.id, persistent = safe, reason = if (safe) "valores (único a la vista)" else "valores (sesión)")
        }
        return decisions
    }

    /** Une una señal agrupada en la sesión con otra que no es su pareja (R3.1) */
    private fun crossesSessionPair(antId: String, bleId: String, partners: Map<String, Set<String>>): Boolean =
        partners[antId]?.let { bleId !in it } == true || partners[bleId]?.let { antId !in it } == true

    private fun varies(h: List<Int>) = h.isNotEmpty() && h.max() - h.min() >= t.minRange

    /** La pareja es la mejor para ambas señales y ningún otro candidato queda cerca (sin empate) */
    private fun isUnambiguous(pair: Pair<String, String>, d: Double, diffs: Map<Pair<String, String>, Double>): Boolean =
        diffs.none { (other, od) ->
            other != pair && (other.first == pair.first || other.second == pair.second) && od < d + t.tieMargin
        }

    companion object {
        /** El nº ANT+ (≥ 4 dígitos) aparece al final de los dígitos del nombre BLE */
        fun nameMatch(antId: String, bleName: String): Boolean {
            val number = antId.removePrefix("ANT:")
            return number.length >= 4 && bleName.filter { it.isDigit() }.endsWith(number)
        }
    }
}

/**
 * Mínima diferencia media entre dos series (muestreadas a 1 Hz) probando desfases de -maxLag..maxLag
 * muestras. ANT+ y BLE del mismo aparato llegan desfasados unos segundos.
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
