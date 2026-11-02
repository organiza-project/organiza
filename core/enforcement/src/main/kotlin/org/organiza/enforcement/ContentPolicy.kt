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
        is BlockTarget.App, null -> ContentDecision.ALLOW
    }

    /** What to intervene on for this screen, or null to allow it. */
    public fun target(
        snapshot: ScreenSnapshot,
        blockList: BlockList,
        blockShortForm: Boolean,
        strict: Boolean,
    ): BlockTarget? {
        if (strict && namesThisApp(snapshot)) return BlockTarget.Guard(snapshot.app)
        val url = urlBarId(snapshot.app)?.let { snapshot.urlBarText }?.let(::parseUrl)
        url?.let { blockList.entryFor(it.host) }?.let { return BlockTarget.Site(snapshot.app, it) }
        if (blockShortForm && (url.isShortForm() || snapshot.viewIds.any(SHORT_FORM_VIEW_IDS::contains))) {
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

    private fun ParsedUrl?.isShortForm(): Boolean {
        val url = this ?: return false
        return SHORT_FORM_PATHS.any { (site, prefix) ->
            site.covers(url.host) && (url.path == prefix || url.path.startsWith("$prefix/"))
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
            site("youtube.com") to "/shorts",
            site("instagram.com") to "/reels",
            site("instagram.com") to "/reel",
            site("facebook.com") to "/reel",
            site("tiktok.com") to "",
        )

        // Private view IDs, which apps can rename in any release. A Shorts tab label would
        // be no use here: it is on screen all the time, so matching it blocks the whole app.
        private val SHORT_FORM_VIEW_IDS = setOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_player_page_container",
            "com.instagram.android:id/clips_viewer_view_pager",
        )

        private fun site(raw: String): DomainName = (DomainName.parse(raw) as Parsed.Valid).value

        /** The URL bar's view ID, or null when [app] is not a known browser. */
        public fun urlBarId(app: PackageName): String? = URL_BAR_IDS[app.value]

        /** True for system screens that can remove Organiza. Only these expose visible text. */
        public fun isGuarded(app: PackageName): Boolean = app.value in GUARDED_PACKAGES
    }
}
