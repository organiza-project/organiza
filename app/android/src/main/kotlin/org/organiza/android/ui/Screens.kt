package org.organiza.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.organiza.presentation.BarKind
import org.organiza.presentation.Cell
import org.organiza.presentation.FocusState
import org.organiza.presentation.HabitMatrix
import org.organiza.presentation.ProtectionItem

/** Every user action the screens can ask for. The activity maps these to the app (ADR-0012). */
interface Actions {
    fun startSession(apps: String, sites: String, minutes: String, strict: Boolean, shortForm: Boolean)
    fun stopSession()
    fun askGatekeeper(reason: String)
    fun fix(item: ProtectionItem)
    fun addHabit(name: String)
    fun markToday(habitId: String, name: String, done: Boolean)
    fun startTalking()
    fun stopTalking()
    fun confirmCommand()
    fun discardCommand()
    fun importCalendar()
    fun weeklyReview()
    fun syncArena()
    fun shareInvite()
    fun createChallenge(habit: String, title: String, days: String, freezes: String, relay: String, name: String)
    fun joinChallenge(invite: String, habit: String, name: String)
    fun saveKey(key: String)
    fun removeKey()
    fun setVoiceConsent(on: Boolean)
}

@Composable
private fun Message(text: String?) {
    if (text == null) return
    Panel { Txt(text, Type.body) }
}

@Composable
fun FocusScreen(ui: UiSnapshot, actions: Actions) {
    val focus = ui.focus ?: return
    val colors = Theme.colors
    var starting by rememberSaveable { mutableStateOf(false) }
    TitleBlock("Focus", focus.subline)
    Panel {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Txt(if (focus.state == FocusState.OFF) "Blocking" else "Time left", Type.small, colors.muted)
                Txt(focus.headline, Type.figure, if (focus.state == FocusState.OFF) colors.muted else colors.text)
            }
            if (focus.state == FocusState.STRICT) Pill("Strict", colors.raised, colors.text)
        }
        Spacer(Modifier.height(12.dp))
        DayStripView(focus.strip, "Today's sessions. " + focus.subline)
        // A legend lists only the kinds on screen (app-screens §6.4).
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
        }
    }
    if (starting && focus.state == FocusState.OFF) StartPanel(actions) { starting = false }
    if (focus.state != FocusState.OFF) BlockedPanel(ui)
    if (focus.state == FocusState.STRICT) GatekeeperPanel(actions)
    Message(ui.message)
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
private fun BlockedPanel(ui: UiSnapshot) {
    val colors = Theme.colors
    Panel {
        PanelHeader("Blocked now") {
            Txt("${ui.blockedApps.size + ui.blockedSites.size}", Type.small, colors.muted)
        }
        val rows = ui.blockedApps.map { it to "App" } + ui.blockedSites.map { it to "Website, and its subdomains" }
        if (rows.isEmpty()) Txt("This session's list is empty, so nothing is blocked.", Type.body, colors.muted)
        rows.forEachIndexed { index, (name, kind) ->
            if (index > 0) Rule()
            Row(Modifier.fillMaxWidth()) {
                Txt(name, Type.body, modifier = Modifier.weight(1f), maxLines = 1)
                Txt(kind, Type.small, colors.muted)
            }
        }
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

@Composable
private fun StartPanel(actions: Actions, close: () -> Unit) {
    var apps by rememberSaveable { mutableStateOf("") }
    var sites by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("45") }
    var strict by rememberSaveable { mutableStateOf(false) }
    var shortForm by rememberSaveable { mutableStateOf(false) }
    Panel {
        PanelHeader("New session")
        Field(apps, { apps = it }, "Apps to block, by package name")
        Field(sites, { sites = it }, "Websites to block")
        Field(minutes, { minutes = it }, "Minutes", number = true)
        SwitchRow("Strict", "Cannot be stopped early. Up to 24 hours", strict) { strict = it }
        SwitchRow("Shorts and Reels", "Block short video feeds inside apps", shortForm) { shortForm = it }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("Start", ButtonKind.PRIMARY) {
                actions.startSession(apps, sites, minutes, strict, shortForm)
                close()
            }
            Action("Cancel", ButtonKind.TEXT, onClick = close)
        }
    }
}

