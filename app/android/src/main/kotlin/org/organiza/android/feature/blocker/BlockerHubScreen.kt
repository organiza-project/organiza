package org.organiza.android.feature.blocker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.organiza.android.ui.*
import org.organiza.enforcement.ShortFormFeed
import org.organiza.enforcement.UsageLimits
import org.organiza.presentation.Permission

/** The distraction blocker hub (wireframe screen 6): the focus list, daily limits and short video switches. */
@Composable
fun BlockerHubScreen(ui: UiSnapshot, actions: Actions, back: () -> Unit) {
    val colors = Theme.colors
    val hub = ui.hub
    var showAll by rememberSaveable { mutableStateOf(false) }
    var site by rememberSaveable { mutableStateOf("") }
    TopBar("Distraction blocker hub", back)
    MessagePanel(ui.message)
    Panel {
        PanelHeader("Inside apps")
        SwitchRow("Block Reels tab only", "Instagram stays open; its Reels feed is paused all day", hub.blockReels) {
            actions.setFeed(ShortFormFeed.INSTAGRAM_REELS, it)
        }
        Rule()
        SwitchRow("Block YT Shorts", "YouTube stays open; Shorts are paused all day", hub.blockShorts) {
            actions.setFeed(ShortFormFeed.YOUTUBE_SHORTS, it)
        }
    }
    if (!hub.usageAllowed) {
        Panel {
            Txt("Daily limits need usage access to see how long each app was open today.", Type.body)
            Spacer(Modifier.height(8.dp))
            Action("Allow usage access", ButtonKind.SECONDARY) { actions.grant(Permission.USAGE) }
        }
    }
    val shown = hub.apps.filter { showAll || it.onList || it.limitMinutes != null }
    Panel {
        PanelHeader("Apps") { Action(if (showAll) "Show mine" else "Add apps", ButtonKind.TEXT) { showAll = !showAll } }
        if (shown.isEmpty()) Txt("No apps yet. Choose Add apps to pick some.", Type.body, colors.muted)
        shown.forEachIndexed { index, app ->
            if (index > 0) Rule()
            HubAppRow(app, actions)
        }
    }
    Panel {
        PanelHeader("Websites")
        hub.sites.forEach { domain ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Txt(domain, Type.body, modifier = Modifier.weight(1f))
                Action("Remove", ButtonKind.TEXT) { actions.removeFocusSite(domain) }
            }
        }
        Field(site, { site = it }, "Add a site, like reddit.com")
        Action("Add site", ButtonKind.SECONDARY, enabled = site.isNotBlank()) { actions.addFocusSite(site); site = "" }
        Txt("Sites are blocked with their subdomains during sessions.", Type.small, colors.muted)
    }
}

@Composable
private fun HubAppRow(app: HubApp, actions: Actions) {
    val colors = Theme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(app.label, Type.bodyStrong, maxLines = 1)
                val limit = app.limitMinutes
                val used = if (limit != null) "${app.usedMinutes} of $limit min today" else "${app.usedMinutes} min today"
                Txt(used, Type.small, if (app.locked) colors.negative else colors.muted)
            }
            if (app.locked) Icon(Glyph.LOCK, colors.negative, modifier = Modifier.padding(end = 8.dp))
        }
        val limit = app.limitMinutes
        if (limit != null) {
            Spacer(Modifier.height(6.dp))
            ProgressPill(app.usedMinutes.toDouble() / limit, ink = if (app.locked) colors.negative else colors.text)
        }
        SwitchRow("Block in focus sessions", "On the focus list", app.onList) { actions.setFocusApp(app.pkg, it) }
        SwitchRow("Daily limit", limit?.let { "Locks after $it minutes a day" } ?: "No limit", limit != null) {
            actions.setLimit(app.pkg, if (it) 60 else null)
        }
        if (limit != null) {
            val steps = UsageLimits.STEPS
            val index = steps.indexOfFirst { it.toMinutes() == limit }.coerceAtLeast(0)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                val range = steps.first().toMinutes().toInt()..steps.last().toMinutes().toInt()
                // Minus and plus move one step along the slider's values, not one minute.
                Stepper(limit.toInt(), "min a day", range) { wanted ->
                    val next = if (wanted > limit) steps.getOrNull(index + 1) else steps.getOrNull(index - 1)
                    next?.let { actions.setLimit(app.pkg, it.toMinutes()) }
                }
            }
        }
    }
}
