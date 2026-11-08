package org.organiza.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The five destinations, with Home in the middle as the wireframe draws it (ADR-0018). */
enum class Tab(val label: String, val glyph: Glyph) {
    HABITS("Habits", Glyph.HABITS),
    ARENA("Arena", Glyph.ARENA),
    HOME("Home", Glyph.HOME),
    METRICS("Metrics", Glyph.METRICS),
    PROFILE("Profile", Glyph.PROFILE),
}

/** Screens that open on top of a tab and close with back. */
enum class Route(val title: String) {
    NEW_HABIT("New habit"),
    AGENDA("Calendar"),
    IMPORT("Import schedule"),
    DICTATION("Dictate schedule"),
    COPILOT("Copilot"),
}

@Composable
fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val colors = Theme.colors
    Row(
        Modifier.fillMaxWidth().background(colors.ground).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val active = tab == selected
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .semantics {
                        contentDescription = tab.label
                        this.selected = active
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (tab == Tab.HOME) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(if (active) colors.primary else colors.surface, CircleShape)
                            .border(Space.stroke, colors.border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(tab.glyph, if (active) colors.onPrimary else colors.text, 22.dp) }
                } else {
                    Icon(tab.glyph, if (active) colors.text else colors.muted, 22.dp)
                    Txt(tab.label, Type.small, if (active) colors.text else colors.muted, Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

/** The title row of a screen opened on top: back, then its name. */
@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(Space.target).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" },
            contentAlignment = Alignment.CenterStart,
        ) { Icon(Glyph.BACK) }
        Txt(title, Type.title, modifier = Modifier.semantics { heading() })
    }
}

/** Opens the copilot chat from any tab (ADR-0020). */
@Composable
fun CopilotFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = Theme.colors
    Box(
        modifier
            .size(56.dp)
            .background(colors.primary, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Open the copilot" },
        contentAlignment = Alignment.Center,
    ) { Icon(Glyph.CHAT, colors.onPrimary, 24.dp) }
}
