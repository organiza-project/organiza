package org.organiza.desktop

import androidx.compose.runtime.mutableStateOf
import java.time.LocalDate
import org.organiza.enforcement.ShortFormFeed
import org.organiza.presentation.Permission
import org.organiza.presentation.ProtectionItem
import org.organiza.storage.FocusTier
import org.organiza.storage.HabitFrequency
import org.organiza.storage.RpgKind
import org.organiza.sync.BackendKind
import org.organiza.ui.Actions
import org.organiza.ui.CalendarMode
import org.organiza.ui.UiSnapshot

/**
 * The desktop state holder. Stage 1 of the Linux port draws every screen; storage, sync, AI and
 * enforcement are wired in the next stages (ADR-0023), so until then each action says so on screen.
 */
class DesktopApp : Actions {
    val ui = mutableStateOf(UiSnapshot())

    private fun notYet() {
        ui.value = ui.value.copy(message = NOT_YET)
    }

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
    override fun grant(permission: Permission) = notYet()
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
