package org.organiza.feature.onboarding

import org.organiza.ui.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.organiza.feature.ai.AiKeyPanel
import org.organiza.feature.sync.BackendPanel
import org.organiza.ui.Action
import org.organiza.ui.Actions
import org.organiza.ui.ButtonKind
import org.organiza.ui.LogoMark
import org.organiza.ui.MessagePanel
import org.organiza.ui.Panel
import org.organiza.ui.PanelHeader
import org.organiza.ui.Rule
import org.organiza.ui.Space
import org.organiza.ui.Theme
import org.organiza.ui.TitleBlock
import org.organiza.ui.Txt
import org.organiza.ui.Type
import org.organiza.ui.UiSnapshot
import org.organiza.presentation.Onboarding
import org.organiza.presentation.OnboardingStep
import org.organiza.presentation.PermissionRow

/** The first run (ADR-0014). Every step can be left for later, and Profile can run it again. */
@Composable
fun OnboardingScreen(ui: UiSnapshot, actions: Actions) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    BackHandler(enabled = step != OnboardingStep.WELCOME) { step = Onboarding.back(step) }
    fun advance() {
        step = Onboarding.next(step)
        if (step == OnboardingStep.DONE) actions.finishOnboarding()
    }
    Txt(Onboarding.progress(step), Type.small, Theme.colors.muted, Modifier.padding(top = 20.dp))
    when (step) {
        OnboardingStep.WELCOME -> Welcome()
        OnboardingStep.PERMISSIONS -> PermissionsStep(ui, actions)
        OnboardingStep.BACKEND -> {
            TitleBlock("Where your data lives", "Everything works offline. A backend is only for challenges with friends.")
            BackendPanel(ui.account, actions)
        }
        OnboardingStep.AI -> {
            TitleBlock("AI coach", "Optional. Bring your own Groq key, or skip and add one later in Profile.")
            AiKeyPanel(ui, actions, title = "Groq key")
        }
        OnboardingStep.DONE -> Unit
    }
    MessagePanel(ui.message)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val label = if (step == OnboardingStep.AI && ui.hasApiKey) "Finish" else Onboarding.continueLabel(step, ui.permissions, ui.device)
        Action(label, ButtonKind.PRIMARY) { advance() }
        if (step != OnboardingStep.WELCOME) Action("Back", ButtonKind.TEXT) { step = Onboarding.back(step) }
    }
}

@Composable
private fun Welcome() {
    val colors = Theme.colors
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.Start) {
        LogoMark()
        Spacer(Modifier.height(20.dp))
        Txt("Organiza", Type.figure, modifier = Modifier.semantics { heading() })
        Txt("Focus on purpose, on your own terms.", Type.section, colors.muted)
    }
    Panel {
        Point("Local first", "Sessions, habits and what you block live encrypted on this device. It all works with no account and no network.")
        Rule()
        Point("Private by design", "No tracking and no analytics. App names, websites and calendar titles never leave this device.")
        Rule()
        Point(
            "Mindful friction",
            "A blocked app does not slam a wall in your face. You get a pause, a reason and a phrase to type, and every choice is yours to look back on.",
        )
    }
}

@Composable
private fun Point(title: String, body: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Txt(title, Type.bodyStrong)
        Txt(body, Type.body, Theme.colors.muted)
    }
}

@Composable
private fun PermissionsStep(ui: UiSnapshot, actions: Actions) {
    TitleBlock("Permissions", "What each one is for. Grant them now or later from the Focus screen.")
    Panel {
        Onboarding.permissions(ui.permissions, ui.device).forEachIndexed { index, row ->
            if (index > 0) Rule()
            PermissionItem(row, actions)
        }
    }
}

@Composable
private fun PermissionItem(row: PermissionRow, actions: Actions) {
    val colors = Theme.colors
    var help by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = Space.target), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Txt(row.permission.title + if (row.required || !row.grantable) "" else " · optional", Type.bodyStrong)
            }
            when {
                row.granted -> Txt("On", Type.body, colors.muted)
                row.grantable -> Action("Allow", ButtonKind.SECONDARY) { actions.grant(row.permission) }
                else -> Txt("With app blocking", Type.small, colors.muted)
            }
        }
        Txt(row.permission.why, Type.small, colors.muted)
        if (!row.granted && row.grantable) {
            // A quiet link: five bold ones would outweigh the permission titles.
            Txt(
                if (help) "Hide help" else "Can't turn it on?",
                Type.small,
                colors.muted,
                Modifier
                    .heightIn(min = Space.target)
                    .clickable(role = Role.Button) { help = !help }
                    .wrapContentHeight(Alignment.CenterVertically),
            )
            if (help) Txt(row.permission.fallback, Type.small, colors.text)
        }
    }
}
