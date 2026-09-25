package com.tuapp.tabatatrainer.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager as AndroidSensorManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Podómetro del móvil (Sensor.TYPE_STEP_COUNTER).
 * [rawSteps] es el acumulado del sistema desde el arranque; quien lo use calcula las diferencias.
 */
@Singleton
class StepCounter @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as AndroidSensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val _rawSteps = MutableStateFlow<Long?>(null)
    val rawSteps: StateFlow<Long?> = _rawSteps

    private var listening = false

    fun start() {
        if (listening || sensor == null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.w("👣 Sin permiso ACTIVITY_RECOGNITION")
            return
        }
        // Latencia 0: queremos los pasos en cuanto llegan, no en lotes
        listening = sensorManager.registerListener(this, sensor, AndroidSensorManager.SENSOR_DELAY_UI, 0)
        Timber.d("👣 Podómetro iniciado: $listening")
    }

    fun stop() {
        if (!listening) return
        sensorManager.unregisterListener(this)
        listening = false
        _rawSteps.value = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        _rawSteps.value = event.values[0].toLong()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
