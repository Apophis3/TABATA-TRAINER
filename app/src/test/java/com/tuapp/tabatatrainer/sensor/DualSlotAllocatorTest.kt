package com.tuapp.tabatatrainer.sensor

import com.tuapp.tabatatrainer.sensor.SensorKind.HR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DualSlotAllocatorTest {

    private val book = DeviceBook()
    private val alloc = DualSlotAllocator(HR, book, "HR", handoffMaxDiff = 15)
    private var now = 0L

    private fun ant(n: String, v: Int = 100, connected: Boolean = true, fresh: Boolean = connected) =
        SignalState("ANT:$n", "ANT $n", isAnt = true, connected = connected, fresh = fresh, value = v)

    private fun ble(n: String, v: Int = 100, connected: Boolean = true, fresh: Boolean = connected) =
        SignalState("BLE:$n", "BLE $n", isAnt = false, connected = connected, fresh = fresh, value = v)

    /** Avanza [seconds] segundos con las mismas señales y devuelve el último estado de los huecos */
    private fun step(vararg s: SignalState, seconds: Int = 1): List<SlotStatus> {
        var r = emptyList<SlotStatus>()
        repeat(seconds) { now += 1_000; r = alloc.tick(s.toList(), now) }
        return r
    }

    // --- Un aparato, un hueco, ANT+ preferente --------------------------------------------------

    @Test
    fun `banda dual guardada ocupa un solo hueco y pasa a ANT al aparecer`() {
        book.link(HR, "ANT:1", "BLE:1", "Banda", manual = false)
        var st = step(ble("1"))
        assertEquals("BLE:1", st[0].sourceId)
        st = step(ble("1"), ant("1"))
        assertEquals("ANT:1", st[0].sourceId)
        assertEquals("ANT+", st[0].protocol)
        assertEquals("Banda", st[0].alias)
        assertNull(st[1].deviceKey)
    }

    @Test
    fun `si cae ANT pasa a BLE sin cambiar de hueco y al volver recupera ANT`() {
        book.link(HR, "ANT:1", "BLE:1", "Banda", manual = false)
        assertEquals("ANT:1", step(ant("1"), ble("1"))[0].sourceId)
        var st = step(ant("1", connected = false), ble("1"))
        assertEquals("BLE:1", st[0].sourceId)
        assertNull(st[1].deviceKey)
        st = step(ant("1", fresh = false), ble("1"))   // conectada pero sin dato: sigue BLE
        assertEquals("BLE:1", st[0].sourceId)
        assertEquals("ANT:1", step(ant("1"), ble("1"))[0].sourceId)
    }

    @Test
    fun `hueco conectado sin dato reciente no es fresco (R7_1)`() {
        book.link(HR, "ANT:1", "BLE:1", "Banda", manual = false)
        var st = step(ant("1"), ble("1"))
        assertEquals(true, st[0].fresh)
        assertEquals(true, st[0].connected)
        // Banda quitada: sigue conectada un rato pero sin datos nuevos en ninguna señal
        st = step(ant("1", fresh = false), ble("1", fresh = false))
        assertEquals(true, st[0].connected)
        assertEquals(false, st[0].fresh)
        assertEquals(false, st[1].fresh)
    }

    @Test
    fun `ANT desconocida espera y al agruparse en la sesion se une al hueco del BLE`() {
        assertEquals("BLE:1", step(ble("1"))[0].sourceId)
        var st = step(ble("1"), ant("1"), seconds = 10)
        assertEquals("BLE:1", st[0].sourceId)
        assertNull("en cuarentena no recibe hueco", st[1].deviceKey)
        alloc.joinSession("ANT:1", alloc.groupOf("BLE:1"))   // lo que hace el hub tras PairingPolicy
        st = step(ble("1"), ant("1"))
        assertEquals("ANT:1", st[0].sourceId)
        assertNull(st[1].deviceKey)
    }

    // --- Dos bandas: ANT+ desplaza a BLE -------------------------------------------------------------

    @Test
    fun `dos bandas indistinguibles con los BLE antes acaban las dos en ANT`() {
        var st = step(ble("A"), ble("B"))
        assertEquals(listOf("BLE", "BLE"), st.map { it.protocol })
        val all = arrayOf(ble("A"), ble("B"), ant("A"), ant("B"))
        st = step(*all, seconds = 30)
        assertEquals("en cuarentena aún", listOf("BLE", "BLE"), st.map { it.protocol })
        st = step(*all)
        assertEquals(listOf("ANT+", "ANT+"), st.map { it.protocol })
        assertEquals(setOf("ANT:A", "ANT:B"), st.map { it.sourceId }.toSet())
        // y se mantiene: los BLE no recuperan hueco
        st = step(*all, seconds = 120)
        assertEquals(setOf("ANT:A", "ANT:B"), st.map { it.sourceId }.toSet())
    }

    @Test
    fun `una ANT caida un momento no cede su hueco a un aparato solo BLE`() {
        val other = ble("C", v = 60)   // banda solo-BLE de otra persona (valores distintos: no hay relevo)
        step(ant("A"), ant("B", v = 140))
        var st = step(ant("A"), ant("B", v = 140), other, seconds = 5)
        assertEquals(listOf("ANT:A", "ANT:B"), st.map { it.deviceKey })
        st = step(ant("A", connected = false), ant("B", v = 140), other, seconds = 50)
        assertEquals("hueco reservado a la ANT caída", "ANT:A", st[0].deviceKey)
        assertNull(st[0].sourceId)
        // pasado el margen, el aparato solo-BLE puede usarlo
        st = step(ant("A", connected = false), ant("B", v = 140), other, seconds = 20)
        assertEquals("BLE:C", st[0].deviceKey)
        // vuelve la ANT: tras su espera recupera el hueco (ANT+ desplaza a BLE) en el mismo sitio
        st = step(ant("A"), ant("B", v = 140), other, seconds = 31)
        assertEquals(listOf("ANT:A", "ANT:B"), st.map { it.deviceKey })
    }

    // --- Hueco preferido -------------------------------------------------------------------------------

    @Test
    fun `aparato con hueco HR1 guardado lo ocupa aunque llegue segundo`() {
        assertEquals("ANT:A", step(ant("A"))[0].deviceKey)
        book.remember(HR, "ANT:B", "Banda B")
        book.setSlot(HR, "ANT:B", 0)
        val st = step(ant("A"), ant("B", v = 140))
        assertEquals("ANT:B", st[0].deviceKey)
        assertEquals("ANT:A", st[1].deviceKey)
    }

    @Test
    fun `aparato sin hueco guardado evita el reservado si hay otro libre`() {
        book.remember(HR, "ANT:B", "Banda B")
        book.setSlot(HR, "ANT:B", 0)
        val st = step(ant("A"))
        assertNull(st[0].deviceKey)
        assertEquals("ANT:A", st[1].deviceKey)
    }

    @Test
    fun `la reserva es blanda si todos los huecos estan reservados`() {
        book.remember(HR, "ANT:A", "Banda A"); book.setSlot(HR, "ANT:A", 0)
        book.remember(HR, "ANT:B", "Banda B"); book.setSlot(HR, "ANT:B", 1)
        assertEquals("ANT:C", step(ant("C"))[0].deviceKey)
        val st = step(ant("C"), ant("A", v = 140))
        assertEquals("ANT:A", st[0].deviceKey)
        assertEquals("ANT:C", st[1].deviceKey)
    }

    // --- Cambio de pantalla (radios paradas un rato) -------------------------------------------------

    @Test
    fun `tras parar radios cada aparato vuelve a su hueco aunque lleguen en otro orden`() {
        step(ant("B", v = 140))
        var st = step(ant("B", v = 140), ant("A"))
        assertEquals(listOf("ANT:B", "ANT:A"), st.map { it.deviceKey })
        alloc.pause()
        step(seconds = 3)
        step(ant("A"))
        st = step(ant("A"), ant("B", v = 140))
        assertEquals(listOf("ANT:B", "ANT:A"), st.map { it.deviceKey })
    }

    // --- Batería -------------------------------------------------------------------------------------

    @Test
    fun `aparato quitado deja el hueco sin bateria`() {
        val bat = SensorBattery.parseBleBatteryLevel(byteArrayOf(80))
        val bleWithBat = ble("1").copy(battery = bat)
        book.link(HR, "ANT:1", "BLE:1", "Banda", manual = false)
        var st = step(ant("1"), bleWithBat)
        assertEquals("ANT:1", st[0].sourceId)
        assertEquals("la ANT+ usa la batería del BLE", bat, st[0].battery)
        st = step(ant("1", connected = false), bleWithBat.copy(connected = false, fresh = false))
        assertNull(st[0].sourceId)
        assertNull("sin señal no se muestra batería", st[0].battery)
    }

    @Test
    fun `clear olvida el reparto`() {
        step(ant("B", v = 140)); step(ant("B", v = 140), ant("A"))
        alloc.clear()
        val st = step(ant("A"), ant("B", v = 140))
        assertEquals("ANT:A", st[0].deviceKey)
    }
}
