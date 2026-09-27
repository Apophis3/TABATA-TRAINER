package com.tuapp.tabatatrainer.sensor

/** Nivel de batería de un sensor, común a BLE (%) y ANT+ (estado) — spec 005 */
enum class BatteryLevel { GOOD, OK, LOW, CRITICAL, UNKNOWN }

/**
 * Batería de un sensor.
 * [percent] solo viene por BLE (Battery Level 0x2A19); [voltage] solo por ANT+ (si el sensor lo envía).
 */
data class SensorBattery(
    val percent: Int? = null,
    val level: BatteryLevel = BatteryLevel.UNKNOWN,
    val voltage: Float? = null
) {
    val isLow: Boolean get() = level == BatteryLevel.LOW || level == BatteryLevel.CRITICAL

    companion object {
        /** ≥50 GOOD, 20–49 OK, 10–19 LOW, <10 CRITICAL; fuera de 0..100 → null */
        fun fromPercent(percent: Int): SensorBattery? {
            if (percent !in 0..100) return null
            val level = when {
                percent >= 50 -> BatteryLevel.GOOD
                percent >= 20 -> BatteryLevel.OK
                percent >= 10 -> BatteryLevel.LOW
                else -> BatteryLevel.CRITICAL
            }
            return SensorBattery(percent = percent, level = level)
        }

        /**
         * Desde el estado ANT+ (`BatteryStatus.name` del plugin: NEW, GOOD, OK, LOW, CRITICAL, INVALID, UNRECOGNIZED).
         * Se recibe como texto para que la lógica no dependa del SDK de ANT+ y se pueda testear.
         * Voltaje negativo o no finito (el plugin usa valores inválidos cuando no lo envía) → null.
         */
        fun fromAntStatus(statusName: String?, voltage: Float? = null): SensorBattery {
            val level = when (statusName?.uppercase()) {
                "NEW", "GOOD" -> BatteryLevel.GOOD
                "OK" -> BatteryLevel.OK
                "LOW" -> BatteryLevel.LOW
                "CRITICAL" -> BatteryLevel.CRITICAL
                else -> BatteryLevel.UNKNOWN
            }
            val v = voltage?.takeIf { it.isFinite() && it > 0f }
            return SensorBattery(percent = null, level = level, voltage = v)
        }

        /** Battery Level (0x2A19): 1 byte sin signo con el %. Vacío o > 100 → null */
        fun parseBleBatteryLevel(value: ByteArray?): SensorBattery? {
            if (value == null || value.isEmpty()) return null
            return fromPercent(value[0].toInt() and 0xFF)
        }
    }
}
