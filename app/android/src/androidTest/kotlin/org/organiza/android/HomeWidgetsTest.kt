package org.organiza.android

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.organiza.android.feature.widgets.HomeWidgets
import org.organiza.presentation.HomeWidget
import org.organiza.presentation.WidgetHabit

@RunWith(AndroidJUnit4::class)
class HomeWidgetsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val widget = HomeWidget(
        percent = "50%",
        headline = "1 of 2 goals met",
        habits = listOf(WidgetHabit("b", "Run", doneToday = false), WidgetHabit("a", "Read", doneToday = true)),
        screenTime = "1h 05m today",
        next = "Focus at 14:00",
        streak = 4,
        nudge = "4-day streak. Still open: Run",
    )

    // Drawn the way a launcher draws them, so a layout a widget cannot hold fails here, not on a home screen.
    @Test
    fun theTodayWidgetShowsProgressOpenHabitsAndTheDay() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = HomeWidgets.today(context, widget).apply(context, FrameLayout(context))
            assertEquals("50%", view.findViewById<TextView>(R.id.percent).text)
            assertEquals("Run", view.findViewById<TextView>(R.id.name0).text)
            assertEquals("✓", view.findViewById<TextView>(R.id.check1).text)
            assertEquals(View.GONE, view.findViewById<View>(R.id.row2).visibility)
            assertEquals("1h 05m today · Focus at 14:00", view.findViewById<TextView>(R.id.footer).text)
        }
    }

    @Test
    fun theStreakWidgetShowsTheDaysAndTheNudge() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = HomeWidgets.streak(context, widget).apply(context, FrameLayout(context))
            assertEquals("4", view.findViewById<TextView>(R.id.streak).text)
            assertEquals("4-day streak. Still open: Run", view.findViewById<TextView>(R.id.nudge).text)
        }
    }
}
