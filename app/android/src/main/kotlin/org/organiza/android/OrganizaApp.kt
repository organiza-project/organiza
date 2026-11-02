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
import org.organiza.android.data.AndroidUsageHistory
import org.organiza.android.data.DatabaseKey
import org.organiza.android.data.KeystoreApiKeyStore
import org.organiza.android.data.KeystoreSecretStore
import org.organiza.android.data.SqlCipherDatabase
import org.organiza.android.data.StorageException
import org.organiza.android.data.StorageFailure
import org.organiza.android.feature.ai.CopilotController
import org.organiza.android.feature.blocker.DnsVpnService
import org.organiza.android.feature.blocker.InterventionHost
import org.organiza.android.feature.blocker.OrganizaAccessibilityService
import org.organiza.android.feature.blocker.OrganizaDeviceAdminReceiver
import org.organiza.android.feature.onboarding.Permissions
import org.organiza.android.feature.sync.SyncController
import org.organiza.arena.ArenaEngine
import org.organiza.arena.ChallengeRules
import org.organiza.arena.CreateResult
import org.organiza.arena.JoinResult
import org.organiza.arena.SyncResult
import org.organiza.calendar.IcsCalendar
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.port.BlockTarget
import org.organiza.domain.port.Capability
import org.organiza.domain.port.Clock
import org.organiza.domain.port.EnforcementCapabilities
import org.organiza.enforcement.ActiveSession
import org.organiza.enforcement.InterventionFlow
import org.organiza.enforcement.InterventionOutcome
import org.organiza.enforcement.OverridePasses
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
import org.organiza.storage.InterventionChoice
import org.organiza.storage.InterventionStore
import org.organiza.storage.MigrationResult
import org.organiza.storage.Migrations
import org.organiza.storage.SessionState
import org.organiza.storage.SessionStore
import org.organiza.storage.StoredIntervention
import org.organiza.storage.StoredRule
import org.organiza.storage.RuleStore
import org.organiza.storage.TamperStore
import androidx.compose.runtime.mutableStateOf
import org.organiza.android.ui.ChallengeView
import org.organiza.android.ui.UiSnapshot
import org.organiza.android.ui.Upcoming
import org.organiza.presentation.ArenaModel
import org.organiza.presentation.ChoicesModel
import org.organiza.presentation.FocusModel
import org.organiza.presentation.FocusState
import org.organiza.presentation.Format
import org.organiza.presentation.HabitInput
import org.organiza.presentation.HabitMatrix
import org.organiza.presentation.SessionView
import org.organiza.voice.VoiceCommand
import org.organiza.voice.VoicePipeline
import org.organiza.voice.VoiceResult

class OrganizaApp : Application(), EnforcementCapabilities, InterventionHost {
    val session = ActiveSession()
    override val clock: Clock = Clock { Instant.now().plus(clockOffsetForTesting) }

    /** Override passes, shared by the accessibility and DNS layers (ADR-0016). */
    val passes by lazy { OverridePasses(clock) }

    /** Today's overrides, which set the next pause. Refreshed with the screens. */
    @Volatile var overridesToday = 0
        private set

    @Volatile private var activeUntil: Instant? = null
    @Volatile private var keySaved = false

    val sync by lazy { SyncController(this) }
    val copilot by lazy { CopilotController(this) }

    /** Lets debug checks reach tomorrow. Only the debug receiver changes it. */
    @Volatile internal var clockOffsetForTesting: Duration = Duration.ZERO

    @Volatile var vpnRunning = false

    /** One line of feedback for the screens. Setting it redraws them. */
    @Volatile var statusMessage: String? = null
        private set(value) {
            field = value
            requestUiRefresh()
            // Feedback is transient: it clears itself unless a newer message replaced it. A counter,
            // not the text, decides that, because the same text can legitimately be set twice.
            val generation = ++messageGeneration
            if (value != null) main.postDelayed({ if (messageGeneration == generation) statusMessage = null }, MESSAGE_MS)
        }

    @Volatile private var messageGeneration = 0L

    /** What the screens show, replaced whole on the main thread (ADR-0012). */
    val uiState = mutableStateOf(UiSnapshot())
    private val syncedAt = mutableMapOf<String, String>()
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

