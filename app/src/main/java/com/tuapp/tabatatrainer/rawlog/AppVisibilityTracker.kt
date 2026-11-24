package com.tuapp.tabatatrainer.rawlog

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Primer o segundo plano de la app para `events.csv` (spec 008, R10; plan §2). Cuenta las actividades visibles
 * (`onStart`/`onStop`); se registra en `TabataApp.onCreate`. Girar la pantalla no cuenta como salir: si la
 * actividad se para porque se está recreando, el estado no cambia.
 *
 * Solo lee; no cambia nada de las actividades. Las llamadas llegan en el hilo principal.
 */
@Singleton
class AppVisibilityTracker @Inject constructor() : Application.ActivityLifecycleCallbacks {

    private var started = 0

    @Volatile
    var isForeground = false
        private set

    /** Lo pone el registrador en bruto; recibe el estado nuevo y la hora del cambio. */
    @Volatile
    var listener: ((foreground: Boolean, stamp: RawStamp) -> Unit)? = null

    override fun onActivityStarted(activity: Activity) {
        started++
        if (!isForeground) change(true)
    }

    override fun onActivityStopped(activity: Activity) {
        if (started > 0) started--
        if (started == 0 && isForeground && !activity.isChangingConfigurations) change(false)
    }

    private fun change(foreground: Boolean) {
        isForeground = foreground
        val stamp = RawStamp(System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos())
        try {
            listener?.invoke(foreground, stamp)
        } catch (_: Exception) {
            // El registro en bruto nunca puede romper la app.
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
