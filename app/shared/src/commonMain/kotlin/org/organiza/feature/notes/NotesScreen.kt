package org.organiza.feature.notes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.organiza.presentation.NoteRow
import org.organiza.ui.*

private val REMINDERS = listOf("No reminder", "Today", "Tomorrow")

/** Notes (ADR-0030): a journal and reminders, typed or dictated, by day and subject. */
@Composable
fun NotesScreen(ui: UiSnapshot, actions: Actions) {
    val notes = ui.notes
    val colors = Theme.colors
    TitleBlock("Notes", "Journal, thoughts and reminders. They stay on this device.")
    MessagePanel(ui.message)
    Composer(notes, actions)
    if (notes.model.subjects.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("All", if (notes.subject == null) ButtonKind.PRIMARY else ButtonKind.SECONDARY) { actions.filterNotes(null) }
            notes.model.subjects.forEach { subject ->
                Action(subject, if (notes.subject == subject) ButtonKind.PRIMARY else ButtonKind.SECONDARY) { actions.filterNotes(subject) }
            }
        }
    }
    if (notes.model.days.isEmpty()) {
        Panel { Txt(if (notes.subject == null) "No notes yet. Write or dictate your first one above." else "No notes under ${notes.subject}.", Type.body, colors.muted) }
    }
    notes.model.days.forEach { day ->
        Txt(day.label, Type.section, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        Panel {
            day.notes.forEachIndexed { index, note ->
                if (index > 0) Rule()
                NoteItem(note, notes.busy, actions)
            }
        }
    }
}

@Composable
private fun Composer(notes: NotesView, actions: Actions) {
    val colors = Theme.colors
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var remind by rememberSaveable { mutableStateOf(REMINDERS.first()) }
    var minute by rememberSaveable { mutableStateOf(9 * 60) }
    var listening by rememberSaveable { mutableStateOf(false) }
    // A new transcript joins whatever text is already typed; the title is only ever typed.
    LaunchedEffect(notes.dictatedAt) { notes.dictated?.let { body = listOf(body, it).filter(String::isNotBlank).joinToString(" ") } }
    Panel {
        PanelHeader("New note")
        Field(title, { title = it.take(80) }, "Title")
        Field(body, { body = it }, "Note: type it, or dictate it below", multiline = true)
        Field(subject, { subject = it }, "Subject, like Journal, Work or Errands")
        Spacer(Modifier.height(8.dp))
        Segmented(REMINDERS, remind, { it }) { remind = it }
        if (remind != REMINDERS.first()) ClockField("Remind me at", minute) { minute = it }
        notes.status?.let { Txt(it, Type.small, colors.muted, Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("Save note", ButtonKind.PRIMARY, enabled = body.isNotBlank() && !notes.busy) {
                actions.addNote(title, body, subject, REMINDERS.indexOf(remind).takeIf { it > 0 }?.minus(1), minute)
                title = ""
                body = ""
                subject = ""
                remind = REMINDERS.first()
            }
            Action(if (listening) "Stop and transcribe" else "Dictate", ButtonKind.SECONDARY, enabled = !notes.busy || listening) {
                if (listening) actions.stopNoteDictation() else actions.startNoteDictation()
                listening = !listening
            }
        }
        Txt("Dictation sends your voice to Groq on your key. Typed notes never leave this device.", Type.small, colors.muted, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun NoteItem(note: NoteRow, busy: Boolean, actions: Actions) {
    val colors = Theme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        note.title?.let { Txt(it, Type.bodyStrong, modifier = Modifier.padding(bottom = 2.dp)) }
        Txt(note.body, Type.body)
        Txt(listOfNotNull(note.time, note.subject, note.reminder).joinToString(" · "), Type.small, colors.muted, Modifier.padding(top = 4.dp))
        // A note cannot come back, so deleting takes a second tap.
        var deleting by remember(note.id) { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (deleting) {
                Action("Delete for good", ButtonKind.DANGER) { actions.deleteNote(note.id) }
                Action("Keep", ButtonKind.TEXT) { deleting = false }
            } else {
                Action("Organize with AI", ButtonKind.TEXT, enabled = !busy) { actions.organizeNote(note.id) }
                Action("Delete", ButtonKind.TEXT) { deleting = true }
            }
        }
    }
}
