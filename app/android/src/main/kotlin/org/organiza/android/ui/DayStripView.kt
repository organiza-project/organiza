package org.organiza.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.organiza.presentation.BarKind
import org.organiza.presentation.DayStrip

/** The signature element: today as a strip, every session a bar, and a line at now (ADR-0012). */
@Composable
fun DayStripView(strip: DayStrip, description: String) {
    val colors = Theme.colors
    Column(Modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val barTop = 8.dp.toPx()
            val barHeight = 20.dp.toPx()
            drawRect(colors.raised, Offset(0f, barTop + barHeight / 2 - 1.dp.toPx()), Size(size.width, 2.dp.toPx()))
            for (tick in strip.ticks) {
                if (tick.at < 0 || tick.at > 1) continue
                val x = (tick.at * size.width).toFloat().coerceIn(0f, size.width - 1f)
                drawRect(colors.control, Offset(x, barTop + 4.dp.toPx()), Size(1.dp.toPx(), barHeight - 8.dp.toPx()))
            }
            for (bar in strip.bars) {
                val start = (bar.start * size.width).toFloat()
                val width = ((bar.end - bar.start) * size.width).toFloat().coerceAtLeast(3.dp.toPx())
                val color = when (bar.kind) {
                    BarKind.NOW -> colors.data
                    BarKind.LATER -> colors.text
                    BarKind.PAST -> colors.control
                }
                drawRoundRect(color, Offset(start, barTop), Size(width, barHeight), CornerRadius(4.dp.toPx()))
            }
            val now = (strip.now * size.width).toFloat()
            drawRect(colors.data, Offset(now - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
        }
        TickLabels(strip)
    }
}

@Composable
private fun TickLabels(strip: DayStrip) {
    val visible = strip.ticks.filter { it.at in 0.0..1.0 }
    Layout(
        content = { visible.forEach { Txt(it.label, Type.small, Theme.colors.muted) } },
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                val centre = (visible[index].at * constraints.maxWidth).toInt()
                // The first and last labels are pulled inside the strip (app-screens §6.3).
                val x = (centre - placeable.width / 2).coerceIn(0, constraints.maxWidth - placeable.width)
                placeable.place(x, 0)
            }
        }
    }
}

@Composable
fun Legend(vararg items: Pair<androidx.compose.ui.graphics.Color, String>) {
    androidx.compose.foundation.layout.Row(Modifier.padding(top = 8.dp)) {
        items.forEach { (color, label) ->
            Box(Modifier.padding(end = 6.dp, top = 5.dp)) {
                Canvas(Modifier.height(8.dp).padding(0.dp)) { drawRoundRect(color, size = Size(8.dp.toPx(), 8.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx())) }
            }
            Txt(label, Type.small, Theme.colors.muted, Modifier.padding(start = 10.dp, end = 14.dp))
        }
    }
}
