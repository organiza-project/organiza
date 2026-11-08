package org.organiza.feature.arena

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import org.organiza.ui.*

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
    ui.nudges.forEach { line ->
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Glyph.BELL)
                Txt(line, Type.body, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
    if (challenge != null) {
        if (challenge.model.podium.isNotEmpty()) Podium(challenge.model.podium)
        Panel {
            PanelHeader("Friend list") { Txt(challenge.model.freshness, Type.small, colors.muted) }
            challenge.model.rows.forEachIndexed { index, row ->
                if (index > 0) Rule()
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(row.name, 40.dp, highlighted = row.isSelf)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Txt(if (row.isSelf) "${row.name} (you)" else row.name, Type.bodyStrong, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Txt("${row.xp} XP", Type.small, colors.muted)
                            Icon(Glyph.FLAME, if (row.streak > 0) colors.text else colors.muted, 14.dp, Modifier.padding(start = 10.dp, end = 2.dp))
                            Txt("${row.streak}", Type.small, colors.muted)
                        }
                        if (row.invalid) Txt("Results could not be verified", Type.small, colors.negative)
                    }
                    if (!row.isSelf && !row.invalid) {
                        Action("Nudge", ButtonKind.SECONDARY) { actions.nudge(challenge.id, row.memberId) }
                    }
                }
            }
            Rule()
            Txt("XP here is 10 per met day and 2 per streak day, from shared results. Results are self-reported.", Type.small, colors.muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Sync now", ButtonKind.PRIMARY) { actions.syncArena() }
                Action("Share invite", ButtonKind.TEXT) { actions.shareInvite() }
            }
        }
    }
    MessagePanel(ui.message)
    NewChallengePanel(actions, ui.account, open = challenge == null)
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
private fun NewChallengePanel(actions: Actions, account: AccountView, open: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(open) }
    var habit by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("30") }
    var freezes by rememberSaveable { mutableStateOf("2") }
    var relay by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    // An invite is a secret, so it stays out of saved instance state.
    var invite by remember { mutableStateOf("") }
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
        if (account.kind == null) {
            Field(relay, { relay = it }, "Relay address (https)")
        } else {
            Txt("Results sync through ${account.url}" + if (account.signedInAs == null) ". Sign in on the Profile screen first." else ".", Type.small, Theme.colors.muted)
            Spacer(Modifier.height(8.dp))
        }
        Action("Create and get invite", ButtonKind.SECONDARY) { actions.createChallenge(habit, title, days, freezes, relay, name) }
    }
}

/** The top three on a stepped podium: first in the middle, raised, with a crown (wireframe screen 8). */
@Composable
private fun Podium(rows: List<org.organiza.presentation.ArenaRow>) {
    val colors = Theme.colors
    val order = listOfNotNull(rows.getOrNull(1), rows.getOrNull(0), rows.getOrNull(2))
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            order.forEach { row ->
                val first = row == rows.first()
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Glyph.CROWN, if (first) colors.data else colors.muted, if (first) 28.dp else 20.dp)
                    Avatar(row.name, if (first) 56.dp else 44.dp, highlighted = row.isSelf)
                    Txt(row.name, Type.small, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .height(if (first) 72.dp else if (row == rows.getOrNull(1)) 52.dp else 40.dp)
                            .podiumStep(colors),
                        contentAlignment = Alignment.Center,
                    ) { Txt(if (first) "1st" else if (row == rows.getOrNull(1)) "2nd" else "3rd", Type.section) }
                }
            }
        }
    }
}

private fun Modifier.podiumStep(colors: Palette): Modifier =
    this.background(colors.raised, androidx.compose.foundation.shape.RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
        .border(Space.stroke, colors.border, androidx.compose.foundation.shape.RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
