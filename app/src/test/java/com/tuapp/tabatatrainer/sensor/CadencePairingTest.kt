package com.tuapp.tabatatrainer.sensor

import com.tuapp.tabatatrainer.sensor.SensorKind.CADENCE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.math.sin

/** Emparejado ANT+ ↔ BLE de cadenciómetros (spec 007, T-03): los ceros (parado) cuentan en el historial */
class CadencePairingTest {

    private val t = PairingThresholds.CADENCE

    /** Pedaleo que sube y baja (variación de sobra) */
    private val rpm = List(60) { (85 + 10 * sin(it / 4.0)).roundToInt() }
    private val stopped = List(60) { 0 }

    private fun delayed(s: List<Int>, k: Int) = List(k) { s.first() } + s.dropLast(k)
    private fun at(series: List<Int>, tick: Int) = series.take(tick).takeLast(20)
    private fun sig(id: String, series: List<Int>, tick: Int) = SignalSnapshot(id, id, true, at(series, tick))

    private fun run(book: DeviceBook, ticks: IntRange, signals: (Int) -> List<SignalSnapshot>): List<Pair<Int, PairDecision>> {
        val policy = PairingPolicy(CADENCE, t)
        return ticks.flatMap { tick -> policy.evaluate(signals(tick), book).map { tick to it } }
    }

    @Test
    fun `uno ANT y uno BLE pedaleando igual se guardan tras la ventana segura`() {
        val d = run(DeviceBook(), 1..60) { tick -> listOf(sig("ANT:1", rpm, tick), sig("BLE:A", delayed(rpm, 2), tick)) }
        assertEquals(t.safeTicks, d.first { it.second.persistent }.first)
        assertTrue(d.all { it.second.antId == "ANT:1" && it.second.bleId == "BLE:A" })
    }

    @Test
    fun `uno ANT y uno BLE parados a 0 rpm no se agrupan ni en la sesion`() {
        val d = run(DeviceBook(), 1..60) { tick -> listOf(sig("ANT:1", stopped, tick), sig("BLE:A", stopped, tick)) }
        assertTrue(d.toString(), d.isEmpty())
    }

    @Test
    fun `parados y luego pedaleando se agrupan en cuanto hay vueltas`() {
        val series = List(20) { 0 } + rpm
        val d = run(DeviceBook(), 1..80) { tick -> listOf(sig("ANT:1", series, tick), sig("BLE:A", delayed(series, 2), tick)) }
        val first = d.first()
        assertTrue("sin pedaleo no hay decisión", first.first > 20)
        assertEquals("ANT:1", first.second.antId)
        assertEquals("BLE:A", first.second.bleId)
    }

    @Test
    fun `parados con el numero ANT en el nombre BLE se emparejan por nombre`() {
        val d = PairingPolicy(CADENCE, t).evaluate(listOf(
            SignalSnapshot("ANT:54321", "Cadencia", true, at(stopped, 1)),
            SignalSnapshot("BLE:AA", "CAD:54321", true, at(stopped, 1))
        ), DeviceBook())
        assertEquals(listOf(PairDecision("ANT:54321", "BLE:AA", persistent = true, reason = "nombre")), d)
    }

    @Test
    fun `dos ANT y dos BLE parados no se agrupan`() {
        val d = run(DeviceBook(), 1..60) { tick ->
            listOf(sig("ANT:1", stopped, tick), sig("ANT:2", stopped, tick), sig("BLE:A", stopped, tick), sig("BLE:B", stopped, tick))
        }
        assertTrue(d.toString(), d.isEmpty())
    }

    @Test
    fun `dos ANT y dos BLE pedaleando nunca se guardan`() {
        val other = rpm.map { it + 20 }
        val d = run(DeviceBook(), 1..60) { tick ->
            listOf(
                sig("ANT:1", rpm, tick), sig("ANT:2", other, tick),
                sig("BLE:A", delayed(rpm, 1), tick), sig("BLE:B", delayed(other, 1), tick)
            )
        }
        assertTrue(d.none { it.second.persistent })
    }
}
