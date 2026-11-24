package org.organiza.feature.metrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.organiza.feature.blocker.AppIcon
import org.organiza.presentation.CategoryShare
import org.organiza.presentation.UsageBar
import org.organiza.presentation.UsageReport
import org.organiza.ui.*

private const val TOP_APPS = 8
private const val SLICES = 5

/**
 * Where the time went, StayFree style (ADR-0031): the range with arrows to move it, a bar a day with
 * gridlines and the daily average, a donut of categories, then the top apps with their icons.
 * [packageOf] finds an app's package from its name, for its icon; apps from other devices may have none.
 */
@Composable
fun ScreenTimePanel(usage: UsageView, actions: Actions, packageOf: (String) -> String? = { null }) {
    val colors = Theme.colors
    val report = usage.report
    Panel {
        PanelHeader("Screen time")
        Segmented(UsageSpan.entries, usage.span, { it.label }) { actions.setUsageSpan(it) }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(Glyph.BACK, "Earlier") { actions.shiftUsage(-1) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(report.title, Type.bodyStrong, colors.muted)
                Txt(report.total, Type.figure)
                if (report.average.isNotEmpty() && report.bars.size > 1) Txt(report.average, Type.small, colors.muted)
            }
            IconButton(Glyph.NEXT, "Later", enabled = usage.canGoForward) { actions.shiftUsage(1) }
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
        Txt("By category", Type.bodyStrong, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Categories(report.categories, report.total)
        Txt("Top apps", Type.bodyStrong, modifier = Modifier.padding(top = 20.dp))
        report.apps.take(TOP_APPS).forEach { app -> AppRow(app.label, app.time, app.fraction, packageOf(app.label)) }
    }
}

@Composable
private fun Edge(label: String, move: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(label, Type.body, modifier = Modifier.weight(1f))
        IconButton(Glyph.BACK, "$label, a day earlier", size = Space.target) { move(-1) }
        IconButton(Glyph.NEXT, "$label, a day later", size = Space.target) { move(1) }
    }
}

/**
 * A slice's colour: the data hue for the largest, blending step by step toward the ink colour for smaller
 * ones, which stays visible on a light or a dark ground; grey for the rest together.
 */
private fun sliceColor(data: Color, ink: Color, muted: Color, index: Int): Color =
    if (index < SLICES) lerp(data, ink, listOf(0f, 0.3f, 0.5f, 0.68f, 0.84f)[index]) else muted

/** The five largest categories and the rest as one slice, so the donut stays readable. */
private fun slices(categories: List<CategoryShare>): List<Pair<String, CategoryShare>> {
    val top = categories.take(SLICES).map { it.category to it }
    val rest = categories.drop(SLICES)
    if (rest.isEmpty()) return top
    val minutes = rest.sumOf { it.minutes }
    return top + ("Everything else" to CategoryShare("Everything else", minutes, "", rest.sumOf { it.fraction }))
}

@Composable
private fun Categories(categories: List<CategoryShare>, total: String) {
    val colors = Theme.colors
    val shown = slices(categories)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            val summary = shown.joinToString { (name, share) -> "$name ${(share.fraction * 100).toInt()} percent" }
            Canvas(Modifier.size(128.dp).semantics { contentDescription = summary }) {
                val stroke = 18.dp.toPx()
                val gap = if (shown.size > 1) 2f else 0f
                var start = -90f
                shown.forEachIndexed { index, (_, share) ->
                    val sweep = (share.fraction * 360).toFloat()
                    drawArc(
                        sliceColor(colors.data, colors.text, colors.muted, index),
                        start + gap / 2,
                        (sweep - gap).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke),
                    )
                    start += sweep
                }
            }
            Txt(total, Type.bodyStrong)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            shown.forEachIndexed { index, (name, share) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(sliceColor(colors.data, colors.text, colors.muted, index), CircleShape))
                    Txt(name, Type.small, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                    Txt("${(share.fraction * 100).toInt()}%", Type.small, colors.muted)
                }
            }
        }
    }
}

@Composable
private fun AppRow(name: String, time: String, fraction: Double, pkg: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (pkg != null) AppIcon(pkg, 32.dp) else AppIcon(name, 32.dp, fallback = Glyph.METRICS)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(name, Type.body, modifier = Modifier.weight(1f), maxLines = 1)
                Txt(time, Type.bodyStrong)
            }
            ProgressPill(fraction, Modifier.padding(top = 4.dp), Theme.colors.data)
        }
    }
}

/**
 * One bar a day, today in the data hue, over dashed gridlines on round times with their labels at the
 * right, and the daily average as a line in the data hue.
 */
@Composable
private fun Bars(bars: List<UsageBar>) {
    val colors = Theme.colors
    if (bars.isEmpty()) return
    val ticks = UsageReport.ticks(bars.maxOf { it.minutes })
    val top = ticks.last().toFloat()
    val daysSoFar = bars.indexOfLast { it.today }.takeIf { it >= 0 }?.plus(1) ?: bars.size
    val average = bars.take(daysSoFar).sumOf { it.minutes }.toFloat() / daysSoFar
    val summary = bars.joinToString { "${it.label} ${it.minutes} minutes" }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Canvas(Modifier.weight(1f).height(160.dp).semantics { contentDescription = summary }) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            ticks.forEach { tick ->
                val y = size.height * (1 - tick / top)
                drawLine(colors.rule, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
            }
            drawLine(colors.rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            val slot = size.width / bars.size
            val barWidth = slot * if (bars.size > 14) 0.7f else 0.56f
            bars.forEachIndexed { index, bar ->
                if (bar.minutes == 0L) return@forEachIndexed
                val height = (size.height * bar.minutes / top).coerceAtLeast(2.dp.toPx())
                drawRoundRect(
                    if (bar.today) colors.data else colors.text,
                    Offset(index * slot + (slot - barWidth) / 2, size.height - height),
                    Size(barWidth, height),
                    CornerRadius(4.dp.toPx()),
                )
            }
            if (bars.size > 1 && average > 0) {
                val y = size.height * (1 - average / top)
                drawLine(colors.data, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
            }
        }
        Box(Modifier.height(160.dp).padding(start = 6.dp)) {
            ticks.forEach { tick ->
                val label = if (tick % 60 == 0L) "${tick / 60}h" else "${tick}m"
                Txt(label, Type.small, colors.muted, Modifier.padding(top = (160 * (1 - tick / top) - 8).coerceAtLeast(0f).dp))
            }
        }
    }
    // Every label fits a week; a month labels about five days.
    val step = if (bars.size <= 7) 1 else (bars.size + 5) / 6
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, end = 30.dp)) {
        bars.forEachIndexed { index, bar ->
            Txt(if (index % step == 0) bar.label else "", Type.small.copy(textAlign = TextAlign.Center), if (bar.today) colors.data else colors.muted, Modifier.weight(1f))
        }
    }
    if (bars.size > 1 && average > 0) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 14.dp, height = 2.dp).background(colors.data))
            Txt("Daily average", Type.small, colors.muted, Modifier.padding(start = 6.dp))
        }
    }
}
