package com.tuapp.tabatatrainer.ui.devices

import com.tuapp.tabatatrainer.sensor.PhysicalDevice
import com.tuapp.tabatatrainer.sensor.SensorKind
import com.tuapp.tabatatrainer.sensor.SensorKind.CADENCE
import com.tuapp.tabatatrainer.sensor.SensorKind.HR
import com.tuapp.tabatatrainer.sensor.VisibleSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedPairingTest {

    private fun ant(n: String, connected: Boolean = true, kind: SensorKind = HR) =
        VisibleSignal(kind, "ANT:$n", "", isAnt = true, value = 100, connected = connected, fresh = connected,
            battery = null, group = "ANT:$n", slot = null, sessionOnly = false)

    private fun ble(n: String, name: String = "BLE $n", connected: Boolean = true) =
        VisibleSignal(HR, "BLE:$n", name, isAnt = false, value = 100, connected = connected, fresh = connected,
            battery = null, group = "BLE:$n", slot = null, sessionOnly = false)

    @Test
    fun `sin senales espera la ANT+`() {
        assertEquals(GuidedStep.WaitingAnt, guidedPairing(HR, emptyList(), emptyList()))
        assertEquals(GuidedStep.WaitingAnt, guidedPairing(HR, emptyList(), listOf(ble("1"))))
    }

    @Test
    fun `con la ANT+ espera la BLE, y una BLE desconectada no cuenta`() {
        assertEquals(GuidedStep.WaitingBle, guidedPairing(HR, emptyList(), listOf(ant("1"))))
        assertEquals(GuidedStep.WaitingBle, guidedPairing(HR, emptyList(), listOf(ant("1"), ble("1", connected = false))))
    }

    @Test
    fun `con mas de un aparato encendido pide apagar los demas`() {
        assertEquals(GuidedStep.TooMany(2, 1), guidedPairing(HR, emptyList(), listOf(ant("1"), ant("2"), ble("1"))))
        assertEquals(GuidedStep.TooMany(1, 2), guidedPairing(HR, emptyList(), listOf(ant("1"), ble("1"), ble("2"))))
    }

    @Test
    fun `las senales de cadencia no cuentan para el pulso`() {
        assertEquals(GuidedStep.WaitingBle, guidedPairing(HR, emptyList(), listOf(ant("1"), ant("2", kind = CADENCE))))
    }

    @Test
    fun `si ya estan unidos lo dice con su nombre`() {
        val saved = PhysicalDevice("ANT:1", HR, "Polar", "ANT:1", "BLE:1", manual = true)
        assertEquals(GuidedStep.AlreadyLinked("Polar"), guidedPairing(HR, listOf(saved), listOf(ant("1"), ble("1"))))
    }

    @Test
    fun `listo para unir, con el nombre BLE o el guardado como sugerencia`() {
        val step = guidedPairing(HR, emptyList(), listOf(ant("1"), ble("2", name = "HRM-Pro")))
        assertTrue(step is GuidedStep.Ready)
        step as GuidedStep.Ready
        assertEquals("ANT:1", step.ant.id)
        assertEquals("BLE:2", step.ble.id)
        assertEquals("HRM-Pro", step.suggestedName)
        // Si una de las dos ya tiene ficha, se sugiere su nombre
        val mine = PhysicalDevice("BLE:2", HR, "Mi banda", null, "BLE:2")
        assertEquals("Mi banda", (guidedPairing(HR, listOf(mine), listOf(ant("1"), ble("2"))) as GuidedStep.Ready).suggestedName)
        // Sin nombres: el de la ANT+
        assertEquals("ANT+ 1", (guidedPairing(HR, emptyList(), listOf(ant("1"), ble("2", name = ""))) as GuidedStep.Ready).suggestedName)
    }
}
