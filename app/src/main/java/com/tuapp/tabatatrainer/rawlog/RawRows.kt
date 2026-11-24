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
    const val BARO = "baro.csv"
    const val EVENTS = "events.csv"
    const val BATTERY = "battery.csv"
    const val META = "meta.json"
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

/** Una muestra del barómetro (`baro.csv`). */
data class BaroSample(
    val stamp: RawStamp,
    val sensorTsNs: Long,
    val pressureHpa: Float,
    val accuracy: Int?
) : RawRow {
    override fun toRow() = row(
        num(stamp.epochMs), num(stamp.elapsedNs), num(sensorTsNs), num(pressureHpa), num(accuracy)
    )

    companion object {
        val HEADER = RawCsvFormat.header("epoch_ms", "elapsed_ns", "sensor_ts_ns", "pressure_hpa", "accuracy")
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
