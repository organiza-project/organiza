package org.organiza.enforcement

import java.time.Duration
import java.time.Instant
import org.organiza.domain.PackageName
import org.organiza.domain.port.BlockSurface
import org.organiza.domain.port.BlockTarget
import org.organiza.domain.port.Clock

/**
 * Turns foreground changes into interventions. Not thread-safe: call it from one thread.
 *
 * Debouncing is leading-edge, so the first event for an app always acts immediately and
 * only repeats of the same app inside the window are dropped. [ignored] holds packages
 * that can take window focus without replacing the app underneath, such as the
 * notification shade, the keyboard and Organiza's own overlay. [exempt] holds apps that
 * must stay reachable whatever the list says, to prevent lockout (ADR-0007).
 *
 * A block sends the person home and shows the intervention there. It stays up on [homes]
 * until the person chooses (ADR-0016), and closes when they reach any other allowed app. An app
 * just sent home can still report a window for a moment, as Chrome does, so for [SETTLE] its own
 * events are not taken as the person going back to it.
 */
public class BlockEngine(
    private val surface: BlockSurface,
    private val clock: Clock,
    private val debounce: Duration,
    private val ignored: Set<PackageName>,
    private val exempt: Set<PackageName>,
    private val homes: Set<PackageName> = emptySet(),
    private val passes: OverridePasses = OverridePasses(clock),
) {
    private var blockList = BlockList.EMPTY
    private var sessionActive = false
    private var foreground: PackageName? = null
    private var handledAt: Instant = Instant.MIN
    private var shown: BlockTarget? = null
    private var sentHomeAt: Instant = Instant.MIN

    /** [active] is false once no session runs, which also closes a short-form or guard intervention. */
    public fun update(list: BlockList, active: Boolean = list != BlockList.EMPTY) {
        blockList = list
        sessionActive = active
        shown?.let { if (!stillBlocked(it)) dismiss() }
        recheck()
    }

    public fun onForeground(app: PackageName) {
        if (app in ignored) return
        val now = clock.now()
        if (app == foreground && Duration.between(handledAt, now) < debounce) return
        foreground = app
        handledAt = now
        when {
            blocksApp(app) -> block(BlockTarget.App(app))
            app in homes -> Unit
            app == shown?.app && Duration.between(sentHomeAt, now) < SETTLE -> Unit
            else -> dismiss()
        }
    }

    /** Blocks [target] regardless of the list, for in-app content and the settings guard. */
    public fun block(target: BlockTarget) {
        if (target.app in exempt) return
        if (passes.allows(target)) return
        foreground = target.app
        handledAt = clock.now()
        shown = target
        sentHomeAt = handledAt
        surface.showOverlay(target)
        surface.goHome()
    }

    /** Applies the list again to the app on screen, for when a pass has ended. */
    public fun recheck() {
        val app = foreground ?: return
        if (blocksApp(app)) block(BlockTarget.App(app))
    }

    /** Closes the intervention, after the person chose to stay focused or took a pass. */
    public fun dismiss() {
        if (shown == null) return
        shown = null
        surface.dismissOverlay()
    }

    private fun blocksApp(app: PackageName): Boolean =
        blockList.blocks(app) && app !in exempt && !passes.allows(BlockTarget.App(app))

    private fun stillBlocked(target: BlockTarget): Boolean = when (target) {
        is BlockTarget.App -> blockList.blocks(target.app)
        is BlockTarget.Site -> target.entry in blockList.domains
        is BlockTarget.ShortForm, is BlockTarget.Guard -> sessionActive
    }

    public companion object {
        /** How long events from an app just sent home are taken as stale. */
        public val SETTLE: Duration = Duration.ofSeconds(2)
    }
}
