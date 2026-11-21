package com.tuapp.tabatatrainer.sensor

import com.tuapp.tabatatrainer.sensor.SensorKind.HR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.math.sin

class PairingPolicyTest {

    private val t = PairingThresholds.HR

    /** Pulso que sube y baja (variación de sobra) */
    private val hr = List(60) { (110 + 20 * sin(it / 4.0)).roundToInt() }

    private fun delayed(s: List<Int>, k: Int) = List(k) { s.first() } + s.dropLast(k)
    private fun plus(s: List<Int>, d: Int) = s.map { it + d }

    /** Historial que vería el hub en el segundo [tick] (como mucho las últimas 20 muestras) */
    private fun at(series: List<Int>, tick: Int) = series.take(tick).takeLast(20)

    private fun sig(id: String, series: List<Int>, tick: Int, name: String = id, fresh: Boolean = true) =
        SignalSnapshot(id, name, fresh, if (fresh) at(series, tick) else emptyList())

    /** Ejecuta la política [ticks] segundos y devuelve todas las decisiones */
    private fun run(
        policy: PairingPolicy, book: DeviceBook, ticks: IntRange,
        signals: (Int) -> List<SignalSnapshot>
    ): List<Pair<Int, PairDecision>> = ticks.flatMap { tick -> policy.evaluate(signals(tick), book).map { tick to it } }

    // --- Nombre ---------------------------------------------------------------------------

    @Test
    fun `numero ANT en el nombre BLE empareja para siempre al momento`() {
        val book = DeviceBook()
        val d = PairingPolicy(HR, t).evaluate(listOf(
            sig("ANT:54321", hr, 1, "HRM-Pro"),
            sig("BLE:AA", plus(hr, 30), 1, "HRM-Pro:54321"),
            sig("ANT:11111", hr, 1),
        ), book)
        assertEquals(listOf(PairDecision("ANT:54321", "BLE:AA", persistent = true, reason = "nombre")), d)
    }

    @Test
    fun `numero ANT corto no cuenta como coincidencia de nombre`() {
        assertFalse(PairingPolicy.nameMatch("ANT:123", "Banda 123"))
        assertTrue(PairingPolicy.nameMatch("ANT:1234", "Banda 1234"))
    }

    @Test
    fun `pareja separada por el usuario no se vuelve a unir ni por nombre`() {
        val book = DeviceBook()
        book.link(HR, "ANT:54321", "BLE:AA", "Banda", manual = false)
        book.split(HR, "ANT:54321")
        val d = run(PairingPolicy(HR, t), book, 1..40) { tick ->
            listOf(sig("ANT:54321", hr, tick), sig("BLE:AA", delayed(hr, 2), tick, "HRM-Pro:54321"))
        }
        assertTrue(d.toString(), d.isEmpty())
    }

    // --- Un solo aparato a la vista -----------------------------------------------------------

    @Test
    fun `una banda dual sola a la vista se guarda tras la ventana de comparacion`() {
        val policy = PairingPolicy(HR, t)
        val book = DeviceBook()
        val d = run(policy, book, 1..40) { tick -> listOf(sig("ANT:1", hr, tick), sig("BLE:A", delayed(hr, 3), tick)) }
        val persistent = d.filter { it.second.persistent }
        assertTrue(d.toString(), persistent.isNotEmpty())
        assertEquals(t.safeTicks, persistent.first().first)
        assertTrue(d.none { it.first < t.minSamples })   // antes de tener muestras no decide nada
        assertEquals(t.minSamples, d.first().first)      // mientras tanto, ya agrupados en la sesión
    }

    @Test
    fun `con pulso plano solo se agrupa en la sesion aunque difiera 3 bpm`() {
        val flatAnt = List(60) { 62 }
        val flatBle = List(60) { 65 }   // el caso que mandaba el BLE a HR2
        val d = run(PairingPolicy(HR, t), DeviceBook(), 1..60) { tick ->
            listOf(sig("ANT:1", flatAnt, tick), sig("BLE:A", flatBle, tick))
        }
        assertTrue(d.isNotEmpty())
        assertTrue(d.none { it.second.persistent })
        assertTrue(d.all { it.second.antId == "ANT:1" && it.second.bleId == "BLE:A" })
    }

    @Test
    fun `si aparece otra senal ANT a mitad de la ventana no se guarda hasta otra ventana completa`() {
        val other = plus(hr, 40)
        val d = run(PairingPolicy(HR, t), DeviceBook(), 1..60) { tick ->
            val base = listOf(sig("ANT:1", hr, tick), sig("BLE:A", delayed(hr, 2), tick))
            if (tick == 20) base + sig("ANT:2", other, tick) else base
        }
        val firstPersistent = d.first { it.second.persistent }.first
        assertEquals(20 + t.safeTicks, firstPersistent)
    }

