package org.organiza.enforcement

import org.organiza.domain.DomainName
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.port.BlockTarget

/**
 * What the accessibility layer read from the current screen. [guardedText] must stay
 * empty unless [ContentPolicy.isGuarded] is true for the app, so that visible text from
 * other apps is never collected.
 */
public data class ScreenSnapshot(
    val app: PackageName,
    val urlBarText: String? = null,
    val viewIds: Set<String> = emptySet(),
    val guardedText: Set<String> = emptySet(),
)

public enum class ContentDecision { ALLOW, BLOCK_DOMAIN, BLOCK_SHORT_FORM, BLOCK_TAMPER }

/** A short video feed that can be blocked on its own, inside the app or in a browser (ADR-0018). */
public enum class ShortFormFeed { YOUTUBE_SHORTS, INSTAGRAM_REELS, FACEBOOK_REELS, TIKTOK }

public class ContentPolicy(private val appLabel: String) {
    public fun decide(
        snapshot: ScreenSnapshot,
        blockList: BlockList,
        blockShortForm: Boolean,
        strict: Boolean,
    ): ContentDecision = when (target(snapshot, blockList, blockShortForm, strict)) {
        is BlockTarget.Guard -> ContentDecision.BLOCK_TAMPER
        is BlockTarget.Site -> ContentDecision.BLOCK_DOMAIN
        is BlockTarget.ShortForm -> ContentDecision.BLOCK_SHORT_FORM
        is BlockTarget.App, is BlockTarget.Limit, null -> ContentDecision.ALLOW
    }

    /** What to intervene on for this screen, or null to allow it. [blockShortForm] means every feed. */
    public fun target(
        snapshot: ScreenSnapshot,
        blockList: BlockList,
        blockShortForm: Boolean,
        strict: Boolean,
    ): BlockTarget? = target(snapshot, blockList, if (blockShortForm) ShortFormFeed.entries.toSet() else emptySet(), strict)

    public fun target(
        snapshot: ScreenSnapshot,
        blockList: BlockList,
        feeds: Set<ShortFormFeed>,
        strict: Boolean,
    ): BlockTarget? {
        if (strict && namesThisApp(snapshot)) return BlockTarget.Guard(snapshot.app)
        val url = urlBarId(snapshot.app)?.let { snapshot.urlBarText }?.let(::parseUrl)
        url?.let { blockList.entryFor(it.host) }?.let { return BlockTarget.Site(snapshot.app, it) }
        if (url.isShortForm(feeds) || snapshot.viewIds.any { SHORT_FORM_VIEW_IDS[it] in feeds }) {
            return BlockTarget.ShortForm(snapshot.app)
        }
        return null
    }

    private fun namesThisApp(snapshot: ScreenSnapshot): Boolean =
        isGuarded(snapshot.app) && snapshot.guardedText.any { it.contains(appLabel, ignoreCase = true) }

    private data class ParsedUrl(val host: DomainName, val path: String)

    private fun parseUrl(raw: String): ParsedUrl? {
        // Chrome appends labels such as "Connection is not secure" to the bar's text. A search
        // starts with an ordinary word, so its first token does not parse as a host below.
        val text = raw.trim().substringBefore(' ')
        val rest = text.substringAfter("://", text)
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = DomainName.parse(authority.substringAfterLast('@').substringBefore(':'))
        if (host !is Parsed.Valid) return null
        val path = rest.drop(authority.length).substringBefore('?').substringBefore('#')
        return ParsedUrl(host.value, path)
    }

    private fun ParsedUrl?.isShortForm(feeds: Set<ShortFormFeed>): Boolean {
        val url = this ?: return false
        return SHORT_FORM_PATHS.any { (feed, site, prefix) ->
            feed in feeds && site.covers(url.host) && (url.path == prefix || url.path.startsWith("$prefix/"))
        }
    }

    public companion object {
        private val URL_BAR_IDS = mapOf(
            "com.android.chrome" to "com.android.chrome:id/url_bar",
            "com.brave.browser" to "com.brave.browser:id/url_bar",
            "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
            "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        )

        private val GUARDED_PACKAGES = setOf(
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
        )

        private val SHORT_FORM_PATHS = listOf(
            Triple(ShortFormFeed.YOUTUBE_SHORTS, site("youtube.com"), "/shorts"),
            Triple(ShortFormFeed.INSTAGRAM_REELS, site("instagram.com"), "/reels"),
            Triple(ShortFormFeed.INSTAGRAM_REELS, site("instagram.com"), "/reel"),
            Triple(ShortFormFeed.FACEBOOK_REELS, site("facebook.com"), "/reel"),
            Triple(ShortFormFeed.TIKTOK, site("tiktok.com"), ""),
        )

        // Private view IDs, which apps can rename in any release. A Shorts tab label would
        // be no use here: it is on screen all the time, so matching it blocks the whole app.
        private val SHORT_FORM_VIEW_IDS = mapOf(
            "com.google.android.youtube:id/reel_recycler" to ShortFormFeed.YOUTUBE_SHORTS,
            "com.google.android.youtube:id/reel_player_page_container" to ShortFormFeed.YOUTUBE_SHORTS,
            "com.instagram.android:id/clips_viewer_view_pager" to ShortFormFeed.INSTAGRAM_REELS,
        )

        private fun site(raw: String): DomainName = (DomainName.parse(raw) as Parsed.Valid).value

        /** The URL bar's view ID, or null when [app] is not a known browser. */
        public fun urlBarId(app: PackageName): String? = URL_BAR_IDS[app.value]

        /** True for system screens that can remove Organiza. Only these expose visible text. */
        public fun isGuarded(app: PackageName): Boolean = app.value in GUARDED_PACKAGES
    }
}
