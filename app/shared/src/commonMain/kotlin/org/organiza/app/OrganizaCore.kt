package org.organiza.app

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.organiza.ai.ApiKeyStore
import org.organiza.ai.AiCoach
import org.organiza.ai.AiResult
import org.organiza.ai.ApiKey
import org.organiza.ai.GroqClient
import org.organiza.ai.GroqEndpoint
import org.organiza.ai.Sanitizer
import org.organiza.ai.UrlConnectionTransport
import org.organiza.ai.UsageFacts
import org.organiza.feature.ai.ChatController
import org.organiza.feature.ai.CopilotController
import org.organiza.feature.blocker.HubController
import org.organiza.feature.schedule.ScheduleController
import org.organiza.feature.schedule.CalendarController
import org.organiza.feature.rpg.RpgController
import org.organiza.feature.blocker.InterventionHost
import org.organiza.feature.sync.DevicesController
import org.organiza.feature.sync.SyncController
import org.organiza.arena.ArenaEngine
import org.organiza.arena.ChallengeRules
import org.organiza.arena.CreateResult
import org.organiza.arena.JoinResult
import org.organiza.arena.NudgeResult
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
import org.organiza.storage.FocusTier
import org.organiza.storage.HabitCriterion
import org.organiza.storage.HabitKind
import org.organiza.storage.HabitPlan
import org.organiza.storage.HabitStore
import org.organiza.storage.RpgStore
import org.organiza.storage.SettingsStore
import org.organiza.storage.UsageLimitStore
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
import org.organiza.ui.ChallengeView
import org.organiza.ui.HabitProgressView
import org.organiza.ui.MonitorView
import org.organiza.ui.UiSnapshot
import org.organiza.ui.Upcoming
import org.organiza.presentation.AgendaModel
import org.organiza.presentation.ArenaModel
import org.organiza.presentation.FocusWeek
import org.organiza.presentation.HomeHabitInput
import org.organiza.presentation.HomeModel
import org.organiza.presentation.ProgressFacts
import org.organiza.presentation.Progression
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

/**
 * The app itself, shared by Android and the Linux desktop (ADR-0025): storage, sessions, habits,
 * Arena, AI and every screen model. [platform] supplies what each OS does differently.
 */
class OrganizaCore(val platform: Platform) : EnforcementCapabilities, InterventionHost {
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
    val devices by lazy { DevicesController(this) }
    val copilot by lazy { CopilotController(this) }
    val chat by lazy { ChatController(this) }

    // Guilt-free summaries of monitor sessions, by session ID, kept in memory only.
    private val monitorSummaries = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile private var monitorBusy = false

    /** Lets debug checks reach tomorrow. Only the debug receiver changes it. */
    @Volatile var clockOffsetForTesting: Duration = Duration.ZERO

    /** One line of feedback for the screens. Setting it redraws them. */
    @Volatile var statusMessage: String? = null
        private set(value) {
            field = value
            requestUiRefresh()
            // Feedback is transient: it clears itself unless a newer message replaced it. A counter,
            // not the text, decides that, because the same text can legitimately be set twice.
            val generation = ++messageGeneration
            if (value != null) timer.schedule({ if (messageGeneration == generation) statusMessage = null }, MESSAGE_MS, TimeUnit.MILLISECONDS)
        }

    @Volatile private var messageGeneration = 0L

    /** What the screens show, replaced whole on the main thread (ADR-0012). */
    val uiState = mutableStateOf(UiSnapshot())
    private val syncedAt = mutableMapOf<String, String>()
    @Volatile var storageFailure: String? = null
        private set
    @Volatile var tamperCount = 0
        private set

    // Storage and everything that touches it run on this one thread, away from the main thread.
    private val storageThread = Executors.newSingleThreadExecutor()

    // AI calls can take many seconds, so they must not hold up enforcement work.
    private val aiThread = Executors.newSingleThreadExecutor()
    private val apiKeys: ApiKeyStore get() = platform.apiKeys

    /** The BYOK key store, for feature controllers that call Groq themselves. */
    val keys: ApiKeyStore get() = apiKeys

