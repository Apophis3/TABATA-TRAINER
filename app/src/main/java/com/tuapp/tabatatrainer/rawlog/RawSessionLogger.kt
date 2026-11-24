package com.tuapp.tabatatrainer.rawlog

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registro en bruto de una sesión (spec 008; plan §3 y §3.1). Lo llaman `FreeRideService` y `WorkoutService` con
 * una línea en cada punto; aquí solo se cambia de hilo, se lee el estado del móvil, se registran los receptores
 * y se escribe `meta.json`. Las reglas de la sesión están en [RawSessionCore].
 *
 * - Si el interruptor está apagado al empezar, no se crea nada (ni hilo ni carpeta) y el resto de llamadas no
 *   hacen nada.
 * - La hora de cada evento se toma en el hilo que llama; la escritura va al hilo «RawLog» de la sesión.
 * - Solo usa el contexto de la aplicación. Todo va en `try/catch`: nunca rompe ni frena la app.
 */
@Singleton
class RawSessionLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: RawLogSettings,
    private val visibility: AppVisibilityTracker
) {
    private class Active(
        val core: RawSessionCore,
        val thread: HandlerThread,
        val handler: Handler
    ) {
        var receiver: BroadcastReceiver? = null
        var lastIgnoringOptimizations: Boolean? = null
        var fused: RawFusedSource? = null
        var fusedRecorder: RawFusedRecorder? = null
    }

    private val lock = Any()
    private var active: Active? = null

    /** Hora de `onCreate` de cada servicio vivo, para anotarla al empezar la sesión. */
    private val servicesCreated = mutableMapOf<String, RawStamp>()

    private val root: File get() = File(context.filesDir, RawFiles.ROOT_DIR)
    private val powerManager: PowerManager? get() = context.getSystemService(PowerManager::class.java)

    init {
        visibility.listener = { foreground, stamp ->
            post(stamp) { s ->
                s.core.event(stamp, if (foreground) RawEventType.APP_FOREGROUND else RawEventType.APP_BACKGROUND, null)
            }
        }
    }

    // ---------------------------------------------------------------- llamadas de los servicios

    fun serviceCreated(service: String) = guard("serviceCreated") {
        val stamp = now()
        synchronized(lock) { servicesCreated[service] = stamp }
        post(stamp) { s -> s.core.event(stamp, RawEventType.SERVICE_CREATED, "service=$service") }
    }

    /**
     * Empieza el registro si el interruptor está encendido. Si había otra sesión abierta, la cierra antes con
     * motivo `replaced`.
     */
    fun start(service: String, sessionId: String, activityType: String) = guard("start") {
        val stamp = now()
        if (!settings.isEnabled()) return@guard
        val (previous, session, created) = synchronized(lock) {
            val previous = active
            val created = servicesCreated.toMap()
            val thread = HandlerThread(THREAD_NAME).apply { start() }
            val core = RawSessionCore(
                root = root,
                sessionId = sessionId,
                service = service,
                activityType = activityType,
                clock = ::now,
                log = { Log.w(TAG, it) },
                onEvent = { Log.i(TAG, "${it.type} ${it.detail.orEmpty()}") }
            )
            val session = Active(core, thread, Handler(thread.looper))
            active = session
            Triple(previous, session, created)
        }
        previous?.let { p -> p.handler.post { finish(p) { p.core.stop(stamp, p.core.service, RawSessionCore.STOP_REPLACED) } } }
        session.handler.post { safely("open") { open(session, stamp, created) } }
    }

    fun pause(service: String) = sessionEvent(service, RawEventType.SESSION_PAUSE)

    fun resume(service: String) = sessionEvent(service, RawEventType.SESSION_RESUME)

    /** Cierra la sesión si es de [service]. Motivo: `user` o `completed`. Llamarlo dos veces no hace nada. */
    fun stop(service: String, reason: String) = guard("stop") {
        val stamp = now()
        val s = detachIfOwnedBy(service) ?: return@guard
        s.handler.post { finish(s) { s.core.stop(stamp, service, reason) } }
    }

    /** `onDestroy` de un servicio: se anota siempre; si es el de la sesión, la cierra (`service_destroyed`). */
    fun serviceDestroyed(service: String) = guard("serviceDestroyed") {
        val stamp = now()
        synchronized(lock) { servicesCreated.remove(service) }
        val owned = detachIfOwnedBy(service)
        if (owned != null) {
            owned.handler.post { finish(owned) { owned.core.serviceDestroyed(stamp, service) } }
        } else {
            post(stamp) { s -> s.core.event(stamp, RawEventType.SERVICE_DESTROYED, "service=$service") }
        }
    }

    fun taskRemoved(service: String) = guard("taskRemoved") {
        val stamp = now()
        post(stamp) { s -> s.core.event(stamp, RawEventType.TASK_REMOVED, "service=$service") }
    }

    fun trimMemory(service: String, level: Int) = guard("trimMemory") {
        val stamp = now()
        post(stamp) { s -> s.core.event(stamp, RawEventType.TRIM_MEMORY, "service=$service;level=$level") }
    }

    // ---------------------------------------------------------------- hilo «RawLog»

    private fun sessionEvent(service: String, type: String) = guard(type) {
        val stamp = now()
        post(stamp) { s -> s.core.sessionEvent(stamp, service, type) }
    }

    /** Lleva [block] al hilo de la sesión abierta, si la hay. */
    private fun post(stamp: RawStamp, block: (Active) -> Unit) {
        val s = synchronized(lock) { active } ?: return
        s.handler.post { safely("event@${stamp.elapsedNs}") { block(s) } }
    }

    /** Quita la sesión abierta si es de [service] (así las llamadas que lleguen después ya no la ven). */
    private fun detachIfOwnedBy(service: String): Active? = synchronized(lock) {
        val s = active
        if (s != null && s.core.service == service) {
            active = null
            s
        } else {
            null
        }
    }

    private fun open(s: Active, stamp: RawStamp, created: Map<String, RawStamp>) {
        if (!s.core.open(stamp, created)) {
            Log.w(TAG, "No se pudo abrir la sesión ${s.core.sessionId}")
            synchronized(lock) { if (active === s) active = null }
            s.thread.quitSafely()
            return
        }
        Log.i(TAG, "Inicio: sesión=${s.core.sessionId} servicio=${s.core.service} carpeta=${s.core.dir.absolutePath}")
        writeInitialState(s, stamp)
        registerReceivers(s)
        startSources(s)
        writeMeta(s)
        s.handler.postDelayed(object : Runnable {
            override fun run() {
                safely("flush") { s.core.flushIfDue() }
                if (s.core.isOpen) s.handler.postDelayed(this, FLUSH_TICK_MS)
            }
        }, FLUSH_TICK_MS)
    }

    /** Cierra en el hilo de la sesión: [close] anota la parada; luego receptores fuera, `meta.json` y fin del hilo. */
    private fun finish(s: Active, close: () -> Boolean) {
        // La petición de posiciones se quita siempre, también si la sesión ya estaba cerrada (R-1: que no siga viva).
        safely("stopSources") { stopSources(s) }
        safely("finish") {
            if (close()) {
                unregisterReceivers(s)
                writeMeta(s)
                Log.i(
                    TAG,
                    "Fin: sesión=${s.core.sessionId} motivo=${s.core.stopReason} filas=" +
                        s.core.fileStats().entries.joinToString { "${it.key}:${it.value.rows}" } +
                        " errores=${s.core.writeErrors}" +
                        (s.fusedRecorder?.let { " fused=${it.resultsReceived}r/${it.locationsReceived}p" } ?: "")
                )
            }
        }
        s.handler.removeCallbacksAndMessages(null)
        s.thread.quitSafely()
    }

    // ---------------------------------------------------------------- fuentes (T-03: Fused)

    /** En el hilo «RawLog», solo con la sesión ya abierta (interruptor encendido). */
    private fun startSources(s: Active) {
        val recorder = RawFusedRecorder(s.core)
        val source = RawFusedSource(context, s.handler, recorder, ::now)
        s.fusedRecorder = recorder
        s.fused = source
        safely("fused.start") { source.start() }
    }

    private fun stopSources(s: Active) {
        val source = s.fused ?: return
        s.fused = null
        source.stop()
        Log.i(TAG, "Fused: petición quitada (sesión ${s.core.sessionId})")
    }

    // ---------------------------------------------------------------- estado del móvil

    private fun writeInitialState(s: Active, stamp: RawStamp) {
        fun initial(detail: String?) = RawSessionCore.initial(detail)
        s.core.event(stamp, RawEventType.PERMISSIONS, initial(permissionsDetail()))
        val pm = powerManager
        if (pm != null) {
            val ignoring = pm.isIgnoringBatteryOptimizations(context.packageName)
            s.lastIgnoringOptimizations = ignoring
            s.core.event(stamp, RawEventType.IGNORING_BATTERY_OPTIMIZATIONS, initial("ignoring=$ignoring"))
            s.core.event(stamp, RawEventType.POWER_SAVE, initial("enabled=${pm.isPowerSaveMode}"))
            s.core.event(stamp, RawEventType.DEVICE_IDLE, initial("idle=${pm.isDeviceIdleMode}"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                s.core.event(stamp, RawEventType.DEVICE_LIGHT_IDLE, initial("idle=${pm.isDeviceLightIdleMode}"))
            }
            val interactive = pm.isInteractive
            s.core.event(
                stamp,
                if (interactive) RawEventType.SCREEN_ON else RawEventType.SCREEN_OFF,
                initial("interactive=$interactive")
            )
        }
        s.core.event(
            stamp,
            if (visibility.isForeground) RawEventType.APP_FOREGROUND else RawEventType.APP_BACKGROUND,
            initial(null)
        )
    }

    private fun permissionsDetail(): String {
        fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return "fine=${granted(Manifest.permission.ACCESS_FINE_LOCATION)}" +
            ";coarse=${granted(Manifest.permission.ACCESS_COARSE_LOCATION)}" +
            ";background=${granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)}"
    }

    private fun registerReceivers(s: Active) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                addAction(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED)
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val stamp = now()
                safely("receiver") { onSystemEvent(s, stamp, intent?.action) }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, null, s.handler, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter, null, s.handler)
        }
        s.receiver = receiver
    }

    private fun unregisterReceivers(s: Active) {
        val r = s.receiver ?: return
        s.receiver = null
        try {
            context.unregisterReceiver(r)
        } catch (_: Exception) {
            // Ya no estaba registrado.
        }
    }

    /** En el hilo «RawLog» (el receptor se registró con su `Handler`). */
    private fun onSystemEvent(s: Active, stamp: RawStamp, action: String?) {
        val pm = powerManager ?: return
        when (action) {
            Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF -> {
                val type = if (action == Intent.ACTION_SCREEN_ON) RawEventType.SCREEN_ON else RawEventType.SCREEN_OFF
                s.core.event(stamp, type, "interactive=${pm.isInteractive}")
                if (action == Intent.ACTION_SCREEN_ON) {
                    // Sin aviso del sistema para este: se vuelve a mirar en cada encendido y se anota si cambió.
                    val ignoring = pm.isIgnoringBatteryOptimizations(context.packageName)
                    if (ignoring != s.lastIgnoringOptimizations) {
                        s.lastIgnoringOptimizations = ignoring
                        s.core.event(stamp, RawEventType.IGNORING_BATTERY_OPTIMIZATIONS, "ignoring=$ignoring")
                    }
                }
            }
            PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED ->
                s.core.event(stamp, RawEventType.DEVICE_IDLE, "idle=${pm.isDeviceIdleMode}")
            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                s.core.event(stamp, RawEventType.POWER_SAVE, "enabled=${pm.isPowerSaveMode}")
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                action == PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED
            ) {
                s.core.event(stamp, RawEventType.DEVICE_LIGHT_IDLE, "idle=${pm.isDeviceLightIdleMode}")
            }
        }
    }

    // ---------------------------------------------------------------- meta.json

    /** Se escribe al empezar y se completa al parar (R12). Primero a `.tmp` y luego se renombra. */
    private fun writeMeta(s: Active) {
        val core = s.core
        val target = File(core.dir, RawFiles.META)
        try {
            val json = JSONObject().apply {
                put("format_version", 1)
                put("device", JSONObject().apply {
                    put("manufacturer", Build.MANUFACTURER)
                    put("model", Build.MODEL)
                    put("device", Build.DEVICE)
                    put("android_release", Build.VERSION.RELEASE)
                    put("sdk_int", Build.VERSION.SDK_INT)
                })
                put("app", appInfo())
                put("session", JSONObject().apply {
                    put("session_id", core.sessionId)
                    put("activity_type", core.activityType)
                    put("service", core.service)
                    core.startStamp?.let {
                        put("start_epoch_ms", it.epochMs)
                        put("start_elapsed_ns", it.elapsedNs)
                    }
                    put("end_epoch_ms", core.stopStamp?.epochMs ?: JSONObject.NULL)
                    put("end_elapsed_ns", core.stopStamp?.elapsedNs ?: JSONObject.NULL)
                    put("stop_reason", core.stopReason ?: JSONObject.NULL)
                    val tz = TimeZone.getDefault()
                    put("timezone", tz.id)
                    put("utc_offset_min", tz.getOffset(core.startStamp?.epochMs ?: System.currentTimeMillis()) / 60_000)
                })
                put("wakelock", false)
                put("app_gps_params", JSONObject().apply {
                    put("interval_ms", APP_GPS_INTERVAL_MS)
                    put("min_interval_ms", APP_GPS_MIN_INTERVAL_MS)
                    put("min_distance_m", APP_GPS_MIN_DISTANCE_M)
                    put("note", "Copiados a mano de GpsManager (constantes privadas, no se toca)")
                })
                put("raw_gps_params", JSONObject().apply {
                    put("source", "fused")
                    put("priority", RawFusedSource.PRIORITY_NAME)
                    put("interval_ms", RawFusedSource.INTERVAL_MS)
                    put("min_interval_ms", RawFusedSource.MIN_INTERVAL_MS)
                    put("min_distance_m", RawFusedSource.MIN_DISTANCE_M.toDouble())
                    put("max_update_delay_ms", RawFusedSource.MAX_UPDATE_DELAY_MS)
                    put("wait_for_accurate_location", RawFusedSource.WAIT_FOR_ACCURATE)
                })
                // Aceptado por el usuario el 2026-11-24 (spec R8, plan §8 R-1).
                put("observer_effect", RawFusedSource.OBSERVER_EFFECT)
                put("observer_effect_note", RawFusedSource.OBSERVER_EFFECT_NOTE)
                s.fusedRecorder?.let { r ->
                    put("fused", JSONObject().apply {
                        put("started", r.started)
                        put("results_received", r.resultsReceived)
                        put("locations_received", r.locationsReceived)
                        put("error", r.lastError ?: JSONObject.NULL)
                    })
                }
                put("files", JSONObject().apply {
                    core.fileStats().forEach { (name, stat) ->
                        put(name, JSONObject().apply {
                            put("rows", stat.rows)
                            put("failed", stat.failed)
                        })
                    }
                })
                put("write_errors", core.writeErrors)
            }
            core.dir.mkdirs()
            val tmp = File(core.dir, RawFiles.META + ".tmp")
            tmp.writeText(json.toString(2), Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) throw java.io.IOException("No se pudo renombrar ${tmp.name}")
            }
        } catch (e: Exception) {
            core.reportWriteError(RawFiles.META, e)
        }
    }

    private fun appInfo(): JSONObject = JSONObject().apply {
        put("package", context.packageName)
        try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            put("version_name", info.versionName ?: JSONObject.NULL)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            put("version_code", code)
        } catch (e: Exception) {
            put("version_error", e.javaClass.simpleName)
        }
    }

    // ---------------------------------------------------------------- utilidades

    private fun now() = RawStamp(System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos())

    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "$what: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private inline fun safely(what: String, block: () -> Unit) = guard(what, block)

    companion object {
        const val TAG = "RawLog"
        const val SERVICE_FREE_RIDE = "FreeRideService"
        const val SERVICE_WORKOUT = "WorkoutService"
        const val ACTIVITY_FREE_RIDE = "FREE_RIDE"
        const val ACTIVITY_TABATA = "TABATA"

        private const val THREAD_NAME = "RawLog"
        private const val FLUSH_TICK_MS = 1_000L

        // Los de GpsManager (UPDATE_INTERVAL, FASTEST_INTERVAL, SMALLEST_DISPLACEMENT), para comparar (R12).
        private const val APP_GPS_INTERVAL_MS = 1000L
        private const val APP_GPS_MIN_INTERVAL_MS = 500L
        private const val APP_GPS_MIN_DISTANCE_M = 1.0
    }
}
