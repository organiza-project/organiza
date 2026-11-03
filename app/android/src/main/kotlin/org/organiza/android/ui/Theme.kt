package org.organiza.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.organiza.android.R

/**
 * The achromatic ledger tokens dealt by taste-skill (ADR-0012), with the wireframe's face (ADR-0018):
 * crisp ink outlines on a white ground. Colour lives on data, not chrome.
 */
@Immutable
data class Palette(
    val ground: Color,
    val surface: Color,
    val raised: Color,
    val border: Color,
    val rule: Color,
    val control: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val data: Color,
    val positive: Color,
    val negative: Color,
    val pillNegativeBg: Color,
    val pillNegativeInk: Color,
    val pillPositiveBg: Color,
    val pillPositiveInk: Color,
)

// White paper and ink lines, as the wireframe draws them.
val LightPalette = Palette(
    ground = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFF1F1F1),
    border = Color(0xFF111111),
    rule = Color(0xFFE4E4E4),
    control = Color(0xFF111111),
    text = Color(0xFF0A0A0A),
    muted = Color(0xFF5F6368),
    primary = Color(0xFF141414),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFF1F1F1),
    data = Color(0xFF1D5FBF),
    positive = Color(0xFF2E7D32),
    negative = Color(0xFFC4242A),
    pillNegativeBg = Color(0xFFFFEDEA),
    pillNegativeInk = Color(0xFF831C1E),
    pillPositiveBg = Color(0xFFDCFAE5),
    pillPositiveInk = Color(0xFF04250E),
)

val DarkPalette = Palette(
    ground = Color(0xFF000000),
    surface = Color(0xFF0B0B0B),
    raised = Color(0xFF1E1E1E),
    border = Color(0xFFEDEDED),
    rule = Color(0xFF262626),
    control = Color(0xFFEDEDED),
    text = Color(0xFFFFFFFF),
    muted = Color(0xFFB4B4B4),
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFF222222),
    // The data hue lifted for 4.5:1 on #111.
    data = Color(0xFF6EA2F2),
    positive = Color(0xFF3FC07A),
    negative = Color(0xFFE5566F),
    pillNegativeBg = Color(0xFF2C1A20),
    pillNegativeInk = Color(0xFFE5566F),
    pillPositiveBg = Color(0xFF172A1D),
    pillPositiveInk = Color(0xFF3FC07A),
)

// Geist ships as one variable file, so each weight sets the axis explicitly.
@OptIn(ExperimentalTextApi::class)
private fun geist(weight: Int) = Font(
    R.font.geist,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Geist = FontFamily(geist(400), geist(500), geist(700))

@Immutable
data class Typography(
    val title: TextStyle,
    val figure: TextStyle,
    val section: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val small: TextStyle,
)

private val numbers = "tnum"

val Type = Typography(
    title = TextStyle(fontFamily = Geist, fontWeight = FontWeight(500), fontSize = 24.sp, lineHeight = 30.sp),
    figure = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight(400),
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = numbers,
    ),
    section = TextStyle(fontFamily = Geist, fontWeight = FontWeight(500), fontSize = 16.sp, lineHeight = 24.sp),
    body = TextStyle(fontFamily = Geist, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = numbers),
    bodyStrong = TextStyle(fontFamily = Geist, fontWeight = FontWeight(500), fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = numbers),
    small = TextStyle(fontFamily = Geist, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = numbers),
)

object Space {
    val margin = 16.dp
    val gap = 12.dp
    val pad = 16.dp
    val target = 44.dp
    val radiusCard = 16.dp
    val radiusControl = 12.dp
    /** The ink line on cards and controls. */
    val stroke = 1.5.dp
}

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun OrganizaTheme(content: @Composable () -> Unit) {
    val palette = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides palette, content = content)
}

object Theme {
    val colors: Palette @Composable get() = LocalPalette.current
}
