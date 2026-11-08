package org.organiza.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.organiza.shared.R

@OptIn(ExperimentalTextApi::class)
internal actual fun geist(weight: Int): Font = Font(
    R.font.geist,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@Composable
internal actual fun is24HourClock(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

@Composable
internal actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) =
    androidx.activity.compose.BackHandler(enabled, onBack)
