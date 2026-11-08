package org.organiza.feature.blocker

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberAppIcon(pkg: String): ImageBitmap? {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.Default) {
            try {
                val drawable = context.packageManager.getApplicationIcon(pkg)
                val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, ICON_PX, ICON_PX)
                drawable.draw(Canvas(bitmap))
                bitmap.asImageBitmap()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
    return icon
}

private const val ICON_PX = 96
