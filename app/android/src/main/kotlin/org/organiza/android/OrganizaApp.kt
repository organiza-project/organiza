package org.organiza.android

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import org.organiza.ai.AiCoach
import org.organiza.ai.AiResult
import org.organiza.ai.ApiKey
import org.organiza.ai.GroqClient
import org.organiza.ai.GroqEndpoint
import org.organiza.ai.Sanitizer
import org.organiza.ai.UrlConnectionTransport
import org.organiza.calendar.IcsCalendar
import org.organiza.domain.Parsed
import org.organiza.domain.port.Capability
import org.organiza.domain.port.Clock
import org.organiza.domain.port.EnforcementCapabilities
import org.organiza.enforcement.ActiveSession
import org.organiza.enforcement.SessionPolicy
import org.organiza.enforcement.TamperMonitor
import org.organiza.scheduler.AiFacts
import org.organiza.scheduler.ManualRequest
import org.organiza.scheduler.RuleEdit
import org.organiza.scheduler.SessionScheduler
import org.organiza.scheduler.StartResult
import org.organiza.scheduler.StopResult
import org.organiza.storage.Database
import org.organiza.storage.MigrationResult
import org.organiza.storage.Migrations
import org.organiza.storage.SessionState
import org.organiza.storage.SessionStore
import org.organiza.storage.StoredRule
import org.organiza.storage.TamperStore

class OrganizaApp : Application(), EnforcementCapabilities {
    val session = ActiveSession()
    val clock = Clock(Instant::now)

    @Volatile var vpnRunning = false

    /** What the setup screen shows. Written on the storage thread. */
    @Volatile var statusMessage: String? = null
        private set
    @Volatile var storageFailure: StorageFailure? = null
        private set
    @Volatile var tamperCount = 0
        private set

    // Storage and everything that touches it run on this one thread, away from the main thread.
    private val storageThread = Executors.newSingleThreadExecutor()

    // AI calls can take many seconds, so they must not hold up enforcement work.
    private val aiThread = Executors.newSingleThreadExecutor()
    private val apiKeys by lazy { KeystoreApiKeyStore(this) }
    internal var aiEndpoint: GroqEndpoint = GroqEndpoint.PRODUCTION
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var storage: Storage? = null
    private val monitor by lazy { TamperMonitor(this, clock) }
    private var monitorArmed = false
    private val tick = Runnable { onStorageThread { reconcile() } }
    @Volatile private var lastRequest = 0L

    private class Storage(val db: Database, val clock: Clock) {
        val scheduler = SessionScheduler(db, clock)
        val sessions = SessionStore(db)
        val tamper = TamperStore(db)
        val aiFacts = AiFacts(db)
    }

