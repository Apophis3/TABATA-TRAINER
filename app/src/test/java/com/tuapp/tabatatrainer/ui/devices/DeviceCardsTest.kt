package com.tuapp.tabatatrainer.ui.devices

import com.tuapp.tabatatrainer.sensor.PhysicalDevice
import com.tuapp.tabatatrainer.sensor.SensorKind.CADENCE
import com.tuapp.tabatatrainer.sensor.SensorKind.HR
import com.tuapp.tabatatrainer.sensor.VisibleSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCardsTest {

    private fun ant(n: String, group: String = "ANT:$n", slot: Int? = null, session: Boolean = false, v: Int = 100) =
        VisibleSignal(HR, "ANT:$n", "ANT $n", isAnt = true, value = v, connected = true, fresh = true, battery = null,
            group = group, slot = slot, sessionOnly = session)

    private fun ble(n: String, name: String = "BLE $n", group: String = "BLE:$n", slot: Int? = null, session: Boolean = false) =
        VisibleSignal(HR, "BLE:$n", name, isAnt = false, value = 100, connected = true, fresh = true, battery = null,
            group = group, slot = slot, sessionOnly = session)

    private fun saved(ant: String?, ble: String?, alias: String, slot: Int? = null) =
        PhysicalDevice(key = ant ?: ble!!, kind = HR, alias = alias, antId = ant, bleId = ble, preferredSlot = slot, manual = true)

    @Test
    fun `no es mio ignora las senales vistas y las de su ficha (R10)`() {
        // Visto solo por ANT+, guardado con las dos señales: se ignoran las dos
        val banda = saved("ANT:1", "BLE:1", "Banda ajena")
        val c = buildDeviceCards(HR, listOf(banda), listOf(ant("1"))).single()
        assertEquals(mapOf("ANT:1" to "Banda ajena · ANT+ 1", "BLE:1" to "Banda ajena · BLE 1"), ignoreIds(c))
        // Sin ficha y sin nombre propio: la etiqueta es la señal
        val suelta = buildDeviceCards(HR, emptyList(), listOf(ant("7"))).single()
        assertEquals(mapOf("ANT:7" to "ANT+ 7"), ignoreIds(suelta))
    }

    @Test
    fun `aparato guardado con sus dos senales vistas es una tarjeta`() {
        val banda = saved("ANT:1", "BLE:1", "Banda roja", slot = 0)
        val cards = buildDeviceCards(HR, listOf(banda), listOf(ble("1", group = "ANT:1", slot = 0), ant("1", slot = 0)))
        val c = cards.single()
        assertEquals(banda, c.device)
        assertEquals("Banda roja", c.title)
        assertEquals("ANT+ primero", listOf("ANT:1", "BLE:1"), c.signals.map { it.id })
        assertEquals(0, c.slot)
        assertTrue(c.isPaired)
        assertNull("emparejado: no se ofrece Unir con…", c.loneId)
        assertFalse(c.sessionOnly)
    }

    @Test
    fun `senal sin guardar tiene su tarjeta con el nombre BLE`() {
        val c = buildDeviceCards(HR, emptyList(), listOf(ble("9", name = "XOSS X2"))).single()
        assertNull(c.device)
        assertEquals("XOSS X2", c.title)
        assertEquals("BLE:9", c.loneId)
        assertEquals("BLE:9", c.primaryId)
        assertEquals("ANT+ 5", buildDeviceCards(HR, emptyList(), listOf(ant("5"))).single().title)
    }

    @Test
    fun `aparato guardado que no se ve va al final, sin senales ni hueco`() {
        val apagado = saved("ANT:2", "BLE:2", "Apagado")
        val cards = buildDeviceCards(HR, listOf(apagado), listOf(ant("1", slot = 0)))
        assertEquals(listOf("ANT:1", "ANT:2"), cards.map { it.group })
        assertTrue(cards[1].signals.isEmpty())
        assertNull(cards[1].slot)
        assertEquals("Apagado", cards[1].title)
    }

    @Test
    fun `orden por hueco y solo las senales del tipo pedido`() {
        val cadence = ant("7").copy(kind = CADENCE)
        val cards = buildDeviceCards(HR, emptyList(), listOf(ant("1", slot = 1), ant("2", slot = 0), ble("3"), cadence))
        assertEquals(listOf("ANT:2", "ANT:1", "BLE:3"), cards.map { it.group })
    }

    @Test
    fun `agrupado solo de sesion se puede deshacer o guardar como un aparato`() {
        val c = buildDeviceCards(
            HR, emptyList(), listOf(ant("5", group = "BLE:5", slot = 0, session = true), ble("5", slot = 0, session = true))
        ).single()
        assertTrue(c.sessionOnly)
        assertEquals(listOf("ANT:5" to "BLE:5"), c.sessionLinks)
        assertTrue(c.canSaveAsOne)
        assertNull(c.loneId)
        assertFalse(c.isPaired)
    }

    @Test
    fun `relevo de un aparato guardado a un BLE desconocido cuenta como agrupado de sesion`() {
        val banda = saved("ANT:1", null, "Banda")
        val c = buildDeviceCards(HR, listOf(banda), listOf(ble("8", group = "ANT:1", slot = 0, session = true))).single()
        assertEquals(banda, c.device)
        assertEquals(listOf("ANT:1" to "BLE:8"), c.sessionLinks)
    }

    @Test
    fun `unir con solo ofrece senales sueltas del otro protocolo`() {
        val cards = buildDeviceCards(
            HR,
            listOf(saved("ANT:1", "BLE:1", "Emparejada")),
            listOf(
                ant("1", slot = 0), ble("1", group = "ANT:1", slot = 0),       // emparejada
                ant("5", group = "BLE:5", session = true), ble("5", session = true), // agrupado de sesión
                ant("7"), ant("9"), ble("8")                                     // sueltas
            )
        )
        fun card(group: String) = cards.first { it.group == group }
        assertEquals(listOf("BLE:8"), linkCandidates(card("ANT:7"), cards).map { it.group })
        assertEquals(setOf("ANT:7", "ANT:9"), linkCandidates(card("BLE:8"), cards).map { it.group }.toSet())
        assertTrue(linkCandidates(card("ANT:1"), cards).isEmpty())
        assertTrue(linkCandidates(card("BLE:5"), cards).isEmpty())
    }

    @Test
    fun `texto de cada senal con su valor solo si es reciente`() {
        assertEquals("ANT+ 12 · 100 bpm", signalLine(ant("12")))
        assertEquals("BLE XOSS · sin dato", signalLine(ble("3", name = "XOSS").copy(fresh = false)))
        assertEquals("BLE 3 · sin señal", signalLine(ble("3", name = "").copy(connected = false, fresh = false)))
        assertEquals("ANT+ 4 · 0 rpm", signalLine(ant("4", v = 0).copy(kind = CADENCE)))
    }
}
