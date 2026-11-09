package org.organiza.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.organiza.feature.ai.AiKeyPanel
import org.organiza.feature.sync.AccountPanel
import org.organiza.feature.sync.BackendPanel
import org.organiza.ui.*
import org.organiza.sync.BackendKind

/** Profile (wireframe screen 9): level, XP and badges from this phone, then the settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(ui: UiSnapshot, actions: Actions) {
    val colors = Theme.colors
    val progression = ui.progression
    var showBackend by rememberSaveable { mutableStateOf(false) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(top = 24.dp).wrapContentWidth(Alignment.CenterHorizontally)) {
        Box(contentAlignment = Alignment.Center) {
            Dial(progression?.fraction ?: 0.0, "", "", 112.dp)
            LogoMark(56.dp)
        }
    }
    if (progression != null) {
        Txt(progression.title, Type.title.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), modifier = Modifier.fillMaxWidth().semantics { heading() })
        Panel {
            Row(Modifier.fillMaxWidth()) {
                Txt("XP", Type.bodyStrong, modifier = Modifier.weight(1f))
                Txt("${progression.xp} · ${progression.toNext} to the next level", Type.small, colors.muted)
            }
            Spacer(Modifier.height(8.dp))
            ProgressPill(progression.fraction, ink = colors.data)
            Txt("Earned on this device from habits, focus time and staying focused. Never shared.", Type.small, colors.muted, Modifier.padding(top = 8.dp))
        }
        Panel {
            PanelHeader("Badges") { Txt("${progression.badges.count { it.earned }} of ${progression.badges.size}", Type.small, colors.muted) }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                progression.badges.forEach { badge ->
                    val glyph = when (badge.id) {
                        "first-session" -> Glyph.CLOCK
                        "week-streak", "month-streak" -> Glyph.FLAME
                        "held-the-line" -> Glyph.SHIELD
                        "team-player" -> Glyph.ARENA
                        else -> Glyph.CROWN
                    }
                    Medallion(glyph, badge.title, badge.earned)
                }
            }
        }
    }
    MessagePanel(ui.message)
    Panel {
        val account = ui.account
        SwitchRow(
            "Supabase cloud sync",
            when {
                account.signedInAs != null -> "Signed in as ${account.signedInAs}"
                account.kind != null -> "On. Sign in below to sync challenges"
                else -> "Off: challenges use a relay you enter"
            },
            account.kind != null,
        ) { on ->
            if (on && account.hostedAvailable) actions.chooseBackend(BackendKind.HOSTED, "", "") else if (!on) actions.chooseBackend(null, "", "")
            // Without a hosted project in this build, turning sync on means choosing a backend first.
            showBackend = on && !account.hostedAvailable
        }
        Action(if (showBackend) "Hide backend settings" else "Backend settings", ButtonKind.TEXT) { showBackend = !showBackend }
        Rule()
        SwitchRow("API configuration", if (ui.hasApiKey) "Groq key saved in the system keystore" else "No Groq key", ui.hasApiKey) { on ->
            if (!on && ui.hasApiKey) actions.removeKey() else showKey = true
        }
        Action(if (showKey) "Hide key settings" else "Key settings", ButtonKind.TEXT) { showKey = !showKey }
    }
    if (showBackend) BackendPanel(ui.account, actions, withAccount = false)
    if (ui.account.kind != null) AccountPanel(ui.account, actions)
    if (showKey) AiKeyPanel(ui, actions)
    VaultPanels(ui, actions)
}

@Composable
private fun VaultPanels(ui: UiSnapshot, actions: Actions) {
    val colors = Theme.colors
    Panel {
        PanelHeader("On this device")
        ui.stored.forEach { (label, value) -> KeyValue(label, value) }
        Rule()
        Txt("Encrypted with a key held by the system keystore. App names, websites and calendar titles never leave this device.", Type.small, colors.muted)
    }
    Panel {
        PanelHeader("Voice")
        SwitchRow("Send recordings to Groq", "Recordings cannot have names removed first. Organiza keeps no audio or transcripts", ui.voiceConsent) {
            actions.setVoiceConsent(it)
        }
    }
    Panel {
        PanelHeader("Calendar")
        Txt("Events tagged #focus or #deepwork become sessions. Only times are stored, never titles.", Type.small, colors.muted)
        Spacer(Modifier.height(8.dp))
        Action("Import an .ics file", ButtonKind.SECONDARY) { actions.importCalendar() }
    }
    Panel {
        PanelHeader("Setup")
        Txt("Walk through permissions, the backend and the AI key again.", Type.small, colors.muted)
        Spacer(Modifier.height(8.dp))
        Action("Run setup again", ButtonKind.SECONDARY) { actions.restartOnboarding() }
    }
}