    @Test
    fun `una senal conectada sin datos tambien impide guardar`() {
        val d = run(PairingPolicy(HR, t), DeviceBook(), 1..40) { tick ->
            listOf(sig("ANT:1", hr, tick), sig("BLE:A", delayed(hr, 2), tick), sig("BLE:B", hr, tick, fresh = false))
        }
        assertTrue(d.isNotEmpty())
        assertTrue(d.none { it.second.persistent })
    }

    @Test
    fun `senales que no coinciden no se agrupan`() {
        val d = run(PairingPolicy(HR, t), DeviceBook(), 1..40) { tick ->
            listOf(sig("ANT:1", hr, tick), sig("BLE:A", plus(hr, 12), tick))
        }
        assertTrue(d.isEmpty())
    }

    @Test
    fun `una senal ya emparejada no entra en las reglas`() {
        val book = DeviceBook()
        book.link(HR, "ANT:1", "BLE:Z", "Otra", manual = true)
        val d = run(PairingPolicy(HR, t), book, 1..40) { tick ->
            listOf(sig("ANT:1", hr, tick), sig("BLE:A", delayed(hr, 2), tick))
        }
        assertTrue(d.isEmpty())
    }

    // --- Dos bandas puestas (4 señales parecidas) ---------------------------------------------

    @Test
    fun `dos bandas distinguibles se agrupan bien pero nunca se guardan`() {
        val bandB = plus(hr, 3)
        val d = run(PairingPolicy(HR, t), DeviceBook(), 1..60) { tick ->
            listOf(
                sig("ANT:A", hr, tick), sig("BLE:A", delayed(hr, 2), tick),
                sig("ANT:B", bandB, tick), sig("BLE:B", delayed(bandB, 1), tick),
            )
        }
        assertTrue(d.isNotEmpty())
        assertTrue(d.none { it.second.persistent })
        assertTrue(d.toString(), d.all { (it.second.antId to it.second.bleId) in setOf("ANT:A" to "BLE:A", "ANT:B" to "BLE:B") })
    }

    @Test
    fun `dos bandas indistinguibles no se agrupan`() {
        val d = run(PairingPolicy(HR, t), DeviceBook(), 1..60) { tick ->
            listOf(
                sig("ANT:A", hr, tick), sig("BLE:A", delayed(hr, 2), tick),
                sig("ANT:B", hr, tick), sig("BLE:B", delayed(hr, 1), tick),
            )
        }
        assertTrue(d.toString(), d.isEmpty())
    }

    @Test
    fun `segunda banda nueva con la primera ya guardada se guarda tras la ventana`() {
        val book = DeviceBook()
        book.link(HR, "ANT:A", "BLE:A", "Banda A", manual = false)
        val bandB = plus(hr, 1)
        val d = run(PairingPolicy(HR, t), book, 1..40) { tick ->
            listOf(
                sig("ANT:A", hr, tick), sig("BLE:A", delayed(hr, 2), tick),
                sig("ANT:B", bandB, tick), sig("BLE:B", delayed(bandB, 2), tick),
            )
        }
        assertTrue(d.isNotEmpty())
        assertTrue(d.all { it.second.antId == "ANT:B" && it.second.bleId == "BLE:B" })
        assertTrue(d.any { it.second.persistent })
    }

    // --- Desfase (antes en HrDeviceBookTest) -----------------------------------------------------

    @Test
    fun `misma HR con desfase de 3 s se reconoce como el mismo aparato`() {
        val s = listOf(70, 72, 75, 79, 84, 88, 91, 93, 94, 92, 89, 85, 81, 78, 76, 75, 74, 76, 79, 83)
        val late = List(3) { 68 } + s.dropLast(3)
        val noLag = minLaggedAvgDiff(s, late, 0)
        assertTrue("sin desfase: $noLag", noLag > 4.0)
        assertTrue(minLaggedAvgDiff(s, late, 5) < 1.0)
    }

    @Test
    fun `dos personas distintas no se confunden`() {
        val a = listOf(70, 72, 75, 79, 84, 88, 91, 93, 94, 92, 89, 85, 81, 78, 76, 75, 74, 76, 79, 83)
        val b = listOf(110, 108, 105, 103, 100, 99, 101, 104, 108, 112, 115, 117, 116, 113, 110, 107, 105, 104, 106, 109)
        assertTrue(minLaggedAvgDiff(a, b, 5) > 4.0)
    }
}
