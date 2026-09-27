package com.tuapp.tabatatrainer.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorBatteryTest {

    @Test
    fun percentThresholds() {
        assertEquals(BatteryLevel.GOOD, SensorBattery.fromPercent(100)!!.level)
        assertEquals(BatteryLevel.GOOD, SensorBattery.fromPercent(50)!!.level)
        assertEquals(BatteryLevel.OK, SensorBattery.fromPercent(49)!!.level)
        assertEquals(BatteryLevel.OK, SensorBattery.fromPercent(20)!!.level)
        assertEquals(BatteryLevel.LOW, SensorBattery.fromPercent(19)!!.level)
        assertEquals(BatteryLevel.LOW, SensorBattery.fromPercent(10)!!.level)
        assertEquals(BatteryLevel.CRITICAL, SensorBattery.fromPercent(9)!!.level)
        assertEquals(BatteryLevel.CRITICAL, SensorBattery.fromPercent(0)!!.level)
    }

    @Test
    fun percentOutOfRange_isNull() {
        assertNull(SensorBattery.fromPercent(101))
        assertNull(SensorBattery.fromPercent(-1))
    }

    @Test
    fun antStatusMapping() {
        assertEquals(BatteryLevel.GOOD, SensorBattery.fromAntStatus("NEW").level)
        assertEquals(BatteryLevel.GOOD, SensorBattery.fromAntStatus("GOOD").level)
        assertEquals(BatteryLevel.OK, SensorBattery.fromAntStatus("OK").level)
        assertEquals(BatteryLevel.LOW, SensorBattery.fromAntStatus("LOW").level)
        assertEquals(BatteryLevel.CRITICAL, SensorBattery.fromAntStatus("CRITICAL").level)
        assertEquals(BatteryLevel.UNKNOWN, SensorBattery.fromAntStatus("INVALID").level)
        assertEquals(BatteryLevel.UNKNOWN, SensorBattery.fromAntStatus("UNRECOGNIZED").level)
        assertEquals(BatteryLevel.UNKNOWN, SensorBattery.fromAntStatus(null).level)
        assertNull(SensorBattery.fromAntStatus("GOOD").percent)
    }

    @Test
    fun antVoltage_invalidIsDropped() {
        assertEquals(2.9f, SensorBattery.fromAntStatus("OK", 2.9f).voltage!!, 0.001f)
        assertNull(SensorBattery.fromAntStatus("OK", -1f).voltage)
        assertNull(SensorBattery.fromAntStatus("OK", Float.NaN).voltage)
        assertNull(SensorBattery.fromAntStatus("OK", null).voltage)
    }

    @Test
    fun bleBatteryLevelParser() {
        assertEquals(87, SensorBattery.parseBleBatteryLevel(byteArrayOf(87))!!.percent)
        assertEquals(BatteryLevel.CRITICAL, SensorBattery.parseBleBatteryLevel(byteArrayOf(5))!!.level)
        // Byte sin signo: 0xC8 = 200 → inválido
        assertNull(SensorBattery.parseBleBatteryLevel(byteArrayOf(0xC8.toByte())))
        assertNull(SensorBattery.parseBleBatteryLevel(byteArrayOf()))
        assertNull(SensorBattery.parseBleBatteryLevel(null))
    }

    @Test
    fun isLow_onlyForLowAndCritical() {
        assertTrue(SensorBattery.fromPercent(15)!!.isLow)
        assertTrue(SensorBattery.fromPercent(3)!!.isLow)
        assertFalse(SensorBattery.fromPercent(30)!!.isLow)
        assertFalse(SensorBattery.fromAntStatus(null).isLow)
    }
}
