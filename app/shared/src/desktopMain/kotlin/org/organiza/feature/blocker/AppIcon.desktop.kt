package org.organiza.feature.blocker

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

// shortcut: no icon until Linux reads .desktop entries (Stage 2), so the row shows the blank tile.
@Composable
internal actual fun rememberAppIcon(pkg: String): ImageBitmap? = null
