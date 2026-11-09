package org.organiza.feature.blocker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import java.time.Duration
import org.organiza.enforcement.ShortFormFeed
import org.organiza.enforcement.UsageLimits
import org.organiza.presentation.Format
import org.organiza.ui.*

// A bar turns red once 80% of the day's limit is used.
private const val WARN_AT = 0.8

/** One-tap limits under the typed box. */
private val PRESETS = listOf(15, 30, 60, 90)

/** Feeds that can be paused inside an app while the app itself stays open (ADR-0018). */
private val FEEDS = mapOf(
    "com.instagram.android" to (ShortFormFeed.INSTAGRAM_REELS to "Block Reels"),
    "com.google.android.youtube" to (ShortFormFeed.YOUTUBE_SHORTS to "Block Shorts"),
)

/**
 * An app on one line: `LibreWolf  1h 46m / 2h  ▬▬▬`. Tapped, it opens in place to the focus list, the
 * daily limit and its minutes, and folds back on Done or a second tap. Without a limit the bar is the
 * share of [most], the top app's minutes, in grey.
 */
@Composable
internal fun AppLimitCard(app: HubApp, hub: HubView, most: Long, open: Boolean, actions: Actions, onToggle: () -> Unit) {
    val colors = Theme.colors
    val limit = app.limitMinutes
    val share = app.usedMinutes.toDouble() / (limit ?: most.coerceAtLeast(1))
    val warn = app.locked || (limit != null && share >= WARN_AT)
    val ink = if (warn) colors.negative else if (limit != null) colors.text else colors.muted
    val turn by animateFloatAsState(if (open) 90f else 0f)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Space.target)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics {
                    contentDescription = "${app.label}, ${Format.duration(Duration.ofMinutes(app.usedMinutes))} today" +
                        (limit?.let { ", limit $it minutes" } ?: "") + (if (app.locked) ", locked until tomorrow" else "")
                    stateDescription = if (open) "Open" else "Closed"
                }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.pkg, 32.dp)
            Txt(app.label, Type.bodyStrong, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1)
            if (app.locked) Icon(Glyph.LOCK, colors.negative, 16.dp, Modifier.padding(end = 6.dp))
            Txt(Format.usage(app.usedMinutes, limit), Type.bodyStrong, if (warn) colors.negative else colors.text, maxLines = 1)
            ProgressPill(share, Modifier.weight(0.7f).padding(start = 12.dp), ink)
            Icon(Glyph.NEXT, colors.muted, modifier = Modifier.padding(start = 8.dp).rotate(turn))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            LimitControls(app, hub, open, actions, onToggle)
        }
    }
}

@Composable
private fun LimitControls(app: HubApp, hub: HubView, open: Boolean, actions: Actions, done: () -> Unit) {
    val colors = Theme.colors
    // A typed limit is saved as the card starts to fold, so "120" never passes through a 12 minute limit.
    var draft by rememberSaveable(app.pkg) { mutableStateOf(app.limitMinutes?.toInt() ?: 60) }
    val saved by rememberUpdatedState(app.limitMinutes)
    val stillOpen by rememberUpdatedState(open)
    val save = { saved?.let { if (draft.toLong() != it) actions.setLimit(app.pkg, draft.toLong()) } }
    LaunchedEffect(open) { if (!open) save() }
    // Leaving the screen with the card open saves too.
    DisposableEffect(app.pkg) { onDispose { if (stillOpen) save() } }
    Column(Modifier.fillMaxWidth().padding(start = 44.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Txt(app.category, Type.small, colors.muted)
        SwitchRow("Block in focus sessions", "Adds it to your focus list", app.onList) { actions.setFocusApp(app.pkg, it) }
        SwitchRow(
            "Daily limit",
            app.limitMinutes?.let { "Locks for the rest of the day after ${Format.duration(Duration.ofMinutes(it))}, with or without a session" }
                ?: "Off. Turn on to lock the app once today's time runs out",
            app.limitMinutes != null,
        ) { on -> actions.setLimit(app.pkg, if (on) draft.toLong() else null) }
        if (app.limitMinutes != null) MinutesField("Minutes a day", draft, UsageLimits.MINUTES, PRESETS) { draft = it }
        FEEDS[app.pkg]?.let { (feed, title) ->
            val on = if (feed == ShortFormFeed.INSTAGRAM_REELS) hub.blockReels else hub.blockShorts
            SwitchRow(title, "The app stays open; only its short video feed is paused, all day", on) { actions.setFeed(feed, it) }
        }
        Action("Done", ButtonKind.SECONDARY, modifier = Modifier.align(Alignment.End), onClick = done)
    }
}
