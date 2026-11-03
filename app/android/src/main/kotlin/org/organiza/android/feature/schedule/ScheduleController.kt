package org.organiza.android.feature.schedule

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import org.organiza.ai.AiResult
import org.organiza.ai.GroqClient
import org.organiza.ai.Sanitizer
import org.organiza.ai.SuggestedBlock
import org.organiza.ai.UrlConnectionTransport
import org.organiza.android.OrganizaApp
import org.organiza.android.ui.ImportView
import org.organiza.scheduler.VoiceOutcome
import org.organiza.voice.VoiceCommand
import org.organiza.voice.VoicePipeline
import org.organiza.voice.VoiceResult

/**
 * The schedule import hub (ADR-0018). A photo is read on the phone with Tesseract and never leaves
 * it. Its text, a dictation or typed text goes through the sanitizer to the BYOK model, which returns
 * weekly blocks. Blocks become sessions on the focus list only when the person adds them, and only
 * their times are stored.
 */
class ScheduleController(private val app: OrganizaApp) {
    @Volatile var view: ImportView = ImportView()
        private set

    // OCR takes seconds of CPU, so it has its own thread.
    private val ocr = Executors.newSingleThreadExecutor()

    fun setText(text: String) {
        view = view.copy(source = text.take(MAX_TEXT), status = null)
        app.requestUiRefresh()
    }

    fun clear() {
        view = ImportView()
        app.requestUiRefresh()
    }

    fun readPhoto(uri: Uri) = busy("Reading the photo on this phone") {
        ocr.execute {
            val text = try {
                recognise(uri)
            } catch (failure: IOException) {
                Log.e(TAG, "photo read failed: ${failure.javaClass.simpleName}")
                null
            }
            view = if (text.isNullOrBlank()) {
                view.copy(busy = null, status = "No text was found in that photo")
            } else {
                view.copy(source = text.take(MAX_TEXT), blocks = emptyList(), busy = null, status = "Read on this phone. Check it, then extract")
            }
            app.requestUiRefresh()
        }
    }

    fun dictated(samples: ShortArray, sampleRate: Int) = busy("Transcribing") {
        app.onAi {
            val pipeline = VoicePipeline(app.keys, GroqClient(UrlConnectionTransport(), app.aiEndpoint), Sanitizer())
            view = when (val result = pipeline.transcript(samples, sampleRate)) {
                is VoiceResult.Transcript -> view.copy(source = result.text.take(MAX_TEXT), busy = null, status = null)
                VoiceResult.Silent -> view.copy(busy = null, status = "Nothing heard, nothing was sent")
                VoiceResult.TooLong -> view.copy(busy = null, status = "Too long, nothing was sent")
                is VoiceResult.Failed -> view.copy(busy = null, status = "Transcription failed: ${result.failure}")
                else -> view.copy(busy = null)
            }
            app.requestUiRefresh()
        }
    }

    fun extract() {
        val text = view.source
        if (text.isBlank()) return
        busy("Extracting with your AI key") {
            app.onAi {
                view = when (val result = app.coach().extractSchedule(text)) {
                    is AiResult.Ok -> view.copy(
                        blocks = result.value,
                        busy = null,
                        status = if (result.value.isEmpty()) "No times were found" else null,
                    )
                    is AiResult.Failed -> view.copy(busy = null, status = "Extraction failed: ${result.failure}")
                }
                app.requestUiRefresh()
            }
        }
    }

    /** Adds each block as a session on the focus list, at its next weekly time. */
    fun add() {
        val blocks = view.blocks
        if (blocks.isEmpty()) return
        app.onStores { stores ->
            app.hub.focusRule(stores)
            val zone = ZoneId.systemDefault()
            val now = app.clock.now().atZone(zone)
            val added = blocks.count { block -> stores.voice.apply(VoiceCommand.AddSession("focus", next(block, now), block.minutes)) == VoiceOutcome.Done }
            view = ImportView(status = "Added $added of ${blocks.size} to your agenda")
            app.requestUiRefresh()
        }
    }

    private fun next(block: SuggestedBlock, now: ZonedDateTime): java.time.Instant {
        var day: LocalDate = now.toLocalDate()
        while (true) {
            val start = day.atStartOfDay(now.zone).plusMinutes(block.startMinute.toLong())
            if (day.dayOfWeek == block.day && start.isAfter(now)) return start.toInstant()
            day = day.plusDays(1)
        }
    }

    private fun busy(label: String, work: () -> Unit) {
        view = view.copy(busy = label, status = null)
        app.requestUiRefresh()
        work()
    }

    private fun recognise(uri: Uri): String? {
        val bitmap = decode(uri) ?: return null
        val tess = TessBaseAPI()
        return try {
            if (!tess.init(tessData().absolutePath, "eng")) return null
            tess.setImage(bitmap)
            tess.utF8Text
        } finally {
            tess.recycle()
            bitmap.recycle()
        }
    }

    /** Shrinks the photo so its long side is about 2000 pixels, which Tesseract reads well and fast. */
    private fun decode(uri: Uri): Bitmap? {
        val resolver = app.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LONG_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    /** Tesseract reads its model from a folder named tessdata, so the bundled one is copied out once. */
    private fun tessData(): File {
        val root = File(app.noBackupFilesDir, "ocr")
        val model = File(root, "tessdata/eng.traineddata")
        if (!model.exists()) {
            model.parentFile?.mkdirs()
            app.assets.open("tessdata/eng.traineddata").use { input -> model.outputStream().use(input::copyTo) }
        }
        return root
    }

    private companion object {
        const val TAG = "Organiza"
        const val MAX_TEXT = 4000
        const val LONG_SIDE = 2000
    }
}
