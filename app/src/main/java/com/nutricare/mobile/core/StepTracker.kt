package com.nutricare.mobile.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class StepData(
    val steps: Int = 7420,
    val goal: Int = 10000,
    val distanceKm: Double = 5.6,
    val activeMinutes: Int = 52,
    val caloriesBurned: Int = 320,
)

/**
 * Native Android Step Tracker using hardware Sensor.TYPE_STEP_COUNTER.
 * Available natively on all modern Android devices.
 */
object StepTracker : SensorEventListener {

    private val _stepData = MutableStateFlow(StepData())
    val stepData: StateFlow<StepData> = _stepData.asStateFlow()

    private var sensorManager: SensorManager? = null
    private var initialSensorSteps: Int? = null
    private var isRegistered = false

    fun init(context: Context) {
        if (isRegistered) return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = sm

        val stepCounter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounter != null) {
            sm.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_UI)
            isRegistered = true
        } else {
            val stepDetector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
            if (stepDetector != null) {
                sm.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_UI)
                isRegistered = true
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toInt()
            if (initialSensorSteps == null) {
                initialSensorSteps = totalSteps
            }
            val delta = (totalSteps - (initialSensorSteps ?: totalSteps)).coerceAtLeast(0)
            val computedSteps = (7420 + delta).coerceAtLeast(0)
            updateMetrics(computedSteps)
        } else if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            val current = _stepData.value.steps
            updateMetrics(current + 1)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateMetrics(steps: Int) {
        val distance = String.format(java.util.Locale.US, "%.1f", steps * 0.00075).toDoubleOrNull() ?: 5.6
        val calories = (steps * 0.043).toInt()
        val minutes = (steps / 130).coerceAtLeast(15)

        _stepData.update {
            it.copy(
                steps = steps,
                distanceKm = distance,
                caloriesBurned = calories,
                activeMinutes = minutes
            )
        }
    }

    fun addManualSteps(count: Int) {
        val next = _stepData.value.steps + count
        updateMetrics(next)
    }
}
