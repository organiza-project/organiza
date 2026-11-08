package org.organiza.desktop

import androidx.compose.runtime.mutableStateOf
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import org.organiza.domain.port.Clock
import org.organiza.enforcement.ShortFormFeed
import org.organiza.feature.blocker.hubView
import org.organiza.presentation.Permission
import org.organiza.presentation.ProtectionItem
import org.organiza.storage.Database
import org.organiza.storage.FocusLog
import org.organiza.storage.FocusTier
import org.organiza.storage.HabitFrequency
import org.organiza.storage.MigrationResult
import org.organiza.storage.Migrations
import org.organiza.storage.RpgKind
import org.organiza.storage.RuleStore
import org.organiza.storage.Setting
import org.organiza.storage.SettingsStore
import org.organiza.storage.UsageLimitStore
import org.organiza.sync.BackendKind
import org.organiza.ui.Actions
import org.organiza.ui.CalendarMode
import org.organiza.ui.UiSnapshot

/**
 * The desktop state holder (ADR-0023). It keeps the focus log and shows today's use (ADR-0024).
 * Sync, AI and enforcement come in the next stages, so until then those actions say so on screen.
 * All storage work runs on one thread, as on Android.
 */
class DesktopApp(
    private val db: Database,
    private val clock: Clock,
    private val focus: FocusSource,
    private val apps: List<LinuxApp>,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : Actions, AutoCloseable {
    val ui = mutableStateOf(UiSnapshot())

    private val storage = Executors.newSingleThreadScheduledExecutor { Thread(it, "storage").apply { isDaemon = true } }
    private val log = FocusLog(db)
    private val usage = LoggedUsage(log)
    private val packages = apps.associate { it.id to it.pkg }

    @Volatile private var tracking: AutoCloseable? = null

    @Volatile private var closed = false

    init {
        val ready = storage.submit<Boolean> {
            when (Migrations.migrate(db)) {
                is MigrationResult.TooNew -> false
                else -> {
                    // shortcut: a stretch left open by a crash is dropped, since when it ended is unknown.
                    log.last()?.takeIf { it.app != null }?.let { log.record(it.at, null) }
                    true
                }
            }
        }.get()
        if (ready) {
            tracking = focus.start { id ->
                val at = clock.now()
                val app = id?.let(packages::get)
                onStorage { log.record(at, app) }
            }
            storage.scheduleAtFixedRate(::refresh, 1, 1, TimeUnit.MINUTES)
            refreshUi()
        } else {
            say("This data was written by a newer Organiza, so it was left as it is. Update Organiza to open it.")
        }
    }

    /** Rebuilds what the screens show from local data. */
    fun refreshUi() = onStorage(::refresh)

    private fun refresh() {
        val now = clock.now()
        val start = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val allowed = tracking != null
        val today = if (allowed) usage.perApp(start, now).mapKeys { it.key.value } else emptyMap()
        val settings = SettingsStore(db)
        val feeds = buildSet {
            if (settings.flag(Setting.BLOCK_REELS)) add(ShortFormFeed.INSTAGRAM_REELS)
            if (settings.flag(Setting.BLOCK_SHORTS)) add(ShortFormFeed.YOUTUBE_SHORTS)
        }
        val launchable = apps.mapNotNull { app -> app.pkg?.let { Triple(it.value, app.name, app.category) } }
        val hub = hubView(launchable, today, RuleStore(db).all(), UsageLimitStore(db).all(), feeds, allowed)
        ui.value = ui.value.copy(hub = hub)
    }

    /** Ends the stretch in front and stops tracking. The database stays open for its owner. */
    override fun close() {
        if (closed) return
        closed = true
        tracking?.close()
        val at = clock.now()
        storage.submit { log.record(at, null) }.get()
        storage.shutdown()
    }

    private fun onStorage(work: () -> Unit) {
        try {
            if (!closed) storage.execute(work)
        } catch (_: RejectedExecutionException) {
            // Closed meanwhile: the last stretch was already ended.
        }
    }

    private fun say(text: String) {
        ui.value = ui.value.copy(message = text)
    }

    private fun notYet() = say(NOT_YET)

    override fun startSession(apps: String, sites: String, minutes: String, strict: Boolean, shortForm: Boolean) = notYet()
    override fun stopSession() = notYet()
    override fun fix(item: ProtectionItem) = notYet()
    override fun startFocus(minutes: Int, strict: Boolean, shortForm: Boolean, tier: FocusTier) = notYet()
    override fun summarizeMonitor() = notYet()
    override fun setFocusApp(pkg: String, on: Boolean) = notYet()
    override fun addFocusSite(raw: String) = notYet()
    override fun removeFocusSite(domain: String) = notYet()
    override fun setLimit(pkg: String, minutes: Long?) = notYet()
    override fun setFeed(feed: ShortFormFeed, on: Boolean) = notYet()
    override fun addHabit(name: String) = notYet()
    override fun addHabitPlan(
        name: String,
        frequency: HabitFrequency,
        target: Int,
        reminderMinute: Int?,
        goalMinutes: Int?,
        linkedPackage: String?,
    ) = notYet()
    override fun logMinutes(habitId: String, minutes: Int) = notYet()
    override fun markToday(habitId: String, name: String, done: Boolean) = notYet()
    override fun startTalking() = notYet()
    override fun stopTalking() = Unit
    override fun confirmCommand() = notYet()
    override fun discardCommand() = notYet()
    override fun importCalendar() = notYet()
    override fun weeklyReview() = notYet()
    override fun selectDay(day: LocalDate, then: CalendarMode?) = notYet()
    override fun showCalendar(mode: CalendarMode) = notYet()
    override fun saveFocusBlock(replacing: String?, day: LocalDate, startMinute: Int, minutes: Int) = notYet()
    override fun deleteFocusBlock(id: String) = notYet()
    override fun addQuest(kind: RpgKind, title: String) = notYet()
    override fun tapHabit(id: String, up: Boolean) = notYet()
    override fun checkDaily(id: String, done: Boolean) = notYet()
    override fun completeTodo(id: String) = notYet()
    override fun deleteQuest(id: String) = notYet()
    override fun syncArena() = notYet()
    override fun shareInvite() = notYet()
    override fun createChallenge(habit: String, title: String, days: String, freezes: String, relay: String, name: String) = notYet()
    override fun nudge(challengeId: String, memberId: String) = notYet()
    override fun pickTimetablePhoto() = notYet()
    override fun setImportText(text: String) = notYet()
    override fun extractSchedule() = notYet()
    override fun addScheduleBlocks() = notYet()
    override fun clearImport() = notYet()
    override fun startDictation() = notYet()
    override fun stopDictation() = Unit
    override fun dictationLevel(): Float = 0f
    override fun joinChallenge(invite: String, habit: String, name: String) = notYet()
    override fun saveKey(key: String) = notYet()
    override fun removeKey() = notYet()
    override fun setVoiceConsent(on: Boolean) = notYet()
    override fun runCopilot() = notYet()
    override fun sendChat(text: String) = notYet()
    override fun startChatVoice() = notYet()
    override fun stopChatVoice() = Unit
    override fun clearChat() = notYet()
    override fun grant(permission: Permission) = if (permission == Permission.USAGE) say(focus.enable()) else notYet()
    override fun finishOnboarding() = notYet()
    override fun restartOnboarding() = notYet()
    override fun chooseBackend(kind: BackendKind?, url: String, key: String) = notYet()
    override fun signIn(email: String, password: String) = notYet()
    override fun signUp(email: String, password: String) = notYet()
    override fun signOut() = notYet()
    override fun deleteAccount() = notYet()

    private companion object {
        const val NOT_YET = "Not on Linux yet. This arrives with the next stage of the desktop app."
    }
}
