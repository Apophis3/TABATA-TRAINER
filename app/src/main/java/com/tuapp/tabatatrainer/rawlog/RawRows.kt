package com.tuapp.tabatatrainer.rawlog

import com.tuapp.tabatatrainer.rawlog.RawCsvFormat.bool
import com.tuapp.tabatatrainer.rawlog.RawCsvFormat.num
import com.tuapp.tabatatrainer.rawlog.RawCsvFormat.row
import com.tuapp.tabatatrainer.rawlog.RawCsvFormat.text

/**
 * Filas del registro en bruto (spec 008). Datos simples de Kotlin, sin clases de Android, para poder
 * probar el formato en la JVM. La conversión desde `Location`, `SensorEvent`, etc. se hace en las fuentes.
 *
 * Todas las filas empiezan por [RawStamp]: hora de reloj y tiempo desde el arranque del móvil en el
 * momento en que la app recibe el dato.
 */

/** Nombres de los archivos de una sesión dentro de `files/rawlog/<sessionId>/`. */
object RawFiles {
    const val ROOT_DIR = "rawlog"
    const val GPS_FUSED = "gps_fused.csv"
    const val GPS_GNSS = "gps_gnss.csv"
    const val GNSS_SATS = "gnss_sats.csv"
    const val GNSS_STATUS = "gnss_status.csv"
    const val BARO = "baro.csv"
    const val EVENTS = "events.csv"
    const val BATTERY = "battery.csv"
    const val META = "meta.json"

    private val SAFE_SESSION_ID = Regex("[A-Za-z0-9_-]+")

    /** `true` si [sessionId] vale como nombre de carpeta sin salir de `rawlog/` (los de la app son UUID). */
    fun isSafeSessionId(sessionId: String): Boolean = SAFE_SESSION_ID.matches(sessionId)
}

/** Tipos de evento de `events.csv` (spec 008, R10). */
object RawEventType {
    const val SESSION_START = "session_start"
    const val SESSION_PAUSE = "session_pause"
    const val SESSION_RESUME = "session_resume"
    const val SESSION_STOP = "session_stop"
    const val SERVICE_CREATED = "service_created"
    const val SERVICE_DESTROYED = "service_destroyed"
    const val TASK_REMOVED = "task_removed"
    const val TRIM_MEMORY = "trim_memory"
    const val SCREEN_ON = "screen_on"
    const val SCREEN_OFF = "screen_off"
    const val DEVICE_IDLE = "device_idle"
    const val DEVICE_LIGHT_IDLE = "device_light_idle"
    const val POWER_SAVE = "power_save"
    const val IGNORING_BATTERY_OPTIMIZATIONS = "ignoring_battery_optimizations"
    const val APP_FOREGROUND = "app_foreground"
    const val APP_BACKGROUND = "app_background"
    const val LOCATION_AVAILABLE = "location_available"
    const val AUDIO_MODE = "audio_mode"
    const val PERMISSIONS = "permissions"
    const val SOURCE_STARTED = "source_started"
    const val SOURCE_ERROR = "source_error"
    const val WRITE_ERROR = "write_error"
}

data class RawStamp(val epochMs: Long, val elapsedNs: Long)

interface RawRow {
    fun toRow(): String
}

/** Una posición tal como llega (Fused en `gps_fused.csv`, GPS puro en `gps_gnss.csv`). */
data class LocationFix(
    val stamp: RawStamp,
    val locTimeMs: Long,
    val locElapsedNs: Long?,
    val provider: String?,
    val lat: Double,
    val lon: Double,
    val altM: Double?,
    val vertAccM: Float?,
    val hAccM: Float?,
    val speedMs: Float?,
    val speedAccMs: Float?,
    val bearingDeg: Float?,
    val bearingAccDeg: Float?,
    val isMock: Boolean?,
    val batchIndex: Int,
    val batchSize: Int
) : RawRow {
    override fun toRow() = row(
        num(stamp.epochMs), num(stamp.elapsedNs), num(locTimeMs), num(locElapsedNs), text(provider),
        num(lat), num(lon), num(altM), num(vertAccM), num(hAccM), num(speedMs), num(speedAccMs),
        num(bearingDeg), num(bearingAccDeg), bool(isMock), num(batchIndex), num(batchSize)
    )

    companion object {
        val HEADER = RawCsvFormat.header(
            "epoch_ms", "elapsed_ns", "loc_time_ms", "loc_elapsed_ns", "provider", "lat", "lon", "alt_m",
            "vert_acc_m", "h_acc_m", "speed_ms", "speed_acc_ms", "bearing_deg", "bearing_acc_deg", "is_mock",
            "batch_index", "batch_size"
        )
    }
}

