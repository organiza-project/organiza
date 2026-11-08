package org.organiza.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One outline icon set, drawn for Organiza on a 24-unit grid with round 1.75 strokes (ADR-0018).
 * Icons are grey or ink and never sit in a tinted box.
 */
enum class Glyph(internal val path: String) {
    HOME("M3 11 L12 4 L21 11 M5 9.5 V20 H19 V9.5 M10 20 V14 H14 V20"),
    HABITS("M10 6 H20 M10 12 H20 M10 18 H20 M3.5 6 L5 7.5 L7.5 4.5 M3.5 12 L5 13.5 L7.5 10.5 M3.5 18 L5 19.5 L7.5 16.5"),
    ARENA("M3 8 L7.5 12 L12 5 L16.5 12 L21 8 L19 18.5 H5 Z"),
    METRICS("M5 20 V13 M11 20 V6 M17 20 V10 M3 20 H21"),
    PROFILE("M12 12 A4 4 0 1 0 12 4 A4 4 0 1 0 12 12 M4.5 20 C5.5 16.5 8.5 14.5 12 14.5 C15.5 14.5 18.5 16.5 19.5 20"),
    PLUS("M12 5 V19 M5 12 H19"),
    MINUS("M5 12 H19"),
    BACK("M15 5 L8 12 L15 19"),
    NEXT("M9 5 L16 12 L9 19"),
    CALENDAR("M4 6 H20 V20 H4 Z M4 10 H20 M8 3 V7 M16 3 V7"),
    CAMERA("M4 8 H8 L10 5.5 H14 L16 8 H20 V19 H4 Z M12 16.5 A3.5 3.5 0 1 0 12 9.5 A3.5 3.5 0 1 0 12 16.5"),
    DOCUMENT("M6 3 H14 L19 8 V21 H6 Z M14 3 V8 H19 M9 13 H16 M9 17 H16"),
    MIC("M12 15 A3 3 0 0 0 15 12 V6 A3 3 0 0 0 9 6 V12 A3 3 0 0 0 12 15 M6 11 A6 6 0 0 0 18 11 M12 17 V21"),
    LOCK("M6 11 H18 V20 H6 Z M8.5 11 V8 A3.5 3.5 0 0 1 15.5 8 V11"),
    SHIELD("M12 3 L19 6 V11 C19 15.5 16 19 12 21 C8 19 5 15.5 5 11 V6 Z"),
    FLAME("M12 21 C8.5 21 6 18.5 6 15.5 C6 12 9 10 9.5 6.5 C11.5 8 12 9.5 12 11 C13 10 13.5 8.5 13.5 7 C16.5 9 18 12 18 15.5 C18 18.5 15.5 21 12 21 Z"),
    BELL("M6 16 V11 A6 6 0 0 1 18 11 V16 L19.5 18 H4.5 Z M10 20.5 A2 2 0 0 0 14 20.5"),
    CLOCK("M12 21 A9 9 0 1 0 12 3 A9 9 0 1 0 12 21 M12 7.5 V12 L15 14"),
    CROWN("M4 9 L8 12.5 L12 6 L16 12.5 L20 9 L18.5 17 H5.5 Z"),
    CHECK("M5 12.5 L10 17 L19 7"),
    CHAT("M4 5 H20 V16 H10 L6 19.5 V16 H4 Z M8 9.5 H16 M8 12.5 H13"),
    SEND("M4.5 12 L19.5 4.5 L15 19.5 L11.5 13 Z M11.5 13 L19.5 4.5"),
}

private val vectors = mutableMapOf<Glyph, ImageVector>()

private fun vector(glyph: Glyph): ImageVector = vectors.getOrPut(glyph) {
    ImageVector.Builder(glyph.name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes(glyph.path),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.75f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
}

@Composable
fun Icon(glyph: Glyph, color: Color = Theme.colors.text, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Image(rememberVectorPainter(vector(glyph)), contentDescription = null, colorFilter = ColorFilter.tint(color), modifier = modifier.size(size))
}
