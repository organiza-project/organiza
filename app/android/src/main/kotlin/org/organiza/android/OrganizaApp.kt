package org.organiza.android

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.pm.ApplicationInfo
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executors
import org.organiza.ai.AiCoach
import org.organiza.ai.AiResult
import org.organiza.ai.ApiKey
import org.organiza.ai.GroqClient
import org.organiza.ai.GroqEndpoint
import org.organiza.ai.Sanitizer
import org.organiza.ai.UrlConnectionTransport
import org.organiza.arena.ArenaEngine
import org.organiza.arena.ChallengeRules
import org.organiza.arena.CreateResult
import org.organiza.arena.HttpArenaRelay
import org.organiza.arena.JoinResult
import org.organiza.arena.SyncResult
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
import org.organiza.scheduler.VoiceExecutor
import org.organiza.scheduler.VoiceOutcome
import org.organiza.storage.Database
import org.organiza.storage.HabitCriterion
import org.organiza.storage.HabitKind
import org.organiza.storage.HabitStore
import org.organiza.storage.MigrationResult
import org.organiza.storage.Migrations
import org.organiza.storage.SessionState
import org.organiza.storage.SessionStore
import org.organiza.storage.StoredRule
import org.organiza.storage.TamperStore
import org.organiza.voice.VoiceCommand
import org.organiza.voice.VoicePipeline
import org.organiza.voice.VoiceResult

class OrganizaApp : Application(), EnforcementCapabilities {
    val session = ActiveSession()
    val clock = Clock { Instant.now().plus(clockOffsetForTesting) }

    /** Lets debug checks reach tomorrow. Only the debug receiver changes it. */
    @Volatile internal var clockOffsetForTesting: Duration = Duration.ZERO

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

    private inner class Storage(val db: Database, val clock: Clock) {
        val scheduler = SessionScheduler(db, clock)
        val sessions = SessionStore(db)
        val tamper = TamperStore(db)
        val aiFacts = AiFacts(db)
        val habits = HabitStore(db)
        val voice = VoiceExecutor(db, clock, scheduler)
        val arena = ArenaEngine(
            db,
            clock,
            ZoneId.systemDefault(),
            KeystoreSecretStore(this@OrganizaApp),
            usageHistory,
            this@OrganizaApp,
            allowInsecureRelay = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        ) { url -> HttpArenaRelay(UrlConnectionTransport(), url) }
    }

    private val usageHistory by lazy { AndroidUsageHistory(this) }

    // Relay calls can take many seconds, so they must not hold up enforcement work.
    private val arenaThread = Executors.newSingleThreadExecutor()
    private var lastArenaRun = 0L
    private var lastArenaDate: LocalDate? = null

    /** The leaderboard text for the setup screen. */
    @Volatile var arenaSummary: String = ""
        private set

    /** The last invite created, ready to share. It is not logged or shown in status text. */
    @Volatile var latestInvite: String? = null
        private set

    private fun onArenaThread(work: () -> Unit) {
        arenaThread.execute {
            try {
                work()
            } catch (failure: RuntimeException) {
                Log.e(TAG, "arena work failed: ${failure.javaClass.simpleName}")
                statusMessage = "Arena error"
            }
        }
    }

    /** Closes finished days and syncs: each new day, and at most hourly otherwise. */
    private fun runArenaIfDue() {
        val today = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()
        val now = SystemClock.elapsedRealtime()
        val due = lastArenaDate != today || lastArenaRun == 0L || now - lastArenaRun >= ARENA_INTERVAL_MS
        if (!due) return
        lastArenaDate = today
        lastArenaRun = now
        arenaNow()
    }

    fun arenaNow() = onArenaThread {
        val arena = storage?.arena ?: return@onArenaThread
        arena.closeDays()
        var unavailable = false
        for (challenge in arena.challenges()) {
            if (arena.sync(challenge.id) == SyncResult.RelayUnavailable) unavailable = true
        }
        arenaSummary = describeArena(arena) + if (unavailable) "\nRelay unavailable, results are queued" else ""
    }

    private fun describeArena(arena: ArenaEngine): String = arena.challenges().joinToString("\n") { challenge ->
        val rows = arena.leaderboard(challenge.id).joinToString(" | ") { member ->
            "${member.name} ${member.successes} days, streak ${member.standing.streak}, " +
                "${member.standing.status.name.lowercase()}, ${member.verification.name.lowercase()}"
        }
        "${challenge.title} (${challenge.state}): $rows"
    }

    private fun habitIdFor(name: String): String {
        val habits = storage?.habits ?: return ""
        habits.active().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.let { return it.id }
        return UUID.randomUUID().toString().also { habits.add(it, name.trim(), clock.now()) }
    }