/**
 * Una fila de `baro.csv` (tras T-08): un segundo de lecturas del barómetro con su media ([pressureHpa]), cuántas
 * hubo y la mínima y máxima. Lo calcula [BaroSecondAverager]. Sin altitud.
 */
data class BaroSample(
    val stamp: RawStamp,
    val pressureHpa: Double,
    val nSamples: Int,
    val minHpa: Float,
    val maxHpa: Float
) : RawRow {
    override fun toRow() = row(
        num(stamp.epochMs), num(stamp.elapsedNs), num(pressureHpa), num(nSamples), num(minHpa), num(maxHpa)
    )

    companion object {
        val HEADER = RawCsvFormat.header("epoch_ms", "elapsed_ns", "pressure_hpa", "n_samples", "min_hpa", "max_hpa")
    }
}

/** Una lectura de batería (`battery.csv`). Valores crudos de Android, sin cambiar unidades ni signo. */
data class BatterySample(
    val stamp: RawStamp,
    val levelPct: Int?,
    val chargeCounterUah: Int?,
    val currentNowUa: Long?,
    val tempDc: Int?,
    val status: Int?,
    val plugged: Int?,
    val screenOn: Boolean?,
    val trigger: String
) : RawRow {
    override fun toRow() = row(
        num(stamp.epochMs), num(stamp.elapsedNs), num(levelPct), num(chargeCounterUah), num(currentNowUa),
        num(tempDc), num(status), num(plugged), bool(screenOn), text(trigger)
    )

    companion object {
        val HEADER = RawCsvFormat.header(
            "epoch_ms", "elapsed_ns", "level_pct", "charge_counter_uah", "current_now_ua", "temp_dc", "status",
            "plugged", "screen_on", "trigger"
        )

        const val TRIGGER_PERIODIC = "periodic"
        const val TRIGGER_SCREEN_ON = "screen_on"
        const val TRIGGER_SCREEN_OFF = "screen_off"
        const val TRIGGER_START = "start"
        const val TRIGGER_STOP = "stop"
    }
}

/** Un evento (`events.csv`). */
data class RawEvent(
    val stamp: RawStamp,
    val type: String,
    val detail: String?
) : RawRow {
    override fun toRow() = row(num(stamp.epochMs), num(stamp.elapsedNs), text(type), text(detail))

    companion object {
        val HEADER = RawCsvFormat.header("epoch_ms", "elapsed_ns", "type", "detail")
    }
}

/** Un satélite de una instantánea de `GnssStatus` (`gnss_sats.csv`, fase 2). */
data class GnssSat(
    val stamp: RawStamp,
    val snapshot: Long,
    val svid: Int,
    val constellation: Int,
    val cn0DbHz: Float?,
    val usedInFix: Boolean,
    val elevationDeg: Float?,
    val azimuthDeg: Float?,
    val hasAlmanac: Boolean,
    val hasEphemeris: Boolean
) : RawRow {
    override fun toRow() = row(
        num(stamp.epochMs), num(stamp.elapsedNs), num(snapshot), num(svid), num(constellation), num(cn0DbHz),
        bool(usedInFix), num(elevationDeg), num(azimuthDeg), bool(hasAlmanac), bool(hasEphemeris)
    )

    companion object {
        val HEADER = RawCsvFormat.header(
            "epoch_ms", "elapsed_ns", "snapshot", "svid", "constellation", "cn0_dbhz", "used_in_fix",
            "elevation_deg", "azimuth_deg", "has_almanac", "has_ephemeris"
        )
    }
}

