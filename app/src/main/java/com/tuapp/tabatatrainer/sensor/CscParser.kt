package com.tuapp.tabatatrainer.sensor

/**
 * Cadencia (rpm) a partir de la medida CSC de BLE (0x2A5B): vueltas de biela acumuladas y hora del último
 * evento (1/1024 s). Lógica pura; una instancia por cadenciómetro (spec 007, T-03).
 *
 * - El primer paquete solo sirve de referencia (no da rpm).
 * - Contadores de 16 bits: se corrige el desbordamiento (65536).
 * - Sin vuelta nueva durante [zeroAfterMs] → 0 rpm (parado). 0 rpm es un dato válido.
 * - Valores fuera de 0–[MAX_RPM] se descartan.
 */
class CscParser(private val zeroAfterMs: Long = ZERO_AFTER_MS) {
    companion object {
        const val ZERO_AFTER_MS = 3_000L
        const val MAX_RPM = 300
        private const val MIN_DIFF_TIME = 50      // 1/1024 s: más rápido que esto no es real
        private const val MAX_DIFF_TIME = 4096    // 4 s: hueco de datos, no se calcula con él
        private const val FLAG_WHEEL = 0x01
        private const val FLAG_CRANK = 0x02
    }

    private var lastRevs = -1
    private var lastTime = -1
    private var lastRevAtMs = 0L

    fun reset() { lastRevs = -1; lastTime = -1; lastRevAtMs = 0L }

    /** rpm de este paquete, 0 si está parado, o null si aún no hay dato (o el paquete no sirve) */
    fun onMeasurement(data: ByteArray, nowMs: Long): Int? {
        if (data.isEmpty()) return null
        val flags = data[0].toInt() and 0xFF
        var offset = 1
        if (flags and FLAG_WHEEL != 0) offset += 6   // vueltas de rueda (4 bytes) + hora (2 bytes)
        if (flags and FLAG_CRANK == 0 || data.size < offset + 4) return null
        val revs = u16(data, offset)
        val time = u16(data, offset + 2)

        val first = lastRevs < 0
        val diffRevs = if (first) 0 else (revs - lastRevs + 65536) % 65536
        val diffTime = if (first) 0 else (time - lastTime + 65536) % 65536
        lastRevs = revs; lastTime = time
        if (first) { lastRevAtMs = nowMs; return null }

        if (diffRevs == 0) return if (nowMs - lastRevAtMs >= zeroAfterMs) 0 else null
        lastRevAtMs = nowMs
        if (diffTime < MIN_DIFF_TIME || diffTime > MAX_DIFF_TIME) return null
        val rpm = diffRevs * 1024 * 60 / diffTime
        return rpm.takeIf { it in 0..MAX_RPM }
    }

    private fun u16(d: ByteArray, i: Int) = (d[i].toInt() and 0xFF) or ((d[i + 1].toInt() and 0xFF) shl 8)
}
