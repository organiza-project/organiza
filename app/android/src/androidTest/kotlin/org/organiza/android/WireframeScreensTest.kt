package org.organiza.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
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
import org.organiza.android.feature.blocker.BlockerHubScreen
import org.organiza.android.feature.habits.NewHabitScreen
import org.organiza.android.feature.home.HomeScreen
import org.organiza.android.ui.HubApp
import org.organiza.android.ui.HubView
import org.organiza.android.ui.OrganizaTheme
import org.organiza.android.ui.UiSnapshot
import org.organiza.presentation.HomeHabitInput
import org.organiza.presentation.HomeModel
import org.organiza.storage.HabitFrequency
import org.organiza.storage.HabitPlan

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
        show { NewHabitScreen(actions) { closed = true } }
        compose.onNode(hasSetTextAction()).performTextInput("Run")
        compose.onNodeWithText("Weekly").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals(listOf("addHabitPlan Run WEEKLY 4 null"), actions.calls)
        assertTrue(closed)
    }

    @Test
    fun theHomeDialAndCardsShowTodaysGoals() {
        val today = LocalDate.now()
        val home = HomeModel.build(
            today,
            listOf(HomeHabitInput("a", "Read", HabitPlan.DAILY, mapOf(today to true)), HomeHabitInput("b", "Run", HabitPlan(HabitFrequency.WEEKLY, 3, null), emptyMap())),
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
        show { BlockerHubScreen(UiSnapshot(hub = hub), actions) {} }
        compose.onNodeWithText("Block Reels tab only").performClick()
        compose.onNodeWithText("Daily limit").performScrollTo().performClick()
        assertEquals(listOf("setFeed INSTAGRAM_REELS true", "setLimit com.instagram.android 60"), actions.calls)
    }
}