/**
 * Una entrada de un `GnssStatus`, solo lo que necesita `gnss_status.csv` (la fuente lo copia de Android). Un móvil
 * de doble banda (L1 + L5) da **una entrada por señal**: el mismo satélite puede salir dos veces con el mismo
 * [svid] y [constellation].
 */
data class GnssSatInfo(val constellation: Int, val svid: Int, val cn0DbHz: Float, val usedInFix: Boolean)

/**
 * Una fila de `gnss_status.csv` (spec 008, T-08): resumen de un `GnssStatus`. Solo recuentos y medias.
 * Los recuentos son de **satélites distintos** (constelación + svid), aunque lleguen en dos bandas:
 * [satsVisible] = todos los del aviso; [satsUsed] y los usados por constelación = los que tienen alguna señal usada.
 * Las de C/N0 son **por señal**: [cn0MeanUsedDbHz] = media de las señales usadas (vacío si no hay ninguna);
 * [cn0MaxDbHz] = la mayor de todas (vacío si no hay satélites).
 */
data class GnssStatusRow(
    val stamp: RawStamp,
    val satsVisible: Int,
    val satsUsed: Int,
    val cn0MeanUsedDbHz: Double?,
    val cn0MaxDbHz: Float?,
    val usedGps: Int,
    val usedGlonass: Int,
    val usedGalileo: Int,
    val usedBeidou: Int,
    val usedOther: Int
) : RawRow {
    override fun toRow() = row(
        num(stamp.epochMs), num(stamp.elapsedNs), num(satsVisible), num(satsUsed), num(cn0MeanUsedDbHz),
        num(cn0MaxDbHz), num(usedGps), num(usedGlonass), num(usedGalileo), num(usedBeidou), num(usedOther)
    )

    companion object {
        val HEADER = RawCsvFormat.header(
            "epoch_ms", "elapsed_ns", "sats_visible", "sats_used", "cn0_mean_used_dbhz", "cn0_max_dbhz",
            "used_gps", "used_glonass", "used_galileo", "used_beidou", "used_other"
        )

        // Valores de `GnssStatus.CONSTELLATION_*` (copiados para no depender de Android en los tests).
        const val CONSTELLATION_GPS = 1
        const val CONSTELLATION_GLONASS = 3
        const val CONSTELLATION_BEIDOU = 5
        const val CONSTELLATION_GALILEO = 6

        fun from(stamp: RawStamp, sats: List<GnssSatInfo>): GnssStatusRow {
            val used = sats.filter { it.usedInFix }
            // Satélite = constelación + svid (las dos bandas del mismo satélite cuentan una vez).
            val usedSats = used.map { it.constellation to it.svid }.toSet()
            fun usedOf(c: Int) = usedSats.count { it.first == c }
            val known = setOf(CONSTELLATION_GPS, CONSTELLATION_GLONASS, CONSTELLATION_GALILEO, CONSTELLATION_BEIDOU)
            return GnssStatusRow(
                stamp = stamp,
                satsVisible = sats.map { it.constellation to it.svid }.toSet().size,
                satsUsed = usedSats.size,
                // Con el texto del Float para no arrastrar decimales falsos (38.7f → 38.7, no 38.70000076).
                cn0MeanUsedDbHz = if (used.isEmpty()) null else used.sumOf { it.cn0DbHz.toString().toDouble() } / used.size,
                cn0MaxDbHz = sats.maxOfOrNull { it.cn0DbHz },
                usedGps = usedOf(CONSTELLATION_GPS),
                usedGlonass = usedOf(CONSTELLATION_GLONASS),
                usedGalileo = usedOf(CONSTELLATION_GALILEO),
                usedBeidou = usedOf(CONSTELLATION_BEIDOU),
                usedOther = usedSats.count { it.first !in known }
            )
        }
    }
}
