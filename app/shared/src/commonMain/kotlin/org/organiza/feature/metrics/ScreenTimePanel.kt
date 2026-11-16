package org.organiza.feature.metrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.organiza.ui.*

private const val TOP_APPS = 8

/** Where the time went, StayFree style (ADR-0031): a bar a day over the range, then categories and apps. */
@Composable
fun ScreenTimePanel(usage: UsageView, actions: Actions) {
    val colors = Theme.colors
    val report = usage.report
    Panel {
        PanelHeader("Screen time")
        Segmented(UsageSpan.entries, usage.span, { it.label }) { actions.setUsageSpan(it) }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Action("Earlier", ButtonKind.TEXT) { actions.shiftUsage(-1) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(report.title, Type.section)
                Txt(report.total, Type.bodyStrong, colors.muted)
            }
            Action("Later", ButtonKind.TEXT, enabled = usage.canGoForward) { actions.shiftUsage(1) }
        }
        if (usage.span == UsageSpan.RANGE) {
            Edge("From ${usage.from}") { actions.shiftUsageEdge(true, it) }
            Edge("To ${usage.to}") { actions.shiftUsageEdge(false, it) }
        }
        Bars(report.bars)
        if (usage.linked) {
            Segmented(listOf(true, false), usage.allDevices, { if (it) "All my devices" else "This device" }) { actions.setUsageAllDevices(it) }
        }
        if (report.categories.isEmpty()) {
            Txt("No app use recorded in this range.", Type.body, colors.muted, Modifier.padding(top = 8.dp))
            return@Panel
        }
        Txt("By category", Type.bodyStrong, modifier = Modifier.padding(top = 16.dp))
        report.categories.forEach { Share(it.category, it.time, it.fraction) }
        Txt("Top apps", Type.bodyStrong, modifier = Modifier.padding(top = 16.dp))
        report.apps.take(TOP_APPS).forEach { Share(it.label, it.time, it.fraction) }
    }
}

@Composable
private fun Edge(label: String, move: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(label, Type.body, modifier = Modifier.weight(1f))
        Action("Day before", ButtonKind.TEXT) { move(-1) }
        Action("Day after", ButtonKind.TEXT) { move(1) }
    }
}

@Composable
private fun Share(name: String, time: String, fraction: Double) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(name, Type.body, modifier = Modifier.weight(1f), maxLines = 1)
            Txt(time, Type.bodyStrong)
        }
        ProgressPill(fraction, Modifier.padding(top = 4.dp), Theme.colors.data)
    }
}

/** One bar a day in ink, today in the data hue, with the zero line drawn even when empty. */
@Composable
private fun Bars(bars: List<org.organiza.presentation.UsageBar>) {
    val colors = Theme.colors
    if (bars.isEmpty()) return
    val most = bars.maxOf { it.minutes }.coerceAtLeast(1)
    val summary = bars.joinToString { "${it.label} ${it.minutes} minutes" }
    Canvas(Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp).semantics { contentDescription = summary }) {
        val slot = size.width / bars.size
        val barWidth = slot * if (bars.size > 14) 0.7f else 0.5f
        drawLine(colors.rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        bars.forEachIndexed { index, bar ->
            if (bar.minutes == 0L) return@forEachIndexed
            val height = size.height * bar.minutes / most
            drawRoundRect(if (bar.today) colors.data else colors.text, Offset(index * slot + (slot - barWidth) / 2, size.height - height), Size(barWidth, height), CornerRadius(3.dp.toPx()))
        }
    }
    // Every label fits a week; a month labels about five days.
    val step = if (bars.size <= 7) 1 else (bars.size + 5) / 6
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        bars.forEachIndexed { index, bar ->
            Txt(if (index % step == 0) bar.label else "", Type.small.copy(textAlign = TextAlign.Center), if (bar.today) colors.data else colors.muted, Modifier.weight(1f))
        }
    }
}
