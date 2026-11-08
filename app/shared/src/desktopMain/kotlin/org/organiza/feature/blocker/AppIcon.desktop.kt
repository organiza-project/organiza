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
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.organiza.desktop.LinuxApps

@Composable
internal actual fun rememberAppIcon(pkg: String): ImageBitmap? {
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            LinuxApps.find(pkg)?.icon?.let(LinuxApps::iconFile)?.let { runCatching { load(it) }.getOrNull() }
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

private const val ICON_PX = 96
