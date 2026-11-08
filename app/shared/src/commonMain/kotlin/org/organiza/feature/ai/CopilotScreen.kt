package org.organiza.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.organiza.ui.*

private val STARTERS = listOf(
    "How can I spend less time on my phone?",
    "Log 30 minutes of reading",
    "Schedule a meeting with Sam tomorrow at 2 pm",
    "Create a new daily habit for coding",
)

/** The copilot chat (ADR-0020): type or hold the mic, and confirm any change it proposes. */
@Composable
fun CopilotScreen(ui: UiSnapshot, actions: Actions, back: () -> Unit) {
    val colors = Theme.colors
    val chat = ui.chat
    var draft by rememberSaveable { mutableStateOf("") }
    TopBar("Copilot", back)
    Txt(
        "Ask about your time, or tell it what to do. Usage is sent by category only, never by app or site, and the chat is forgotten when Organiza closes.",
        Type.small,
        colors.muted,
    )
    if (chat.turns.isEmpty()) {
        Panel {
            PanelHeader("Try")
            STARTERS.forEach { line -> Action(line, ButtonKind.TEXT) { actions.sendChat(line) } }
        }
    }
    chat.turns.forEach { turn -> Bubble(turn.text, turn.fromUser) }
    if (chat.busy) Bubble("Thinking…", fromUser = false)
    if (ui.pendingCommand != null) {
        Panel {
            Txt(ui.pendingCommand, Type.bodyStrong)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Confirm", ButtonKind.PRIMARY) { actions.confirmCommand() }
                Action("Discard", ButtonKind.TEXT) { actions.discardCommand() }
            }
        }
    }
    MessagePanel(ui.message)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { Field(draft, { draft = it.take(500) }, "Message the copilot") }
        MicButton(actions)
        RoundButton(Glyph.SEND, "Send", enabled = draft.isNotBlank() && !chat.busy) {
            actions.sendChat(draft)
            draft = ""
        }
    }
    if (chat.turns.isNotEmpty()) Action("Clear chat", ButtonKind.TEXT) { actions.clearChat() }
}

@Composable
private fun Bubble(text: String, fromUser: Boolean) {
    val colors = Theme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .widthIn(max = 520.dp)
                .padding(start = if (fromUser) 48.dp else 0.dp, end = if (fromUser) 0.dp else 48.dp)
                .background(if (fromUser) colors.primary else colors.surface, shape)
                .then(if (fromUser) Modifier else Modifier.border(Space.stroke, colors.rule, shape))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) { Txt(text, Type.body, if (fromUser) colors.onPrimary else colors.text) }
    }
}

@Composable
private fun RoundButton(glyph: Glyph, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = Theme.colors
    Box(
        Modifier
            .size(Space.target + 4.dp)
            .background(if (enabled) colors.primary else colors.surface, CircleShape)
            .border(Space.stroke, colors.border, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(glyph, if (enabled) colors.onPrimary else colors.muted) }
}

/** Hold to dictate. The words land in the chat as a message once released. */
@Composable
private fun MicButton(actions: Actions) {
    val colors = Theme.colors
    var held by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(Space.target + 4.dp)
            .background(if (held) colors.data else colors.surface, CircleShape)
            .border(Space.stroke, colors.border, CircleShape)
            .semantics { contentDescription = if (held) "Listening. Release to send" else "Hold to speak" }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    held = true
                    actions.startChatVoice()
                    tryAwaitRelease()
                    held = false
                    actions.stopChatVoice()
                })
            },
        contentAlignment = Alignment.Center,
    ) { Icon(Glyph.MIC, if (held) colors.onPrimary else colors.text) }
}
