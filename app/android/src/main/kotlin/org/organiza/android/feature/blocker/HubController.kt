package org.organiza.android.feature.blocker

import android.content.Intent
import java.time.Duration
import java.time.ZoneId
import org.organiza.android.OrganizaApp
import org.organiza.android.data.AndroidUsageHistory
import org.organiza.android.ui.HubApp
import org.organiza.android.ui.HubView
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
class HubController(private val app: OrganizaApp) {
    /** Read by the accessibility service on every foreground change. */
    @Volatile var limits: Map<PackageName, Duration> = emptyMap()
        private set

    @Volatile var feeds: Set<ShortFormFeed> = emptySet()
        private set

    private val usage by lazy { AndroidUsageHistory(app) }

    /** The rule tagged #focus is the focus list: calendar events and imported blocks use it too. */
    fun focusRule(stores: OrganizaApp.Storage): StoredRule =
        stores.rules.all().firstOrNull { it.calendarTag == FOCUS_TAG }
            ?: StoredRule(FOCUS_RULE, "Focus list", FOCUS_TAG, strict = false, blockShortForm = false, enabled = true, emptySet(), emptySet())
                .also { stores.scheduler.saveRule(it) }

    fun load(stores: OrganizaApp.Storage) {
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
        if (minutes == null) stores.limits.remove(app) else stores.limits.set(app, UsageLimits.nearest(Duration.ofMinutes(minutes)))
    }

    fun setFeed(feed: ShortFormFeed, on: Boolean) = edit { stores ->
        when (feed) {
            ShortFormFeed.INSTAGRAM_REELS -> stores.settings.setFlag(Setting.BLOCK_REELS, on)
            ShortFormFeed.YOUTUBE_SHORTS -> stores.settings.setFlag(Setting.BLOCK_SHORTS, on)
            else -> Unit
        }
    }

    /** Today's use for the apps on the list or with a limit, and every launchable app to add. Storage thread. */
    fun view(stores: OrganizaApp.Storage, zone: ZoneId): HubView {
        val rule = stores.rules.all().firstOrNull { it.calendarTag == FOCUS_TAG }
        val listed = rule?.packages.orEmpty()
        val allowed = usage.permitted()
        val now = app.clock.now()
        val start = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val installed = launchable()
        val rows = installed.map { (pkg, label) ->
            val parsed = parsePackage(pkg)
            val used = if (allowed && parsed != null && (parsed in listed || parsed in limits)) usage.foregroundTime(parsed, start, now) else Duration.ZERO
            val limit = parsed?.let(limits::get)
            HubApp(pkg, label, parsed in listed, used.toMinutes(), limit?.toMinutes(), limit != null && UsageLimits.over(limit, used))
        }.sortedWith(compareByDescending<HubApp> { it.onList || it.limitMinutes != null }.thenBy { it.label.lowercase() })
        return HubView(rows, rule?.domains.orEmpty().map { it.value }.sorted(), ShortFormFeed.INSTAGRAM_REELS in feeds, ShortFormFeed.YOUTUBE_SHORTS in feeds, allowed)
    }

    private fun edit(work: (OrganizaApp.Storage) -> Unit) = app.onStores { stores ->
        work(stores)
        load(stores)
        app.requestUiRefresh()
    }

    private var launchableCache: List<Pair<String, String>>? = null

    private fun launchable(): List<Pair<String, String>> = launchableCache ?: run {
        val pm = app.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(main, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != app.packageName }
            .distinctBy { it.first }
            .also { launchableCache = it }
    }

    private fun parsePackage(raw: String): PackageName? = (PackageName.parse(raw) as? Parsed.Valid)?.value

    companion object {
        const val FOCUS_TAG = "#focus"
        private const val FOCUS_RULE = "focus-list"
    }
}