    val hub by lazy { HubController(this) }
    val schedule by lazy { ScheduleController(this) }
    val calendar by lazy { CalendarController(this) }
    val game by lazy { RpgController(this) }
    private var reminderIds: Set<String> = emptySet()
    var aiEndpoint: GroqEndpoint = GroqEndpoint.PRODUCTION
    // Delayed work: clearing a message, the next reconcile and the tamper check after a change.
    private val timer = Executors.newSingleThreadScheduledExecutor { Thread(it, "organiza-timer").apply { isDaemon = true } }
    private var tick: ScheduledFuture<*>? = null
    private var storage: Storage? = null
    private val monitor by lazy { TamperMonitor(this, clock) }
    private var monitorArmed = false
    @Volatile private var lastRequest = 0L

    /** The stores, for feature controllers. Use them on the storage thread only, through [onStores]. */
    inner class Storage(val db: Database, val clock: Clock) {
        val scheduler = SessionScheduler(db, clock)
        val interventions = InterventionStore(db)
        val limits = UsageLimitStore(db)
        val settings = SettingsStore(db)
        val sessions = SessionStore(db)
        val tamper = TamperStore(db)
        val aiFacts = AiFacts(db)
        val habits = HabitStore(db)
        val rpg = RpgStore(db)
        val voice = VoiceExecutor(db, clock, scheduler)
        val rules = RuleStore(db)
        val arena = ArenaEngine(
            db,
            clock,
            ZoneId.systemDefault(),
            platform.secrets,
            usageHistory,
            this@OrganizaCore,
            allowInsecureRelay = platform.debug,
        ) { url -> sync.backend.sync.relayFor(url) }
    }

