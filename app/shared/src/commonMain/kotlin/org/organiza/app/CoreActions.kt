package org.organiza.app

import java.time.Duration
import java.time.LocalDate
import org.organiza.arena.ChallengeRules
import org.organiza.enforcement.ShortFormFeed
import org.organiza.feature.focus.SessionInput
import org.organiza.presentation.Permission
import org.organiza.presentation.Protection
import org.organiza.presentation.ProtectionItem
import org.organiza.scheduler.ManualRequest
import org.organiza.storage.FocusTier
import org.organiza.storage.HabitFrequency
import org.organiza.storage.HabitPlan
import org.organiza.storage.RpgKind
import org.organiza.sync.BackendKind
import org.organiza.ui.Actions
import org.organiza.ui.CalendarMode

/** Audio held in memory while a talk button is down (ADR-0010). */
interface Recorder {
    val sampleRate: Int

    /** Loudness of the last chunk, from 0 to 1, for the dictation waveform. */
    val level: Float

    fun stop(): ShortArray
}

/**
 * Every screen action, mapped onto [core] the same way on each platform. A platform supplies the
 * microphone and the few actions that open its own pickers, dialogs and settings.
 */
abstract class CoreActions(protected val core: OrganizaCore) : Actions {
    /** Starts recording, or returns null after saying why it could not (or asking for access). */
    protected abstract fun record(): Recorder?

    /** Asks whether recordings may go to Groq. Voice stays off until the person agrees. */
    protected open fun askVoiceConsent() = core.showMessage("Turn on voice in Profile first. Recordings go to Groq with your own key")

    private var talk: Recorder? = null
    private var dictation: Recorder? = null
    private var chatVoice: Recorder? = null

    private fun startRecording(): Recorder? {
        if (!core.voiceConsent) {
            askVoiceConsent()
            return null
        }
        return record()
    }

    /** Recording never continues once the screen is left (ADR-0010). */
    fun stopAllRecording() {
        listOfNotNull(talk, dictation, chatVoice).forEach { it.stop() }
        talk = null
        dictation = null
        chatVoice = null
    }

    override fun startSession(apps: String, sites: String, minutes: String, strict: Boolean, shortForm: Boolean) {
        val input = SessionInput.parse(apps, sites)
        val request = ManualRequest(input.blockList.packages, input.blockList.domains, strict, shortForm)
        core.startManual(request, Duration.ofMinutes(minutes.toLongOrNull() ?: 0))
        if (input.rejected.isNotEmpty()) core.showMessage("Could not read: ${input.rejected.joinToString()}")
    }

    override fun stopSession() = core.stopSession()

    override fun fix(item: ProtectionItem) = grant(
        when (item.kind) {
            Protection.ACCESSIBILITY -> Permission.ACCESSIBILITY
            Protection.VPN_CONSENT -> Permission.VPN
            Protection.BATTERY -> Permission.BATTERY
            Protection.DEVICE_ADMIN -> Permission.DEVICE_ADMIN
            Protection.USAGE -> Permission.USAGE
            Protection.TRACKING -> Permission.TRACKING
            Protection.AUTOSTART -> Permission.AUTOSTART
        },
    )

    override fun finishOnboarding() {
        core.onboarded = true
        core.copilot.runIfDue()
    }

    override fun restartOnboarding() {
        core.onboarded = false
    }

    override fun chooseBackend(kind: BackendKind?, url: String, key: String) = core.sync.choose(kind, url, key)

    override fun signIn(email: String, password: String) = core.sync.signIn(email, password)

    override fun signUp(email: String, password: String) = core.sync.signUp(email, password)

    override fun signOut() = core.sync.signOut()

    override fun linkDevices(code: String) = core.devices.link(code)

    override fun unlinkDevices() = core.devices.unlink()

    override fun runCopilot() = core.copilot.run()

    override fun sendChat(text: String) = core.chat.send(text)

    override fun clearChat() = core.chat.clear()

    override fun startChatVoice() {
        chatVoice = startRecording()
    }

    override fun stopChatVoice() {
        val active = chatVoice ?: return
        chatVoice = null
        core.chatDictated(active.stop(), active.sampleRate)
    }

    override fun addHabit(name: String) = core.addHabit(name)

