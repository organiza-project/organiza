package org.organiza.feature.schedule

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
import org.organiza.app.OrganizaCore
import org.organiza.ui.ImportView
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
class ScheduleController(private val app: OrganizaCore) {
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

    /** Reads a timetable photo with [read], on this device only, then shows its text for checking. */
    fun readPhoto(read: () -> String?) = busy("Reading the photo on this device") {
        ocr.execute {
            val text = try {
                read()
            } catch (failure: IOException) {
                app.platform.log("photo read failed: ${failure.javaClass.simpleName}")
                null
            }
            view = if (text.isNullOrBlank()) {
                view.copy(busy = null, status = "No text was found in that photo")
            } else {
                view.copy(source = text.take(MAX_TEXT), blocks = emptyList(), busy = null, status = "Read on this device. Check it, then extract")
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

    private companion object {
        const val MAX_TEXT = 4000
    }
}
