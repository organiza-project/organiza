package org.organiza.android.feature.focus

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.organiza.android.ui.*
import org.organiza.presentation.BarKind
import org.organiza.presentation.ChoicesModel
import org.organiza.presentation.Format
import org.organiza.presentation.FocusState
import org.organiza.presentation.ProtectionItem
import org.organiza.storage.FocusTier

/** The session card on Home: what is blocked and until when, with start or stop (ADR-0018). */
@Composable
fun FocusCard(ui: UiSnapshot, actions: Actions, open: (Route) -> Unit) {
    val focus = ui.focus ?: return
    val colors = Theme.colors
    var starting by rememberSaveable { mutableStateOf(false) }
    Panel {
        PanelHeader("Focus") {
            if (focus.state == FocusState.STRICT) Pill("Strict", colors.raised, colors.text)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Txt(if (focus.state == FocusState.OFF) "Blocking" else "Time left", Type.small, colors.muted)
                Txt(focus.headline, Type.figure, if (focus.state == FocusState.OFF) colors.muted else colors.text)
            }
        }
        Txt(focus.subline, Type.small, colors.muted)
        Spacer(Modifier.height(12.dp))
        DayStripView(focus.strip, "Today's sessions. " + focus.subline)
        val kinds = focus.strip.bars.map { it.kind }.toSet()
        val legend = listOfNotNull(
            (colors.data to "Now").takeIf { BarKind.NOW in kinds },
            (colors.text to "Later").takeIf { BarKind.LATER in kinds },
            (colors.control to "Done").takeIf { BarKind.PAST in kinds },
        )
        if (legend.isNotEmpty()) Legend(*legend.toTypedArray())
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (focus.state == FocusState.OFF) {
                Action("Start a session", ButtonKind.PRIMARY) { starting = !starting }
            } else if (ui.canStop) {
                Action("Stop session", ButtonKind.SECONDARY) { actions.stopSession() }
            }
            Action("Calendar", ButtonKind.TEXT) { open(Route.AGENDA) }
            Action("Blocker hub", ButtonKind.TEXT) { open(Route.BLOCKER) }
        }
        if (starting && focus.state == FocusState.OFF) {
            Rule()
            StartForm(ui, actions, open) { starting = false }
        }
        if (focus.state != FocusState.OFF) {
            Rule()
            BlockedNow(ui)
        } else {
            ui.monitor?.let { Rule(); MonitorSummary(it, ui.hasApiKey, actions) }
        }
    }
}

/** Where the time went in the last monitor-only session, with an optional AI take on it. */
@Composable
private fun MonitorSummary(monitor: MonitorView, hasKey: Boolean, actions: Actions) {
    val colors = Theme.colors
    Txt("Last monitor session · ${monitor.minutes} min", Type.bodyStrong)
    if (monitor.apps.isEmpty()) Txt("No app time recorded. Allow usage access to see it.", Type.small, colors.muted)
    monitor.apps.forEach { (label, minutes) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Txt(label, Type.body, modifier = Modifier.weight(1f), maxLines = 1)
            Txt("$minutes min", Type.small, colors.muted)
        }
    }
    when {
        monitor.summary != null -> Txt(monitor.summary, Type.body, modifier = Modifier.padding(top = 6.dp))
        hasKey -> Action(if (monitor.busy) "Thinking" else "Guilt-free summary", ButtonKind.TEXT, enabled = !monitor.busy) {
            actions.summarizeMonitor()
        }
    }
}