@Composable
private fun GatekeeperPanel(actions: Actions) {
    var reason by rememberSaveable { mutableStateOf("") }
    Panel {
        PanelHeader("Ask to leave")
        Txt("The gatekeeper's answer is advice. A strict session still runs to its end.", Type.small, Theme.colors.muted)
        Spacer(Modifier.height(8.dp))
        Field(reason, { reason = it }, "Why do you need to leave?")
        Action("Ask", ButtonKind.SECONDARY) { actions.askGatekeeper(reason) }
    }
}

@Composable
fun HabitsScreen(ui: UiSnapshot, actions: Actions) {
    val matrix = ui.habits ?: return
    val colors = Theme.colors
    var newHabit by rememberSaveable { mutableStateOf("") }
    TitleBlock("Habits", matrix.finding)
    Panel {
        PanelHeader("Last 30 days") { Txt("Today", Type.small, colors.data) }
        if (matrix.rows.isEmpty()) {
            Txt("Add a habit below, or hold the talk button and say \"add habit read\".", Type.body, colors.muted)
        }
        matrix.rows.forEachIndexed { index, row ->
            if (index > 0) Rule()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(row.name, Type.bodyStrong, maxLines = 1)
                    Txt("${row.met} of ${row.days} days · streak ${row.streak}", Type.small, colors.muted)
                }
                val today = row.cells.last()
                val done = today == Cell.MET
                if (done) {
                    Txt("Done today", Type.small, colors.muted, Modifier.padding(end = 8.dp))
                    Action("Undo", ButtonKind.TEXT) { actions.markToday(row.id, row.name, false) }
                } else {
                    Action("Mark done", ButtonKind.SECONDARY) { actions.markToday(row.id, row.name, true) }
                }
            }
            HabitCells(row.cells, "${row.name}: ${row.met} of ${row.days} days met")
        }
        if (matrix.rows.isNotEmpty()) MatrixLegend()
    }
    Panel {
        PanelHeader("Speak a command")
        Txt("Say \"mark read as done\" or \"schedule focus tomorrow at 9\". Nothing changes until you confirm.", Type.small, colors.muted)
        Spacer(Modifier.height(8.dp))
        TalkButton(actions)
        if (ui.pendingCommand != null) {
            Spacer(Modifier.height(8.dp))
            Txt(ui.pendingCommand, Type.bodyStrong)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Confirm", ButtonKind.PRIMARY) { actions.confirmCommand() }
                Action("Discard", ButtonKind.TEXT) { actions.discardCommand() }
            }
        }
    }
    Message(ui.message)
    Panel {
        PanelHeader("Coming up") { Action("Import .ics", ButtonKind.TEXT) { actions.importCalendar() } }
        if (ui.upcoming.isEmpty()) Txt("No sessions in the next 7 days. Tag calendar events #focus to schedule them.", Type.body, colors.muted)
        ui.upcoming.forEachIndexed { index, item ->
            if (index > 0) Rule()
            Row(Modifier.fillMaxWidth()) {
                Txt(item.label, Type.body, modifier = Modifier.weight(1f), maxLines = 1)
                Txt("${item.time} · ${item.length}", Type.body, colors.muted)
            }
        }
    }
    Panel {
        PanelHeader("Add a habit")
        Field(newHabit, { newHabit = it }, "Name")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("Add", ButtonKind.SECONDARY) {
                actions.addHabit(newHabit)
                newHabit = ""
            }
            Action("Weekly review", ButtonKind.TEXT) { actions.weeklyReview() }
        }
    }
}