    private val usageHistory: AppUsage get() = platform.usage

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
                platform.log("arena work failed: ${failure.javaClass.simpleName}")
                statusMessage = "Arena error"
            }
        }
    }

    /** Closes finished days and syncs: each new day, and at most hourly otherwise. */
    private fun runArenaIfDue() {
        val today = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()
        val now = System.nanoTime() / 1_000_000
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

    /** Sets every habit reminder again, after a change, a reminder firing or a reboot. */
    fun rescheduleReminders() = onStorageThread {
        val active = storage ?: return@onStorageThread
        val reminders = active.habits.reminders()
        platform.scheduleReminders(reminders, reminderIds)
        reminderIds = reminders.map { it.id }.toSet()
    }

    fun addHabitPlan(name: String, plan: HabitPlan) = onStorageThread {
        val active = storage ?: return@onStorageThread
        val clean = name.trim()
        if (clean.isEmpty()) return@onStorageThread
        val existing = active.habits.active().firstOrNull { it.name.equals(clean, ignoreCase = true) }
        if (existing != null) {
            active.habits.setPlan(existing.id, plan)
            statusMessage = "Updated $clean"
        } else {
            active.habits.add(UUID.randomUUID().toString(), clean, plan, clock.now())
            statusMessage = "Added $clean"
        }
        rescheduleReminders()
        refreshUi()
    }

    /**
     * Starts a session at the chosen tier (ADR-0020): the focus list the hub edits, everything but calls
     * and messages, or nothing at all while the apps used are watched.
     */
    fun startFocus(minutes: Int, strict: Boolean, shortForm: Boolean, tier: FocusTier) = onStorageThread {
        val active = storage ?: return@onStorageThread
        val request = when (tier) {
            FocusTier.LOCKDOWN -> ManualRequest(lockdownApps(), emptySet(), strict, shortForm, tier = tier)
            FocusTier.MONITOR -> ManualRequest(emptySet(), emptySet(), tier = tier)
            FocusTier.STANDARD -> {
                val rule = hub.focusRule(active)
                if (rule.packages.isEmpty() && rule.domains.isEmpty() && !shortForm) {
                    statusMessage = "Add apps or sites to your focus list in Metrics first"
                    return@onStorageThread
                }
                ManualRequest(rule.packages, rule.domains, strict, shortForm)
            }
        }
        statusMessage = when (active.scheduler.startManual(request, Duration.ofMinutes(minutes.toLong()))) {
            StartResult.Started -> if (tier == FocusTier.MONITOR) "Monitoring started. Nothing is blocked" else "Session started"
            StartResult.InvalidDuration -> "Length not allowed. Strict sessions can run up to 24 hours."
        }
        reconcile()
    }

    /** What lockdown leaves open besides the launcher, dialer and Organiza, which are always exempt (ADR-0007). */
    private fun lockdownApps(): Set<PackageName> = platform.lockdownApps()

    /** Today's screen time by category, focus time and habits, for the AI. Never app names. Storage thread. */
    fun usageFacts(stores: Storage): UsageFacts {
        val zone = ZoneId.systemDefault()
        val now = clock.now()
        val today = now.atZone(zone).toLocalDate()
        val byCategory = minutesByCategory(today.atStartOfDay(zone).toInstant(), now)
        val habits = stores.aiFacts.habits(today.minusDays(7), today.plusDays(1), Sanitizer())
        return UsageFacts(byCategory.values.sum(), byCategory, stores.aiFacts.weekly(now.minus(WEEK), now, habits).focusMinutes, habits)
    }

    private fun minutesByCategory(from: Instant, to: Instant): Map<String, Long> {
        if (!usageHistory.permitted()) return emptyMap()
        val categories = hub.categories()
        return usageHistory.perApp(from, to).entries
            .filter { it.key.value in categories }
            .groupBy({ categories.getValue(it.key.value) }, { it.value.toMinutes() })
            .mapValues { it.value.sum() }
            .filterValues { it > 0 }
    }

    fun logMinutes(habitId: String, minutes: Int) = onStorageThread {
        val active = storage ?: return@onStorageThread
        if (minutes !in 1..MAX_LOG_MINUTES) return@onStorageThread
        val today = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()
        if (active.habits.loggedMinutes(habitId, today) + minutes > MAX_LOG_MINUTES) {
            statusMessage = "A day has only 1440 minutes"
            return@onStorageThread
        }
        val total = active.habits.logMinutes(habitId, today, minutes)
        statusMessage = "Logged $minutes min. $total min today"
        refreshUi()
    }

    /** Asks for a guilt-free look at the last monitor session, by app category only. */
    fun summarizeMonitor() = onStorageThread {
        val last = lastMonitor() ?: return@onStorageThread
        val end = last.finishedAt ?: return@onStorageThread
        val minutes = Duration.between(last.start, end).toMinutes()
        val byCategory = minutesByCategory(last.start, end)
        monitorBusy = true
        refreshUi()
        onAiThread {
            try {
                when (val result = coach().monitorSummary(minutes, UsageFacts(byCategory.values.sum(), byCategory, minutes, emptyList()))) {
                    is AiResult.Ok -> monitorSummaries[last.id] = result.value
                    is AiResult.Failed -> statusMessage = "Summary failed: ${result.failure}"
                }
            } finally {
                monitorBusy = false
                requestUiRefresh()
            }
        }
    }

    /** The most recent finished monitor session from the last day. Storage thread. */
    private fun lastMonitor(): org.organiza.storage.StoredSession? {
        val active = storage ?: return null
        val now = clock.now()
        return (active.sessions.inState(SessionState.COMPLETED) + active.sessions.inState(SessionState.ABORTED))
            .filter { it.tier == FocusTier.MONITOR && it.finishedAt != null }
            .maxByOrNull { it.finishedAt!! }
            ?.takeIf { Duration.between(it.finishedAt, now) < Duration.ofDays(1) }
    }

    /** A dictated chat message: transcribed with the user's consent, then sent like a typed one. */
    fun chatDictated(samples: ShortArray, sampleRate: Int) = onAiThread {
        val pipeline = VoicePipeline(apiKeys, GroqClient(UrlConnectionTransport(), aiEndpoint), Sanitizer())
        when (val result = pipeline.transcript(samples, sampleRate)) {
            is VoiceResult.Transcript -> if (result.text.isBlank()) statusMessage = "Voice: nothing heard" else chat.send(result.text)
            VoiceResult.Silent -> statusMessage = "Voice: nothing heard, nothing was sent"
            VoiceResult.TooLong -> statusMessage = "Voice: too long, nothing was sent"
            is VoiceResult.Failed -> statusMessage = "Voice failed: ${result.failure}"
            else -> Unit
        }
    }

    /** A command from the chat, waiting for Confirm like a spoken one (ADR-0010). */
    fun propose(command: VoiceCommand) {
        pendingCommand = command
        requestUiRefresh()
    }

    fun nudge(challengeId: String, memberId: String) = onArenaThread {
        val arena = storage?.arena ?: return@onArenaThread
        statusMessage = when (arena.nudge(challengeId, memberId)) {
            NudgeResult.Sent -> "Nudge sent"
            NudgeResult.AlreadyToday -> "You already nudged them today"
            NudgeResult.RelayUnavailable -> "The relay could not be reached. Try again later"
            else -> "That member could not be nudged"
        }
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

    fun addCriterionHabitForTesting(name: String, kind: String, target: String) = onStorageThread {
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
        get() = prefs.flag(VOICE_CONSENT)
        set(value) = prefs.put(mapOf(VOICE_CONSENT to value.toString()))

    private val prefs by lazy { platform.prefs(PREFERENCES) }

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
            is VoiceResult.Transcript -> "Voice: not understood, nothing was changed"
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
            VoiceOutcome.OpenCalendar -> platform.openCalendar(command as VoiceCommand.AddEvent)
        }
        reconcile()
    }

    fun discardVoiceCommand() {
        pendingCommand = null
        statusMessage = "Discarded"
    }

    private val UPCOMING_DAY = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.UK)

    /** Opens storage and puts the schedule in force. Call once, when the app process starts. */
    fun start() {
        onStorageThread {
            openStorage()
            reconcile()
        }
        devices.start()
    }

    /** Runs [work] with the stores on the storage thread, if storage opened. */
    fun onStores(work: (Storage) -> Unit) = onStorageThread { storage?.let(work) }

    fun onStorageThread(work: () -> Unit) {
        storageThread.execute {
            try {
                work()
            } catch (failure: RuntimeException) {
                // Enforcement has to outlive a failed write. The exception type names the
                // failing operation, and carries no user data.
                platform.log("storage work failed: ${failure.javaClass.simpleName}")
                statusMessage = "Storage error"
            }
        }
    }

    private fun openStorage() {
        try {
            val db = platform.openDatabase()
            if (Migrations.migrate(db) is MigrationResult.TooNew) throw StorageUnavailable(DATABASE_TOO_NEW)
            storage = Storage(db, clock).also { hub.load(it) }
            // Read once: on the desktop each read starts a keyring helper. Saving and removing keep it current.
            keySaved = apiKeys.load() != null
            rescheduleReminders()
        } catch (failure: StorageUnavailable) {
            // Never delete or recreate the database here (ADR-0008).
            storageFailure = failure.message
        }
    }

    override fun granted(): Set<Capability> = platform.granted()

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
        platform.applyPolicy(state.policy)
        runArenaIfDue()
        // Give a restarted VPN time to come up before a stored baseline is compared.
        timer.schedule({ checkTamper() }, TAMPER_GRACE_MS, TimeUnit.MILLISECONDS)
        val untilNext = state.nextTransition?.let { Duration.between(clock.now(), it).toMillis() } ?: MAX_TICK_MS
        tick?.cancel(false)
        tick = timer.schedule({ onStorageThread { reconcile() } }, untilNext.coerceIn(MIN_TICK_MS, MAX_TICK_MS), TimeUnit.MILLISECONDS)
        refreshUi()
    }

    fun requestUiRefresh() = onStorageThread { refreshUi() }

    /** Re-reads the schedule now, so a block added or moved is armed for its start. */
    fun requestReconcileNow() = onStorageThread { reconcile() }

    fun showMessage(text: String) {
        statusMessage = text
    }

    /** Builds every screen's model from local data. Storage thread only. */
    private fun refreshUi() {
        val active = storage
        val zone = ZoneId.systemDefault()
        val now = clock.now()
        val today = now.atZone(zone).toLocalDate()
        val onboarding = !onboarded
        val permissions = platform.permissions()
        val snapshot = if (active == null) {
            UiSnapshot(
                onboarding = onboarding,
                permissions = permissions,
                device = platform.device,
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
            val consented = platform.siteBlockingAllowed()
            val capabilities = granted().minus(Capability.VPN).plus(if (consented) setOf(Capability.VPN) else emptySet())
            val weekAgo = now.minus(WEEK)
            val focus = FocusModel.build(
                now,
                zone,
                session.current,
                views,
                capabilities,
                active.tamper.all().count { it.at >= weekAgo },
                batteryExempt = platform.batteryExempt(),
                device = platform.device,
            )
            val startOfDay = today.atStartOfDay(zone).toInstant()
            val todays = active.interventions.between(startOfDay, now.plusSeconds(1))
            overridesToday = todays.count { it.choice == InterventionChoice.OVERRODE }
            val choices = ChoicesModel.build(todays, zone, ::appLabel, copilot.windows(active.interventions, zone))
            // Minute goals and linked apps settle first, so a goal met in the app shows as done (ADR-0020).
            val habitProgress = active.habits.active().mapNotNull { habit ->
                val plan = active.habits.plan(habit.id) ?: return@mapNotNull null
                val linked = plan.linkedPackage?.let { (PackageName.parse(it) as? Parsed.Valid)?.value }
                if (plan.goalMinutes == null && linked == null) return@mapNotNull null
                val appMinutes = if (linked != null && usageHistory.permitted()) usageHistory.foregroundTime(linked, startOfDay, now).toMinutes().toInt() else 0
                habit.id to HabitProgressView(plan.goalMinutes, active.habits.settle(habit.id, today, appMinutes), linked?.let(::appLabel))
            }.toMap()
            val monitor = lastMonitor()?.let { last ->
                val end = last.finishedAt ?: last.end
                val apps = if (usageHistory.permitted()) {
                    usageHistory.perApp(last.start, end).filterKeys { it.value != platform.selfPackage }.entries
                        .sortedByDescending { it.value }.take(MONITOR_APPS)
                        .map { appLabel(it.key) to it.value.toMinutes() }.filter { it.second > 0 }
                } else {
                    emptyList()
                }
                MonitorView(Duration.between(last.start, end).toMinutes(), apps, monitorSummaries[last.id], monitorBusy)
            }
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
            val plans = active.habits.active().map { habit ->
                HomeHabitInput(
                    habit.id,
                    habit.name,
                    active.habits.plan(habit.id) ?: HabitPlan.DAILY,
                    active.habits.results(habit.id, today.minusDays(HOME_DAYS), today.plusDays(1)),
                )
            }
            val allSessions = sessions
            val stayed = active.interventions.between(Instant.EPOCH, now.plusSeconds(1)).count { it.choice == InterventionChoice.STAYED }
            val metDays = plans.sumOf { input -> active.habits.results(input.id, LocalDate.EPOCH, today.plusDays(1)).count { it.value } }
            val streak = plans.maxOfOrNull { input -> longestStreak(active.habits.results(input.id, LocalDate.EPOCH, today.plusDays(1))) } ?: 0
            val arenaList = active.arena.challenges()
            val facts = ProgressFacts(
                habitDaysMet = metDays,
                focusMinutes = allSessions.filter { it.state == SessionState.COMPLETED }
                    .sumOf { Duration.between(it.start, it.finishedAt ?: it.end).toMinutes().coerceAtLeast(0) },
                stayedFocused = stayed,
                longestStreak = streak,
                challengesJoined = arenaList.size,
                challengesCompleted = arenaList.count { it.state == "completed" },
            )
            val baseXp = Progression.xp(facts)
            game.settle(active, today, zone, baseXp)
            val hero = game.hero(active, baseXp, Progression.badges(facts))
            val progression = hero.progression
            val nudges = arenaList.flatMap { challenge -> active.arena.nudgesForMe(challenge.id).map { "$it nudged you in ${challenge.title}" } }
            UiSnapshot(
                home = HomeModel.build(today, plans, now.atZone(zone).hour),
                agendaToday = AgendaModel.build(now, zone, today, views),
                agendaTomorrow = AgendaModel.build(now, zone, today.plusDays(1), views),
                progression = progression,
                week = FocusWeek.build(today, zone, allSessions),
                hub = hub.view(active, zone),
                calendar = calendar.view(now, zone, views),
                rpg = game.view(active, today, hero),
                schedule = schedule.view,
                nudges = nudges,
                selfName = arenaList.firstOrNull()?.displayName?.takeIf(String::isNotBlank) ?: "You",
                onboarding = onboarding,
                permissions = permissions,
                device = platform.device,
                focus = focus,
                canStop = focus.state == FocusState.ACTIVE,
                choices = choices,
                copilot = copilot.view(),
                account = sync.view(),
                habits = HabitMatrix.build(today, habits),
                habitProgress = habitProgress,
                chat = chat.view(),
                monitor = monitor,
                monitoring = sessions.any { it.state == SessionState.ACTIVE && it.tier == FocusTier.MONITOR },
                lockdown = session.current.blockList.allowOnly,
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
        platform.onMain { uiState.value = snapshot }
    }

    /** The installed app's name, or its package when it is not installed. */
    fun appLabel(app: PackageName): String = platform.appLabel(app)

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

    fun markHabitDaysAgoForTesting(name: String, daysAgo: Long, done: Boolean) = onStorageThread {
        val voice = storage?.voice ?: return@onStorageThread
        val day = clock.now().atZone(ZoneId.systemDefault()).toLocalDate().minusDays(daysAgo)
        voice.apply(VoiceCommand.MarkHabit(name, day, done))
        refreshUi()
    }

    /** Looks up an invite on the storage thread and hands it to [use] on the main thread. */
    fun inviteToShare(use: (String?) -> Unit) = onStorageThread {
        val invite = latestInvite ?: storage?.arena?.let { arena -> arena.challenges().firstOrNull()?.let { arena.invite(it.id) } }
        platform.onMain { use(invite) }
    }

    /** Called on foreground changes, so a due session applies when the phone is in use. */
    fun requestReconcile() {
        val now = System.nanoTime() / 1_000_000
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

    fun coach(model: String = GroqClient.DEFAULT_MODEL) = AiCoach(apiKeys, GroqClient(UrlConnectionTransport(), aiEndpoint, model), Sanitizer())

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
            platform.onMain { done(text) }
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
        get() = prefs.flag(ONBOARDED)
        set(value) {
            prefs.put(mapOf(ONBOARDED to value.toString()))
            requestUiRefresh()
        }

    private fun onAiThread(work: () -> Unit) {
        aiThread.execute {
            try {
                work()
            } catch (failure: RuntimeException) {
                platform.log("ai work failed: ${failure.javaClass.simpleName}")
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
    @Volatile var stayFocusedForTesting: (() -> Unit)? = null

    /** Debug and test support: ends strict sessions too, and reports state to the log. */
    fun endAllForTesting() = onStorageThread {
        storage?.scheduler?.endAllForTesting()
        reconcile()
    }

    fun logStatus() = onStorageThread {
        val capabilities = granted().sorted().joinToString(",")
        val tamper = storage?.tamper?.all().orEmpty().joinToString(",") { it.capability.name }
        val failure = storageFailure.orEmpty()
        val choices = storage?.interventions?.between(Instant.EPOCH, clock.now().plusSeconds(1)).orEmpty()
            .joinToString(",") { it.choice.name.lowercase() }
        platform.debugLine(
            "capabilities=$capabilities tamper=$tamper storageFailure=$failure " +
                "active=${session.current != SessionPolicy.IDLE} " +
                "habits=${storage?.habits?.active().orEmpty().joinToString("|") { it.name }} " +
                "arena=[${arenaSummary.replace('\n', ';')}] choices=[$choices] copilot=${copilot.view().advice != null} " +
                "message=${statusMessage?.replace('\n', ' ')}",
        )
    }

    companion object {
        private const val DATABASE_TOO_NEW = "DATABASE_TOO_NEW"
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
        private const val HOME_DAYS = 40L
        private const val MAX_LOG_MINUTES = 1440
        private const val MONITOR_APPS = 5

        /** The longest run of consecutive met days in [results]. */
        fun longestStreak(results: Map<LocalDate, Boolean>): Int {
            var best = 0
            var run = 0
            var previous: LocalDate? = null
            for ((day, met) in results.toSortedMap()) {
                run = if (met && previous != null && day == previous.plusDays(1)) run + 1 else if (met) 1 else 0
                best = maxOf(best, run)
                previous = day
            }
            return best
        }
        private const val MESSAGE_MS = 6_000L
    }
}
