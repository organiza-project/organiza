package org.organiza.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrandTest {
    private fun render(painter: Painter, side: Int): BufferedImage {
        val bitmap = ImageBitmap(side, side)
        val size = Size(side.toFloat(), side.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) { with(painter) { draw(size) } }
        return bitmap.toAwtImage()
    }

    private fun BufferedImage.at(x: Double, y: Double) = java.awt.Color(getRGB((width * x).toInt(), (height * y).toInt()), true)

    @Test
    fun `the window icon is the brand icon at every size a window manager asks for`() {
        listOf(16, 24, 32, 48, 64, 128, 256).forEach { side ->
            val icon = render(Brand.icon, side)
            // Below 32 pixels the dot is narrower than a pixel and only tints the centre.
            if (side >= 32) assertEquals(0x6EA2F2, icon.at(0.5, 0.5).rgb and 0xFFFFFF, "the dot at $side")
            // Smoothing leaves a trace of the ground in the corner pixel.
            assertTrue(icon.at(0.0, 0.0).alpha < 8, "the rounded corner at $side")
            assertEquals(0x14, icon.at(0.5, 0.08).red, "the dark ground at $side")
        }
    }

    @Test
    fun `the top bar mark is the open ring, light on a clear ground`() {
        val mark = render(Brand.mark, 22)
        assertEquals(0, mark.at(0.0, 0.0).alpha)
        // The ring's gap is on the upper right; the ring itself runs through the left edge of the mark.
        assertEquals(0, mark.at(0.80, 0.30).alpha, "the gap")
        assertEquals(0xF2, mark.at(0.15, 0.5).red, "the ring")
    }
}
