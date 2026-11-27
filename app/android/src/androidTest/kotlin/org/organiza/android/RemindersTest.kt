package org.organiza.android

import android.app.Notification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.organiza.android.feature.habits.Reminders

@RunWith(AndroidJUnit4::class)
class RemindersTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aNoteReminderKeepsTheNoteOffTheLockScreen() {
        val shown = Reminders.notification(context, "Talk to the therapist about work", "note-1", "A reminder from your notes")
        assertEquals(Notification.VISIBILITY_PRIVATE, shown.visibility)
        val locked = checkNotNull(shown.publicVersion).extras
        assertFalse(locked.getCharSequence(Notification.EXTRA_TITLE).toString().contains("therapist"))
        assertFalse(locked.getCharSequence(Notification.EXTRA_TEXT).toString().contains("therapist"))
    }

    @Test
    fun aHabitReminderOffersDoneAndLaterAndTheEveningCheckInStandsApart() {
        val habit = Reminders.notification(context, "Read", "habit-a", "Keep your 3-day streak going")
        assertEquals(listOf("Done", "Later"), habit.actions.map { it.title.toString() })
        val evening = Reminders.notification(context, "Evening check-in", org.organiza.presentation.ReminderText.EVENING, "Still open: Run")
        assertEquals("Evening check-in", evening.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(null, evening.actions)
        assertEquals("evening_checkin", evening.channelId)
    }

    @Test
    fun laterHasItsOwnAlarmSoRescheduledDailyRemindersNeverReplaceIt() {
        // An alarm is keyed by its pending intent; sharing the daily one let any reschedule move "Later" to tomorrow.
        assertNotEquals(Reminders.intent(context, "habit-a", "Read"), Reminders.laterIntent(context, "habit-a", "Read"))
    }
}
