package org.organiza.enforcement

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.organiza.domain.port.BlockTarget
import org.organiza.testing.FakeClock

class OverridePassesTest {
    private val clock = FakeClock(Instant.parse("2026-11-02T10:00:00Z"))
    private val passes = OverridePasses(clock)
    private val instagram = pkg("com.instagram.android")
    private val chrome = pkg("com.android.chrome")
    private val list = BlockList(setOf(instagram, pkg("com.zhiliaoapp.musically")), setOf(domain("reddit.com"), domain("x.com")))

    @Test
    fun `nothing is allowed before a pass is granted`() {
        assertFalse(passes.allows(BlockTarget.App(instagram)))
        assertEquals(list, passes.apply(list))
    }

    @Test
    fun `an app pass lifts that app only, until it ends`() {
        passes.grant(BlockTarget.App(instagram), Duration.ofMinutes(5))
        assertTrue(passes.allows(BlockTarget.App(instagram)))
        assertEquals(setOf(pkg("com.zhiliaoapp.musically")), passes.apply(list).packages)
        assertEquals(list.domains, passes.apply(list).domains)
        clock.advance(Duration.ofMinutes(5))
        assertFalse(passes.allows(BlockTarget.App(instagram)))
        assertEquals(list, passes.apply(list))
    }

    @Test
    fun `a site pass lifts the block-list entry in every browser`() {
        passes.grant(BlockTarget.Site(chrome, domain("reddit.com")), Duration.ofMinutes(1))
        assertTrue(passes.allows(BlockTarget.Site(pkg("org.mozilla.firefox"), domain("reddit.com"))))
        assertEquals(setOf(domain("x.com")), passes.apply(list).domains)
        assertEquals(list.packages, passes.apply(list).packages)
    }

    @Test
    fun `a short-form pass is for one app's feed and leaves the app block alone`() {
        passes.grant(BlockTarget.ShortForm(instagram), Duration.ofMinutes(1))
        assertTrue(passes.allowsShortForm(instagram))
        assertFalse(passes.allowsShortForm(pkg("com.google.android.youtube")))
        assertFalse(passes.allows(BlockTarget.App(instagram)))
    }

    @Test
    fun `the settings guard can never be passed`() {
        passes.grant(BlockTarget.Guard(pkg("com.android.settings")), Duration.ofMinutes(15))
        assertFalse(passes.allows(BlockTarget.Guard(pkg("com.android.settings"))))
        assertNull(passes.nextExpiry())
    }

    @Test
    fun `passes are limited to fifteen minutes`() {
        passes.grant(BlockTarget.App(instagram), Duration.ofHours(3))
        assertEquals(clock.now().plus(OverridePasses.MAX_PASS), passes.nextExpiry())
    }

    @Test
    fun `the next expiry is the soonest live pass`() {
        passes.grant(BlockTarget.App(instagram), Duration.ofMinutes(15))
        passes.grant(BlockTarget.Site(chrome, domain("x.com")), Duration.ofMinutes(5))
        assertEquals(clock.now().plus(Duration.ofMinutes(5)), passes.nextExpiry())
        clock.advance(Duration.ofMinutes(6))
        assertEquals(clock.now().plus(Duration.ofMinutes(9)), passes.nextExpiry())
    }

    @Test
    fun `clearing ends every pass, for when a session ends`() {
        passes.grant(BlockTarget.App(instagram), Duration.ofMinutes(5))
        passes.clear()
        assertFalse(passes.allows(BlockTarget.App(instagram)))
    }

    @Test
    fun `a limit pass is for that app's limit only`() {
        passes.grant(BlockTarget.Limit(instagram), Duration.ofMinutes(5))
        assertTrue(passes.allows(BlockTarget.Limit(instagram)))
        assertFalse(passes.allows(BlockTarget.App(instagram)))
    }
}
