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
    val steps: Int = 0,
    val goal: Int = 10000,
    val distanceKm: Double = 0.0,
    val activeMinutes: Int = 0,
    val caloriesBurned: Int = 0,
)

/**
 * Native Android Step Tracker using hardware Sensor.TYPE_STEP_COUNTER and
 * Sensor.TYPE_STEP_DETECTOR with daily SharedPreferences persistence.
 * Starts at 0 steps each day and tracks genuine hardware sensor data.
 */
object StepTracker : SensorEventListener {

    private val _stepData = MutableStateFlow(StepData())
    val stepData: StateFlow<StepData> = _stepData.asStateFlow()

    private var sensorManager: SensorManager? = null
    private var appContext: Context? = null
    private var isRegistered = false

    private const val PREFS_NAME = "nutricare_step_tracker"
    private const val KEY_DATE = "step_tracker_date"
    private const val KEY_BASELINE = "step_tracker_baseline"
    private const val KEY_STEPS_TODAY = "step_tracker_steps_today"

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app

        // Restore today's saved steps
        loadSavedSteps(app)

        if (isRegistered) return
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
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

    private fun getTodayDateString(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return sdf.format(java.util.Date())
    }

    private fun loadSavedSteps(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_DATE, null)
        val today = getTodayDateString()

        if (savedDate == today) {
            val steps = prefs.getInt(KEY_STEPS_TODAY, 0)
            updateMetrics(steps, persist = false)
        } else {
            // New day: reset today's steps to 0
            prefs.edit()
                .putString(KEY_DATE, today)
                .putInt(KEY_STEPS_TODAY, 0)
                .remove(KEY_BASELINE)
                .apply()
            updateMetrics(0, persist = false)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        val savedDate = prefs.getString(KEY_DATE, null)

        if (savedDate != today) {
            prefs.edit()
                .putString(KEY_DATE, today)
                .putInt(KEY_STEPS_TODAY, 0)
                .remove(KEY_BASELINE)
                .apply()
        }

        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0].toInt()
            var baseline = prefs.getInt(KEY_BASELINE, -1)

            if (baseline < 0 || totalStepsSinceBoot < baseline) {
                // First reading of the day or device rebooted
                baseline = totalStepsSinceBoot
                prefs.edit().putInt(KEY_BASELINE, baseline).apply()
            }

            val todaySteps = (totalStepsSinceBoot - baseline).coerceAtLeast(0)
            updateMetrics(todaySteps, persist = true)
        } else if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            val current = _stepData.value.steps
            updateMetrics(current + 1, persist = true)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateMetrics(steps: Int, persist: Boolean = true) {
        val distance = String.format(java.util.Locale.US, "%.2f", steps * 0.00075).toDoubleOrNull() ?: 0.0
        val calories = (steps * 0.04).toInt()
        val minutes = (steps / 100)

        _stepData.update {
            it.copy(
                steps = steps,
                distanceKm = distance,
                caloriesBurned = calories,
                activeMinutes = minutes
            )
        }

        if (persist) {
            appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.putInt(KEY_STEPS_TODAY, steps)
                ?.apply()
        }
    }

    fun addManualSteps(count: Int) {
        val next = _stepData.value.steps + count
        updateMetrics(next, persist = true)
    }
}
