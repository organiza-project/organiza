package org.organiza.android.feature.home

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
import androidx.compose.foundation.layout.wrapContentWidth
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
import org.organiza.android.feature.focus.FocusCard
import org.organiza.android.ui.*
import org.organiza.presentation.HomeHabit

/** Home (wireframe screen 2): the daily goal dial, the session card and today's habits. */
@Composable
fun HomeScreen(ui: UiSnapshot, actions: Actions, open: (Route) -> Unit, goTo: (Tab) -> Unit) {
    val colors = Theme.colors
    val home = ui.home
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt("Home", Type.title, modifier = Modifier.semantics { heading() })
            Txt(home?.headline ?: "", Type.body, colors.muted)
        }
        Box(Modifier.clickable(role = Role.Button) { goTo(Tab.PROFILE) }.semantics { contentDescription = "Profile" }) {
            Avatar(ui.selfName)
        }
    }
    if (home != null) {
        Box(Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).padding(vertical = 8.dp)) {
            Dial(home.completion, "${(home.completion * 100).toInt()}%", "Daily goal")
        }
    }
    MessagePanel(ui.message)
    FocusCard(ui, actions, open)
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
