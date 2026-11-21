package com.tuapp.tabatatrainer.sensor

import com.tuapp.tabatatrainer.sensor.SensorKind.CADENCE
import com.tuapp.tabatatrainer.sensor.SensorKind.HR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reparto de huecos C1/C2 (spec 007, T-03): mismas reglas que el pulso, con 0 rpm como dato válido */
class CadenceSlotAllocatorTest {

    private val book = DeviceBook()
    private val alloc = DualSlotAllocator(CADENCE, book, "C", handoffMaxDiff = 15)
    private var now = 0L

    private fun ant(n: String, v: Int = 85, connected: Boolean = true, fresh: Boolean = connected) =
        SignalState("ANT:$n", "ANT $n", isAnt = true, connected = connected, fresh = fresh, value = v)

    private fun ble(n: String, v: Int = 85, connected: Boolean = true, fresh: Boolean = connected) =
        SignalState("BLE:$n", "BLE $n", isAnt = false, connected = connected, fresh = fresh, value = v)

    private fun step(vararg s: SignalState, seconds: Int = 1): List<SlotStatus> {
        var r = emptyList<SlotStatus>()
        repeat(seconds) { now += 1_000; r = alloc.tick(s.toList(), now) }
        return r
    }

    @Test
    fun `cadenciometro dual guardado ocupa un solo hueco con ANT preferente`() {
        book.link(CADENCE, "ANT:1", "BLE:1", "Cadencia", manual = false)
        var st = step(ble("1"))
        assertEquals("BLE:1", st[0].sourceId)
        st = step(ble("1"), ant("1"))
        assertEquals("ANT:1", st[0].sourceId)
        assertEquals("Cadencia", st[0].alias)
        assertNull(st[1].deviceKey)
    }

    @Test
    fun `si cae ANT pasa a BLE sin cambiar de hueco y al volver recupera ANT`() {
        book.link(CADENCE, "ANT:1", "BLE:1", "Cadencia", manual = false)
        assertEquals("ANT:1", step(ant("1"), ble("1"))[0].sourceId)
        val st = step(ant("1", connected = false), ble("1"))
        assertEquals("BLE:1", st[0].sourceId)
        assertNull(st[1].deviceKey)
        assertEquals("ANT:1", step(ant("1"), ble("1"))[0].sourceId)
    }

    @Test
    fun `parado a 0 rpm sigue conectado en su hueco y con ANT como fuente`() {
        book.link(CADENCE, "ANT:1", "BLE:1", "Cadencia", manual = false)
        step(ant("1"), ble("1"))
        val st = step(ant("1", v = 0), ble("1", v = 0), seconds = 30)
        assertEquals("ANT:1", st[0].sourceId)
        assertTrue(st[0].connected)
        assertEquals(0, st[0].value)
        assertNull(st[1].deviceKey)
    }

    @Test
    fun `cadenciometro nuevo arrancando parado recibe hueco`() {
        val st = step(ant("1", v = 0), seconds = 15)
        assertEquals("ANT:1", st[0].deviceKey)
        assertEquals(0, st[0].value)
    }

    @Test
    fun `ANT conectada sin datos (biela parada) conserva el hueco`() {
        step(ant("1"))
        val st = step(ant("1", v = 0, fresh = false), seconds = 30)
        assertEquals("ANT:1", st[0].deviceKey)
        assertEquals("ANT:1", st[0].sourceId)
    }

    @Test
    fun `aparato con hueco C1 guardado lo ocupa aunque llegue segundo`() {
        assertEquals("ANT:A", step(ant("A"))[0].deviceKey)
        book.remember(CADENCE, "ANT:B", "Cadencia B")
        book.setSlot(CADENCE, "ANT:B", 0)
        val st = step(ant("A"), ant("B", v = 0))
        assertEquals("ANT:B", st[0].deviceKey)
        assertEquals("ANT:A", st[1].deviceKey)
    }

    @Test
    fun `los vinculos de pulso no afectan a la cadencia`() {
        book.link(HR, "ANT:1", "BLE:1", "Banda", manual = false)
        step(ant("1"))
        val st = step(ant("1"), ble("1"), seconds = 15)
        assertEquals("ANT:1", st[0].sourceId)
        assertTrue("el BLE no es del mismo aparato de cadencia", alloc.groupOf("BLE:1") != alloc.groupOf("ANT:1"))
    }
}
