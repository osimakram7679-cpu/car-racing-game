package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Provides smoothed tilt steering (-1f..+1f) from the device accelerometer
 * when ControlMode.TILT is active.
 */
class TiltSteeringManager(
    context: Context,
    private val onTiltChanged: (Float) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var smoothedSteer = 0f
    private var isRegistered = false

    fun start() {
        if (isRegistered || accelerometer == null) return
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        isRegistered = true
    }

    fun stop() {
        if (!isRegistered) return
        sensorManager?.unregisterListener(this)
        isRegistered = false
        smoothedSteer = 0f
        onTiltChanged(0f)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        // Negative X on portrait tilt right -> invert for intuitive steering
        val rawSteer = (-values[0] / 4.2f).coerceIn(-1f, 1f)
        val deadzoneSteer = if (kotlin.math.abs(rawSteer) < 0.08f) 0f else rawSteer
        smoothedSteer += (deadzoneSteer - smoothedSteer) * 0.25f
        onTiltChanged(smoothedSteer)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
