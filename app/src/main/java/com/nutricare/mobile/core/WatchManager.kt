package com.nutricare.mobile.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class WatchVitals(
    val isConnected: Boolean = false,
    val deviceName: String = "No watch connected",
    val deviceType: String = "Bluetooth Disconnected",
    val heartRate: Int? = null,
    val spo2: Int? = null,
    val bloodPressure: String? = null,
    val sleepHours: Double? = null,
    val skinTemp: Double? = null,
    val battery: Int = 0,
    val isDemo: Boolean = false,
    val lastSyncTime: String = "Not Synced",
)

/**
 * Shared Smartwatch Manager providing real-time vitals & connection status
 * across HomeScreen and WearablesScreen.
 */
object WatchManager {

    private val _vitals = MutableStateFlow(WatchVitals())
    val vitals: StateFlow<WatchVitals> = _vitals.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)
    private var telemetryJob: Job? = null

    fun connectDevice(name: String, type: String = "BLE Heart Rate Monitor", initialHr: Int = 78) {
        _vitals.update {
            it.copy(
                isConnected = true,
                deviceName = name,
                deviceType = type,
                heartRate = initialHr,
                spo2 = 98,
                bloodPressure = "118/78",
                sleepHours = 7.8,
                skinTemp = 36.6,
                battery = 92,
                isDemo = false,
                lastSyncTime = "Just Now"
            )
        }
        startTelemetryPulse()
    }

    fun toggleDemo() {
        if (_vitals.value.isConnected && _vitals.value.isDemo) {
            disconnect()
        } else {
            _vitals.update {
                it.copy(
                    isConnected = true,
                    deviceName = "Apple Watch Series 9",
                    deviceType = "Smartwatch Vitals (Simulated)",
                    heartRate = 76,
                    spo2 = 99,
                    bloodPressure = "120/80",
                    sleepHours = 7.5,
                    skinTemp = 36.5,
                    battery = 88,
                    isDemo = true,
                    lastSyncTime = "Just Now"
                )
            }
            startTelemetryPulse()
        }
    }

    fun updateHeartRate(hr: Int) {
        _vitals.update { it.copy(heartRate = hr, lastSyncTime = "Just Now") }
    }

    fun disconnect() {
        telemetryJob?.cancel()
        _vitals.update {
            WatchVitals(
                isConnected = false,
                deviceName = "No watch connected",
                deviceType = "Bluetooth Disconnected",
                heartRate = null,
                spo2 = null,
                bloodPressure = null,
                battery = 0,
                isDemo = false,
                lastSyncTime = "Not Synced"
            )
        }
    }

    private fun startTelemetryPulse() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive && _vitals.value.isConnected) {
                delay(3500)
                _vitals.update { current ->
                    if (!current.isConnected) current
                    else {
                        val baseHr = current.heartRate ?: 76
                        val delta = Random.nextInt(-2, 3)
                        val nextHr = (baseHr + delta).coerceIn(58, 128)
                        current.copy(heartRate = nextHr)
                    }
                }
            }
        }
    }
}
