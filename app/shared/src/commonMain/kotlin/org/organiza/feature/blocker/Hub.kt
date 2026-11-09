package org.organiza.feature.blocker

import java.time.Duration
import org.organiza.app.InstalledApp
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.enforcement.ShortFormFeed
import org.organiza.enforcement.UsageLimits
import org.organiza.presentation.Format
import org.organiza.storage.StoredRule
import org.organiza.ui.HubApp
import org.organiza.ui.HubView

/** The rule tagged #focus is the focus list: calendar events and imported blocks use it too. */
const val FOCUS_TAG = "#focus"

/**
 * The distraction hub (ADR-0018) from today's use per package and every launchable app as package,
 * label and category. Launchers and system UI have no launcher entry, so they never count as screen time.
 */
fun hubView(
    launchable: List<InstalledApp>,
    today: Map<String, Duration>,
    rules: List<StoredRule>,
    limits: Map<PackageName, Duration>,
    feeds: Set<ShortFormFeed>,
    allowed: Boolean,
): HubView {
    val rule = rules.firstOrNull { it.calendarTag == FOCUS_TAG }
    val listed = rule?.packages.orEmpty()
    val rows = launchable.map { (pkg, label, category) ->
        val parsed = (PackageName.parse(pkg) as? Parsed.Valid)?.value
        val used = today[pkg] ?: Duration.ZERO
        val limit = parsed?.let(limits::get)
        HubApp(pkg, label, parsed in listed, used.toMinutes(), limit?.toMinutes(), limit != null && UsageLimits.over(limit, used), category)
    }.sortedWith(compareByDescending<HubApp> { it.onList || it.limitMinutes != null }.thenBy { it.label.lowercase() })
    val screenTime = if (allowed) Format.duration(rows.fold(Duration.ZERO) { sum, row -> sum + (today[row.pkg] ?: Duration.ZERO) }) else null
    return HubView(rows, rule?.domains.orEmpty().map { it.value }.sorted(), ShortFormFeed.INSTAGRAM_REELS in feeds, ShortFormFeed.YOUTUBE_SHORTS in feeds, allowed, screenTime)
}
