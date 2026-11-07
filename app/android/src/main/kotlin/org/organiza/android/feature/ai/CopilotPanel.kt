package org.organiza.android.feature.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.format.TextStyle
import java.util.Locale
import org.organiza.android.ui.Action
import org.organiza.android.ui.Actions
import org.organiza.android.ui.ButtonKind
import org.organiza.android.ui.Field
import org.organiza.android.ui.Panel
import org.organiza.android.ui.PanelHeader
import org.organiza.android.ui.Pill
import org.organiza.android.ui.Rule
import org.organiza.android.ui.Theme
import org.organiza.android.ui.Txt
import org.organiza.android.ui.Type
import org.organiza.android.ui.UiSnapshot

/** The copilot's proposals (ADR-0017). Nothing here changes the schedule by itself. */
@Composable
fun CopilotPanel(ui: UiSnapshot, actions: Actions) {
    val colors = Theme.colors
    val copilot = ui.copilot
    Panel {
        PanelHeader("Copilot") { copilot.ranOn?.let { Txt(it, Type.small, colors.muted) } }
        if (!ui.hasApiKey) {
            Txt(
                "Add your Groq key in Profile for weekly focus blocks and habit tips. Your hardest hours are worked out on this phone without one.",
                Type.small,
                colors.muted,
            )
            return@Panel
        }
        val advice = copilot.advice
        if (advice == null) {
            Txt("Proposes focus blocks around your calendar from when you get distracted, never from which apps.", Type.small, colors.muted)
        } else {
            advice.focusBlocks.forEachIndexed { index, block ->
                if (index > 0) Rule()
                val day = block.day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                val start = "%02d:%02d".format(block.startMinute / 60, block.startMinute % 60)
                Txt(block.label, Type.bodyStrong)
                Txt("$day $start · ${block.minutes} min", Type.small, colors.muted)
            }
            (advice.warnings + advice.habitTips).forEach { line ->
                Rule()
                Txt(line, Type.body)
            }
            Spacer(Modifier.height(8.dp))
            Txt("Suggestions only. Add the ones you want to your calendar with a #focus tag.", Type.small, colors.muted)
        }
        Spacer(Modifier.height(8.dp))
        Action(if (copilot.busy) "Thinking" else if (advice == null) "Ask the copilot" else "Ask again", ButtonKind.SECONDARY, enabled = !copilot.busy) {
            actions.runCopilot()
        }
    }
}

/** The BYOK key form, shared by onboarding and Profile (ADR-0009). */
@Composable
fun AiKeyPanel(ui: UiSnapshot, actions: Actions, title: String = "AI coach") {
    val colors = Theme.colors
    // Never rememberSaveable: a key must not reach saved instance state.
    var key by remember { mutableStateOf("") }
    Panel {
        PanelHeader(title) { Pill(if (ui.hasApiKey) "Key saved" else "No key", colors.raised, colors.text) }
        Txt(
            "Optional, with your own Groq key, kept in Android Keystore. What is sent: counts of blocked attempts by hour, " +
                "screen time by app category, habit names and results, calendar times, what you type to the copilot, and in " +
                "strict sessions the reason you type, with names, links and numbers removed where detected. App and website " +
                "names are never sent.",
            Type.small,
            colors.muted,
        )
        Spacer(Modifier.height(8.dp))
        Field(key, { key = it }, "Groq API key", secret = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("Save key", ButtonKind.SECONDARY, enabled = key.isNotBlank()) { actions.saveKey(key); key = "" }
            if (ui.hasApiKey) Action("Remove key", ButtonKind.DANGER) { actions.removeKey() }
        }
    }
}
