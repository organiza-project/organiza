package org.organiza.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.organiza.feature.ai.CopilotScreen
import org.organiza.feature.arena.ArenaScreen
import org.organiza.feature.habits.HabitsScreen
import org.organiza.feature.habits.NewHabitScreen
import org.organiza.feature.home.HomeScreen
import org.organiza.feature.metrics.MetricsScreen
import org.organiza.feature.onboarding.OnboardingScreen
import org.organiza.feature.profile.ProfileScreen
import org.organiza.feature.schedule.CalendarScreen
import org.organiza.feature.schedule.DictationScreen
import org.organiza.feature.schedule.ImportScreen

/** Every tab and screen, the same on each platform. The platform supplies the snapshot and the actions. */
@Composable
fun OrganizaRoot(ui: UiSnapshot, actions: Actions) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    // Screens opened on top of a tab (ADR-0018). Back closes the top one.
    val stack = remember { mutableStateListOf<Route>() }
    val open: (Route) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.isNotEmpty() && !ui.onboarding) { back() }
    val colors = Theme.colors
    Column(Modifier.fillMaxSize().background(colors.ground).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val route = stack.lastOrNull()
        // Each tab and screen keeps its own scroll position, so switching never opens one halfway down.
        val scroll = rememberSaveable(tab, route, saver = ScrollState.Saver) {
            ScrollState(0)
        }
        val onTab = !ui.onboarding && route == null
        // Content stops at 720dp and centres on a tablet, so short panels never stretch across it.
        Box(Modifier.weight(1f).fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp)
                .padding(start = Space.margin, end = Space.margin, bottom = Space.margin),
            verticalArrangement = Arrangement.spacedBy(Space.gap),
        ) {
            when {
                ui.onboarding -> OnboardingScreen(ui, actions)
                route == Route.NEW_HABIT -> NewHabitScreen(ui, actions, back)
                route == Route.AGENDA -> CalendarScreen(ui, actions, back, open)
                route == Route.IMPORT -> ImportScreen(ui, actions, back, open)
                route == Route.DICTATION -> DictationScreen(ui, actions, back)
                route == Route.COPILOT -> CopilotScreen(ui, actions, back)
                tab == Tab.HOME -> HomeScreen(ui, actions, open) { tab = it }
                tab == Tab.HABITS -> HabitsScreen(ui, actions, open)
                tab == Tab.ARENA -> ArenaScreen(ui, actions)
                tab == Tab.METRICS -> MetricsScreen(ui, actions)
                else -> ProfileScreen(ui, actions)
            }
        }
        // The page ends at its last panel. The copilot button steps aside there instead of
        // padding every page with empty space for it.
        val atEnd = scroll.maxValue > 0 && scroll.value >= scroll.maxValue - END_SLACK
        FadingFab(onTab && !atEnd, Modifier.align(Alignment.BottomEnd).padding(16.dp)) { open(Route.COPILOT) }
        }
        if (onTab) BottomBar(tab) { tab = it }
    }
}

/** The copilot button, faded out at the end of a page so it never covers the last panel. */
@Composable
private fun FadingFab(visible: Boolean, modifier: Modifier, onClick: () -> Unit) {
    AnimatedVisibility(visible, modifier, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) { CopilotFab(onClick = onClick) }
}

// Within this many pixels of the end counts as the end, so a fling that stops just short still clears the button.
private const val END_SLACK = 24

/** The system back gesture or button, where the platform has one. */
@Composable
internal expect fun BackHandler(enabled: Boolean, onBack: () -> Unit)
