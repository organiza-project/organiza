package org.organiza.android.feature.habits

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.ZoneId
import java.time.ZonedDateTime
import org.organiza.android.MainActivity
import org.organiza.android.OrganizaApp
import org.organiza.android.R
import org.organiza.storage.HabitReminder

/**
 * Local habit reminders (ADR-0018). An inexact daily alarm per habit posts a notification. The
 * habit's name stays on the phone, and no exact-alarm permission is needed.
 */
object Reminders {
    private const val CHANNEL = "habit_reminders"
    private const val EXTRA_ID = "habit_id"
    private const val EXTRA_NAME = "habit_name"
    private const val WINDOW_MS = 10 * 60 * 1000L

    /** Replaces every scheduled reminder with [reminders]. */
    fun schedule(context: Context, reminders: List<HabitReminder>, previous: Set<String>) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        (previous - reminders.map { it.id }.toSet()).forEach { alarms.cancel(intent(context, it, "")) }
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        reminders.forEach { reminder ->
            var at = now.toLocalDate().atStartOfDay(now.zone).plusMinutes(reminder.minute.toLong())
            if (!at.isAfter(now)) at = at.plusDays(1)
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), WINDOW_MS, intent(context, reminder.id, reminder.name))
        }
    }

    private fun intent(context: Context, id: String, name: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.hashCode(),
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_NAME, name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun notify(context: Context, name: String, id: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(name)
            .setContentText("Time for this habit. Mark it done in Organiza.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(id.hashCode(), notification)
    }

    internal fun readId(intent: Intent): String = intent.getStringExtra(EXTRA_ID).orEmpty()

    internal fun readName(intent: Intent): String = intent.getStringExtra(EXTRA_NAME).orEmpty()
}

/** Posts one reminder, then puts every reminder back for tomorrow. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.notify(context, Reminders.readName(intent), Reminders.readId(intent))
        OrganizaApp.from(context).rescheduleReminders()
    }
}

/** Alarms do not survive a reboot, so they are set again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) OrganizaApp.from(context).rescheduleReminders()
    }
}
