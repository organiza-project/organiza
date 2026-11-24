package org.organiza.feature.onboarding

import org.organiza.ui.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import org.organiza.feature.sync.DeviceGuide
import org.organiza.ui.Glyph
import org.organiza.ui.Icon
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
import org.organiza.presentation.Permission
import org.organiza.presentation.OnboardingStep
import org.organiza.presentation.PermissionRow

/**
 * The first run (ADR-0014, ADR-0037): six screens, one theme each, so no screen asks for much at once.
 * Every step can be left for later, and Profile can run it again.
 */
@Composable
fun OnboardingScreen(ui: UiSnapshot, actions: Actions) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    BackHandler(enabled = step != OnboardingStep.WELCOME) { step = Onboarding.back(step) }
    fun advance() {
        step = Onboarding.next(step)
        if (step == OnboardingStep.DONE) actions.finishOnboarding()
    }
    // Each step fills the window, with its buttons at the bottom, like a page of its own.
    val window = LocalWindowInfo.current.containerSize.height
    val tall = with(LocalDensity.current) { window.toDp() } - 120.dp
    Column(Modifier.fillMaxWidth().heightIn(min = tall), verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.gap)) {
            Dots(step)
            when (step) {
                OnboardingStep.WELCOME -> Welcome()
                OnboardingStep.BLOCKING -> {
                    Hero(Glyph.SHIELD, "Pause what pulls you in", "When you open something you asked to avoid, Organiza steps in with a pause, not a wall.")
                    Permissions(ui, actions, step)
                }
                OnboardingStep.FURTHER -> {
                    Hero(Glyph.METRICS, "Go a step further", "See where your time goes, keep distracting sites away, and make Organiza hard to remove on a weak day.")
                    Permissions(ui, actions, step)
                }
                OnboardingStep.LOCKOUT -> {
                    Hero(Glyph.LOCK, "A firmer promise", "Optional, and off unless you turn it on. Use it if you know you talk yourself out of limits.")
                    org.organiza.feature.profile.LockoutPanel(ui, actions)
                }
                OnboardingStep.DEVICES -> {
                    Hero(Glyph.DEVICES, "Phone and computer, one picture", "Optional. Link your devices and check any one of them for all your screen time.")
                    DeviceGuide(ui.account)
                    BackendPanel(ui.account, actions)
                }
                OnboardingStep.AI -> {
                    Hero(Glyph.CHAT, "A coach that knows you", "Optional. Bring your own Groq key for a guide to your goals, or add one later in Profile.")
                    AiKeyPanel(ui, actions, title = "Groq key")
                }
                OnboardingStep.DONE -> Unit
            }
            MessagePanel(ui.message)
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step != OnboardingStep.WELCOME) Action("Back", ButtonKind.TEXT) { step = Onboarding.back(step) }
            Spacer(Modifier.weight(1f))
            val label = if (step == OnboardingStep.AI && ui.hasApiKey) "Finish" else Onboarding.continueLabel(step, ui.permissions, ui.device)
            Action(label, ButtonKind.PRIMARY) { advance() }
        }
    }
}

/** Where the person is: a dot a step, the current one long, read out as "Step n of 6". */
@Composable
private fun Dots(step: OnboardingStep) {
    val colors = Theme.colors
    val progress = Onboarding.progress(step)
    Row(Modifier.padding(top = 20.dp).semantics { contentDescription = progress }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OnboardingStep.entries.filter { it != OnboardingStep.DONE }.forEach { each ->
            val current = each == step
            Box(Modifier.size(width = if (current) 24.dp else 8.dp, height = 8.dp).background(if (each.ordinal <= step.ordinal) colors.text else colors.rule, CircleShape))
        }
    }
}

/** A step's large glyph, headline and one friendly sentence. */
@Composable
private fun Hero(glyph: Glyph, title: String, body: String) {
    val colors = Theme.colors
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp)) {
        Box(Modifier.size(80.dp).background(colors.data, CircleShape), contentAlignment = Alignment.Center) { Icon(glyph, colors.ground, 40.dp) }
        Txt(title, Type.figure, modifier = Modifier.padding(top = 20.dp).semantics { heading() })
        Txt(body, Type.body, colors.muted, Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Welcome() {
    val colors = Theme.colors
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        LogoMark(96.dp)
        Txt("Hi, this is Organiza", Type.figure, modifier = Modifier.padding(top = 20.dp).semantics { heading() })
        Txt("Your habits, notes and screen time in one calm place, on your own terms.", Type.section, colors.muted, Modifier.padding(top = 6.dp))
    }
    Panel {
        Point(Glyph.SHIELD, "Pauses, not walls", "A blocked app gets a pause and a reason. Every choice stays yours to look back on.")
        Rule()
        Point(Glyph.FLAME, "Good habits, kept", "Habits, notes and reminders, typed or dictated, with XP as you keep going.")
        Rule()
        Point(Glyph.LOCK, "Yours alone", "Everything is encrypted on this device and works with no account. Nothing tracks you.")
    }
}

@Composable
private fun Point(glyph: Glyph, title: String, body: String) {
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(glyph, Theme.colors.data, 24.dp)
        Column {
            Txt(title, Type.bodyStrong)
            Txt(body, Type.body, Theme.colors.muted)
        }
    }
}

@Composable
private fun Permissions(ui: UiSnapshot, actions: Actions, step: OnboardingStep) {
    Panel {
        Onboarding.permissions(ui.permissions, ui.device, step).forEachIndexed { index, row ->
            if (index > 0) Rule()
            PermissionItem(row, actions)
        }
    }
    Txt("Change any of these later in Profile.", Type.small, Theme.colors.muted)
}

@Composable
private fun PermissionItem(row: PermissionRow, actions: Actions) {
    val colors = Theme.colors
    // App blocking's steps are open from the start: on Android 13 and later most people need them.
    var help by rememberSaveable { mutableStateOf(row.permission == Permission.ACCESSIBILITY) }
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
            if (help && row.permission == Permission.ACCESSIBILITY) {
                Action("Open App info", ButtonKind.SECONDARY, modifier = Modifier.padding(top = 8.dp)) { actions.openAppInfo() }
            }
        }
    }
}
