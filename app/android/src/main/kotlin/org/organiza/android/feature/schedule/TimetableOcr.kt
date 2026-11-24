package org.organiza.android.feature.schedule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/** Reads a timetable photo on the phone (ADR-0018). The photo never leaves it. */
class TimetableOcr(private val context: Context) {
    /** The text in the photo at [uri], read on the phone with Tesseract, or null when there is none. */
    fun recognise(uri: Uri): String? {
        val bitmap = decode(uri) ?: return null
        val tess = TessBaseAPI()
        val (root, languages) = tessData()
        return try {
            if (!tess.init(root.absolutePath, languages)) return null
            tess.setImage(bitmap)
            tess.utF8Text
        } finally {
            tess.recycle()
            bitmap.recycle()
        }
    }

    /** Shrinks the photo so its long side is about 2000 pixels, which Tesseract reads well and fast. */
    private fun decode(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LONG_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    /**
     * Tesseract reads its models from a folder named tessdata, so the bundled ones (English and Arabic) are
     * copied out once. Returns that folder and the languages to read with, such as `eng+ara`.
     */
    private fun tessData(): Pair<File, String> {
        val root = File(context.noBackupFilesDir, "ocr")
        val names = context.assets.list("tessdata").orEmpty().filter { it.endsWith(".traineddata") }.sorted()
        names.forEach { name ->
            val model = File(root, "tessdata/$name")
            if (!model.exists()) {
                model.parentFile?.mkdirs()
                context.assets.open("tessdata/$name").use { input -> model.outputStream().use(input::copyTo) }
            }
        }
        // English first: it is the language Tesseract falls back on for digits and times.
        return root to names.map { it.removeSuffix(".traineddata") }.sortedBy { it != "eng" }.joinToString("+")
    }

    private companion object {
        const val LONG_SIDE = 2000
    }
}
