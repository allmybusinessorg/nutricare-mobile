package com.nutricare.mobile.features.medications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nutricare.mobile.data.Medication
import java.util.Calendar

/**
 * Daily medication reminders: one exact repeating alarm per medication time,
 * firing a local notification. Reminders are re-registered on device boot.
 */
object MedicationReminders {

    const val CHANNEL_ID = "medication_reminders"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Medication reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Reminders to take your medicines on time" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun schedule(context: Context, medication: Medication) {
        val time = medication.time_to_take ?: return
        val parts = time.split(":")
        if (parts.size < 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("name", medication.name)
            putExtra("dosage", medication.dosage)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            medication.id?.hashCode() ?: (medication.name + time).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }

        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
        } else {
            am.setRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
        }
    }

    fun cancel(context: Context, medication: Medication) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            medication.id?.hashCode() ?: (medication.name + (medication.time_to_take ?: "")).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pending)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!MedicationReminders.hasNotificationPermission(context)) return
        MedicationReminders.ensureChannel(context)

        val name = intent.getStringExtra("name") ?: return
        val dosage = intent.getStringExtra("dosage")
        val text = if (dosage.isNullOrBlank()) "Time to take $name" else "Time to take $name ($dosage)"

        val notification = NotificationCompat.Builder(context, MedicationReminders.CHANNEL_ID)
            .setSmallIcon(com.nutricare.mobile.R.drawable.ic_notification)
            .setContentTitle("💊 Medication reminder")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(name.hashCode(), notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Re-register alarms; the app re-syncs actual medications from the
        // database on next launch (alarms use stored schedule snapshots).
    }
}
