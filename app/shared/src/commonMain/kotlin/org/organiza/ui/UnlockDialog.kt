package org.organiza.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.organiza.presentation.Lockout

/** The lockout (ADR-0032): the change waits until the essay is typed, one letter at a time. */
@Composable
fun UnlockDialog(reason: String, actions: Actions) {
    val colors = Theme.colors
    // Remembered only while open: the typed essay is never saved.
    var typed by remember(reason) { mutableStateOf("") }
    Dialog(onDismissRequest = { actions.cancelUnlock() }) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().background(colors.ground, RoundedCornerShape(Space.radiusCard)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Txt(reason, Type.section)
            Txt("Lockout is on. Type the text below, letter by letter. Pasting does not work.", Type.small, colors.muted)
            Txt(Lockout.ESSAY, Type.body)
            Field(typed, { typed = Lockout.accept(typed, it) }, "Type it here")
            Txt("${typed.length} of ${Lockout.ESSAY.length} letters", Type.small, colors.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Make the change", ButtonKind.DANGER, enabled = Lockout.done(typed)) { actions.unlock(typed) }
                Action("Keep my rules", ButtonKind.PRIMARY) { actions.cancelUnlock() }
            }
        }
    }
}
