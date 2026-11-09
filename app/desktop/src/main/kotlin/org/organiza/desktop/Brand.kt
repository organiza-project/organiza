package org.organiza.desktop

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.awt.image.BufferedImage

/**
 * The icon from docs/brand/icon.svg and the mark from docs/brand/mark.svg, drawn as shapes. Compose
 * redraws a painter at each size the desktop asks for, where a shrunk 512-pixel picture comes out jagged.
 */
object Brand {
    /** The app icon, the light mark on a dark rounded square, for the window and the dock. */
    val icon: Painter = BrandPainter(ground = true)

    /** The mark alone, light, for the dark top bar. */
    val mark: Painter = BrandPainter(ground = false)

    /**
     * The icon drawn at each size the window manager may show it. Compose hands the window a single
     * picture, which Mutter and KWin scale to the dock, Alt+Tab and title bar sizes and blur.
     */
    val iconImages: List<BufferedImage> by lazy { listOf(16, 24, 32, 48, 64, 128, 256).map { render(icon, it) } }

    internal fun render(painter: Painter, side: Int): BufferedImage {
        val bitmap = ImageBitmap(side, side)
        val size = Size(side.toFloat(), side.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) { with(painter) { draw(size) } }
        return bitmap.toAwtImage()
    }
}

// The 108-unit grid of the SVGs. The mark alone is drawn larger, on a 60-unit grid, as LogoMark does.
private class BrandPainter(private val ground: Boolean) : Painter() {
    override val intrinsicSize: Size = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        val unit = size.minDimension / if (ground) 108f else 60f
        if (ground) drawRoundRect(Color(0xFF141414), cornerRadius = CornerRadius(24 * unit))
        val radius = 21 * unit
        drawArc(
            Color(0xFFF2F4F7),
            startAngle = -15f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 8.5f * unit, cap = StrokeCap.Round),
        )
        drawCircle(Color(0xFF6EA2F2), radius = 5.75f * unit, center = center)
    }
}
