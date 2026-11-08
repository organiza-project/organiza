package org.organiza.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.text.DateFormat
import java.text.SimpleDateFormat

private val geistBytes: ByteArray by lazy {
    checkNotNull(Palette::class.java.getResourceAsStream("/font/geist.ttf")) { "font/geist.ttf is missing" }.use { it.readBytes() }
}

internal actual fun geist(weight: Int): Font = Font(
    "geist-$weight",
    geistBytes,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

// The locale's short time pattern says whether it counts hours to 24 (H) or to 12 (h).
@Composable
internal actual fun is24HourClock(): Boolean =
    (DateFormat.getTimeInstance(DateFormat.SHORT) as? SimpleDateFormat)?.toPattern()?.contains('H') ?: true

// Every screen opened on top of a tab has its own back button, which is how a desktop goes back.
@Composable
internal actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
