package org.organiza.android.feature.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import java.time.Duration
import org.organiza.android.OrganizaApp
import org.organiza.android.R
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.port.BlockTarget
import org.organiza.enforcement.BlockEngine
import org.organiza.enforcement.ContentPolicy
import org.organiza.enforcement.InterventionFlow
import org.organiza.enforcement.InterventionOutcome
import org.organiza.enforcement.LockoutExemptions
import org.organiza.enforcement.ScreenSnapshot
import org.organiza.enforcement.SessionPolicy
import org.organiza.enforcement.ShortFormFeed
import org.organiza.enforcement.UsageLimits
import org.organiza.android.data.AndroidUsageHistory
import java.time.ZoneId
import java.util.concurrent.Executors

class OrganizaAccessibilityService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val app get() = OrganizaApp.from(this)
    private val contentPolicy by lazy { ContentPolicy(getString(R.string.app_name)) }
    private var engine: BlockEngine? = null
    private var subscription: AutoCloseable? = null
    private var surface: InterventionOverlay? = null
    private var lastInspection = 0L
    private var trailingSource: PackageName? = null
    private val trailingInspection = Runnable {
        val blockEngine = engine
        val source = trailingSource
        if (blockEngine != null && source != null) {
            lastInspection = SystemClock.elapsedRealtime()
            inspect(source, app.core.session.current, blockEngine)
        }
    }

    private val passEnded = Runnable {
        engine?.recheck()
        current?.let(::checkLimit)
    }

    // Daily limits (ADR-0018): usage is read off the main thread, then acted on here.
    private val usage by lazy { AndroidUsageHistory(this) }
    private val limitWorker = Executors.newSingleThreadExecutor()
    private var ignored: Set<PackageName> = emptySet()
    private var current: PackageName? = null
    private val limitTick = Runnable { current?.let(::checkLimit) }

    override fun onServiceConnected() {
        release()
        val blockSurface = InterventionOverlay(this, app, ::chosen)
        surface = blockSurface
        val launchers = launcherPackages()
        ignored = transientPackages()
        val blockEngine = BlockEngine(
            blockSurface,
            app.core.clock,
            DEBOUNCE,
            ignored,
            exemptPackages(launchers),
            homes = parsePackages(launchers),
            passes = app.core.passes,
        )
        engine = blockEngine
        app.core.stayFocusedForTesting = { main.post { blockEngine.dismiss() } }
        subscription = app.core.session.subscribe { policy ->
            main.post { blockEngine.update(policy.blockList, policy != SessionPolicy.IDLE) }
        }
    }

    /** The person finished an intervention. An override grants its pass and reopens what was blocked. */
    private fun chosen(flow: InterventionFlow, outcome: InterventionOutcome) {
        if (outcome is InterventionOutcome.Overrode) {
            app.core.passes.grant(flow.target, Duration.ofMinutes(outcome.minutes.toLong()))
            schedulePassEnd()
        }
        engine?.dismiss()
        if (outcome is InterventionOutcome.Overrode) reopen(flow.target)
    }

    private fun schedulePassEnd() {
        main.removeCallbacks(passEnded)
        main.removeCallbacks(limitTick)
        val until = app.core.passes.nextExpiry() ?: return
        main.postDelayed(passEnded, Duration.between(app.core.clock.now(), until).toMillis() + PASS_GRACE_MS)
    }

    private fun reopen(target: BlockTarget) {
        val launch = packageManager.getLaunchIntentForPackage(target.app.value) ?: return
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val blockEngine = engine ?: return
        val source = (PackageName.parse(event.packageName?.toString().orEmpty()) as? Parsed.Valid)?.value ?: return
        val policy = app.core.session.current
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Tracked while idle too, so a new session can act on the app already open.
                blockEngine.onForeground(source)
                if (source !in ignored) {
                    current = source
                    checkLimit(source)
                }
                inspect(source, policy, blockEngine)
                app.core.requestReconcile()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = SystemClock.elapsedRealtime()
                val wait = INSPECTION_INTERVAL_MS - (now - lastInspection)
                if (wait > 0) {
                    // Check again once the window closes. Dropping this event outright would
                    // miss a page that finishes drawing inside the window.
                    main.removeCallbacks(trailingInspection)
                    trailingSource = source
                    main.postDelayed(trailingInspection, wait)
                    return
                }
                lastInspection = now
                inspect(source, policy, blockEngine)
            }
        }
    }

    /** Short video feeds to block now: every feed in a session that asks for it, plus the hub's switches. */
    private fun feeds(policy: SessionPolicy): Set<ShortFormFeed> =
        (if (policy.blockShortForm) ShortFormFeed.entries.toSet() else emptySet()) + app.core.hub.feeds

    private fun inspect(source: PackageName, policy: SessionPolicy, blockEngine: BlockEngine) {
        val feeds = feeds(policy)
        if (policy == SessionPolicy.IDLE && feeds.isEmpty()) return
        val snapshot = snapshot(source, policy, feeds) ?: return
        // Passes are checked by the engine, so the policy sees the full list.
        contentPolicy.target(snapshot, policy.blockList, feeds, policy.strict)?.let(blockEngine::block)
    }

    /** Blocks [source] once today's use reaches its limit, or checks again when it would. */
    private fun checkLimit(source: PackageName) {
        main.removeCallbacks(limitTick)
        val limit = app.core.hub.limits[source] ?: return
        val target = BlockTarget.Limit(source)
        if (app.core.passes.allows(target) || !usage.permitted()) return
        limitWorker.execute {
            val now = app.core.clock.now()
            val zone = ZoneId.systemDefault()
            // Other devices' time counts against the same limit (ADR-0028).
            val used = usage.foregroundTime(source, now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant(), now) + app.core.devices.elsewhere(source)
            main.post {
                if (current != source) return@post
                if (UsageLimits.over(limit, used)) {
                    engine?.block(target)
                } else {
                    main.postDelayed(limitTick, UsageLimits.left(limit, used).toMillis() + LIMIT_GRACE_MS)
                }
            }
        }
    }

    /** Reads only what the session needs for this app, and returns null when that is nothing. */
    private fun snapshot(source: PackageName, policy: SessionPolicy, feeds: Set<ShortFormFeed>): ScreenSnapshot? {
        val urlBarId = ContentPolicy.urlBarId(source)
        val readUrl = urlBarId != null
        val readText = policy.strict && ContentPolicy.isGuarded(source)
        val readIds = feeds.isNotEmpty()
        if (!readUrl && !readText && !readIds) return null
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() != source.value) return null
        val url = urlBarId?.let { root.findAccessibilityNodeInfosByViewId(it).firstOrNull()?.text?.toString() }
        val viewIds = mutableSetOf<String>()
        val text = mutableSetOf<String>()
        if (readText || readIds) {
            walk(root, viewIds.takeIf { readIds }, text.takeIf { readText })
        }
        return ScreenSnapshot(source, url, viewIds, text)
    }

    private fun walk(root: AccessibilityNodeInfo, viewIds: MutableSet<String>?, text: MutableSet<String>?) {
        val pending = ArrayDeque(listOf(root))
        var visited = 0
        while (pending.isNotEmpty() && visited < MAX_NODES) {
            val node = pending.removeLast()
            visited++
            node.viewIdResourceName?.let { viewIds?.add(it) }
            node.text?.let { text?.add(it.toString()) }
            for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
        }
    }

    private fun transientPackages(): Set<PackageName> {
        val keyboards = getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        return parsePackages(keyboards + listOf(packageName, "com.android.systemui"))
    }

    private fun launcherPackages(): List<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }
    }

    private fun exemptPackages(launchers: List<String>): Set<PackageName> {
        val dialer = getSystemService(TelecomManager::class.java).defaultDialerPackage
        return LockoutExemptions.of(packageName, launchers, dialer)
    }

    private fun parsePackages(names: List<String>): Set<PackageName> =
        names.mapNotNull { (PackageName.parse(it) as? Parsed.Valid)?.value }.toSet()

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        release()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        main.removeCallbacks(trailingInspection)
        main.removeCallbacks(passEnded)
        subscription?.close()
        subscription = null
        surface?.discard()
        surface = null
        engine = null
    }

    private companion object {
        val DEBOUNCE: Duration = Duration.ofMillis(300)
        const val INSPECTION_INTERVAL_MS = 500L
        const val MAX_NODES = 2000
        const val PASS_GRACE_MS = 250L
        const val LIMIT_GRACE_MS = 1_000L
    }
}
