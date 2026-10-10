package com.tuapp.tabatatrainer.util.laps

/** Tipo de vuelta: AUTO = cada 1000 m exactos; MANUAL = la marca el usuario con el botón */
enum class LapType { AUTO, MANUAL }

/** Origen de la cadencia de una vuelta (se persiste como texto en `lap_records.cadenceSource`) */
enum class CadenceSource { PEDOMETER, BIKE_SENSOR, NONE }

/**
 * Una vuelta ya cerrada.
 * [order] = orden global de creación; [typeNumber] = número dentro de su serie (AUTO 1,2,3… / MANUAL 1,2,3…).
 * Los instantes de reloj de pared ([startWallMs]/[endWallMs]) los aporta quien llama (el libro no tiene reloj propio).
 */
data class LapRecord(
    val order: Int,
    val type: LapType,
    val typeNumber: Int,
    val startSeconds: Int,
    val endSeconds: Int,
    val startMeters: Float,
    val endMeters: Float,
    val startWallMs: Long,
    val endWallMs: Long
) {
    val durationSeconds: Int get() = endSeconds - startSeconds
    val distanceMeters: Float get() = endMeters - startMeters
}

/**
 * Libro de vueltas de una sesión: dos series independientes.
 *
 * - AUTO: cada km exacto ([AutoLapCrossing]); su ventana arranca donde terminó la anterior.
 * - MANUAL: cada pulsación acepta un intervalo contiguo con la anterior manual (o con el inicio de la ruta).
 *   Las manuales NO alteran las AUTO ni al revés; cada serie tiene su propia numeración.
 * - Una manual demasiado corta ([LapGuard]) se rechaza y NO mueve la ventana: el tiempo sigue contando
 *   para la siguiente pulsación.
 * - Sin Android y sin reloj: tiempo, distancia y reloj de pared entran por parámetro.
 */
class LapBook {

    private val _laps = mutableListOf<LapRecord>()

    private var autoStartSeconds = 0
    private var autoStartMeters = 0f
    private var autoStartWallMs = 0L

    private var manualStartSeconds = 0
    private var manualStartMeters = 0f
    private var manualStartWallMs = 0L

    /** Todas las vueltas, en orden de creación */
    val laps: List<LapRecord> get() = _laps

    val autoCount: Int get() = _laps.count { it.type == LapType.AUTO }
    val manualCount: Int get() = _laps.count { it.type == LapType.MANUAL }

    /** Última vuelta manual registrada (con su distancia y tiempo reales), o null si no hay ninguna */
    val lastManual: LapRecord? get() = _laps.lastOrNull { it.type == LapType.MANUAL }

    /** Inicio (s de cronómetro) de la vuelta manual en curso */
    val currentManualStartSeconds: Int get() = manualStartSeconds

    /** Inicio (m acumulados) de la vuelta manual en curso */
    val currentManualStartMeters: Float get() = manualStartMeters

    /** Inicio (s de cronómetro) del tramo AUTO en curso */
    val currentAutoStartSeconds: Int get() = autoStartSeconds

    /** Inicio (m acumulados) del tramo AUTO en curso */
    val currentAutoStartMeters: Float get() = autoStartMeters

    /** Reinicia el libro al comenzar una ruta ([startWallMs] = instante de pared del inicio) */
    fun reset(startWallMs: Long) {
        _laps.clear()
        autoStartSeconds = 0
        autoStartMeters = 0f
        autoStartWallMs = startWallMs
        manualStartSeconds = 0
        manualStartMeters = 0f
        manualStartWallMs = startWallMs
    }

    /** Cierra el AUTO de [crossing]: exactamente 1000 m desde el cruce anterior */
    fun addAuto(crossing: AutoLapCrossing, endWallMs: Long): LapRecord {
        val endSeconds = Math.round(crossing.timeSeconds).toInt().coerceAtLeast(autoStartSeconds)
        val lap = LapRecord(
            order = _laps.size + 1,
            type = LapType.AUTO,
            typeNumber = autoCount + 1,
            startSeconds = autoStartSeconds,
            endSeconds = endSeconds,
            startMeters = autoStartMeters,
            endMeters = crossing.distanceMeters,
            startWallMs = autoStartWallMs,
            endWallMs = endWallMs
        )
        _laps += lap
        autoStartSeconds = endSeconds
        autoStartMeters = crossing.distanceMeters
        autoStartWallMs = endWallMs
        return lap
    }

    /**
     * Cierra una vuelta manual hasta ([nowSeconds], [nowMeters]).
     * @return la vuelta creada, o null si [LapGuard] la rechaza (dura menos de 5 s)
     */
    fun addManual(nowSeconds: Int, nowMeters: Float, nowWallMs: Long): LapRecord? {
        if (LapGuard.check(nowSeconds - manualStartSeconds) != LapGuard.Verdict.OK) return null

        val lap = LapRecord(
            order = _laps.size + 1,
            type = LapType.MANUAL,
            typeNumber = manualCount + 1,
            startSeconds = manualStartSeconds,
            endSeconds = nowSeconds,
            startMeters = manualStartMeters,
            endMeters = nowMeters.coerceAtLeast(manualStartMeters),
            startWallMs = manualStartWallMs,
            endWallMs = nowWallMs
        )
        _laps += lap
        manualStartSeconds = nowSeconds
        manualStartMeters = lap.endMeters
        manualStartWallMs = nowWallMs
        return lap
    }
}
