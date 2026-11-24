package com.tuapp.tabatatrainer

import android.app.Application
import android.content.pm.ApplicationInfo
import com.tuapp.tabatatrainer.rawlog.AppVisibilityTracker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class TabataApp : Application() {

    /** Primer/segundo plano para el registro en bruto (spec 008). Solo cuenta actividades visibles. */
    @Inject lateinit var appVisibilityTracker: AppVisibilityTracker

    override fun onCreate() {
        super.onCreate()
        // Logs de Timber solo en compilaciones de depuración
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        }
        registerActivityLifecycleCallbacks(appVisibilityTracker)
    }
}
