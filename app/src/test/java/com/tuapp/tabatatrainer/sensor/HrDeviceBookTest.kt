package com.tuapp.tabatatrainer.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HrDeviceBookTest {

    @Test
    fun `ANT y BLE vinculados pertenecen al mismo aparato`() {
        val book = HrDeviceBook()
        assertTrue(book.link("ANT:12345", "BLE:AA:BB", "Polar H10"))
        assertEquals("ANT:12345", book.keyOf("ANT:12345"))
        assertEquals("ANT:12345", book.keyOf("BLE:AA:BB"))
        assertEquals("BLE:AA:BB", book.partnerOf("ANT:12345"))
        assertEquals("ANT:12345", book.partnerOf("BLE:AA:BB"))
        assertNull(book.keyOf("BLE:CC:DD"))
    }

    @Test
    fun `vincular dos veces lo mismo no cambia nada`() {
        val book = HrDeviceBook()
        book.link("ANT:1", "BLE:X", "A")
        assertFalse(book.link("ANT:1", "BLE:X", "A"))
        assertEquals(1, book.all().size)
    }

    @Test
    fun `una identidad solo pertenece a un aparato`() {
        val book = HrDeviceBook()
        book.link("ANT:1", "BLE:X", "A")
        book.link("ANT:2", "BLE:X", "B")
        assertEquals(1, book.all().size)
        assertNull(book.keyOf("ANT:1"))
        assertEquals("ANT:2", book.keyOf("BLE:X"))
    }

    @Test
    fun `el registro sobrevive a guardar y cargar`() {
        val book = HrDeviceBook()
        book.link("ANT:1", "BLE:AA:BB:CC", "Banda\tcon tab")
        book.link("ANT:2", "BLE:DD", "Otra")
        val loaded = HrDeviceBook.parse(book.serialize())
        assertEquals(2, loaded.all().size)
        assertEquals("ANT:1", loaded.keyOf("BLE:AA:BB:CC"))
        assertEquals("ANT:2", loaded.keyOf("BLE:DD"))
    }

    @Test
    fun `texto vacio o corrupto da registro vacio`() {
        assertTrue(HrDeviceBook.parse(null).all().isEmpty())
        assertTrue(HrDeviceBook.parse("basura\nmas\tbasura").all().isEmpty())
    }

    @Test
    fun `misma HR con desfase de 3 s se reconoce como el mismo aparato`() {
        val hr = listOf(70, 72, 75, 79, 84, 88, 91, 93, 94, 92, 89, 85, 81, 78, 76, 75, 74, 76, 79, 83)
        val delayed = List(3) { 68 } + hr.dropLast(3)
        // Sin compensar el desfase la diferencia es grande...
        val noLag = minLaggedAvgDiff(hr, delayed, 0)
        assertTrue("sin desfase: $noLag", noLag > 4.0)
        // ...compensándolo es prácticamente cero
        assertTrue(minLaggedAvgDiff(hr, delayed, 5) < 1.0)
    }

    @Test
    fun `dos personas distintas no se confunden`() {
        val a = listOf(70, 72, 75, 79, 84, 88, 91, 93, 94, 92, 89, 85, 81, 78, 76, 75, 74, 76, 79, 83)
        val b = listOf(110, 108, 105, 103, 100, 99, 101, 104, 108, 112, 115, 117, 116, 113, 110, 107, 105, 104, 106, 109)
        assertTrue(minLaggedAvgDiff(a, b, 5) > 4.0)
    }
}
