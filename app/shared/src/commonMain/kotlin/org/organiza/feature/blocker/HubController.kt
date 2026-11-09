package org.organiza.feature.blocker

import java.time.Duration
import java.time.ZoneId
import org.organiza.app.OrganizaCore
import org.organiza.ui.HubView
import org.organiza.domain.DomainName
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.enforcement.ShortFormFeed
import org.organiza.enforcement.UsageLimits
import org.organiza.storage.Setting
import org.organiza.storage.StoredRule

/**
 * The distraction blocker hub (ADR-0018): the focus list that sessions block, daily limits per app,
 * and the always-on short video switches. It keeps the limits and feeds the services read.
 */
class HubController(private val app: OrganizaCore) {
    /** Read by the accessibility service on every foreground change. */
    @Volatile var limits: Map<PackageName, Duration> = emptyMap()
        private set

    @Volatile var feeds: Set<ShortFormFeed> = emptySet()
        private set

    private val usage get() = app.platform.usage

    fun focusRule(stores: OrganizaCore.Storage): StoredRule =
        stores.rules.all().firstOrNull { it.calendarTag == FOCUS_TAG }
            ?: StoredRule(FOCUS_RULE, "Focus list", FOCUS_TAG, strict = false, blockShortForm = false, enabled = true, emptySet(), emptySet())
                .also { stores.scheduler.saveRule(it) }

    fun load(stores: OrganizaCore.Storage) {
        limits = stores.limits.all()
        feeds = buildSet {
            if (stores.settings.flag(Setting.BLOCK_REELS)) add(ShortFormFeed.INSTAGRAM_REELS)
            if (stores.settings.flag(Setting.BLOCK_SHORTS)) add(ShortFormFeed.YOUTUBE_SHORTS)
        }
    }

    fun setApp(pkg: String, on: Boolean) = edit { stores ->
        val app = parsePackage(pkg) ?: return@edit
        val rule = focusRule(stores)
        stores.scheduler.saveRule(rule.copy(packages = if (on) rule.packages + app else rule.packages - app))
    }

    fun addSite(raw: String) = edit { stores ->
        when (val parsed = DomainName.parse(raw.trim().removePrefix("https://").removePrefix("http://").substringBefore('/'))) {
            is Parsed.Valid -> {
                val rule = focusRule(stores)
                stores.scheduler.saveRule(rule.copy(domains = rule.domains + parsed.value))
            }
            is Parsed.Invalid -> app.showMessage("That is not a website address")
        }
    }

    fun removeSite(domain: String) = edit { stores ->
        val rule = focusRule(stores)
        stores.scheduler.saveRule(rule.copy(domains = rule.domains.filterNot { it.value == domain }.toSet()))
    }

    fun setLimit(pkg: String, minutes: Long?) = edit { stores ->
        val app = parsePackage(pkg) ?: return@edit
        when {
            minutes == null -> stores.limits.remove(app)
            minutes in UsageLimits.MINUTES.first..UsageLimits.MINUTES.last -> stores.limits.set(app, Duration.ofMinutes(minutes))
        }
    }

    fun setFeed(feed: ShortFormFeed, on: Boolean) = edit { stores ->
        when (feed) {
            ShortFormFeed.INSTAGRAM_REELS -> stores.settings.setFlag(Setting.BLOCK_REELS, on)
            ShortFormFeed.YOUTUBE_SHORTS -> stores.settings.setFlag(Setting.BLOCK_SHORTS, on)
            else -> Unit
        }
    }

    /** Today's use of every launchable app, its category and limit, and the day's screen time. Storage thread. */
    fun view(stores: OrganizaCore.Storage, zone: ZoneId): HubView {
        val allowed = usage.permitted()
        val now = app.clock.now()
        val start = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        // One pass over today's events covers every app, rather than one query per row.
        val today = if (allowed) usage.perApp(start, now).mapKeys { it.key.value } else emptyMap()
        return hubView(app.platform.launchable(), today, stores.rules.all(), limits, feeds, allowed, app.devices::elsewhere)
    }

    /** The category each launchable app declares, by package. Usage is only ever sent to the AI this way. */
    fun categories(): Map<String, String> = app.platform.launchable().associate { it.pkg to it.category }

    private fun edit(work: (OrganizaCore.Storage) -> Unit) = app.onStores { stores ->
        work(stores)
        load(stores)
        app.requestUiRefresh()
    }

    private fun parsePackage(raw: String): PackageName? = (PackageName.parse(raw) as? Parsed.Valid)?.value

    private companion object {
        const val FOCUS_RULE = "focus-list"
    }
}
