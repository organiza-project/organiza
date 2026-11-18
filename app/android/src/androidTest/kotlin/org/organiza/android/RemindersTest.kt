package org.organiza.android

import android.app.Notification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.organiza.android.feature.habits.Reminders

@RunWith(AndroidJUnit4::class)
class RemindersTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aNoteReminderKeepsTheNoteOffTheLockScreen() {
        val shown = Reminders.notification(context, "Talk to the therapist about work", "note-1")
        assertEquals(Notification.VISIBILITY_PRIVATE, shown.visibility)
        val locked = checkNotNull(shown.publicVersion).extras
        assertFalse(locked.getCharSequence(Notification.EXTRA_TITLE).toString().contains("therapist"))
        assertFalse(locked.getCharSequence(Notification.EXTRA_TEXT).toString().contains("therapist"))
    }
}