    fun createChallenge(habitName: String, title: String, rules: ChallengeRules, relayUrl: String, displayName: String) =
        onStorageThread {
            val arena = storage?.arena ?: return@onStorageThread
            val today = clock.now().atZone(ZoneId.systemDefault()).toLocalDate().toString()
            when (val result = arena.create(habitIdFor(habitName), title, today, rules, relayUrl, displayName)) {
                is CreateResult.Created -> {
                    latestInvite = result.invite.encode()
                    statusMessage = "Challenge created. Share the invite like a password."
                }
                CreateResult.InsecureRelay -> statusMessage = "The relay must use https"
                CreateResult.InvalidRules -> statusMessage = "Those challenge rules are not allowed"
                CreateResult.UnknownHabit -> statusMessage = "Choose a habit first"
            }
            arenaNow()
        }

    fun joinChallenge(invite: String, habitName: String, displayName: String) = onStorageThread {
        val arena = storage?.arena ?: return@onStorageThread
        statusMessage = when (arena.join(invite, habitIdFor(habitName), displayName)) {
            is JoinResult.Joined -> "Joined the challenge"
            JoinResult.AlreadyJoined -> "You already joined this challenge"
            JoinResult.InvalidInvite -> "That is not a valid invite"
            JoinResult.TooLate -> "This challenge has already started"
            JoinResult.UnknownHabit -> "Choose a habit first"
        }
        arenaNow()
    }

    internal fun addCriterionHabitForTesting(name: String, kind: String, target: String) = onStorageThread {
        val criterion = HabitCriterion(HabitKind.valueOf(kind), target, null)
        storage?.habits?.add(UUID.randomUUID().toString(), name, criterion, clock.now())
    }

    fun markHabitDone(name: String) = onStorageThread {
        val voice = storage?.voice ?: return@onStorageThread
        val today = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()
        statusMessage = when (voice.apply(VoiceCommand.MarkHabit(name.trim(), today, true))) {
            VoiceOutcome.Done -> "Marked done"
            else -> "No habit with that name"
        }
    }

    /** A heard command waiting for the user's tap. Nothing happens until then (ADR-0010). */
    @Volatile var pendingCommand: VoiceCommand? = null
        private set

    /** Whether the user has agreed that recordings go to Groq. */
    var voiceConsent: Boolean
        get() = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(VOICE_CONSENT, false)
        set(value) = getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(VOICE_CONSENT, value).apply()

    fun handleSpeech(samples: ShortArray, sampleRate: Int) = onStorageThread {
        val known = storage?.habits?.active().orEmpty().map { it.name }
        onAiThread {
            val pipeline = VoicePipeline(apiKeys, GroqClient(UrlConnectionTransport(), aiEndpoint), Sanitizer())
            val zone = ZoneId.systemDefault()
            show(pipeline.fromAudio(samples, sampleRate, clock.now().atZone(zone), known))
        }
    }

    private fun show(result: VoiceResult) {
        pendingCommand = (result as? VoiceResult.Understood)?.command
        statusMessage = when (result) {
            is VoiceResult.Understood -> "Confirm: " + result.command.describe()
            VoiceResult.NotUnderstood -> "Voice: not understood, nothing was changed"
            VoiceResult.Silent -> "Voice: nothing heard, nothing was sent"
            VoiceResult.TooLong -> "Voice: too long, nothing was sent"
            is VoiceResult.Failed -> "Voice failed: ${result.failure}"
        }
    }

    fun confirmVoiceCommand() = onStorageThread {
        val command = pendingCommand ?: return@onStorageThread
        val voice = storage?.voice ?: return@onStorageThread
        pendingCommand = null
        statusMessage = when (val outcome = voice.apply(command)) {
            VoiceOutcome.Done -> "Done: " + command.describe()
            VoiceOutcome.AlreadyExists -> "That habit already exists"
            VoiceOutcome.NoSuchHabit -> "No habit with that name"
            is VoiceOutcome.AmbiguousHabit -> "Which one: ${outcome.matches.joinToString()}?"
            VoiceOutcome.NoSuchRule -> "No rule has that calendar tag"
            VoiceOutcome.NothingToCancel -> "No upcoming session to cancel. Running sessions cannot be cancelled by voice."
            VoiceOutcome.RefusedStrict -> "The schedule cannot change during a strict session"
            VoiceOutcome.InvalidTime -> "That time is not allowed"
        }
        reconcile()
    }

    fun discardVoiceCommand() {
        pendingCommand = null
        statusMessage = "Discarded"
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
        if (usageHistory.permitted()) add(Capability.USAGE_STATS)
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
        runArenaIfDue()
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
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val habits = facts.habits(today.minusDays(7), today.plusDays(1), Sanitizer())
        val week = facts.weekly(now.minus(WEEK), now, habits)
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
                "active=${session.current != SessionPolicy.IDLE} " +
                "habits=${storage?.habits?.active().orEmpty().joinToString("|") { it.name }} " +
                "arena=[${arenaSummary.replace('\n', ';')}] message=${statusMessage?.replace('\n', ' ')}",
        )
    }

    companion object {
        private const val TAG = "Organiza"
        private const val DATABASE_NAME = "organiza.db"
        private const val ARENA_INTERVAL_MS = 60 * 60 * 1000L
        private const val PREFERENCES = "organiza"
        private const val VOICE_CONSENT = "voice_consent"
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
