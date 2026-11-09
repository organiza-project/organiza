package org.organiza.feature.blocker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.organiza.ui.*
import org.organiza.enforcement.ShortFormFeed

/** The distraction blocker hub, inside Metrics (ADR-0021): the focus list, daily limits and short video switches. */
@Composable
fun BlockerHub(ui: UiSnapshot, actions: Actions, openApp: MutableState<String?> = rememberSaveable { mutableStateOf(null) }) {
    val colors = Theme.colors
    val hub = ui.hub
    var showAll by rememberSaveable { mutableStateOf(false) }
    var site by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    Txt("Distraction blocker", Type.section, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
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
    val mine = hub.apps.filter { it.onList || it.limitMinutes != null }
    var open by openApp
    Panel {
        PanelHeader("Your apps") { Action(if (showAll) "Done" else "Add apps", ButtonKind.TEXT) { showAll = !showAll } }
        if (mine.isEmpty()) Txt("No apps yet. Choose Add apps to pick some.", Type.body, colors.muted)
        mine.forEachIndexed { index, app ->
            if (index > 0) Rule()
            key(app.pkg) {
                AppLimitCard(app, hub, mine.maxOf { it.usedMinutes }, open == app.pkg, actions) { open = if (open == app.pkg) null else app.pkg }
            }
        }
    }
    if (showAll) {
        // The picker: every launchable app, searchable, grouped by the category it declares.
        val query = search.trim().lowercase()
        val found = hub.apps.filter { it !in mine && (query.isEmpty() || query in it.label.lowercase() || query in it.pkg) }
        Panel {
            PanelHeader("Add apps")
            Field(search, { search = it }, "Search apps")
            if (found.isEmpty()) Txt("No app matches.", Type.body, colors.muted)
            found.groupBy { it.category }.toSortedMap().forEach { (category, apps) ->
                Txt(category, Type.small, colors.muted, Modifier.padding(top = 12.dp, bottom = 4.dp))
                apps.forEach { app -> PickRow(app, actions) }
            }
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

/** One app to pick: its icon, name and today's use, with quick add to the list or a daily limit. */
@Composable
private fun PickRow(app: HubApp, actions: Actions) {
    val colors = Theme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app.pkg)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Txt(app.label, Type.bodyStrong, maxLines = 1)
            Txt("${app.usedMinutes} min today", Type.small, colors.muted)
        }
        Action("Limit", ButtonKind.TEXT) { actions.setLimit(app.pkg, 60) }
        Spacer(Modifier.width(8.dp))
        Action("Block", ButtonKind.SECONDARY) { actions.setFocusApp(app.pkg, true) }
    }
}

/** The app's launcher icon, loaded off the main thread. */
@Composable
internal fun AppIcon(pkg: String, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val icon = rememberAppIcon(pkg)
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.size(size).clip(shape).background(Theme.colors.raised, shape)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}

/** The launcher icon of an installed app, or null while it loads or when there is none. */
@Composable
internal expect fun rememberAppIcon(pkg: String): ImageBitmap?
