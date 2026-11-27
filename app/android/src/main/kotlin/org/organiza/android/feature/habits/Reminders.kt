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
import kotlin.concurrent.thread
import org.organiza.presentation.ReminderText
import org.organiza.storage.Reminder

/**
 * Local habit reminders (ADR-0018). An inexact daily alarm per habit posts a notification. The
 * habit's name stays on the phone, and no exact-alarm permission is needed.
 */
object Reminders {
    private const val CHANNEL = "habit_reminders"
    private const val EXTRA_ID = "habit_id"
    private const val EXTRA_NAME = "habit_name"
    private const val WINDOW_MS = 10 * 60 * 1000L
    private const val LATER_MS = 60 * 60 * 1000L
    private const val EVENING_CHANNEL = "evening_checkin"
    internal const val DONE = "org.organiza.reminder.DONE"
    internal const val LATER = "org.organiza.reminder.LATER"

    /** The id prefix of a note's reminder, set by the core. */
    const val NOTE = ReminderText.NOTE

    /** Replaces every scheduled reminder with [reminders]. */
    fun schedule(context: Context, reminders: List<Reminder>, previous: Set<String>) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        (previous - reminders.map { it.id }.toSet()).forEach { alarms.cancel(intent(context, it, "")) }
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        reminders.forEach { reminder ->
            // A note's reminder is one moment; a habit's comes back at its minute every day.
            var at = reminder.at?.atZone(now.zone) ?: now.toLocalDate().atStartOfDay(now.zone).plusMinutes(reminder.minute.toLong())
            if (!at.isAfter(now)) if (reminder.at != null) return@forEach else at = at.plusDays(1)
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), WINDOW_MS, intent(context, reminder.id, reminder.name))
        }
    }

    internal fun intent(context: Context, id: String, name: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.hashCode(),
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_NAME, name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Posts reminder [id] saying [text]; a habit's comes with Done and Later. */
    internal fun notify(context: Context, name: String, id: String, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT))
        // Its own channel, so the evening check-in can be turned off apart from habit reminders.
        manager.createNotificationChannel(NotificationChannel(EVENING_CHANNEL, "Evening check-in", NotificationManager.IMPORTANCE_DEFAULT))
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        manager.notify(id.hashCode(), notification(context, name, id, text))
    }

    /** The reminder; on a locked screen only a plain line shows, never the note or the habit's name. */
    internal fun notification(context: Context, name: String, id: String, text: String): android.app.Notification {
        val evening = id == ReminderText.EVENING
        val channel = if (evening) EVENING_CHANNEL else CHANNEL
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val locked = android.app.Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Organiza")
            .setContentText(if (id.startsWith(NOTE)) "A reminder from your notes" else "A reminder from Organiza")
            .build()
        val builder = android.app.Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(if (evening) "Evening check-in" else name)
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(locked)
            .setContentIntent(open)
            .setAutoCancel(true)
        if (!evening && !id.startsWith(NOTE)) {
            builder.addAction(android.app.Notification.Action.Builder(null, "Done", action(context, DONE, id, name)).build())
            builder.addAction(android.app.Notification.Action.Builder(null, "Later", action(context, LATER, id, name)).build())
        }
        return builder.build()
    }

    private fun action(context: Context, action: String, id: String, name: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        (action + id).hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id).putExtra(EXTRA_NAME, name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Rings again in an hour, on an alarm of its own, so rescheduling the daily reminders never moves it. */
    internal fun later(context: Context, id: String, name: String) {
        val at = System.currentTimeMillis() + LATER_MS
        context.getSystemService(AlarmManager::class.java).setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, laterIntent(context, id, name))
    }

    internal fun laterIntent(context: Context, id: String, name: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        (LATER + id).hashCode(),
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_NAME, name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun dismiss(context: Context, id: String) = context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())

    internal fun readId(intent: Intent): String = intent.getStringExtra(EXTRA_ID).orEmpty()

    internal fun readName(intent: Intent): String = intent.getStringExtra(EXTRA_NAME).orEmpty()
}

/** Posts one reminder, then puts every reminder back for tomorrow. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = Reminders.readId(intent)
        val name = Reminders.readName(intent)
        val core = OrganizaApp.from(context).core
        when (intent.action) {
            Reminders.DONE -> return core.markHabit(name, true).also { Reminders.dismiss(context, id) }
            Reminders.LATER -> return Reminders.later(context, id, name).also { Reminders.dismiss(context, id) }
        }
        // The process may have just started for this alarm; wait briefly for today's snapshot, so a habit
        // already done stays quiet and the evening check-in knows what is open.
        val pending = goAsync()
        core.requestUiRefresh()
        thread(name = "organiza-reminder") {
            try {
                var waited = 0L
                while (core.uiState.value.widget == null && waited < WAIT_MS) {
                    Thread.sleep(STEP_MS)
                    waited += STEP_MS
                }
                ReminderText.of(id, name, core.uiState.value.widget)?.let { Reminders.notify(context, name, id, it) }
                core.rescheduleReminders()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val STEP_MS = 100L
    }
}

/** Alarms do not survive a reboot, so they are set again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) OrganizaApp.from(context).core.rescheduleReminders()
    }
}
