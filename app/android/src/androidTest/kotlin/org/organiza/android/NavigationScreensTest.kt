package org.organiza.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.organiza.android.feature.habits.NewHabitScreen
import org.organiza.android.feature.home.HomeScreen
import org.organiza.android.ui.CalendarView
import org.organiza.android.ui.OrganizaTheme
import org.organiza.android.ui.Route
import org.organiza.android.ui.RpgTaskView
import org.organiza.android.ui.RpgView
import org.organiza.android.ui.Tab
import org.organiza.android.ui.UiSnapshot
import org.organiza.enforcement.SessionPolicy
import org.organiza.presentation.CalendarMonth
import org.organiza.presentation.FocusModel
import org.organiza.presentation.Format
import org.organiza.storage.RpgKind

/** Home's new order, the hub's way to Metrics, and typed times (ADR-0021). */
@RunWith(AndroidJUnit4::class)
class NavigationScreensTest {
    @get:Rule val compose = createComposeRule()
    private val actions = RecordingActions()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        OrganizaTheme { Column(Modifier.verticalScroll(rememberScrollState())) { content() } }
    }

    @Test
    fun homeLeadsWithTheSessionAndCalendarThenTheSameQuestsAsHabits() {
        val today = LocalDate.now()
        val focus = FocusModel.build(Instant.now(), ZoneId.systemDefault(), SessionPolicy.IDLE, emptyList(), emptySet(), 0)
        val calendar = CalendarView(selected = today, month = CalendarMonth.build(YearMonth.from(today), today, today, emptyMap()))
        val rpg = RpgView(tasks = listOf(RpgTaskView("d", RpgKind.DAILY, "Drink water", done = false)))
        val opened = mutableListOf<Route>()
        val tabs = mutableListOf<Tab>()
        show { HomeScreen(UiSnapshot(focus = focus, calendar = calendar, rpg = rpg), actions, { opened += it }, { tabs += it }) }
        compose.onNodeWithContentDescription("Organiza").assertExists()
        compose.onNodeWithText("Start a focus session").assertExists()
        compose.onNodeWithContentDescription("${Format.dayMonth(today)}, 0 blocks").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Done Drink water").performScrollTo().performClick()
        compose.onNodeWithText("Start a focus session").performScrollTo().performClick()
        compose.onNodeWithText("Edit the focus list in Metrics").performScrollTo().performClick()
        assertEquals(listOf("selectDay $today null", "checkDaily d true"), actions.calls)
        assertEquals(listOf(Route.AGENDA), opened)
        assertEquals(listOf(Tab.METRICS), tabs)
    }

    @Test
    fun aReminderTimeIsTypedOnTheNumberPad() {
        show { NewHabitScreen(UiSnapshot(), actions) {} }
        compose.onNode(hasSetTextAction()).performTextInput("Stretch")
        compose.onNodeWithText("Reminder").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Remind me at, hours").performScrollTo().performTextReplacement("07")
        compose.onNodeWithContentDescription("Remind me at, minutes").performTextReplacement("45")
        // The emulator may keep a 12 hour clock, where the form starts at 8 PM.
        if (compose.onAllNodesWithText("AM").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("AM").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals(listOf("addHabitPlan Stretch DAILY 1 465 null null"), actions.calls)
    }

    @Test
    fun aTimeOutsideTheClockIsRefusedUntilFixed() {
        show { NewHabitScreen(UiSnapshot(), actions) {} }
        compose.onNodeWithText("Reminder").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Remind me at, minutes").performScrollTo().performTextReplacement("75")
        compose.onNodeWithText("minutes 0 to 59", substring = true).assertExists()
    }
}
