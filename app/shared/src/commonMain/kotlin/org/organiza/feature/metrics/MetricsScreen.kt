package org.organiza.feature.metrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.organiza.feature.ai.CopilotPanel
import org.organiza.feature.blocker.BlockerHub
import org.organiza.feature.focus.ChoicesPanel
import org.organiza.feature.focus.ProtectionPanel
import org.organiza.ui.*
import org.organiza.presentation.FocusWeek

/** Metrics: today's app usage, the distraction blocker, focus this week, today's choices, the copilot and protection. */
@Composable
fun MetricsScreen(ui: UiSnapshot, actions: Actions) {
    TitleBlock("Metrics", ui.week?.total)
    MessagePanel(ui.message)
    // One app card open at a time across both lists, so two drafts of one limit never race.
    val openApp = rememberSaveable { mutableStateOf<String?>(null) }
    UsageDashboard(ui, actions, openApp)
    BlockerHub(ui, actions, openApp)
    ui.week?.let { FocusWeekPanel(it) }
    ui.choices?.let { ChoicesPanel(it) }
    CopilotPanel(ui, actions)
    ProtectionPanel(ui, actions)
}

/** One bar per day in ink, today in the data hue, with the zero line drawn even when empty. */
@Composable
private fun FocusWeekPanel(week: FocusWeek) {
    val colors = Theme.colors
    Panel {
        PanelHeader("Focus, last 7 days")
        val summary = week.days.joinToString { "${it.label} ${it.minutes} minutes" }
        Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = summary }) {
            val slot = size.width / week.days.size
            val barWidth = slot * 0.5f
            val top = 8.dp.toPx()
            val usable = size.height - top
            drawLine(colors.rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            week.days.forEachIndexed { index, day ->
                if (day.minutes == 0L || week.maxMinutes == 0L) return@forEachIndexed
                val height = usable * day.minutes / week.maxMinutes
                drawRoundRect(
                    if (day.isToday) colors.data else colors.text,
                    Offset(index * slot + (slot - barWidth) / 2, size.height - height),
                    Size(barWidth, height),
                    CornerRadius(4.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            week.days.forEach { day ->
                Txt(day.label, Type.small.copy(textAlign = TextAlign.Center), if (day.isToday) colors.data else colors.muted, Modifier.weight(1f))
            }
        }
        if (week.maxMinutes == 0L) Txt("No finished sessions this week yet.", Type.body, colors.muted, Modifier.padding(top = 8.dp))
    }
}