    override fun onCreate() {
        super.onCreate()
        // The system does not reliably call onUnbind when the service is switched off, so
        // the setting itself is watched and the OS is asked directly in granted().
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) = checkTamper()
            },
        )
        onStorageThread {
            openStorage()
            reconcile()
        }
    }

    private fun onStorageThread(work: () -> Unit) {
        storageThread.execute {
            try {
                work()
            } catch (failure: RuntimeException) {
                // Enforcement has to outlive a failed write. The exception type names the
                // failing operation, and carries no user data.
                Log.e(TAG, "storage work failed: ${failure.javaClass.simpleName}")
                statusMessage = "Storage error"
            }
        }
    }

    private fun openStorage() {
        try {
            val file = getDatabasePath(DATABASE_NAME)
            file.parentFile?.mkdirs()
            val key = DatabaseKey(this, file).load()
            val db = try {
                SqlCipherDatabase.open(file, key)
            } finally {
                key.fill(0)
            }
            if (Migrations.migrate(db) is MigrationResult.TooNew) throw StorageException(StorageFailure.DATABASE_TOO_NEW)
            storage = Storage(db, clock)
        } catch (failure: StorageException) {
            // Never delete or recreate the database here (ADR-0008).
            storageFailure = failure.failure
        }
    }

    private fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
        val self = ComponentName(this, OrganizaAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
    }

    override fun granted(): Set<Capability> = buildSet {
        if (accessibilityEnabled()) {
            add(Capability.ACCESSIBILITY)
            add(Capability.OVERLAY)
        }
        if (vpnRunning) add(Capability.VPN)
        if (deviceAdminActive()) add(Capability.DEVICE_ADMIN)
    }

    fun deviceAdminActive(): Boolean =
        getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent(this))

    /** Applies due transitions and puts the resulting policy in force. Storage thread only. */
    private fun reconcile() {
        val active = storage ?: return
        val state = active.scheduler.reconcile()
        if (state.policy != session.current) session.set(state.policy)
        if (state.activeSessionIds.isEmpty()) {
            if (monitorArmed) monitor.disarm()
            monitorArmed = false
        } else if (!monitorArmed) {
            monitor.arm(active.sessions.grantedCapabilities(state.activeSessionIds.first()) ?: granted())
            monitorArmed = true
        }
        syncVpn(state.policy)
        // Give a restarted VPN time to come up before a stored baseline is compared.
        main.postDelayed({ checkTamper() }, TAMPER_GRACE_MS)
        val untilNext = state.nextTransition?.let { Duration.between(clock.now(), it).toMillis() } ?: MAX_TICK_MS
        main.removeCallbacks(tick)
        main.postDelayed(tick, untilNext.coerceIn(MIN_TICK_MS, MAX_TICK_MS))
    }

    private fun syncVpn(policy: SessionPolicy) {
        val wanted = policy.blockList.domains.isNotEmpty() && VpnService.prepare(this) == null
        try {
            if (wanted && !vpnRunning) {
                startService(Intent(this, DnsVpnService::class.java))
            } else if (!wanted && vpnRunning) {
                startService(Intent(this, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_STOP))
            }
        } catch (_: IllegalStateException) {
            // Android refused a background start. The next tick tries again.
            statusMessage = "Could not change the VPN yet"
        }
    }

    /** Called on foreground changes, so a due session applies when the phone is in use. */
    fun requestReconcile() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRequest < REQUEST_INTERVAL_MS) return
        lastRequest = now
        onStorageThread { reconcile() }
    }

    fun startManual(request: ManualRequest, duration: Duration) = onStorageThread {
        val scheduler = storage?.scheduler ?: return@onStorageThread
        statusMessage = when (scheduler.startManual(request, duration)) {
            StartResult.Started -> "Session started"
            StartResult.InvalidDuration -> "Length not allowed. Strict sessions can run up to 24 hours."
        }
        reconcile()
    }

    fun stopSession() = onStorageThread {
        val scheduler = storage?.scheduler ?: return@onStorageThread
        statusMessage = when (scheduler.stopActive()) {
            StopResult.Stopped -> "Session stopped"
            StopResult.RefusedStrict -> "A strict session is running and cannot be stopped early."
        }
        reconcile()
    }

    fun saveRule(rule: StoredRule) = onStorageThread {
        val scheduler = storage?.scheduler ?: return@onStorageThread
        statusMessage = when (scheduler.saveRule(rule)) {
            RuleEdit.Saved -> "Rule saved"
            RuleEdit.RefusedStrict -> "This rule is in a running strict session and cannot be changed."
        }
        reconcile()
    }

    fun importCalendar(text: String) = onStorageThread {
        val scheduler = storage?.scheduler ?: return@onStorageThread
        val now = clock.now()
        val until = now.plus(CALENDAR_WINDOW)
        val read = IcsCalendar.read(text, now, until, ZoneId.systemDefault())
        val sync = scheduler.syncCalendar(read.occurrences, now, until)
        statusMessage = "Calendar: ${sync.added} added, ${sync.updated} updated, ${sync.removed} removed, " +
            "${sync.skippedStrict.size} strict events too long, ${read.issues.size} events not understood"
        reconcile()
    }

    fun saveApiKey(raw: String) = onAiThread {
        statusMessage = when (val parsed = ApiKey.parse(raw)) {
            is Parsed.Valid -> {
                apiKeys.save(parsed.value)
                "API key saved"
            }
            is Parsed.Invalid -> "That does not look like an API key"
        }
    }

    fun clearApiKey() = onAiThread {
        apiKeys.clear()
        statusMessage = "API key removed"
    }

    fun requestWeeklyReview() = onStorageThread {
        val facts = storage?.aiFacts ?: return@onStorageThread
        val now = clock.now()
        val week = facts.weekly(now.minus(WEEK), now)
        onAiThread {
            statusMessage = when (val result = coach().weeklyReview(week)) {
                is AiResult.Ok -> "Weekly review: ${result.value.summary}\n" + result.value.suggestions.joinToString("\n")
                is AiResult.Failed -> "Weekly review failed: ${result.failure}"
            }
        }
    }

    fun requestSchedule(goal: String) = onStorageThread {
        val facts = storage?.aiFacts ?: return@onStorageThread
        val now = clock.now()
        val busy = facts.busy(now, now.plus(WEEK), ZoneId.systemDefault())
        onAiThread {
            statusMessage = when (val result = coach().suggestSchedule(goal, busy)) {
                is AiResult.Ok -> "Suggested (not applied): " + result.value.blocks.joinToString { block ->
                    "${block.day} ${block.startMinute / 60}:${"%02d".format(block.startMinute % 60)} " +
                        "for ${block.minutes} min, ${block.label}"
                }
                is AiResult.Failed -> "Schedule suggestion failed: ${result.failure}"
            }
        }
    }

    /** Shows the gatekeeper's decision. A grant does not end the session (ADR-0009). */
    fun askGatekeeper(request: String, minutesLeft: Long) = onAiThread {
        val decision = coach().gatekeeper(minutesLeft, request)
        statusMessage = "Gatekeeper: granted=${decision.granted} reason=${decision.reason} failure=${decision.failure}"
    }

    private fun coach() = AiCoach(apiKeys, GroqClient(UrlConnectionTransport(), aiEndpoint), Sanitizer())

    private fun onAiThread(work: () -> Unit) {
        aiThread.execute {
            try {
                work()
            } catch (failure: RuntimeException) {
                Log.e(TAG, "ai work failed: ${failure.javaClass.simpleName}")
                statusMessage = "AI error"
            }
        }
    }

    /** Called whenever the OS may have taken a capability away. */
    fun checkTamper() = onStorageThread {
        val active = storage ?: return@onStorageThread
        val lost = monitor.check()
        val current = active.sessions.inState(SessionState.ACTIVE).firstOrNull()?.id
        lost.forEach { active.tamper.record(current, it.capability, it.at) }
        if (monitorArmed) active.sessions.saveGrantedCapabilities(granted())
        tamperCount = active.tamper.all().size
    }

    /** Debug and test support: ends strict sessions too, and reports state to the log. */
    internal fun endAllForTesting() = onStorageThread {
        storage?.scheduler?.endAllForTesting()
        reconcile()
    }

    internal fun logStatus() = onStorageThread {
        val capabilities = granted().sorted().joinToString(",")
        val tamper = storage?.tamper?.all().orEmpty().joinToString(",") { it.capability.name }
        val failure = storageFailure?.name.orEmpty()
        Log.i(
            "OrganizaDebug",
            "capabilities=$capabilities tamper=$tamper storageFailure=$failure " +
                "active=${session.current != SessionPolicy.IDLE} message=${statusMessage?.replace('\n', ' ')}",
        )
    }

    companion object {
        private const val TAG = "Organiza"
        private const val DATABASE_NAME = "organiza.db"
        private const val MIN_TICK_MS = 500L
        private const val MAX_TICK_MS = 30_000L
        private const val REQUEST_INTERVAL_MS = 5_000L
        private const val TAMPER_GRACE_MS = 5_000L
        private val CALENDAR_WINDOW: Duration = Duration.ofDays(60)
        private val WEEK: Duration = Duration.ofDays(7)

        fun from(context: Context): OrganizaApp = context.applicationContext as OrganizaApp

        fun adminComponent(context: Context) = ComponentName(context, OrganizaDeviceAdminReceiver::class.java)
    }
}
