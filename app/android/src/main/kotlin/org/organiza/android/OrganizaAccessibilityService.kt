package org.organiza.android

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
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.enforcement.BlockEngine
import org.organiza.enforcement.ContentDecision
import org.organiza.enforcement.ContentPolicy
import org.organiza.enforcement.LockoutExemptions
import org.organiza.enforcement.ScreenSnapshot
import org.organiza.enforcement.SessionPolicy

class OrganizaAccessibilityService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val app get() = OrganizaApp.from(this)
    private val contentPolicy by lazy { ContentPolicy(getString(R.string.app_name)) }
    private var engine: BlockEngine? = null
    private var subscription: AutoCloseable? = null
    private var surface: AccessibilityBlockSurface? = null
    private var lastInspection = 0L
    private var trailingSource: PackageName? = null
    private val trailingInspection = Runnable {
        val blockEngine = engine
        val source = trailingSource
        if (blockEngine != null && source != null) {
            lastInspection = SystemClock.elapsedRealtime()
            inspect(source, app.session.current, blockEngine)
        }
    }

    override fun onServiceConnected() {
        release()
        val blockSurface = AccessibilityBlockSurface(this)
        surface = blockSurface
        val blockEngine = BlockEngine(blockSurface, app.clock, DEBOUNCE, transientPackages(), exemptPackages())
        engine = blockEngine
        subscription = app.session.subscribe { policy ->
            main.post { blockEngine.update(policy.blockList) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val blockEngine = engine ?: return
        val source = (PackageName.parse(event.packageName?.toString().orEmpty()) as? Parsed.Valid)?.value ?: return
        val policy = app.session.current
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Tracked while idle too, so a new session can act on the app already open.
                blockEngine.onForeground(source)
                inspect(source, policy, blockEngine)
                app.requestReconcile()
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

    private fun inspect(source: PackageName, policy: SessionPolicy, blockEngine: BlockEngine) {
        if (policy == SessionPolicy.IDLE) return
        val snapshot = snapshot(source, policy) ?: return
        val decision = contentPolicy.decide(snapshot, policy.blockList, policy.blockShortForm, policy.strict)
        if (decision != ContentDecision.ALLOW) blockEngine.block(source)
    }

    /** Reads only what the session needs for this app, and returns null when that is nothing. */
    private fun snapshot(source: PackageName, policy: SessionPolicy): ScreenSnapshot? {
        val urlBarId = ContentPolicy.urlBarId(source)
        val readUrl = urlBarId != null
        val readText = policy.strict && ContentPolicy.isGuarded(source)
        if (!readUrl && !readText && !policy.blockShortForm) return null
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() != source.value) return null
        val url = urlBarId?.let { root.findAccessibilityNodeInfosByViewId(it).firstOrNull()?.text?.toString() }
        val viewIds = mutableSetOf<String>()
        val text = mutableSetOf<String>()
        if (readText || policy.blockShortForm) {
            walk(root, viewIds.takeIf { policy.blockShortForm }, text.takeIf { readText })
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

    private fun exemptPackages(): Set<PackageName> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }
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
        subscription?.close()
        subscription = null
        surface?.dismissOverlay()
        surface = null
        engine = null
    }

    private companion object {
        val DEBOUNCE: Duration = Duration.ofMillis(300)
        const val INSPECTION_INTERVAL_MS = 500L
        const val MAX_NODES = 2000
    }
}