    /** The stores, for feature controllers. Use them on the storage thread only, through [onStores]. */
    inner class Storage(val db: Database, val clock: Clock) {
        val scheduler = SessionScheduler(db, clock)
        val interventions = InterventionStore(db)
        val sessions = SessionStore(db)
        val tamper = TamperStore(db)
        val aiFacts = AiFacts(db)
        val habits = HabitStore(db)
        val voice = VoiceExecutor(db, clock, scheduler)
        val rules = RuleStore(db)
        val arena = ArenaEngine(
            db,
            clock,
            ZoneId.systemDefault(),
            KeystoreSecretStore(this@OrganizaApp),
            usageHistory,
            this@OrganizaApp,
            allowInsecureRelay = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        ) { url -> sync.backend.sync.relayFor(url) }
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
            when (arena.sync(challenge.id)) {
                is SyncResult.Synced -> syncedAt[challenge.id] = clock.now().atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5)
                SyncResult.RelayUnavailable -> unavailable = true
                else -> Unit
            }
        }
        arenaSummary = describeArena(arena) + if (unavailable) "\nRelay unavailable, results are queued" else ""
        if (unavailable) statusMessage = "Relay unavailable, results are queued" else requestUiRefresh()
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
            val relay = relayUrl.ifBlank { sync.backend.sync.defaultRelayUrl().orEmpty() }
            when (val result = arena.create(habitIdFor(habitName), title, today, rules, relay, displayName)) {
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

    private val UPCOMING_DAY = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.UK)

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

    /** Runs [work] with the stores on the storage thread, if storage opened. */
    fun onStores(work: (Storage) -> Unit) = onStorageThread { storage?.let(work) }

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
        if (state.policy != session.current) {
            // A pass belongs to the session it was taken in.
            if (state.policy == SessionPolicy.IDLE) passes.clear()
            session.set(state.policy)
        }
        activeUntil = active.sessions.inState(SessionState.ACTIVE).maxOfOrNull { it.end }
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
        refreshUi()
    }

    fun requestUiRefresh() = onStorageThread { refreshUi() }

    fun showMessage(text: String) {
        statusMessage = text
    }

    /** Builds every screen's model from local data. Storage thread only. */
    private fun refreshUi() {
        val active = storage
        val zone = ZoneId.systemDefault()
        val now = clock.now()
        val today = now.atZone(zone).toLocalDate()
        keySaved = apiKeys.load() != null
        val onboarding = !onboarded
        val permissions = Permissions.granted(this)
        val snapshot = if (active == null) {
            UiSnapshot(
                onboarding = onboarding,
                permissions = permissions,
                account = sync.view(),
                hasApiKey = keySaved,
                message = statusMessage,
                storageProblem = storageFailure?.let { "Storage problem: $it. Your data has not been deleted." },
            )
        } else {
            val sessions = SessionState.entries.flatMap { active.sessions.inState(it) }
            val views = sessions.map { session ->
                val rule = session.ruleId?.let(active.rules::find)
                SessionView(session, rule?.calendarTag, rule?.strict == true)
            }
            val consented = VpnService.prepare(this) == null
            val capabilities = granted().minus(Capability.VPN).plus(if (consented) setOf(Capability.VPN) else emptySet())
            val weekAgo = now.minus(WEEK)
            val focus = FocusModel.build(
                now,
                zone,
                session.current,
                views,
                capabilities,
                active.tamper.all().count { it.at >= weekAgo },
                batteryExempt = Permissions.batteryExempt(this),
            )
            val startOfDay = today.atStartOfDay(zone).toInstant()
            val todays = active.interventions.between(startOfDay, now.plusSeconds(1))
            overridesToday = todays.count { it.choice == InterventionChoice.OVERRODE }
            val choices = ChoicesModel.build(todays, zone, ::appLabel, copilot.windows(active.interventions, zone))
            val habits = active.habits.active().map { habit ->
                HabitInput(
                    habit.id,
                    habit.name,
                    active.habits.createdAt(habit.id)?.atZone(zone)?.toLocalDate() ?: today,
                    active.habits.results(habit.id, today.minusDays(HabitMatrix.DAYS.toLong()), today.plusDays(1)),
                )
            }
            val upcoming = views
                .filter { it.session.state == SessionState.SCHEDULED && it.session.start.isBefore(now.plus(WEEK)) }
                .sortedBy { it.session.start }
                .take(MAX_UPCOMING)
                .map { view ->
                    val start = view.session.start.atZone(zone)
                    val day = when (start.toLocalDate()) {
                        today -> "Today"
                        today.plusDays(1) -> "Tomorrow"
                        else -> start.format(UPCOMING_DAY)
                    }
                    Upcoming(
                        view.tag ?: "Session",
                        "$day ${start.toLocalTime().toString().take(5)}",
                        Format.duration(Duration.between(view.session.start, view.session.end)),
                    )
                }
            val challenges = active.arena.challenges().map { challenge ->
                val board = active.arena.leaderboard(challenge.id)
                ChallengeView(
                    challenge.id,
                    challenge.title,
                    challenge.state,
                    ArenaModel.build(today, challenge.startsOn, challenge.lengthDays, challenge.memberId, board, syncedAt[challenge.id]),
                )
            }
            UiSnapshot(
                onboarding = onboarding,
                permissions = permissions,
                focus = focus,
                canStop = focus.state == FocusState.ACTIVE,
                choices = choices,
                copilot = copilot.view(),
                account = sync.view(),
                habits = HabitMatrix.build(today, habits),
                upcoming = upcoming,
                challenges = challenges,
                hasApiKey = keySaved,
                voiceConsent = voiceConsent,
                pendingCommand = pendingCommand?.let { "Heard: " + it.describe() },
                message = statusMessage,
                storageProblem = storageFailure?.let { "Storage problem: $it. Your data has not been deleted." },
                latestInvite = latestInvite,
                blockedApps = session.current.blockList.packages.map(::appLabel).sorted(),
                blockedSites = session.current.blockList.domains.map { it.value }.sorted(),
                stored = listOf(
                    "Sessions" to sessions.size.toString(),
                    "Habits" to habits.size.toString(),
                    "Challenges" to challenges.size.toString(),
                    "Tamper events" to active.tamper.all().size.toString(),
                    "Interventions" to active.interventions.between(Instant.EPOCH, now.plusSeconds(1)).size.toString(),
                ),
            )
        }
        main.post { uiState.value = snapshot }
    }

    /** The installed app's name, or its package when it is not installed. */
    fun appLabel(app: PackageName): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(app.value, 0)).toString()
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        app.value
    }

    fun addHabit(name: String) = onStorageThread {
        if (name.isBlank()) return@onStorageThread
        val voice = storage?.voice ?: return@onStorageThread
        statusMessage = when (voice.apply(VoiceCommand.AddHabit(name.trim()))) {
            VoiceOutcome.Done -> "Added ${name.trim()}"
            VoiceOutcome.AlreadyExists -> "That habit already exists"
            else -> "Could not add that habit"
        }
    }

    fun markHabit(name: String, done: Boolean) = onStorageThread {
        val voice = storage?.voice ?: return@onStorageThread
        val today = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()
        voice.apply(VoiceCommand.MarkHabit(name, today, done))
        refreshUi()
    }

    internal fun markHabitDaysAgoForTesting(name: String, daysAgo: Long, done: Boolean) = onStorageThread {
        val voice = storage?.voice ?: return@onStorageThread
        val day = clock.now().atZone(ZoneId.systemDefault()).toLocalDate().minusDays(daysAgo)
        voice.apply(VoiceCommand.MarkHabit(name, day, done))
        refreshUi()
    }

    /** Looks up an invite on the storage thread and hands it to [use] on the main thread. */
    fun inviteToShare(use: (String?) -> Unit) = onStorageThread {
        val invite = latestInvite ?: storage?.arena?.let { arena -> arena.challenges().firstOrNull()?.let { arena.invite(it.id) } }
        main.post { use(invite) }
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
                keySaved = true
                "API key saved"
            }
            is Parsed.Invalid -> "That does not look like an API key"
        }
    }

    fun clearApiKey() = onAiThread {
        apiKeys.clear()
        keySaved = false
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

    fun coach() = AiCoach(apiKeys, GroqClient(UrlConnectionTransport(), aiEndpoint), Sanitizer())

    fun hasApiKey(): Boolean = keySaved

    fun onAi(work: () -> Unit) = onAiThread(work)

    /** Logs a finished intervention (ADR-0016). Called once per intervention by the overlay. */
    fun recordIntervention(flow: InterventionFlow, outcome: InterventionOutcome) = onStorageThread {
        val active = storage ?: return@onStorageThread
        val overrode = outcome as? InterventionOutcome.Overrode
        active.interventions.record(
            StoredIntervention(
                sessionId = active.sessions.inState(SessionState.ACTIVE).firstOrNull()?.id,
                target = flow.target,
                choice = if (overrode == null) InterventionChoice.STAYED else InterventionChoice.OVERRODE,
                reason = overrode?.reason,
                note = overrode?.note?.takeIf(String::isNotBlank),
                phraseId = overrode?.let { flow.phrase.id },
                pause = overrode?.pause ?: Duration.ZERO,
                passMinutes = overrode?.minutes,
                strict = flow.strict,
                reflected = overrode?.reflected == true,
                at = clock.now(),
            ),
        )
        if (overrode != null) overridesToday++
        refreshUi()
    }

    /** Asks for an AI reflection on a strict override, and hands back null on any failure (ADR-0017). */
    override fun reflect(flow: InterventionFlow, done: (String?) -> Unit) {
        val reason = flow.chosenReason ?: return done(null)
        val note = flow.chosenNote
        val minutesLeft = activeUntil?.let { Duration.between(clock.now(), it).toMinutes() } ?: 0
        val today = overridesToday
        onAiThread {
            val text = (coach().reflect(minutesLeft, today, reason, note) as? AiResult.Ok)?.value
            main.post { done(text) }
        }
    }

    override fun targetLabel(target: BlockTarget): String = ChoicesModel.label(target, ::appLabel)

    /** The intervention's second line: what kind of session, and how long is left. */
    override fun sessionLine(strict: Boolean): String {
        val left = activeUntil?.let { Format.duration(Duration.between(clock.now(), it)) }
        val kind = if (strict) "Strict session" else "Focus session"
        return if (left == null) kind else "$kind, $left left"
    }

    /** Whether the first-run flow has been finished. */
    var onboarded: Boolean
        get() = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(ONBOARDED, false)
        set(value) {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(ONBOARDED, value).apply()
            requestUiRefresh()
        }

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

    /** Debug and test support: closes the intervention on screen as if Stay focused was tapped. */
    @Volatile internal var stayFocusedForTesting: (() -> Unit)? = null

    /** Debug and test support: ends strict sessions too, and reports state to the log. */
    internal fun endAllForTesting() = onStorageThread {
        storage?.scheduler?.endAllForTesting()
        reconcile()
    }

    internal fun logStatus() = onStorageThread {
        val capabilities = granted().sorted().joinToString(",")
        val tamper = storage?.tamper?.all().orEmpty().joinToString(",") { it.capability.name }
        val failure = storageFailure?.name.orEmpty()
        val choices = storage?.interventions?.between(Instant.EPOCH, clock.now().plusSeconds(1)).orEmpty()
            .joinToString(",") { it.choice.name.lowercase() }
        Log.i(
            "OrganizaDebug",
            "capabilities=$capabilities tamper=$tamper storageFailure=$failure " +
                "active=${session.current != SessionPolicy.IDLE} " +
                "habits=${storage?.habits?.active().orEmpty().joinToString("|") { it.name }} " +
                "arena=[${arenaSummary.replace('\n', ';')}] choices=[$choices] copilot=${copilot.view().advice != null} " +
                "message=${statusMessage?.replace('\n', ' ')}",
        )
    }

    companion object {
        private const val TAG = "Organiza"
        private const val DATABASE_NAME = "organiza.db"
        private const val ARENA_INTERVAL_MS = 60 * 60 * 1000L
        private const val PREFERENCES = "organiza"
        private const val VOICE_CONSENT = "voice_consent"
        private const val ONBOARDED = "onboarded"
        private const val MIN_TICK_MS = 500L
        private const val MAX_TICK_MS = 30_000L
        private const val REQUEST_INTERVAL_MS = 5_000L
        private const val TAMPER_GRACE_MS = 5_000L
        private val CALENDAR_WINDOW: Duration = Duration.ofDays(60)
        private val WEEK: Duration = Duration.ofDays(7)
        private const val MAX_UPCOMING = 5
        private const val MESSAGE_MS = 6_000L

        fun from(context: Context): OrganizaApp = context.applicationContext as OrganizaApp

        fun adminComponent(context: Context) = ComponentName(context, OrganizaDeviceAdminReceiver::class.java)
    }
}
