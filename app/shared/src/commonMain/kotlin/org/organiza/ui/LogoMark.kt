package org.organiza.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The mark from docs/brand/mark.svg, drawn with the theme's ink and data hue so it holds in dark
 * mode. Geometry is in the icon's 108-unit grid: ring radius 21, stroke 8.5, a 60 degree gap.
 */
@Composable
fun LogoMark(size: Dp = 72.dp) {
    val colors = Theme.colors
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / GRID
        val radius = RING_RADIUS * unit
        val centre = Offset(this.size.width / 2, this.size.height / 2)
        drawArc(
            colors.text,
            startAngle = GAP_END,
            sweepAngle = SWEEP,
            useCenter = false,
            topLeft = Offset(centre.x - radius, centre.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = STROKE * unit, cap = StrokeCap.Round),
        )
        drawCircle(colors.data, radius = DOT_RADIUS * unit, center = centre)
    }
}

// The mark spans 108 units with a margin, so it is drawn at a larger scale than the icon.
private const val GRID = 60f
private const val RING_RADIUS = 21f
private const val STROKE = 8.5f
private const val DOT_RADIUS = 5.75f
private const val GAP_END = -15f
private const val SWEEP = 300f
