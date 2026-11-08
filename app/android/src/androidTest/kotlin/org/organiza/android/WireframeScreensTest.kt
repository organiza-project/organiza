package org.organiza.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.organiza.feature.blocker.BlockerHub
import org.organiza.feature.habits.NewHabitScreen
import org.organiza.feature.home.HomeScreen
import org.organiza.ui.HubApp
import org.organiza.ui.HubView
import org.organiza.ui.OrganizaTheme
import org.organiza.ui.UiSnapshot
import org.organiza.presentation.HomeHabitInput
import org.organiza.presentation.HomeModel
import org.organiza.storage.HabitFrequency
import org.organiza.storage.HabitPlan
import org.organiza.storage.RpgKind
import org.organiza.feature.rpg.QuestsPanel
import org.organiza.feature.schedule.CalendarScreen
import org.organiza.ui.CalendarView
import org.organiza.ui.RpgTaskView
import org.organiza.ui.RpgView
import org.organiza.presentation.CalendarMonth
import org.organiza.presentation.Format
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class WireframeScreensTest {
    @get:Rule val compose = createComposeRule()
    private val actions = RecordingActions()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        OrganizaTheme { Column(Modifier.verticalScroll(rememberScrollState())) { content() } }
    }

    @Test
    fun aWeeklyHabitIsSavedWithItsTarget() {
        var closed = false
        show { NewHabitScreen(UiSnapshot(), actions) { closed = true } }
        compose.onNode(hasSetTextAction()).performTextInput("Run")
        compose.onNodeWithText("Weekly").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals(listOf("addHabitPlan Run WEEKLY 4 null null null"), actions.calls)
        assertTrue(closed)
    }

    @Test
    fun theHomeDialAndCardsShowTodaysGoals() {
        val today = LocalDate.now()
        val home = HomeModel.build(
            today,
            listOf(HomeHabitInput("a", "Read", HabitPlan.DAILY, mapOf(today to true)), HomeHabitInput("b", "Run", HabitPlan(HabitFrequency.WEEKLY, 3, null), emptyMap())),
            hour = 9,
        )
        show { HomeScreen(UiSnapshot(home = home), actions, {}, {}) }
        compose.onNodeWithText("50%").assertExists()
        compose.onNodeWithText("1 of 2 goals met").assertExists()
        compose.onNodeWithText("0 of 3 this week").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Done today: Run").performScrollTo().performClick()
        assertEquals(listOf("markToday Run"), actions.calls)
    }

    @Test
    fun theHubSwitchesFeedsAndSetsALimit() {
        val hub = HubView(
            apps = listOf(HubApp("com.instagram.android", "Instagram", onList = true, usedMinutes = 50, limitMinutes = null, locked = false)),
            usageAllowed = true,
        )
        show { BlockerHub(UiSnapshot(hub = hub), actions) }
        compose.onNodeWithText("Block Reels tab only").performClick()
        compose.onNodeWithText("Daily limit").performScrollTo().performClick()
        assertEquals(listOf("setFeed INSTAGRAM_REELS true", "setLimit com.instagram.android 60"), actions.calls)
    }

    @Test
    fun theHubWarnsNearALimitAndPicksAppsBySearch() {
        val hub = HubView(
            apps = listOf(
                HubApp("com.instagram.android", "Instagram", onList = false, usedMinutes = 50, limitMinutes = 60, locked = false, category = "Social"),
                HubApp("org.example.chess", "Chess", onList = false, usedMinutes = 3, limitMinutes = null, locked = false, category = "Games"),
                HubApp("org.example.maps", "Maps", onList = false, usedMinutes = 0, limitMinutes = null, locked = false, category = "Maps"),
            ),
            usageAllowed = true,
            screenTime = "53 m",
        )
        show { BlockerHub(UiSnapshot(hub = hub), actions) }
        compose.onNodeWithText("50/60 min today").performScrollTo().assertExists()
        compose.onNodeWithText("Add apps").performScrollTo().performClick()
        compose.onNodeWithText("Search apps").performScrollTo()
        // The search field comes after Instagram's minutes box and before the website field.
        compose.onAllNodes(hasSetTextAction() and !hasContentDescription("Minutes a day"))[0].performTextInput("che")
        compose.onNodeWithText("Maps").assertDoesNotExist()
        compose.onNodeWithText("Games").performScrollTo().assertExists()
        compose.onNodeWithText("Block").performScrollTo().performClick()
        assertEquals(listOf("setFocusApp org.example.chess true"), actions.calls)
    }

    @Test
    fun aDayIsChosenFromTheMonthAndTakesAFocusBlock() {
        val today = LocalDate.now()
        val other = today.withDayOfMonth(if (today.dayOfMonth == 15) 16 else 15)
        val calendar = CalendarView(selected = today, month = CalendarMonth.build(YearMonth.from(today), today, today, emptyMap()))
        show { CalendarScreen(UiSnapshot(calendar = calendar), actions, {}, {}) }
        compose.onNodeWithContentDescription("${Format.dayMonth(other)}, 0 blocks").performClick()
        compose.onNodeWithText("Add block").performScrollTo().performClick()
        compose.onNodeWithText("Week").performScrollTo().performClick()
        assertEquals(listOf("selectDay $other null", "saveFocusBlock null $today 540 60", "showCalendar WEEK"), actions.calls)
    }

    @Test
    fun dailiesCheckOffAndHabitsCountBothWays() {
        val rpg = RpgView(
            hp = 42,
            badge = "Lvl 3 · Novice",
            level = 3,
            rankLine = "Initiate at level 10 · 2,100 XP to go",
            tasks = listOf(RpgTaskView("d", RpgKind.DAILY, "Drink water", done = false), RpgTaskView("h", RpgKind.HABIT, "Stretch", done = false)),
        )
        show { QuestsPanel(rpg, actions) }
        compose.onNodeWithContentDescription("HP 42 / 50").assertExists()
        compose.onNodeWithText("Novice").assertExists()
        compose.onNodeWithContentDescription("Level 3").assertExists()
        compose.onNodeWithContentDescription("Done Drink water").performClick()
        compose.onNodeWithText("Habits 1").performClick()
        compose.onNodeWithContentDescription("Did Stretch").performClick()
        compose.onNodeWithContentDescription("Slip on Stretch").performClick()
        assertEquals(listOf("checkDaily d true", "tapHabit h true", "tapHabit h false"), actions.calls)
    }
}