@Composable
private fun HabitCells(cells: List<Cell>, description: String) {
    val colors = Theme.colors
    Canvas(Modifier.fillMaxWidth().height(22.dp).padding(top = 6.dp).semantics { contentDescription = description }) {
        val gap = 2.dp.toPx()
        val cell = (size.width - gap * (cells.size - 1)) / cells.size
        val h = size.height
        cells.forEachIndexed { index, state ->
            val x = index * (cell + gap)
            val radius = CornerRadius(2.dp.toPx())
            when (state) {
                Cell.MET -> drawRoundRect(if (index == cells.lastIndex) colors.data else colors.text, Offset(x, 0f), Size(cell, h), radius)
                Cell.MISSED -> drawRoundRect(colors.control, Offset(x + 0.75f, 0.75f), Size(cell - 1.5f, h - 1.5f), radius, style = Stroke(1.5.dp.toPx()))
                Cell.UNMARKED_PAST -> drawRect(colors.control, Offset(x, h / 2 - 1), Size(cell, 2f))
                Cell.TODAY_OPEN -> drawRoundRect(colors.data, Offset(x + 0.75f, 0.75f), Size(cell - 1.5f, h - 1.5f), radius, style = Stroke(1.5.dp.toPx()))
                Cell.NOT_STARTED -> Unit
            }
        }
    }
}

@Composable
private fun MatrixLegend() {
    val colors = Theme.colors
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendMark { drawRoundRect(colors.text, cornerRadius = CornerRadius(2.dp.toPx())) }
        Txt("Done", Type.small, colors.muted, Modifier.padding(start = 6.dp, end = 14.dp))
        LegendMark { drawRoundRect(colors.control, style = Stroke(1.5.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx())) }
        Txt("Missed", Type.small, colors.muted, Modifier.padding(start = 6.dp, end = 14.dp))
        LegendMark { drawRect(colors.control, Offset(0f, size.height / 2 - 1), Size(size.width, 2f)) }
        Txt("Not marked", Type.small, colors.muted, Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun LegendMark(draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    Canvas(Modifier.width(9.dp).height(12.dp), onDraw = draw)
}

@Composable
private fun TalkButton(actions: Actions) {
    val colors = Theme.colors
    var held by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(if (held) colors.primary else colors.secondary, RoundedCornerShape(Space.radiusControl))
            .semantics { contentDescription = "Hold to speak a command" }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    held = true
                    actions.startTalking()
                    tryAwaitRelease()
                    held = false
                    actions.stopTalking()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Txt(if (held) "Listening. Release to send" else "Hold to speak a command", Type.bodyStrong, if (held) colors.onPrimary else colors.text)
    }
}

@Composable
fun ArenaScreen(ui: UiSnapshot, actions: Actions) {
    val colors = Theme.colors
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val challenge = ui.challenges.firstOrNull { it.id == selected } ?: ui.challenges.firstOrNull()
    TitleBlock(
        "Arena",
        challenge?.let { "${it.title}. ${it.model.dayLine}. ${it.model.finding}" }
            ?: "No challenges yet. Join one with a friend's invite, or start your own.",
    )
    if (ui.challenges.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.challenges.forEach { item ->
                Action(item.title, if (item.id == challenge?.id) ButtonKind.SECONDARY else ButtonKind.TEXT) { selected = item.id }
            }
        }
    }
    if (challenge != null) {
        Panel {
            PanelHeader("Standings") { Txt(challenge.model.freshness, Type.small, colors.muted) }
            challenge.model.rows.forEachIndexed { index, row ->
                if (index > 0) Rule()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(row.rank, Type.body, colors.muted, Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(if (row.isSelf) "${row.name} (you)" else row.name, Type.bodyStrong, maxLines = 1)
                        val detail = "streak ${row.streak} · ${row.status.lowercase()} · ${row.verification.lowercase()}"
                        Txt(detail, Type.small, if (row.invalid) colors.negative else colors.muted)
                    }
                    Txt(row.progress, Type.body)
                }
                PlanTrack(row.fraction, if (row.isSelf) colors.data else colors.text, row.invalid)
                if (row.isSelf && row.penalties.isNotEmpty()) {
                    Txt("Penalties: " + row.penalties.joinToString(", "), Type.small, colors.muted, Modifier.padding(top = 4.dp))
                }
            }
            Rule()
            Txt("Results are self-reported. Confirmed means they were not rewritten, not that they are proven true.", Type.small, colors.muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Sync now", ButtonKind.PRIMARY) { actions.syncArena() }
                Action("Share invite", ButtonKind.TEXT) { actions.shareInvite() }
            }
        }
    }
    Message(ui.message)
    NewChallengePanel(actions, open = challenge == null)
}

