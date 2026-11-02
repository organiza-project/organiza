package org.organiza.android.feature.vault

import org.organiza.android.feature.ai.AiKeyPanel
import org.organiza.android.feature.sync.BackendPanel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.organiza.android.ui.*

@Composable
fun VaultScreen(ui: UiSnapshot, actions: Actions) {
    val colors = Theme.colors
    TitleBlock("Vault", "Settings and what this phone keeps. Protection lives on the Focus screen.")
    ui.storageProblem?.let { Panel { Txt(it, Type.body, colors.negative) } }
    MessagePanel(ui.message)
    Panel {
        PanelHeader("On this phone")
        ui.stored.forEach { (label, value) -> KeyValue(label, value) }
        Rule()
        Txt(
            "Encrypted with a key held by Android Keystore. App use, websites and calendar titles never leave the phone.",
            Type.small,
            colors.muted,
        )
    }
    AiKeyPanel(ui, actions)
    Panel {
        PanelHeader("Voice")
        SwitchRow(
            "Send recordings to Groq",
            "Recordings cannot have names removed first. Organiza keeps no audio or transcripts",
            ui.voiceConsent,
        ) { actions.setVoiceConsent(it) }
    }
    Panel {
        PanelHeader("Calendar")
        Txt("Events tagged #focus or #deepwork become sessions. Only times are stored, never titles.", Type.small, colors.muted)
        Spacer(Modifier.height(8.dp))
        Action("Import an .ics file", ButtonKind.SECONDARY) { actions.importCalendar() }
    }
    BackendPanel(ui.account, actions)
    Panel {
        PanelHeader("Setup")
        Txt("Walk through permissions, the backend and the AI key again.", Type.small, colors.muted)
        Spacer(Modifier.height(8.dp))
        Action("Run setup again", ButtonKind.SECONDARY) { actions.restartOnboarding() }
    }
}
