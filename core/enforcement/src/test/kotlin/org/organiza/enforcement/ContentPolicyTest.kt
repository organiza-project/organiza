package org.organiza.enforcement

import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.domain.port.BlockTarget

class ContentPolicyTest {
    private val chrome = pkg("com.android.chrome")
    private val youtube = pkg("com.google.android.youtube")
    private val settings = pkg("com.android.settings")
    private val list = BlockList(emptySet(), setOf(domain("reddit.com")))
    private val policy = ContentPolicy(appLabel = "Organiza")

    private fun decide(
        snapshot: ScreenSnapshot,
        blockShortForm: Boolean = false,
        strict: Boolean = false,
    ) = policy.decide(snapshot, list, blockShortForm, strict)

    @Test
    fun `reads the url bar only for known browsers`() {
        assertEquals("com.android.chrome:id/url_bar", ContentPolicy.urlBarId(chrome))
        assertEquals(null, ContentPolicy.urlBarId(youtube))
    }

    @Test
    fun `blocks a browser on a blocked domain`() {
        val snapshot = ScreenSnapshot(chrome, urlBarText = "https://old.reddit.com/r/all")
        assertEquals(ContentDecision.BLOCK_DOMAIN, decide(snapshot))
    }

    @Test
    fun `handles url bars that hide the scheme`() {
        assertEquals(ContentDecision.BLOCK_DOMAIN, decide(ScreenSnapshot(chrome, urlBarText = "reddit.com/r/all")))
    }

    @Test
    fun `labels chrome appends after the url do not hide it`() {
        val labelled = ScreenSnapshot(
            chrome,
            urlBarText = "old.reddit.com/login/?reason=lor2 Connection is not secure",
        )
        assertEquals(ContentDecision.BLOCK_DOMAIN, decide(labelled))
        val shorts = ScreenSnapshot(chrome, urlBarText = "m.youtube.com/shorts/abc Connection is not secure")
        assertEquals(ContentDecision.BLOCK_SHORT_FORM, decide(shorts, blockShortForm = true))
    }

    @Test
    fun `allows other domains and search text`() {
        assertEquals(ContentDecision.ALLOW, decide(ScreenSnapshot(chrome, urlBarText = "example.com")))
        assertEquals(ContentDecision.ALLOW, decide(ScreenSnapshot(chrome, urlBarText = "example.com Connection is not secure")))
        assertEquals(ContentDecision.ALLOW, decide(ScreenSnapshot(chrome, urlBarText = "how to use reddit.com well")))
        assertEquals(ContentDecision.ALLOW, decide(ScreenSnapshot(chrome, urlBarText = null)))
    }

    @Test
    fun `ignores url text in apps that are not browsers`() {
        assertEquals(ContentDecision.ALLOW, decide(ScreenSnapshot(youtube, urlBarText = "reddit.com")))
    }

    @Test
    fun `short form urls block only when the setting is on`() {
        val shorts = ScreenSnapshot(chrome, urlBarText = "m.youtube.com/shorts/abc")
        assertEquals(ContentDecision.BLOCK_SHORT_FORM, decide(shorts, blockShortForm = true))
        assertEquals(ContentDecision.ALLOW, decide(shorts, blockShortForm = false))
    }

    @Test
    fun `a normal video on the same site is not short form`() {
        val watch = ScreenSnapshot(chrome, urlBarText = "youtube.com/watch?v=shorts")
        assertEquals(ContentDecision.ALLOW, decide(watch, blockShortForm = true))
    }

    @Test
    fun `short form players are matched by view id`() {
        val player = ScreenSnapshot(youtube, viewIds = setOf("com.google.android.youtube:id/reel_recycler"))
        assertEquals(ContentDecision.BLOCK_SHORT_FORM, decide(player, blockShortForm = true))
    }

    @Test
    fun `a Shorts tab label alone does not block the app`() {
        val home = ScreenSnapshot(
            youtube,
            viewIds = setOf("com.google.android.youtube:id/pivot_bar"),
            guardedText = setOf("Shorts"),
        )
        assertEquals(ContentDecision.ALLOW, decide(home, blockShortForm = true))
    }

    @Test
    fun `strict mode guards settings pages that name the app`() {
        val page = ScreenSnapshot(settings, guardedText = setOf("Installed apps", "ORGANIZA"))
        assertEquals(ContentDecision.BLOCK_TAMPER, decide(page, strict = true))
    }

    @Test
    fun `settings stay usable outside strict mode and on other pages`() {
        assertEquals(
            ContentDecision.ALLOW,
            decide(ScreenSnapshot(settings, guardedText = setOf("Organiza")), strict = false),
        )
        assertEquals(
            ContentDecision.ALLOW,
            decide(ScreenSnapshot(settings, guardedText = setOf("Wi-Fi", "Display")), strict = true),
        )
    }

    @Test
    fun `visible text is only collected for guarded packages`() {
        assertEquals(true, ContentPolicy.isGuarded(settings))
        assertEquals(true, ContentPolicy.isGuarded(pkg("com.google.android.packageinstaller")))
        assertEquals(false, ContentPolicy.isGuarded(chrome))
    }

    @Test
    fun `a site block names the list entry it matched, for a pass`() {
        val snapshot = ScreenSnapshot(chrome, urlBarText = "https://old.reddit.com/r/all")
        assertEquals(BlockTarget.Site(chrome, domain("reddit.com")), policy.target(snapshot, list, false, false))
    }

    @Test
    fun `short-form and guard blocks name their app`() {
        val shorts = ScreenSnapshot(youtube, viewIds = setOf("com.google.android.youtube:id/reel_recycler"))
        assertEquals(BlockTarget.ShortForm(youtube), policy.target(shorts, list, true, false))
        val guard = ScreenSnapshot(settings, guardedText = setOf("Organiza"))
        assertEquals(BlockTarget.Guard(settings), policy.target(guard, list, false, true))
        assertEquals(null, policy.target(ScreenSnapshot(youtube), list, true, false))
    }

    @Test
    fun `each short video feed can be blocked on its own`() {
        val instagram = pkg("com.instagram.android")
        val reels = ScreenSnapshot(instagram, viewIds = setOf("com.instagram.android:id/clips_viewer_view_pager"))
        val shorts = ScreenSnapshot(youtube, viewIds = setOf("com.google.android.youtube:id/reel_recycler"))
        val reelsOnly = setOf(ShortFormFeed.INSTAGRAM_REELS)
        assertEquals(BlockTarget.ShortForm(instagram), policy.target(reels, list, reelsOnly, false))
        assertEquals(null, policy.target(shorts, list, reelsOnly, false))
        assertEquals(BlockTarget.ShortForm(youtube), policy.target(shorts, list, setOf(ShortFormFeed.YOUTUBE_SHORTS), false))
    }

    @Test
    fun `short video paths in a browser follow the same feeds`() {
        val shortsUrl = ScreenSnapshot(chrome, urlBarText = "m.youtube.com/shorts/abc")
        assertEquals(null, policy.target(shortsUrl, list, setOf(ShortFormFeed.INSTAGRAM_REELS), false))
        assertEquals(BlockTarget.ShortForm(chrome), policy.target(shortsUrl, list, setOf(ShortFormFeed.YOUTUBE_SHORTS), false))
    }
}
