package org.organiza.feature.metrics

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.organiza.feature.blocker.AppLimitCard
import org.organiza.ui.*
import org.organiza.presentation.Permission

private const val TOP = 8

/** Today's apps ranked by use, StayFree style: one line each, opening in place to set a limit (ADR-0020). */
@Composable
fun UsageDashboard(ui: UiSnapshot, actions: Actions, openApp: MutableState<String?> = rememberSaveable { mutableStateOf(null) }) {
    val colors = Theme.colors
    val hub = ui.hub
    var showAll by rememberSaveable { mutableStateOf(false) }
    var open by openApp
    Panel {
        PanelHeader("Usage today") { hub.screenTime?.let { Txt(it, Type.bodyStrong) } }
        if (!hub.usageAllowed) {
            Txt("Allow usage access to see how long each app was open today. It is read on this device only.", Type.body, colors.muted)
            Spacer(Modifier.height(8.dp))
            Action("Allow usage access", ButtonKind.SECONDARY) { actions.grant(Permission.USAGE) }
            return@Panel
        }
        val used = hub.apps.filter { it.usedMinutes > 0 }.sortedByDescending { it.usedMinutes }
        if (used.isEmpty()) Txt("No app has reached a full minute yet today.", Type.body, colors.muted)
        val most = used.firstOrNull()?.usedMinutes ?: 1
        (if (showAll) used else used.take(TOP)).forEachIndexed { index, app ->
            if (index > 0) Rule()
            key(app.pkg) { AppLimitCard(app, hub, most, open == app.pkg, actions) { open = if (open == app.pkg) null else app.pkg } }
        }
        if (used.size > TOP) Action(if (showAll) "Show fewer" else "Show all ${used.size} apps", ButtonKind.TEXT) { showAll = !showAll }
    }
}
