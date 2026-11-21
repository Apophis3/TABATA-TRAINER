package com.tuapp.tabatatrainer.sensor

import com.tuapp.tabatatrainer.sensor.SensorKind.CADENCE
import com.tuapp.tabatatrainer.sensor.SensorKind.HR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceBookTest {

    @Test
    fun `ANT y BLE vinculados pertenecen al mismo aparato`() {
        val book = DeviceBook()
        assertTrue(book.link(HR, "ANT:12345", "BLE:AA:BB", "Polar H10", manual = false))
        assertEquals("ANT:12345", book.pairKeyOf(HR, "ANT:12345"))
        assertEquals("ANT:12345", book.pairKeyOf(HR, "BLE:AA:BB"))
        assertEquals("BLE:AA:BB", book.partnerOf(HR, "ANT:12345"))
        assertEquals("ANT:12345", book.partnerOf(HR, "BLE:AA:BB"))
        assertNull(book.pairKeyOf(HR, "BLE:CC:DD"))
        assertEquals("Polar H10", book.deviceOf(HR, "BLE:AA:BB")!!.alias)
    }

    @Test
    fun `vincular dos veces lo mismo no cambia nada`() {
        val book = DeviceBook()
        book.link(HR, "ANT:1", "BLE:X", "A", manual = false)
        assertFalse(book.link(HR, "ANT:1", "BLE:X", "A", manual = false))
        assertEquals(1, book.all().size)
    }

    @Test
    fun `una identidad solo pertenece a un aparato`() {
        val book = DeviceBook()
        book.link(HR, "ANT:1", "BLE:X", "A", manual = false)
        book.link(HR, "ANT:2", "BLE:X", "B", manual = false)
        assertEquals(1, book.all().size)
        assertNull(book.deviceOf(HR, "ANT:1"))
        assertEquals("ANT:2", book.pairKeyOf(HR, "BLE:X"))
    }

    @Test
    fun `pulso y cadencia son independientes aunque compartan numero ANT`() {
        val book = DeviceBook()
        book.link(HR, "ANT:1234", "BLE:HR", "Banda", manual = false)
        book.link(CADENCE, "ANT:1234", "BLE:CAD", "Cadencia", manual = false)
        assertEquals("BLE:HR", book.partnerOf(HR, "ANT:1234"))
        assertEquals("BLE:CAD", book.partnerOf(CADENCE, "ANT:1234"))
        assertEquals(2, book.all().size)
    }

    // --- Persistencia -----------------------------------------------------------------------

    @Test
    fun `el registro sobrevive a guardar y cargar`() {
        val book = DeviceBook()
        book.link(HR, "ANT:1", "BLE:AA:BB:CC", "Banda\tcon tab", manual = false)
        book.link(HR, "ANT:2", "BLE:DD", "Otra", manual = true)
        book.link(CADENCE, "ANT:3", "BLE:EE", "Cad", manual = false)
        book.setSlot(HR, "ANT:2", 0)
        book.remember(CADENCE, "BLE:FF", "Solo BLE")
        book.split(HR, "ANT:1")
        val loaded = DeviceBook.parse(book.serialize())
        assertEquals(book.all().toSet(), loaded.all().toSet())
        assertTrue(loaded.isBlocked(HR, "ANT:1", "BLE:AA:BB:CC"))
        assertEquals(0, loaded.deviceOf(HR, "BLE:DD")!!.preferredSlot)
        assertTrue(loaded.deviceOf(HR, "ANT:2")!!.manual)
    }

    @Test
    fun `el formato de la spec 003 se migra como pulsometros automaticos`() {
        val v1 = "ANT:1\tANT:1\tBLE:AA\tPolar\nANT:2\tANT:2\tBLE:BB\tGarmin"
        val book = DeviceBook.parse(v1)
        assertEquals(2, book.all(HR).size)
        val polar = book.deviceOf(HR, "BLE:AA")!!
        assertEquals("ANT:1", polar.key)
        assertEquals("Polar", polar.alias)
        assertFalse(polar.manual)
        assertNull(polar.preferredSlot)
        // Y sigue ahí tras pasar a v2
        val again = DeviceBook.parse(book.serialize())
        assertTrue(book.serialize().startsWith("v2"))
        assertEquals("BLE:AA", again.partnerOf(HR, "ANT:1"))
        assertEquals("BLE:BB", again.partnerOf(HR, "ANT:2"))
    }

    @Test
    fun `texto vacio o corrupto da registro vacio sin fallar`() {
        assertTrue(DeviceBook.parse(null).all().isEmpty())
        assertTrue(DeviceBook.parse("basura\nmas\tbasura").all().isEmpty())
        val v2 = "v2\nD\tANT:1\tHR\tANT:1\tBLE:AA\tx\t0\tOk\nD\tcorta\nD\tK\tNOEXISTE\tANT:9\t\t\t0\tA\nB\tHR\tnada\tBLE:1\nB\nZZZ"
        val book = DeviceBook.parse(v2)
        assertEquals(1, book.all().size)
        assertNull(book.deviceOf(HR, "ANT:1")!!.preferredSlot)   // hueco ilegible → sin hueco
        assertTrue(book.blocked().isEmpty())
    }

    // --- Manual manda -------------------------------------------------------------------------

    @Test
    fun `el automatico no rompe un emparejado manual`() {
        val book = DeviceBook()
        book.link(HR, "ANT:1", "BLE:X", "Mía", manual = true)
        assertFalse(book.link(HR, "ANT:1", "BLE:Y", "Auto", manual = false))
        assertFalse(book.link(HR, "ANT:2", "BLE:X", "Auto", manual = false))
        assertEquals("BLE:X", book.partnerOf(HR, "ANT:1"))
    }

    @Test
    fun `el manual sustituye a un emparejado automatico`() {
        val book = DeviceBook()
        book.link(HR, "ANT:1", "BLE:Y", "Auto", manual = false)
        assertTrue(book.link(HR, "ANT:1", "BLE:X", "Mía", manual = true))
        assertEquals("BLE:X", book.partnerOf(HR, "ANT:1"))
        assertNull(book.deviceOf(HR, "BLE:Y"))
        // Confirmar a mano un emparejado automático lo marca como manual
        book.link(HR, "ANT:2", "BLE:Z", "Auto", manual = false)
        assertTrue(book.link(HR, "ANT:2", "BLE:Z", null, manual = true))
        assertTrue(book.deviceOf(HR, "ANT:2")!!.manual)
    }

    @Test
    fun `separar deja dos aparatos de una senal y bloquea la pareja para el automatico`() {
        val book = DeviceBook()
        book.link(HR, "ANT:1", "BLE:X", "Banda", manual = false)
        book.setSlot(HR, "ANT:1", 1)
        assertTrue(book.split(HR, "ANT:1"))
        assertNull(book.partnerOf(HR, "ANT:1"))
        assertNull(book.pairKeyOf(HR, "BLE:X"))
        assertNotNull(book.deviceOf(HR, "BLE:X"))
        assertEquals(1, book.deviceOf(HR, "ANT:1")!!.preferredSlot)
        assertNull(book.deviceOf(HR, "BLE:X")!!.preferredSlot)
        assertTrue(book.isBlocked(HR, "ANT:1", "BLE:X"))
        assertFalse(book.link(HR, "ANT:1", "BLE:X", "Auto", manual = false))
        // El usuario puede volver a unirlos a mano, y eso desbloquea
        assertTrue(book.link(HR, "ANT:1", "BLE:X", null, manual = true))
        assertFalse(book.isBlocked(HR, "ANT:1", "BLE:X"))
    }

    @Test
    fun `no son el mismo aparato bloquea la pareja sin crear fichas, se guarda y un vinculo manual la desbloquea`() {
        val book = DeviceBook()
        assertTrue(book.block(HR, "ANT:1", "BLE:X"))
        assertFalse("ya estaba bloqueada", book.block(HR, "ANT:1", "BLE:X"))
        assertTrue(book.isBlocked(HR, "ANT:1", "BLE:X"))
        assertFalse("pulso y cadencia van aparte", book.isBlocked(CADENCE, "ANT:1", "BLE:X"))
        assertTrue(book.all().isEmpty())
        assertFalse(book.link(HR, "ANT:1", "BLE:X", "Auto", manual = false))
        // Sobrevive a guardar y cargar
        val loaded = DeviceBook.parse(book.serialize())
        assertTrue(loaded.isBlocked(HR, "ANT:1", "BLE:X"))
        // Unir a mano desde la hoja la desbloquea
        assertTrue(loaded.link(HR, "ANT:1", "BLE:X", "Mía", manual = true))
        assertFalse(loaded.isBlocked(HR, "ANT:1", "BLE:X"))
        assertEquals("BLE:X", loaded.partnerOf(HR, "ANT:1"))
    }

    @Test
    fun `tras separar se pueden emparejar con su pareja correcta`() {
        val book = DeviceBook()
        book.link(HR, "ANT:A", "BLE:B", "Cruzado", manual = false)   // emparejado erróneo
        book.split(HR, "ANT:A")
        assertTrue(book.link(HR, "ANT:A", "BLE:A", "Banda A", manual = false))
        assertEquals("BLE:A", book.partnerOf(HR, "ANT:A"))
    }

    // --- Hueco, alias, olvidar -----------------------------------------------------------------

    @Test
    fun `un hueco es de un solo aparato por tipo`() {
        val book = DeviceBook()
        book.link(HR, "ANT:1", "BLE:X", "A", manual = false)
        book.link(HR, "ANT:2", "BLE:Y", "B", manual = false)
        book.link(CADENCE, "ANT:3", "BLE:Z", "C", manual = false)
        book.setSlot(HR, "ANT:1", 0)
        book.setSlot(CADENCE, "ANT:3", 0)
        assertTrue(book.setSlot(HR, "ANT:2", 0))
        assertNull(book.deviceOf(HR, "ANT:1")!!.preferredSlot)
        assertEquals(0, book.deviceOf(HR, "ANT:2")!!.preferredSlot)
        assertEquals(0, book.deviceOf(CADENCE, "ANT:3")!!.preferredSlot)
    }

    @Test
    fun `al emparejar se conservan el hueco y el alias de la ficha previa`() {
        val book = DeviceBook()
        val key = book.remember(HR, "BLE:X", "Mi banda")
        assertNull(book.pairKeyOf(HR, "BLE:X"))   // una sola señal: no cuenta como emparejado
        book.setSlot(HR, key, 1)
        book.link(HR, "ANT:7", "BLE:X", "Nombre ANT", manual = false)
        val d = book.deviceOf(HR, "ANT:7")!!
        assertEquals("ANT:7", d.key)
        assertEquals("Mi banda", d.alias)
        assertEquals(1, d.preferredSlot)
    }

    @Test
    fun `renombrar y olvidar`() {
        val book = DeviceBook()
        book.link(HR, "ANT:1", "BLE:X", "A", manual = false)
        assertTrue(book.rename(HR, "ANT:1", "  Banda roja "))
        assertEquals("Banda roja", book.deviceOf(HR, "BLE:X")!!.alias)
        assertFalse(book.rename(HR, "ANT:1", "   "))
        book.split(HR, "ANT:1")
        assertTrue(book.forget(HR, "ANT:1"))
        assertNull(book.deviceOf(HR, "ANT:1"))
        assertFalse(book.isBlocked(HR, "ANT:1", "BLE:X"))
    }
}
