package org.organiza.feature.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.organiza.feature.focus.FocusCard
import org.organiza.feature.rpg.QuestsPanel
import org.organiza.feature.schedule.WeekPanel
import org.organiza.ui.*
import org.organiza.presentation.HomeHabit
import org.organiza.presentation.HomeModel

/**
 * Home (ADR-0022), in the order a day is checked: the daily goal, the session to start, the week and
 * today's blocks, then the same habits, dailies and to-dos as the Habits tab, read from the one snapshot.
 */
@Composable
fun HomeScreen(ui: UiSnapshot, actions: Actions, open: (Route) -> Unit, goTo: (Tab) -> Unit) {
    val colors = Theme.colors
    val home = ui.home
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.semantics { contentDescription = "Organiza" }) { LogoMark(40.dp) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Txt(home?.greeting ?: "Home", Type.title, modifier = Modifier.semantics { heading() })
            home?.let { Txt(it.date, Type.small, colors.muted) }
        }
    }
    MessagePanel(ui.message)
    home?.let { GoalPanel(it) }
    FocusCard(ui, actions) { goTo(Tab.METRICS) }
    WeekPanel(ui.calendar, ui.agendaToday, actions) { open(Route.AGENDA) }
    Panel {
        PanelHeader("Today's habits") { Action("New", ButtonKind.TEXT) { open(Route.NEW_HABIT) } }
        val habits = home?.habits.orEmpty()
        if (habits.isEmpty()) {
            Txt("No habits yet. Add one to start filling the dial.", Type.body, colors.muted)
            Spacer(Modifier.height(8.dp))
            Action("New habit", ButtonKind.PRIMARY) { open(Route.NEW_HABIT) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            habits.forEach { habit -> HabitCard(habit) { done -> actions.markToday(habit.id, habit.name, done) } }
        }
    }
    QuestsPanel(ui.rpg, actions, hero = false)
}

/** The day's status at a glance: the dial beside the goals met and what is left. */
@Composable
private fun GoalPanel(home: HomeModel) {
    val colors = Theme.colors
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dial(home.completion, "${(home.completion * 100).toInt()}%", "today", 128.dp)
            Column(Modifier.weight(1f).padding(start = 20.dp)) {
                Txt("Daily goal", Type.small, colors.muted, modifier = Modifier.semantics { heading() })
                Txt(home.headline, Type.section)
                Txt(home.detail, Type.small, colors.muted, Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** One habit: a small ring, its name, a pill of the period's progress, and today's toggle. */
@Composable
private fun HabitCard(habit: HomeHabit, mark: (Boolean) -> Unit) {
    val colors = Theme.colors
    val progress = habit.progress
    Row(
        Modifier
            .fillMaxWidth()
            .border(Space.stroke, colors.border, androidx.compose.foundation.shape.RoundedCornerShape(Space.radiusControl))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Space.target)
                .border(if (progress.doneToday) 3.dp else Space.stroke, if (progress.doneToday) colors.data else colors.border, CircleShape)
                .clickable(role = Role.Checkbox) { mark(!progress.doneToday) }
                .semantics {
                    contentDescription = "Done today: ${habit.name}"
                    stateDescription = if (progress.doneToday) "Done" else "Not done"
                },
            contentAlignment = Alignment.Center,
        ) { if (progress.doneToday) Icon(Glyph.CHECK, colors.data) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(habit.name, Type.bodyStrong, modifier = Modifier.weight(1f), maxLines = 1)
                Txt("${progress.done}/${progress.target}", Type.body, colors.muted)
            }
            Spacer(Modifier.height(6.dp))
            ProgressPill(progress.fraction)
            Txt(progress.label, Type.small, colors.muted, Modifier.padding(top = 4.dp))
        }
    }
}