    override fun logMinutes(habitId: String, minutes: Int) = core.logMinutes(habitId, minutes)

    override fun addHabitPlan(name: String, frequency: HabitFrequency, target: Int, reminderMinute: Int?, goalMinutes: Int?, linkedPackage: String?) =
        core.addHabitPlan(name, HabitPlan(frequency, target, reminderMinute, goalMinutes, linkedPackage))

    override fun startFocus(minutes: Int, strict: Boolean, shortForm: Boolean, tier: FocusTier) = core.startFocus(minutes, strict, shortForm, tier)

    override fun summarizeMonitor() = core.summarizeMonitor()

    override fun setFocusApp(pkg: String, on: Boolean) = core.hub.setApp(pkg, on)

    override fun addFocusSite(raw: String) = core.hub.addSite(raw)

    override fun removeFocusSite(domain: String) = core.hub.removeSite(domain)

    override fun setLimit(pkg: String, minutes: Long?) = core.hub.setLimit(pkg, minutes)

    override fun setFeed(feed: ShortFormFeed, on: Boolean) = core.hub.setFeed(feed, on)

    override fun nudge(challengeId: String, memberId: String) = core.nudge(challengeId, memberId)

    override fun selectDay(day: LocalDate, then: CalendarMode?) = core.calendar.select(day, then)

    override fun showCalendar(mode: CalendarMode) = core.calendar.show(mode)

    override fun saveFocusBlock(replacing: String?, day: LocalDate, startMinute: Int, minutes: Int) = core.calendar.save(replacing, day, startMinute, minutes)

    override fun deleteFocusBlock(id: String) = core.calendar.delete(id)

    override fun addQuest(kind: RpgKind, title: String) = core.game.add(kind, title)

    override fun tapHabit(id: String, up: Boolean) = core.game.habit(id, up)

    override fun checkDaily(id: String, done: Boolean) = core.game.daily(id, done)

    override fun completeTodo(id: String) = core.game.complete(id)

    override fun deleteQuest(id: String) = core.game.delete(id)

    override fun setImportText(text: String) = core.schedule.setText(text)

    override fun extractSchedule() = core.schedule.extract()

    override fun addScheduleBlocks() = core.schedule.add()

    override fun clearImport() = core.schedule.clear()

    override fun startDictation() {
        dictation = startRecording()
    }

    override fun stopDictation() {
        val active = dictation ?: return
        dictation = null
        core.schedule.dictated(active.stop(), active.sampleRate)
    }

    override fun dictationLevel(): Float = dictation?.level ?: 0f

    override fun markToday(habitId: String, name: String, done: Boolean) = core.markHabit(name, done)

    override fun startTalking() {
        talk = startRecording()
    }

    override fun stopTalking() {
        val active = talk ?: return
        talk = null
        core.handleSpeech(active.stop(), active.sampleRate)
    }

    override fun confirmCommand() = core.confirmVoiceCommand()

    override fun discardCommand() = core.discardVoiceCommand()

    override fun weeklyReview() = core.requestWeeklyReview()

    override fun syncArena() = core.arenaNow()

    override fun createChallenge(habit: String, title: String, days: String, freezes: String, relay: String, name: String) {
        val rules = ChallengeRules(days.toIntOrNull() ?: 0, freezes.toIntOrNull() ?: 0)
        core.createChallenge(habit, title, rules, relay.trim(), name)
    }

    override fun joinChallenge(invite: String, habit: String, name: String) = core.joinChallenge(invite, habit, name)

    override fun saveKey(key: String) = core.saveApiKey(key)

    override fun removeKey() = core.clearApiKey()

    override fun setVoiceConsent(on: Boolean) {
        core.voiceConsent = on
        core.requestUiRefresh()
    }

    companion object {
        const val VOICE_NOTICE = "While you hold the button, your voice is recorded and sent to Groq using your own " +
            "key, so they can turn it into text. Unlike typed text, a recording cannot have names removed first. " +
            "Organiza keeps no recordings or transcripts, and silence is never sent."
        const val DELETE_NOTICE = "This deletes your account on the backend and every challenge result it holds for you, " +
            "right away. It cannot be undone. Habits, sessions and usage on this device are not touched."
    }
}
