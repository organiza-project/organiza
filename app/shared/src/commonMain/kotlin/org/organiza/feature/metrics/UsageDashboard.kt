package org.organiza.feature.metrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Duration
import org.organiza.feature.blocker.AppIcon
import org.organiza.ui.*
import org.organiza.enforcement.ShortFormFeed
import org.organiza.enforcement.UsageLimits
import org.organiza.presentation.Format
import org.organiza.presentation.Permission

// A bar turns red once 80% of the day's limit is used, as in the blocker below.
private const val WARN_AT = 0.8
private const val TOP = 8

/** Feeds that can be paused inside an app while the app itself stays open (ADR-0018). */
private val FEEDS = mapOf(
    "com.instagram.android" to (ShortFormFeed.INSTAGRAM_REELS to "Block Reels"),
    "com.google.android.youtube" to (ShortFormFeed.YOUTUBE_SHORTS to "Block Shorts"),
)

/** One-tap limits under the typed box. */
private val PRESETS = listOf(15, 30, 60, 90)

/** Today's apps ranked by use, StayFree style: icon, time and how far into its limit (ADR-0020). */
@Composable
fun UsageDashboard(ui: UiSnapshot, actions: Actions) {
    val colors = Theme.colors
    val hub = ui.hub
    var showAll by rememberSaveable { mutableStateOf(false) }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    Panel {
        PanelHeader("Usage today") { hub.screenTime?.let { Txt(it, Type.bodyStrong) } }
        if (!hub.usageAllowed) {
            Txt("Allow usage access to see how long each app was open today. It is read on this phone only.", Type.body, colors.muted)
            Spacer(Modifier.height(8.dp))
            Action("Allow usage access", ButtonKind.SECONDARY) { actions.grant(Permission.USAGE) }
            return@Panel
        }
        val used = hub.apps.filter { it.usedMinutes > 0 }.sortedByDescending { it.usedMinutes }
        if (used.isEmpty()) Txt("No app has reached a full minute yet today.", Type.body, colors.muted)
        val most = used.firstOrNull()?.usedMinutes ?: 1
        (if (showAll) used else used.take(TOP)).forEachIndexed { index, app ->
            if (index > 0) Rule()
            UsageRow(app, most) { chosen = app.pkg }
        }
        if (used.size > TOP) Action(if (showAll) "Show fewer" else "Show all ${used.size} apps", ButtonKind.TEXT) { showAll = !showAll }
    }
    hub.apps.firstOrNull { it.pkg == chosen }?.let { app -> AppSheet(app, hub, actions) { chosen = null } }
}

@Composable
private fun UsageRow(app: HubApp, most: Long, onOpen: () -> Unit) {
    val colors = Theme.colors
    val limit = app.limitMinutes
    // With a limit the bar is the share of it used; without one, the share of today's top app, in grey.
    val share = if (limit != null) app.usedMinutes.toDouble() / limit else app.usedMinutes.toDouble() / most
    val warn = app.locked || (limit != null && share >= WARN_AT)
    val time = Format.duration(Duration.ofMinutes(app.usedMinutes))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.target + 12.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = "${app.label}, $time today" + (limit?.let { ", limit $it minutes" } ?: "") }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.pkg, 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(app.label, Type.bodyStrong, modifier = Modifier.weight(1f), maxLines = 1)
                Txt(time, Type.bodyStrong, if (warn) colors.negative else colors.text)
            }
            Spacer(Modifier.height(6.dp))
            ProgressPill(share, ink = if (warn) colors.negative else if (limit != null) colors.text else colors.muted)
            Txt(
                when {
                    app.locked -> "Limit reached. Locked until tomorrow"
                    limit != null -> "${Format.duration(Duration.ofMinutes((limit - app.usedMinutes).coerceAtLeast(0)))} left of ${Format.duration(Duration.ofMinutes(limit))}"
                    else -> "No limit. Tap to set one"
                },
                Type.small,
                if (warn) colors.negative else colors.muted,
                Modifier.padding(top = 4.dp),
            )
        }
        Icon(Glyph.NEXT, colors.muted)
    }
}

/** The app's sheet: a daily limit in one tap, the focus list, and in-app feed blocks where they exist. */
@Composable
private fun AppSheet(app: HubApp, hub: HubView, actions: Actions, dismiss: () -> Unit) {
    val colors = Theme.colors
    // A typed limit is saved as the sheet closes, so "120" never passes through a 12 minute limit.
    var draft by rememberSaveable(app.pkg) { mutableStateOf(app.limitMinutes?.toInt() ?: 60) }
    val close = {
        if (app.limitMinutes != null && draft.toLong() != app.limitMinutes) actions.setLimit(app.pkg, draft.toLong())
        dismiss()
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = close, indication = null, interactionSource = null), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .background(colors.ground, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    // Taps inside the sheet must not reach the scrim behind it.
                    .clickable(onClick = {}, indication = null, interactionSource = null)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = Space.margin, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).widthIn(min = 40.dp).height(4.dp).background(colors.control, RoundedCornerShape(2.dp)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.pkg, 48.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Txt(app.label, Type.section, maxLines = 1)
                        Txt("${Format.duration(Duration.ofMinutes(app.usedMinutes))} today · ${app.category}", Type.small, colors.muted)
                    }
                }
                SwitchRow(
                    "Daily limit",
                    app.limitMinutes?.let { "Locks for the rest of the day after ${Format.duration(Duration.ofMinutes(it))}, with or without a session" }
                        ?: "Off. Turn on to lock the app once today's time runs out",
                    app.limitMinutes != null,
                ) { on -> actions.setLimit(app.pkg, if (on) draft.toLong() else null) }
                if (app.limitMinutes != null) MinutesField("Minutes a day", draft, UsageLimits.MINUTES, PRESETS) { draft = it }
                Rule()
                SwitchRow("Block in focus sessions", "Adds it to your focus list", app.onList) { actions.setFocusApp(app.pkg, it) }
                FEEDS[app.pkg]?.let { (feed, title) ->
                    val on = if (feed == ShortFormFeed.INSTAGRAM_REELS) hub.blockReels else hub.blockShorts
                    SwitchRow(title, "The app stays open; only its short video feed is paused, all day", on) { actions.setFeed(feed, it) }
                }
                Action("Done", ButtonKind.PRIMARY, modifier = Modifier.fillMaxWidth(), onClick = close)
            }
        }
    }
}

