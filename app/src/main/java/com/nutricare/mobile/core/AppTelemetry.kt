package com.nutricare.mobile.core

import android.content.Context
import android.os.SystemClock
import com.nutricare.mobile.core.ServiceLocator
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Enterprise app telemetry & user time-spent tracker.
 * Pushes session duration, active heartbeats, and device platform
 * to Supabase so the Web Admin Panel can monitor user engagement in real-time.
 */
object AppTelemetry {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sessionStartTime = 0L
    private var isTracking = false
    private const val PREFS_NAME = "nutricare_telemetry"
    private const val KEY_TOTAL_SECONDS = "total_app_time_seconds"

    fun onAppForeground(context: Context) {
        if (isTracking) return
        isTracking = true
        sessionStartTime = SystemClock.elapsedRealtime()

        scope.launch {
            // Heartbeat loop every 45 seconds while active
            while (isActive && isTracking) {
                delay(45_000)
                syncHeartbeat(context)
            }
        }
    }

    fun onAppBackground(context: Context) {
        if (!isTracking) return
        isTracking = false
        val durationMillis = SystemClock.elapsedRealtime() - sessionStartTime
        val durationSeconds = (durationMillis / 1000).coerceAtLeast(0)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentTotal = prefs.getLong(KEY_TOTAL_SECONDS, 0L) + durationSeconds
        prefs.edit().putLong(KEY_TOTAL_SECONDS, currentTotal).apply()

        scope.launch {
            syncHeartbeat(context, currentTotal)
        }
    }

    private suspend fun syncHeartbeat(context: Context, overrideTotal: Long? = null) {
        runCatching {
            val client = ServiceLocator.supabase
            val user = client.auth.currentUserOrNull() ?: return@runCatching

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentSessionSec = if (isTracking && sessionStartTime > 0) {
                (SystemClock.elapsedRealtime() - sessionStartTime) / 1000
            } else 0L

            val totalSec = overrideTotal ?: (prefs.getLong(KEY_TOTAL_SECONDS, 0L) + currentSessionSec)
            val nowIso = Instant.now().toString()

            client.from("profiles").update(
                mapOf(
                    "last_seen_at" to nowIso,
                    "platform" to "android",
                    "app_time_spent_seconds" to totalSec
                )
            ) {
                filter { eq("id", user.id) }
            }
        }
    }
}
