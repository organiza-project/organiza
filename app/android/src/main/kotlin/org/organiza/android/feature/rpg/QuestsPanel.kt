package org.organiza.android.feature.rpg

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.organiza.android.ui.*
import org.organiza.storage.RpgKind

private val EDGE = 2.dp
private val DROP = 4.dp
private val CORNER = RoundedCornerShape(6.dp)

/** The hero and the Habitica-style lists (ADR-0019): thick ink edges and hard shadows that give way when pressed. */
@Composable
fun QuestsPanel(rpg: RpgView, actions: Actions) {
    val colors = Theme.colors
    var kind by rememberSaveable { mutableStateOf(RpgKind.DAILY) }
    var title by rememberSaveable { mutableStateOf("") }
    Slab {
        Txt(rpg.badge, Type.section, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        StatBar("HP", rpg.hp.toDouble() / rpg.maxHp, "${rpg.hp} / ${rpg.maxHp}", colors.negative)
        Spacer(Modifier.height(10.dp))
        StatBar("XP", rpg.xpFraction, rpg.xpCaption, colors.data)
    }
    Segmented(RpgKind.entries, kind, { label(it) + " " + rpg.tasks.count { task -> task.kind == it } }) { kind = it }
    Txt(rule(kind), Type.small, colors.muted)
    rpg.tasks.filter { it.kind == kind }.forEach { task -> TaskCard(task, actions) }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(title, { title = it }, "New ${single(kind)}", Modifier.weight(1f))
        Box(Modifier.padding(bottom = 12.dp)) {
            Action("Add", ButtonKind.PRIMARY, enabled = title.isNotBlank()) {
                actions.addQuest(kind, title)
                title = ""
            }
        }
    }
}

private fun label(kind: RpgKind) = when (kind) {
    RpgKind.HABIT -> "Habits"
    RpgKind.DAILY -> "Dailies"
    RpgKind.TODO -> "To-dos"
}

private fun single(kind: RpgKind) = when (kind) {
    RpgKind.HABIT -> "habit"
    RpgKind.DAILY -> "daily"
    RpgKind.TODO -> "to-do"
}

private fun rule(kind: RpgKind) = when (kind) {
    RpgKind.HABIT -> "Tap + for a good one (+5 XP) and − for a slip (−5 HP), as often as it happens."
    RpgKind.DAILY -> "+10 XP each. Left undone at midnight, each costs 8 HP."
    RpgKind.TODO -> "+15 XP once, then it is gone."
}

/** A labelled bar with a thick edge, its fill eased to the new value. */
@Composable
private fun StatBar(label: String, fraction: Double, caption: String, fill: Color) {
    val colors = Theme.colors
    val shown by animateFloatAsState(fraction.toFloat().coerceIn(0f, 1f), spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = label)
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "$label $caption" }) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Txt(label, Type.bodyStrong, modifier = Modifier.weight(1f))
            Txt(caption, Type.small, colors.muted)
        }
        Box(Modifier.fillMaxWidth().height(18.dp).background(colors.raised, CORNER).border(EDGE, colors.border, CORNER)) {
            if (shown > 0f) Box(Modifier.fillMaxWidth(shown).fillMaxHeight().padding(EDGE).background(fill, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun TaskCard(task: RpgTaskView, actions: Actions) {
    val colors = Theme.colors
    Slab {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (task.kind) {
                RpgKind.DAILY -> Press(if (task.done) Glyph.CHECK else null, if (task.done) "Undo ${task.title}" else "Done ${task.title}", task.done) {
                    actions.checkDaily(task.id, !task.done)
                }
                RpgKind.TODO -> Press(null, "Complete ${task.title}", false) { actions.completeTodo(task.id) }
                RpgKind.HABIT -> Unit
            }
            Txt(task.title, Type.bodyStrong, if (task.done) colors.muted else colors.text, Modifier.weight(1f), maxLines = 2)
            if (task.kind == RpgKind.HABIT) {
                Press(Glyph.MINUS, "Slip on ${task.title}", false) { actions.tapHabit(task.id, false) }
                Press(Glyph.PLUS, "Did ${task.title}", false) { actions.tapHabit(task.id, true) }
            }
            Action("Remove", ButtonKind.TEXT) { actions.deleteQuest(task.id) }
        }
    }
}

/** A panel with an ink edge and a hard offset shadow. */
@Composable
private fun Slab(content: @Composable () -> Unit) {
    val colors = Theme.colors
    Box(Modifier.fillMaxWidth().padding(end = DROP, bottom = DROP)) {
        Box(Modifier.matchParentSize().offset(DROP, DROP).background(colors.border, CORNER))
        Column(Modifier.fillMaxWidth().background(colors.surface, CORNER).border(EDGE, colors.border, CORNER).padding(14.dp)) { content() }
    }
}

/** A square key that sinks into its shadow and ticks the phone when pressed. */
@Composable
private fun Press(glyph: Glyph?, description: String, filled: Boolean, onClick: () -> Unit) {
    val colors = Theme.colors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink by animateDpAsState(if (pressed) DROP else 0.dp, spring(stiffness = Spring.StiffnessHigh), label = "sink")
    Box(Modifier.size(Space.target + DROP)) {
        Box(Modifier.size(Space.target).offset(DROP, DROP).background(colors.border, CORNER))
        Box(
            Modifier
                .offset(sink, sink)
                .size(Space.target)
                .background(if (filled) colors.data else colors.surface, CORNER)
                .border(EDGE, colors.border, CORNER)
                .clickable(interaction, null, role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .semantics {
                    contentDescription = description
                    if (glyph == null || glyph == Glyph.CHECK) stateDescription = if (filled) "Done" else "Not done"
                }
                .heightIn(min = Space.target),
            contentAlignment = Alignment.Center,
        ) { glyph?.let { Icon(it, if (filled) colors.onPrimary else colors.text, 22.dp) } }
    }
}
