package org.organiza.feature.notes

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.organiza.ai.AiResult
import org.organiza.ai.GroqClient
import org.organiza.ai.Sanitizer
import org.organiza.ai.UrlConnectionTransport
import org.organiza.app.OrganizaCore
import org.organiza.presentation.NotesModel
import org.organiza.storage.Note
import org.organiza.ui.NotesView
import org.organiza.voice.VoicePipeline
import org.organiza.voice.VoiceResult

/** Notes typed or dictated, by day and subject, with AI only when asked about one note (ADR-0030). */
class NotesController(private val app: OrganizaCore) {
    @Volatile private var subject: String? = null
    @Volatile private var status: String? = null
    @Volatile private var busy = false
    @Volatile private var dictated: String? = null
    @Volatile private var dictatedAt = 0L

    /** Storage thread. */
    fun view(stores: OrganizaCore.Storage, today: LocalDate, zone: ZoneId): NotesView =
        NotesView(NotesModel.build(stores.notes.all(), subject, today, zone), subject, status, busy, dictated, dictatedAt)

    fun filter(subject: String?) {
        this.subject = subject
        app.requestUiRefresh()
    }

    /** Saves a note; [remindDay] is 0 for today or 1 for tomorrow, at [minute] of the day, or null for none. */
    fun add(title: String, body: String, subject: String, remindDay: Int?, minute: Int) = app.onStores { stores ->
        val text = body.trim()
        if (text.isEmpty()) return@onStores say("Write or dictate something first")
        val zone = ZoneId.systemDefault()
        val now = app.clock.now()
        val remindAt = remindDay?.let { now.atZone(zone).toLocalDate().plusDays(it.toLong()).atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant() }
        if (remindAt != null && !remindAt.isAfter(now)) return@onStores say("That time has already passed today. Choose a later one")
        stores.notes.add(Note(UUID.randomUUID().toString(), text, subject.trim().take(MAX_SUBJECT).ifEmpty { null }, now, remindAt, title.trim().take(MAX_TITLE).ifEmpty { null }))
        dictated = null
        say(if (remindAt == null) "Note saved" else "Note saved. You will be reminded")
        app.rescheduleReminders()
    }

    fun delete(id: String) = app.onStores { stores ->
        stores.notes.delete(id)
        say("Note deleted")
        app.rescheduleReminders()
    }

    /** Sends this one note, sanitized, to the person's own Groq key for a subject and a reminder time. */
    fun organize(id: String) = app.onStores { stores ->
        val note = stores.notes.all().firstOrNull { it.id == id } ?: return@onStores
        if (!app.hasApiKey()) return@onStores say("Add your Groq key in Profile to organize notes with AI")
        busy = true
        say("Organizing with your AI key")
        val zone = ZoneId.systemDefault()
        app.onAi {
            val result = app.coach().organizeNote(note.body, app.clock.now().atZone(zone).toLocalDate())
            app.onStores { current ->
                busy = false
                when (result) {
                    is AiResult.Ok -> {
                        val at = result.value.remindAt?.atZone(zone)?.toInstant()?.takeIf { it.isAfter(app.clock.now()) }
                        current.notes.update(note.id, note.body, result.value.subject, at ?: note.remindAt, note.title)
                        say("Filed under ${result.value.subject}" + if (at != null) ", with a reminder" else "")
                        app.rescheduleReminders()
                    }
                    is AiResult.Failed -> say("The AI could not organize this note: ${result.failure}. Nothing changed")
                }
            }
        }
    }

    fun dictated(samples: ShortArray, sampleRate: Int) {
        busy = true
        say("Transcribing")
        app.onAi {
            val pipeline = VoicePipeline(app.keys, GroqClient(UrlConnectionTransport(), app.aiEndpoint), Sanitizer())
            busy = false
            when (val result = pipeline.transcript(samples, sampleRate)) {
                is VoiceResult.Transcript -> {
                    dictated = result.text
                    dictatedAt = System.nanoTime()
                    say("Check the text, then save the note")
                }
                VoiceResult.Silent -> say("Nothing heard, nothing was sent")
                VoiceResult.TooLong -> say("Too long, nothing was sent. Keep a note under two minutes")
                is VoiceResult.Failed -> say("Dictation failed: ${result.failure}. Type the note instead")
                else -> say(null)
            }
        }
    }

    private fun say(text: String?) {
        status = text
        app.requestUiRefresh()
    }

    private companion object {
        const val MAX_SUBJECT = 40
        const val MAX_TITLE = 80
    }
}