@Composable
private fun PlanTrack(fraction: Double, color: androidx.compose.ui.graphics.Color, invalid: Boolean) {
    val colors = Theme.colors
    Canvas(Modifier.fillMaxWidth().height(14.dp).padding(top = 6.dp)) {
        val height = 6.dp.toPx()
        val radius = CornerRadius(height / 2)
        drawRoundRect(colors.raised, Offset.Zero, Size(size.width, height), radius)
        if (fraction > 0 && !invalid) drawRoundRect(color, Offset.Zero, Size((size.width * fraction).toFloat(), height), radius)
    }
}

@Composable
private fun NewChallengePanel(actions: Actions, open: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(open) }
    var habit by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("30") }
    var freezes by rememberSaveable { mutableStateOf("2") }
    var relay by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var invite by rememberSaveable { mutableStateOf("") }
    Panel {
        PanelHeader("Start or join a challenge") {
            Action(if (expanded) "Hide" else "Open", ButtonKind.TEXT) { expanded = !expanded }
        }
        if (!expanded) return@Panel
        Txt("Only your daily result and streak are shared, encrypted so the relay cannot read them.", Type.small, Theme.colors.muted)
        Spacer(Modifier.height(8.dp))
        Field(habit, { habit = it }, "Your habit")
        Field(name, { name = it }, "Your name in the group")
        Field(invite, { invite = it }, "Invite", secret = true)
        Action("Join with invite", ButtonKind.SECONDARY) { actions.joinChallenge(invite, habit, name); invite = "" }
        Rule()
        Field(title, { title = it }, "Challenge title")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(days, { days = it }, "Days", Modifier.weight(1f), number = true)
            Field(freezes, { freezes = it }, "Freeze days", Modifier.weight(1f), number = true)
        }
        Field(relay, { relay = it }, "Relay address (https)")
        Action("Create and get invite", ButtonKind.SECONDARY) { actions.createChallenge(habit, title, days, freezes, relay, name) }
    }
}

@Composable
fun VaultScreen(ui: UiSnapshot, actions: Actions) {
    val colors = Theme.colors
    var key by rememberSaveable { mutableStateOf("") }
    TitleBlock("Vault", "Settings and what this phone keeps. Protection lives on the Focus screen.")
    ui.storageProblem?.let { Panel { Txt(it, Type.body, colors.negative) } }
    Message(ui.message)
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
    Panel {
        PanelHeader("AI coach") {
            Pill(if (ui.hasApiKey) "Key saved" else "No key", colors.raised, colors.text)
        }
        Txt(
            "Optional. Uses your own Groq key. Typed text is sent after names, links and numbers are removed where detected, " +
                "which may not catch everything. App and website history is never sent.",
            Type.small,
            colors.muted,
        )
        Spacer(Modifier.height(8.dp))
        Field(key, { key = it }, "Groq API key", secret = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("Save key", ButtonKind.SECONDARY) { actions.saveKey(key); key = "" }
            if (ui.hasApiKey) Action("Remove key", ButtonKind.DANGER) { actions.removeKey() }
        }
    }
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
}

@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val colors = Theme.colors
    Row(
        Modifier.fillMaxWidth().background(colors.ground).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("Focus", "Habits", "Arena", "Vault").forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = Space.target)
                    .background(if (active) colors.raised else colors.ground, RoundedCornerShape(Space.radiusControl))
                    .clickable(role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Txt(label, if (active) Type.bodyStrong else Type.body, if (active) colors.text else colors.muted)
            }
        }
    }
}
