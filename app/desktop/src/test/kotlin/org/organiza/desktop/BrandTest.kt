package org.organiza.desktop

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrandTest {
    private fun BufferedImage.at(x: Double, y: Double) = java.awt.Color(getRGB((width * x).toInt(), (height * y).toInt()), true)

    @Test
    fun `the window icon is the brand icon at every size a window manager asks for`() {
        // Mutter and KWin pick the nearest size from _NET_WM_ICON and scale it; one size blurs everywhere else.
        assertEquals(listOf(16, 24, 32, 48, 64, 128, 256), Brand.iconImages.map { it.width })
        Brand.iconImages.forEach { icon ->
            val side = icon.width
            // Below 32 pixels the dot is narrower than a pixel and only tints the centre.
            if (side >= 32) assertEquals(0x6EA2F2, icon.at(0.5, 0.5).rgb and 0xFFFFFF, "the dot at $side")
            // Smoothing leaves a trace of the ground in the corner pixel.
            assertTrue(icon.at(0.0, 0.0).alpha < 8, "the rounded corner at $side")
            assertEquals(0x14, icon.at(0.5, 0.08).red, "the dark ground at $side")
        }
    }

    @Test
    fun `the linux tray mark is monochrome on a solid black square`() {
        // AWT's X11 tray has no transparency and fills see-through pixels grey, so nothing here may be clear.
        val tray = Brand.render(Brand.trayMark, 60)
        assertEquals(0xFF000000.toInt(), tray.getRGB(0, 0))
        assertEquals(0xF2, tray.at(0.15, 0.5).red, "the ring")
        assertEquals(0xF2F4F7, tray.at(0.5, 0.5).rgb and 0xFFFFFF, "the dot is the ring's light ink, not blue")
    }

    @Test
    fun `the top bar mark is the open ring, light on a clear ground`() {
        val mark = Brand.render(Brand.mark, 22)
        assertEquals(0, mark.at(0.0, 0.0).alpha)
        // The ring's gap is on the upper right; the ring itself runs through the left edge of the mark.
        assertEquals(0, mark.at(0.80, 0.30).alpha, "the gap")
        assertEquals(0xF2, mark.at(0.15, 0.5).red, "the ring")
    }
}
