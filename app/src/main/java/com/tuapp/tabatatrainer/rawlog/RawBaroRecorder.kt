package com.tuapp.tabatatrainer.rawlog

/** Datos del barómetro para `meta.json` (R12), tal como los da Android. */
data class BaroSensorInfo(
    val name: String?,
    val vendor: String?,
    val maxRangeHpa: Float,
    val resolutionHpa: Float,
    val minDelayUs: Int,
    val fifoMaxEvents: Int
)

/**
 * Parte sin Android de `baro.csv` (spec 008, R9; plan §5; T-08): abre el archivo, junta las lecturas por segundo
 * con [BaroSecondAverager] (una fila por segundo con la media, el número de lecturas, la mínima y la máxima),
 * anota `source_started` / `source_error` y guarda los datos del sensor para `meta.json`.
 * El registro del sensor está en [RawBaroSource].
 *
 * No es seguro entre hilos: solo desde el hilo «RawLog» de la sesión.
 */
class RawBaroRecorder(
    private val core: RawSessionCore,
    val averager: BaroSecondAverager = BaroSecondAverager()
) {
    private var writer: RawFileWriter? = null

    /** `false` si el móvil no tiene barómetro (no se crea el archivo, R9). `null` hasta que se mira. */
    var available: Boolean? = null
        private set

    var sensorInfo: BaroSensorInfo? = null
        private set

    var started = false
        private set

    var lastError: String? = null
        private set

    /** El móvil no tiene barómetro: sin archivo; queda en `meta.json` y como `source_error`. */
    fun unavailable(stamp: RawStamp) {
        available = false
        fail(stamp, REASON_NO_SENSOR)
    }

    /**
     * El sensor se registró: crea `baro.csv` y anota `source_started` con lo pedido.
     * @return `false` si la sesión ya no está abierta.
     */
    fun started(stamp: RawStamp, info: BaroSensorInfo, params: String): Boolean {
        available = true
        sensorInfo = info
        val w = core.openWriter(RawFiles.BARO, BaroSample.HEADER) ?: return false
        writer = w
        started = true
        core.event(stamp, RawEventType.SOURCE_STARTED, "file=${RawFiles.BARO};$params")
        return true
    }

    /** Hay barómetro pero Android no aceptó el registro. */
    fun failed(stamp: RawStamp, info: BaroSensorInfo?, reason: String) {
        available = true
        sensorInfo = info
        fail(stamp, reason)
    }

    /** Una lectura del sensor. Si es de un segundo nuevo, se escribe la fila del anterior (búfer normal de 5 s). */
    fun onSample(stamp: RawStamp, pressureHpa: Float) {
        if (!core.isOpen || !started) return
        write(averager.add(stamp, pressureHpa))
    }

    /** Temporizador de 1 s: escribe el segundo abierto si ya pasó (así no espera a la siguiente lectura). */
    fun tick(now: RawStamp) {
        if (!core.isOpen || !started) return
        write(averager.closeIfPast(now))
    }

    /** Al parar, antes de cerrar la sesión: escribe el último segundo. */
    fun flushPending() {
        if (!core.isOpen || !started) return
        write(averager.drain())
    }

    private fun write(row: BaroSample?) {
        if (row != null) writer?.append(row)
    }

    private fun fail(stamp: RawStamp, reason: String) {
        lastError = reason
        core.event(stamp, RawEventType.SOURCE_ERROR, "file=${RawFiles.BARO};reason=$reason")
    }

    companion object {
        const val REASON_NO_SENSOR = "no_pressure_sensor"
    }
}
