package org.organiza.feature.blocker

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun appIconLoader(): (String) -> ImageBitmap? {
    val packages = LocalContext.current.packageManager
    return { pkg ->
        try {
            val drawable = packages.getApplicationIcon(pkg)
            val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, ICON_PX, ICON_PX)
            drawable.draw(Canvas(bitmap))
            bitmap.asImageBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}

private const val ICON_PX = 96
