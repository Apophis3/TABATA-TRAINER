package com.tuapp.tabatatrainer.rawlog

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler

/**
 * Lecturas de batería para `battery.csv` (spec 008, R11; plan §6): al empezar, cada 60 s, en cada encendido o
 * apagado de pantalla ([onScreen]) y al parar. Todo en el hilo «RawLog» ([handler]). La tarea de 60 s usa
 * `postDelayed` sin alarmas ni WakeLock: si el móvil duerme, la fila no sale, y eso **es** el dato.
 *
 * Valores tal como los da Android, sin cambiar unidades ni signo. Solo la crea [RawSessionLogger] al abrir una
 * sesión con el interruptor encendido (R2).
 *
 * @param screenOn `PowerManager.isInteractive` en el momento de la lectura (`null` si no se sabe).
 */
class RawBatterySource(
    private val context: Context,
    private val handler: Handler,
    private val recorder: RawBatteryRecorder,
    private val clock: () -> RawStamp,
    private val screenOn: () -> Boolean?
) {
    private val batteryManager: BatteryManager? = context.getSystemService(BatteryManager::class.java)
    private var running = false

    private val periodic = object : Runnable {
        override fun run() {
            if (!running) return
            read(BatterySample.TRIGGER_PERIODIC)
            handler.postDelayed(this, PERIOD_MS)
        }
    }

    fun start() {
        if (!recorder.started(clock(), PARAMS_DETAIL)) return
        running = true
        read(BatterySample.TRIGGER_START)
        handler.postDelayed(periodic, PERIOD_MS)
    }

    /** Encendido o apagado de pantalla (lo llama el receptor del registrador, ya en el hilo «RawLog»). */
    fun onScreen(on: Boolean) {
        if (running) read(if (on) BatterySample.TRIGGER_SCREEN_ON else BatterySample.TRIGGER_SCREEN_OFF)
    }

    /** Última lectura (`stop`) y fin de la tarea de 60 s. Llamarlo dos veces no hace nada. */
    fun stop() {
        if (!running) return
        handler.removeCallbacks(periodic)
        read(BatterySample.TRIGGER_STOP)
        running = false
    }

    private fun read(trigger: String) {
        val stamp = clock()
        val bm = batteryManager
        val sticky = stickyBatteryIntent()
        recorder.onReading(
            BatterySample(
                stamp = stamp,
                levelPct = bm?.let { RawBatteryRecorder.rawInt(it.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)) },
                chargeCounterUah = bm?.let {
                    RawBatteryRecorder.rawInt(it.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))
                },
                currentNowUa = bm?.let {
                    RawBatteryRecorder.rawLong(it.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
                },
                tempDc = sticky?.intExtraOrNull(BatteryManager.EXTRA_TEMPERATURE),
                status = sticky?.intExtraOrNull(BatteryManager.EXTRA_STATUS),
                plugged = sticky?.intExtraOrNull(BatteryManager.EXTRA_PLUGGED),
                screenOn = screenOn(),
                trigger = trigger
            )
        )
    }

    /** El último `ACTION_BATTERY_CHANGED` (pegajoso): sin receptor, solo se lee. */
    private fun stickyBatteryIntent(): Intent? = try {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(null, filter)
        }
    } catch (_: Exception) {
        null
    }

    private fun Intent.intExtraOrNull(key: String): Int? = if (hasExtra(key)) getIntExtra(key, 0) else null

    companion object {
        const val PERIOD_MS = 60_000L
        const val PARAMS_DETAIL = "period_ms=$PERIOD_MS;triggers=start,periodic,screen_on,screen_off,stop"
        const val CURRENT_NOTE = "current_now_ua: valor crudo de BatteryManager; en Samsung la unidad y el signo pueden variar"
    }
}
