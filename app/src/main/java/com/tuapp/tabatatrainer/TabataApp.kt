package com.tuapp.tabatatrainer

import android.app.Application
import android.content.pm.ApplicationInfo
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class TabataApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Logs de Timber solo en compilaciones de depuración
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
