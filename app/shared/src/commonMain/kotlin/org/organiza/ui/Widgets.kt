package org.organiza.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The daily goal dial: the signature element (ADR-0018). The arc is the one data hue. */
@Composable
fun Dial(fraction: Double, figure: String, caption: String, size: Dp = 200.dp) {
    val colors = Theme.colors
    Box(Modifier.size(size).semantics { contentDescription = "$caption: $figure" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(colors.raised, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            if (fraction > 0) {
                drawArc(colors.data, -90f, (360 * fraction.coerceIn(0.0, 1.0)).toFloat(), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(figure, Type.figure)
            Txt(caption, Type.small, colors.muted)
        }
    }
}

/** A thin pill track, filled to [fraction] in ink. */
@Composable
fun ProgressPill(fraction: Double, modifier: Modifier = Modifier, ink: androidx.compose.ui.graphics.Color = Theme.colors.text) {
    val colors = Theme.colors
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(colors.raised, cornerRadius = radius)
        val width = (size.width * fraction.coerceIn(0.0, 1.0)).toFloat()
        if (width > 0) drawRoundRect(ink, size = Size(maxOf(width, size.height), size.height), cornerRadius = radius)
    }
}

/** Equal segments in an ink outline; the chosen one is filled. */
@Composable
fun <T> Segmented(options: List<T>, chosen: T, label: (T) -> String, onChoose: (T) -> Unit) {
    val colors = Theme.colors
    val shape = RoundedCornerShape(Space.radiusControl)
    Row(Modifier.fillMaxWidth().border(Space.stroke, colors.border, shape).padding(3.dp)) {
        options.forEach { option ->
            val on = option == chosen
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .background(if (on) colors.primary else colors.surface, RoundedCornerShape(Space.radiusControl - 3.dp))
                    .clickable(role = Role.Tab) { onChoose(option) }
                    .semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) { Txt(label(option), Type.bodyStrong, if (on) colors.onPrimary else colors.text) }
        }
    }
}

/** A number with minus and plus, for targets. */
@Composable
fun Stepper(value: Int, unit: String, range: IntRange, enabled: Boolean = true, onChange: (Int) -> Unit) {
    val colors = Theme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StepButton(Glyph.MINUS, "Fewer", enabled && value > range.first) { onChange(value - 1) }
        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(value.toString(), Type.figure, if (enabled) colors.text else colors.muted)
            Txt(unit, Type.small, colors.muted)
        }
        StepButton(Glyph.PLUS, "More", enabled && value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(glyph: Glyph, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = Theme.colors
    Box(
        Modifier
            .size(Space.target + 4.dp)
            .border(Space.stroke, if (enabled) colors.border else colors.rule, RoundedCornerShape(Space.radiusControl))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(glyph, if (enabled) colors.text else colors.muted) }
}

/** A person's initial in an ink ring. */
@Composable
fun Avatar(name: String, size: Dp = 40.dp, highlighted: Boolean = false) {
    val colors = Theme.colors
    Box(
        Modifier
            .size(size)
            .border(if (highlighted) 2.5.dp else Space.stroke, if (highlighted) colors.data else colors.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Txt(name.trim().take(1).uppercase().ifEmpty { "?" }, Type.section) }
}

/** A badge: an ink ring when earned, a dashed grey ring when not yet. */
@Composable
fun Medallion(glyph: Glyph, title: String, earned: Boolean) {
    val colors = Theme.colors
    Column(
        Modifier.width(88.dp).semantics { stateDescription = if (earned) "Earned" else "Not earned yet" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(56.dp)) {
                val width = (if (earned) 2.dp else 1.5.dp).toPx()
                drawCircle(
                    if (earned) colors.text else colors.control.copy(alpha = 0.35f),
                    radius = size.minDimension / 2 - width,
                    style = Stroke(width, pathEffect = if (earned) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                )
            }
            Icon(glyph, if (earned) colors.text else colors.muted, 24.dp)
        }
        Txt(title, Type.small, if (earned) colors.text else colors.muted, Modifier.padding(top = 6.dp), maxLines = 2)
    }
}