@Composable
private fun StartForm(ui: UiSnapshot, actions: Actions, open: (Route) -> Unit, close: () -> Unit) {
    val colors = Theme.colors
    var minutes by rememberSaveable { mutableStateOf(45) }
    var strict by rememberSaveable { mutableStateOf(false) }
    var shortForm by rememberSaveable { mutableStateOf(false) }
    var tier by rememberSaveable { mutableStateOf(FocusTier.STANDARD) }
    val listed = ui.hub.apps.count { it.onList } + ui.hub.sites.size
    Txt("How hard should it block?", Type.bodyStrong, modifier = Modifier.padding(bottom = 6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceRow("Total lockdown", "Every app is paused except calls, messages and the home screen", tier == FocusTier.LOCKDOWN) {
            tier = FocusTier.LOCKDOWN
        }
        ChoiceRow(
            "Standard focus",
            "Blocks your focus list: ${Format.count(listed, "app or site", "apps and sites")}",
            tier == FocusTier.STANDARD,
        ) { tier = FocusTier.STANDARD }
        ChoiceRow("Monitor only", "Blocks nothing. See where your time went afterwards, without judgement", tier == FocusTier.MONITOR) {
            tier = FocusTier.MONITOR
        }
    }
    if (tier == FocusTier.STANDARD) Action("Edit the focus list", ButtonKind.TEXT) { open(Route.BLOCKER) }
    Spacer(Modifier.height(8.dp))
    Stepper(minutes / 15, "× 15 min = $minutes min", 1..96) { minutes = it * 15 }
    if (tier != FocusTier.MONITOR) {
        SwitchRow("Strict", "Cannot be stopped early, and opening a blocked app takes a longer pause. Up to 24 hours", strict) { strict = it }
        SwitchRow("Shorts and Reels", "Block every short video feed for this session", shortForm) { shortForm = it }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Action("Start", ButtonKind.PRIMARY) {
            val blocking = tier != FocusTier.MONITOR
            actions.startFocus(minutes, strict && blocking, shortForm && blocking, tier)
            close()
        }
        Action("Cancel", ButtonKind.TEXT, onClick = close)
    }
    if (tier == FocusTier.STANDARD && listed == 0) Txt("Your focus list is empty, so only short videos can be blocked.", Type.small, colors.muted)
}

@Composable
private fun BlockedNow(ui: UiSnapshot) {
    val colors = Theme.colors
    if (ui.lockdown) {
        Txt("Total lockdown", Type.bodyStrong)
        val open = (listOf("Phone", "Home screen") + ui.blockedApps).joinToString()
        Txt("Every app is paused. Still open: $open.", Type.body, colors.muted)
        return
    }
    if (ui.monitoring && ui.blockedApps.isEmpty() && ui.blockedSites.isEmpty()) {
        Txt("Monitor only", Type.bodyStrong)
        Txt("Nothing is blocked. The apps you use are counted on this phone and shown when the session ends.", Type.body, colors.muted)
        return
    }
    Txt("Blocked now", Type.bodyStrong)
    val rows = ui.blockedApps.map { it to "App" } + ui.blockedSites.map { it to "Website and its subdomains" }
    if (rows.isEmpty()) Txt("This session's list is empty, so nothing is blocked.", Type.body, colors.muted)
    rows.forEach { (name, kind) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Txt(name, Type.body, modifier = Modifier.weight(1f), maxLines = 1)
            Txt(kind, Type.small, colors.muted)
        }
    }
}

@Composable
fun ProtectionPanel(ui: UiSnapshot, actions: Actions) {
    val focus = ui.focus ?: return
    val colors = Theme.colors
    Panel {
        PanelHeader("Protection") { Txt(focus.protectionSummary, Type.small, colors.muted) }
        focus.protection.forEachIndexed { index, item ->
            if (index > 0) Rule()
            ProtectionRow(item, actions)
        }
        Rule()
        Txt(focus.tamperLine, Type.small, colors.muted)
    }
}

@Composable
private fun ProtectionRow(item: ProtectionItem, actions: Actions) {
    val colors = Theme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = Space.target), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Txt(item.kind.title, Type.bodyStrong)
            Txt(item.kind.detail, Type.small, colors.muted)
        }
        if (item.on) Txt("On", Type.body, colors.muted) else Action(item.kind.action, ButtonKind.SECONDARY) { actions.fix(item) }
    }
}

/** Today's interventions as choices, newest first (ADR-0016). */
@Composable
fun ChoicesPanel(choices: ChoicesModel) {
    val colors = Theme.colors
    Panel {
        PanelHeader("Today's choices") {
            if (choices.overrode > 0) Txt("${choices.overrode} opened", Type.small, colors.muted)
        }
        Txt(choices.headline, Type.body)
        choices.rows.forEach { row ->
            Rule()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Txt(row.time, Type.body, colors.muted, Modifier.width(52.dp))
                Column(Modifier.weight(1f)) {
                    Txt(row.target, Type.bodyStrong, maxLines = 1)
                    row.detail?.let { Txt(it, Type.small, colors.muted) }
                }
                Txt(row.outcome, Type.small, if (row.detail == null) colors.text else colors.muted)
            }
        }
        if (choices.windows.isNotEmpty()) {
            Rule()
            Txt("Hardest hours, last 14 days", Type.small, colors.muted)
            choices.windows.forEach { Txt(it, Type.body) }
        }
    }
}
