package org.organiza.enforcement

import java.time.Duration
import java.time.Instant
import org.organiza.domain.PackageName
import org.organiza.domain.port.BlockSurface
import org.organiza.domain.port.Clock

/**
 * Turns foreground changes into block actions. Not thread-safe: call it from one thread.
 *
 * Debouncing is leading-edge, so the first event for an app always acts immediately and
 * only repeats of the same app inside the window are dropped. [ignored] holds packages
 * that can take window focus without replacing the app underneath, such as the
 * notification shade, the keyboard and Organiza's own overlay. [exempt] holds apps that
 * must stay reachable whatever the list says, to prevent lockout (ADR-0007).
 */
public class BlockEngine(
    private val surface: BlockSurface,
    private val clock: Clock,
    private val debounce: Duration,
    private val ignored: Set<PackageName>,
    private val exempt: Set<PackageName>,
) {
    private var blockList = BlockList.EMPTY
    private var foreground: PackageName? = null
    private var handledAt: Instant = Instant.MIN
    private var overlayVisible = false

    public fun update(list: BlockList) {
        blockList = list
        val app = foreground ?: return
        if (list.blocks(app) && app !in exempt) block(app) else dismiss()
    }

    public fun onForeground(app: PackageName) {
        if (app in ignored) return
        val now = clock.now()
        if (app == foreground && Duration.between(handledAt, now) < debounce) return
        foreground = app
        handledAt = now
        if (blockList.blocks(app) && app !in exempt) block(app) else dismiss()
    }

    /** Blocks [app] regardless of the list, for in-app content and tamper decisions. */
    public fun block(app: PackageName) {
        if (app in exempt) return
        foreground = app
        handledAt = clock.now()
        overlayVisible = true
        surface.showOverlay(app)
        surface.goHome()
    }

    private fun dismiss() {
        if (!overlayVisible) return
        overlayVisible = false
        surface.dismissOverlay()
    }
}
