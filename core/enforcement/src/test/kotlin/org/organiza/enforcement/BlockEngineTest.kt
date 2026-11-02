package org.organiza.enforcement

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.domain.port.BlockAction
import org.organiza.domain.port.BlockTarget
import org.organiza.testing.FakeClock
import org.organiza.testing.RecordingBlockSurface

class BlockEngineTest {
    private val instagram = pkg("com.instagram.android")
    private val launcher = pkg("com.android.launcher3")
    private val systemUi = pkg("com.android.systemui")
    private val dialer = pkg("com.android.dialer")
    private val clock = FakeClock(Instant.parse("2026-11-01T10:00:00Z"))
    private val surface = RecordingBlockSurface()
    private val home = pkg("com.google.android.apps.nexuslauncher")
    private val passes = OverridePasses(clock)
    private val engine = BlockEngine(
        surface,
        clock,
        Duration.ofMillis(300),
        ignored = setOf(systemUi),
        exempt = setOf(dialer, home),
        homes = setOf(home),
        passes = passes,
    ).apply {
        update(BlockList(setOf(instagram), emptySet()))
    }

    @Test
    fun `blocked app gets the overlay and is sent home`() {
        engine.onForeground(instagram)
        assertEquals(listOf(BlockAction.ShowOverlay(BlockTarget.App(instagram)), BlockAction.GoHome), surface.actions)
    }

    @Test
    fun `allowed app does nothing`() {
        engine.onForeground(launcher)
        assertEquals(emptyList(), surface.actions)
    }

    @Test
    fun `repeat events for the same app inside the window are debounced`() {
        engine.onForeground(instagram)
        clock.advance(Duration.ofMillis(100))
        engine.onForeground(instagram)
        assertEquals(2, surface.actions.size)
    }

    @Test
    fun `the same app is enforced again after the window`() {
        engine.onForeground(instagram)
        clock.advance(Duration.ofMillis(301))
        engine.onForeground(instagram)
        assertEquals(4, surface.actions.size)
    }

    @Test
    fun `a quick return to a blocked app is not debounced away`() {
        engine.onForeground(instagram)
        engine.onForeground(launcher)
        clock.advance(Duration.ofMillis(10))
        engine.onForeground(instagram)
        assertEquals(true, surface.overlayVisible)
        assertEquals(BlockAction.GoHome, surface.actions.last())
        assertEquals(2, surface.actions.count { it is BlockAction.ShowOverlay })
    }

    @Test
    fun `reaching an allowed app dismisses the overlay`() {
        engine.onForeground(instagram)
        engine.onForeground(launcher)
        assertEquals(false, surface.overlayVisible)
    }

    @Test
    fun `ignored packages never change enforcement state`() {
        engine.onForeground(instagram)
        engine.onForeground(systemUi)
        assertEquals(true, surface.overlayVisible)
        assertEquals(2, surface.actions.size)
    }

    @Test
    fun `a new block list applies to the app already on screen`() {
        engine.onForeground(launcher)
        engine.update(BlockList(setOf(launcher), emptySet()))
        assertEquals(BlockAction.ShowOverlay(BlockTarget.App(launcher)), surface.actions.first())
    }

    @Test
    fun `clearing the block list dismisses a visible overlay`() {
        engine.onForeground(instagram)
        engine.update(BlockList.EMPTY)
        assertEquals(false, surface.overlayVisible)
    }

    @Test
    fun `an explicit block covers in-app and tamper decisions`() {
        engine.onForeground(launcher)
        engine.block(BlockTarget.App(launcher))
        assertEquals(listOf(BlockAction.ShowOverlay(BlockTarget.App(launcher)), BlockAction.GoHome), surface.actions)
    }

    @Test
    fun `exempt apps are never blocked even when listed`() {
        engine.update(BlockList(setOf(dialer), emptySet()))
        engine.onForeground(dialer)
        engine.block(BlockTarget.App(dialer))
        assertEquals(emptyList(), surface.actions)
    }

    @Test
    fun `reaching an exempt app dismisses the overlay`() {
        engine.onForeground(instagram)
        engine.onForeground(dialer)
        assertEquals(false, surface.overlayVisible)
    }

    @Test
    fun `the intervention stays up on the home screen it sent the person to`() {
        engine.onForeground(instagram)
        engine.onForeground(home)
        assertEquals(true, surface.overlayVisible)
    }

    @Test
    fun `the person's choice closes the intervention`() {
        engine.onForeground(instagram)
        engine.onForeground(home)
        engine.dismiss()
        assertEquals(false, surface.overlayVisible)
        assertEquals(BlockAction.DismissOverlay, surface.actions.last())
    }

    @Test
    fun `a passed app opens without an intervention`() {
        passes.grant(BlockTarget.App(instagram), Duration.ofMinutes(5))
        engine.onForeground(instagram)
        assertEquals(emptyList(), surface.actions)
    }

    @Test
    fun `an expired pass blocks the app on screen at the recheck`() {
        passes.grant(BlockTarget.App(instagram), Duration.ofMinutes(1))
        engine.onForeground(instagram)
        clock.advance(Duration.ofMinutes(1))
        engine.recheck()
        assertEquals(listOf(BlockAction.ShowOverlay(BlockTarget.App(instagram)), BlockAction.GoHome), surface.actions)
    }

    @Test
    fun `a site pass is checked against the blocked entry`() {
        val chrome = pkg("com.android.chrome")
        val reddit = BlockTarget.Site(chrome, domain("reddit.com"))
        passes.grant(reddit, Duration.ofMinutes(5))
        engine.block(reddit)
        engine.block(BlockTarget.Site(chrome, domain("x.com")))
        assertEquals(listOf(BlockAction.ShowOverlay(BlockTarget.Site(chrome, domain("x.com"))), BlockAction.GoHome), surface.actions)
    }

    @Test
    fun `the settings guard ignores passes`() {
        val settings = pkg("com.android.settings")
        passes.grant(BlockTarget.App(settings), Duration.ofMinutes(5))
        engine.block(BlockTarget.Guard(settings))
        assertEquals(BlockAction.ShowOverlay(BlockTarget.Guard(settings)), surface.actions.first())
    }

    @Test
    fun `ending the session closes an intervention left on the home screen`() {
        engine.onForeground(instagram)
        engine.onForeground(home)
        engine.update(BlockList.EMPTY)
        assertEquals(false, surface.overlayVisible)
    }
}
