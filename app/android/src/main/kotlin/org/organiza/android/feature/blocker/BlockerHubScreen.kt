package org.organiza.android.feature.blocker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var search by rememberSaveable { mutableStateOf("") }
    TopBar("Distraction blocker hub", back)
    MessagePanel(ui.message)
    hub.screenTime?.let { TitleBlock(it, "Screen time today, across your apps") }
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
    val mine = hub.apps.filter { it.onList || it.limitMinutes != null }
    Panel {
        PanelHeader("Your apps") { Action(if (showAll) "Done" else "Add apps", ButtonKind.TEXT) { showAll = !showAll } }
        if (mine.isEmpty()) Txt("No apps yet. Choose Add apps to pick some.", Type.body, colors.muted)
        mine.forEachIndexed { index, app ->
            if (index > 0) Rule()
            HubAppRow(app, actions)
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
private fun AppIcon(pkg: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.Default) {
            try {
                val drawable = context.packageManager.getApplicationIcon(pkg)
                val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, ICON_PX, ICON_PX)
                drawable.draw(Canvas(bitmap))
                bitmap.asImageBitmap()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.size(36.dp).clip(shape).background(Theme.colors.raised, shape)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(36.dp)) }
    }
}

private const val ICON_PX = 96

// A bar turns red once 80% of the day's limit is used.
private const val WARN_AT = 0.8

@Composable
private fun HubAppRow(app: HubApp, actions: Actions) {
    val colors = Theme.colors
    val limit = app.limitMinutes
    val share = if (limit != null) app.usedMinutes.toDouble() / limit else 0.0
    val warn = app.locked || share >= WARN_AT
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.pkg)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Txt(app.label, Type.bodyStrong, maxLines = 1)
                val used = if (limit != null) "${app.usedMinutes}/$limit min today" else "${app.usedMinutes} min today"
                Txt(used, Type.small, if (warn) colors.negative else colors.muted)
            }
            if (app.locked) Icon(Glyph.LOCK, colors.negative, modifier = Modifier.padding(end = 8.dp))
        }
        if (limit != null) {
            Spacer(Modifier.height(6.dp))
            ProgressPill(share, ink = if (warn) colors.negative else colors.text)
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
