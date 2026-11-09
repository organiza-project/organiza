package org.organiza.feature.blocker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.MultiResolutionImage
import java.io.File
import java.nio.file.Path
import javax.swing.ImageIcon
import javax.swing.filechooser.FileSystemView
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.organiza.desktop.LinuxApps
import org.organiza.desktop.WINDOWS

@Composable
internal actual fun rememberAppIcon(pkg: String): ImageBitmap? {
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            LinuxApps.find(pkg)?.icon?.let { icon ->
                if (WINDOWS) runCatching { windowsIcon(icon) }.getOrNull() else LinuxApps.iconFile(icon)?.let { runCatching { load(it) }.getOrNull() }
            }
        }
    }
    return icon
}

private fun load(file: Path): ImageBitmap =
    if (file.extension == "svg") {
        val painter = file.inputStream().use { loadSvgPainter(it, Density(1f)) }
        val size = Size(ICON_PX.toFloat(), ICON_PX.toFloat())
        ImageBitmap(ICON_PX, ICON_PX).also { bitmap ->
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) { with(painter) { draw(size) } }
        }
    } else {
        Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
    }

/** The program's own icon from the Windows shell, at the size asked for, up to 256 pixels (ADR-0027). */
private fun windowsIcon(exe: String): ImageBitmap? {
    val icon = FileSystemView.getFileSystemView().getSystemIcon(File(exe), ICON_PX, ICON_PX) as? ImageIcon ?: return null
    val image = icon.image.let { (it as? MultiResolutionImage)?.getResolutionVariant(ICON_PX.toDouble(), ICON_PX.toDouble()) ?: it }
    val bitmap = BufferedImage(ICON_PX, ICON_PX, BufferedImage.TYPE_INT_ARGB)
    bitmap.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        drawImage(image, 0, 0, ICON_PX, ICON_PX, null)
        dispose()
    }
    return bitmap.toComposeImageBitmap()
}

private const val ICON_PX = 96
