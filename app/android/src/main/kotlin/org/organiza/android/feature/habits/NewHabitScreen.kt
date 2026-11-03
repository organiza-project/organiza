package org.organiza.android.feature.habits

import android.app.TimePickerDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.organiza.android.ui.*
import org.organiza.storage.HabitFrequency

/** New habit (wireframe screen 3): a title, how often, a target per period and a reminder. */
@Composable
fun NewHabitScreen(actions: Actions, back: () -> Unit) {
    val colors = Theme.colors
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var frequency by rememberSaveable { mutableStateOf(HabitFrequency.DAILY) }
    var target by rememberSaveable { mutableStateOf(3) }
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
        SwitchRow("Reminder", "A notification on this phone. The habit's name never leaves it", remind) { remind = it }
        if (remind) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Glyph.CLOCK, colors.muted)
                Txt("%02d:%02d".format(minute / 60, minute % 60), Type.section, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Action("Change", ButtonKind.SECONDARY) {
                    TimePickerDialog(context, { _, hour, minutes -> minute = hour * 60 + minutes }, minute / 60, minute % 60, true).show()
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Column {
        Action("Save", ButtonKind.PRIMARY, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            val count = if (frequency == HabitFrequency.DAILY) 1 else target.coerceIn(if (frequency == HabitFrequency.WEEKLY) 1..7 else 1..31)
            actions.addHabitPlan(title, frequency, count, if (remind) minute else null)
            back()
        }
    }
}
