package com.tuapp.tabatatrainer.rawlog

/**
 * Parte sin Android de `battery.csv` (spec 008, R11; plan §6): abre el archivo y escribe cada lectura tal cual.
 * Las lecturas son pocas (cada 60 s, en cada cambio de pantalla, al empezar y al parar), así que cada fila se vacía
 * al instante: si el móvil duerme o el proceso muere, lo ya leído está en el disco.
 * La lectura de `BatteryManager` y la tarea de 60 s están en [RawBatterySource].
 *
 * No es seguro entre hilos: solo desde el hilo «RawLog» de la sesión.
 */
class RawBatteryRecorder(private val core: RawSessionCore) {

    private var writer: RawFileWriter? = null

    var started = false
        private set

    /** Lecturas escritas, por motivo (`periodic`, `screen_on`, …), para `meta.json`. */
    val rowsByTrigger = linkedMapOf<String, Long>()

    /** Crea `battery.csv` y anota `source_started`. @return `false` si la sesión ya no está abierta. */
    fun started(stamp: RawStamp, params: String): Boolean {
        val w = core.openWriter(RawFiles.BATTERY, BatterySample.HEADER) ?: return false
        writer = w
        started = true
        core.event(stamp, RawEventType.SOURCE_STARTED, "file=${RawFiles.BATTERY};$params")
        return true
    }

    fun onReading(sample: BatterySample) {
        if (!core.isOpen || !started) return
        writer?.append(sample, flushNow = true)
        rowsByTrigger[sample.trigger] = (rowsByTrigger[sample.trigger] ?: 0L) + 1
    }

    companion object {
        /** `BatteryManager.getIntProperty` da `Integer.MIN_VALUE` si no lo sabe: vacío, nunca un número (R5). */
        fun rawInt(value: Int): Int? = if (value == Int.MIN_VALUE) null else value

        /** `BatteryManager.getLongProperty` da `Long.MIN_VALUE` si no lo sabe. */
        fun rawLong(value: Long): Long? = if (value == Long.MIN_VALUE) null else value
    }
}
