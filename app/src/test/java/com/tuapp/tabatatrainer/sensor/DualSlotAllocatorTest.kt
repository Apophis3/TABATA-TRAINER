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

    // --- R3.1: parejas de sesión para el emparejado ------------------------------------------------

    @Test
    fun `sessionPartners da solo las parejas agrupadas en la sesion`() {
        book.link(HR, "ANT:3", "BLE:3", "Guardada", manual = false)   // emparejada en el registro: no cuenta
        alloc.joinSession("BLE:1", "ANT:1")                            // agrupada solo en la sesión
        val p = alloc.sessionPartners(listOf("ANT:1", "BLE:1", "ANT:2", "BLE:2", "ANT:3", "BLE:3"))
        assertEquals(mapOf("ANT:1" to setOf("BLE:1"), "BLE:1" to setOf("ANT:1")), p)
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

    // --- Pareja separada a mano (hoja "Dispositivos", T-06) ------------------------------------------

    @Test
    fun `sin bloquear, al caer la ANT un BLE con valor parecido la releva en su hueco`() {
        step(ant("1"))
        val st = step(ant("1", connected = false), ble("1"))
        assertEquals("BLE:1", st[0].sourceId)
        assertNull(st[1].deviceKey)
    }

    @Test
    fun `pareja separada, al caer la ANT el BLE no la releva y va a su propio hueco`() {
        book.block(HR, "ANT:1", "BLE:1")
        step(ant("1"))
        val st = step(ant("1", connected = false), ble("1"))
        assertEquals("hueco guardado a la ANT caída", "ANT:1", st[0].deviceKey)
        assertNull(st[0].sourceId)
        assertEquals("BLE:1", st[1].deviceKey)
        assertEquals("BLE:1", alloc.groupOf("BLE:1"))
    }

    @Test
    fun `pareja separada, el BLE no espera 30 s junto a su ANT`() {
        book.block(HR, "ANT:1", "BLE:1")
        step(ant("1"))
        val st = step(ant("1"), ble("1"))
        assertEquals("ANT:1", st[0].deviceKey)
        assertEquals("sin cuarentena", "BLE:1", st[1].deviceKey)
        // Y la marca "solo en esta sesión" no aparece en la hoja
        assertEquals(listOf(false, false), alloc.describe(listOf(ant("1"), ble("1"))).map { it.sessionOnly })
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

    // --- R2.1 ampliado (paseo 2026-11-22): sin valor congelado -------------------------------------------

    @Test
    fun `fuente conectada sin dato da valor hasta el plazo y luego el hueco se queda sin fuente`() {
        assertEquals("ANT:1", step(ant("1", v = 142))[0].sourceId)
        // 1.er segundo sin dato y 2 más: aún dentro del plazo (3 s)
        var st = step(ant("1", v = 142, fresh = false), seconds = 3)
        assertEquals("ANT:1", st[0].sourceId)
        st = step(ant("1", v = 142, fresh = false))
        assertNull("pasado el plazo no hay fuente", st[0].sourceId)
        assertEquals(0, st[0].value)
        assertEquals(false, st[0].connected)
        assertEquals("el aparato conserva su hueco", "ANT:1", st[0].deviceKey)
        st = step(ant("1", v = 142, fresh = false), seconds = 60)
        assertNull(st[0].sourceId)
        assertEquals("ANT:1", st[0].deviceKey)
    }

    @Test
    fun `tras quedarse sin fuente vuelve con el primer dato nuevo`() {
        step(ant("1"))
        step(ant("1", fresh = false), seconds = 10)
        val st = step(ant("1", v = 95))
        assertEquals("ANT:1", st[0].sourceId)
        assertEquals(95, st[0].value)
        // Y el plazo vuelve a contar desde cero
        assertEquals("ANT:1", step(ant("1", fresh = false), seconds = 3)[0].sourceId)
    }

    // --- R10: ignorar una señal ajena ---------------------------------------------------------------------

    @Test
    fun `ignorar suelta el hueco y la senal no lo vuelve a ocupar`() {
        var st = step(ant("1"), ant("2", v = 142))
        assertEquals(listOf("ANT:1", "ANT:2"), st.map { it.deviceKey })
        alloc.ignore(mapOf("ANT:2" to "ANT+ 2"))
        assertNull("suelta el hueco en el acto", alloc.groupIn(1))
        st = step(ant("1"), ant("2", v = 142), seconds = 40)
        assertEquals("ANT:1", st[0].deviceKey)
        assertNull(st[1].deviceKey)
        assertNull(st[1].sourceId)
        assertEquals("no sale en la hoja", listOf("ANT:1"), alloc.describe(listOf(ant("1"), ant("2"))).map { it.id })
    }

    @Test
    fun `ignorar suelta tambien la otra senal agrupada en la sesion`() {
        step(ble("9", v = 142))
        alloc.joinSession("ANT:9", "BLE:9")
        assertEquals("BLE:9", alloc.groupIn(0))
        alloc.ignore(mapOf("ANT:9" to "ANT+ 9", "BLE:9" to "BLE 9"))
        val st = step(ant("9", v = 142), ble("9", v = 142), seconds = 40)
        assertNull(st[0].deviceKey)
        assertNull(st[1].deviceKey)
        assertEquals(false, alloc.isKnown("ANT:9"))
    }

    @Test
    fun `las ignoradas siguen tras clear y vuelven con unignore`() {
        alloc.ignore(mapOf("ANT:2" to "ANT+ 2"))
        alloc.clear()
        assertEquals(mapOf("ANT:2" to "ANT+ 2"), alloc.ignoredSignals())
        assertNull(step(ant("2"), seconds = 5)[0].deviceKey)
        alloc.unignore("ANT:2")
        assertEquals(emptyMap<String, String>(), alloc.ignoredSignals())
        assertEquals("ANT:2", step(ant("2"))[0].deviceKey)
    }

    // --- R11: aparato nuevo pregunta antes de usarse --------------------------------------------------

    private val ask = DualSlotAllocator(HR, book, "HR", handoffMaxDiff = 15, askNewMs = 15_000L)

    private fun askStep(vararg s: SignalState, seconds: Int = 1): List<SlotStatus> {
        var r = emptyList<SlotStatus>()
        repeat(seconds) { now += 1_000; r = ask.tick(s.toList(), now) }
        return r
    }

    private fun asking() = ask.asking().map { it.id }

    @Test
    fun `aparato nuevo no recibe hueco y se pregunta a los 15 s (R11)`() {
        var st = askStep(ant("9"), seconds = 15)
        assertNull(st[0].deviceKey)
        assertNull(st[1].deviceKey)
        assertEquals(emptyList<String>(), asking())
        st = askStep(ant("9"))
        assertEquals(listOf("ANT:9"), asking())
        assertNull("sin aprobar sigue sin hueco", st[0].deviceKey)
    }

    @Test
    fun `aparato nuevo sin dato vuelve a cero y deja de preguntarse (R11)`() {
        askStep(ant("9"), seconds = 10)
        askStep(ant("9", fresh = false))
        askStep(ant("9"), seconds = 15)
        assertEquals("la cuenta empezó de nuevo", emptyList<String>(), asking())
        askStep(ant("9"))
        assertEquals(listOf("ANT:9"), asking())
        askStep(ant("9", fresh = false))
        assertEquals("sin dato se quita el aviso", emptyList<String>(), asking())
    }

    @Test
    fun `aprobar da hueco al aparato nuevo y quita el aviso (R11)`() {
        askStep(ant("9"), seconds = 16)
        assertEquals(listOf("ANT:9"), asking())
        ask.approve("ANT:9")
        assertEquals(emptyList<String>(), asking())
        assertEquals("ANT:9", askStep(ant("9"))[0].deviceKey)
    }

    @Test
    fun `aparato guardado entra sin preguntar (R11)`() {
        book.remember(HR, "ANT:1", "Mía")
        assertEquals("ANT:1", askStep(ant("1"))[0].deviceKey)
        assertEquals(emptyList<String>(), asking())
    }

    @Test
    fun `la otra senal de un aparato aprobado no pregunta (R11)`() {
        ask.approve("ANT:1")
        askStep(ant("1"))
        ask.joinSession("BLE:1", "ANT:1")
        val st = askStep(ant("1"), ble("1"), seconds = 20)
        assertEquals("ANT:1", st[0].deviceKey)
        assertNull(st[1].deviceKey)
        assertEquals(emptyList<String>(), asking())
    }

    @Test
    fun `un ANT sin aprobar no hace que otro ANT desplace a un BLE propio (R11)`() {
        book.remember(HR, "BLE:1", "Banda 1")
        book.remember(HR, "BLE:2", "Banda 2")
        book.remember(HR, "ANT:3", "Banda 3")
        askStep(ble("1"), ble("2"), seconds = 2)
        val st = askStep(ble("1"), ble("2"), ant("3"), ant("9"), seconds = 40)
        assertEquals("BLE:1", st[0].deviceKey)
        assertEquals("BLE:2", st[1].deviceKey)
    }

    @Test
    fun `ignorar quita el aviso del aparato nuevo (R11)`() {
        askStep(ant("9"), seconds = 16)
        assertEquals(listOf("ANT:9"), asking())
        ask.ignore(mapOf("ANT:9" to "ANT+ 9"))
        assertEquals(emptyList<String>(), asking())
        askStep(ant("9"), seconds = 20)
        assertEquals(emptyList<String>(), asking())
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
