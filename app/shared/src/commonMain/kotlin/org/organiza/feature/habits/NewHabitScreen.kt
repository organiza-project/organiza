package org.organiza.feature.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.organiza.feature.blocker.AppIcon
import org.organiza.ui.*
import org.organiza.storage.HabitFrequency

/** New habit (wireframe screen 3): a title, how often, a target, a minute goal, a linked app and a typed reminder time. */
@Composable
fun NewHabitScreen(ui: UiSnapshot, actions: Actions, back: () -> Unit) {
    val colors = Theme.colors
    var title by rememberSaveable { mutableStateOf("") }
    var frequency by rememberSaveable { mutableStateOf(HabitFrequency.DAILY) }
    var target by rememberSaveable { mutableStateOf(3) }
    var timed by rememberSaveable { mutableStateOf(false) }
    var goal by rememberSaveable { mutableStateOf(30) }
    var linked by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var remind by rememberSaveable { mutableStateOf(false) }
    var minute by rememberSaveable { mutableStateOf(20 * 60) }
    TopBar("New habit", back)
    Panel {
        Field(title, { title = it.take(60) }, "Task title")
        Txt("Frequency", Type.bodyStrong, modifier = Modifier.padding(bottom = 6.dp))
        Segmented(HabitFrequency.entries, frequency, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { frequency = it }
    }
    Panel {
        PanelHeader("Target")
        val daily = frequency == HabitFrequency.DAILY
        val period = when (frequency) {
            HabitFrequency.DAILY -> "a day"
            HabitFrequency.WEEKLY -> "days a week"
            HabitFrequency.MONTHLY -> "days a month"
        }
        val range = if (frequency == HabitFrequency.WEEKLY) 1..7 else 1..31
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Stepper(if (daily) 1 else target.coerceIn(range), if (daily) "once $period" else period, range, enabled = !daily) { target = it }
        }
        if (daily) Txt("A daily habit is done once a day.", Type.small, colors.muted, Modifier.padding(top = 8.dp))
    }
    Panel {
        SwitchRow("Time goal", "A day counts once you reach a number of minutes, logged by hand or in a linked app", timed) { timed = it }
        if (timed) {
            MinutesField("Minutes a day", goal, 1..MAX_GOAL, listOf(15, 30, 45, 60)) { goal = it }
            Rule()
            val app = ui.hub.apps.firstOrNull { it.pkg == linked }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (app != null) AppIcon(app.pkg)
                Column(Modifier.weight(1f).padding(horizontal = if (app != null) 10.dp else 0.dp)) {
                    Txt(app?.label ?: "No linked app", Type.bodyStrong)
                    Txt(
                        if (app != null) "Time in this app counts towards the goal, read on this device" else "Link a reading or practice app to track it for you",
                        Type.small,
                        colors.muted,
                    )
                }
                Action(if (picking) "Done" else if (app == null) "Link app" else "Change", ButtonKind.TEXT) { picking = !picking }
            }
            if (picking) {
                if (!ui.hub.usageAllowed) Txt("Linked apps need usage access. Allow it in Metrics.", Type.small, colors.muted)
                if (linked != null) Action("Unlink", ButtonKind.TEXT) { linked = null; picking = false }
                ui.hub.apps.sortedBy { it.label.lowercase() }.forEach { option ->
                    ChoiceRow(option.label, option.category, option.pkg == linked) { linked = option.pkg; picking = false }
                }
            }
        }
    }
    Panel {
        SwitchRow("Reminder", "A notification on this device. The habit's name never leaves it", remind) { remind = it }
        if (remind) ClockField("Remind me at", minute) { minute = it }
    }
    Spacer(Modifier.height(4.dp))
    Column {
        Action("Save", ButtonKind.PRIMARY, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            val count = if (frequency == HabitFrequency.DAILY) 1 else target.coerceIn(if (frequency == HabitFrequency.WEEKLY) 1..7 else 1..31)
            actions.addHabitPlan(title, frequency, count, if (remind) minute else null, goal.takeIf { timed }, linked.takeIf { timed })
            back()
        }
    }
}

private const val MAX_GOAL = 480
