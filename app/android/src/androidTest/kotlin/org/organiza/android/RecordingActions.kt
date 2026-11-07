package org.organiza.android

import org.organiza.android.ui.Actions
import org.organiza.enforcement.ShortFormFeed
import org.organiza.storage.FocusTier
import org.organiza.storage.HabitFrequency
import org.organiza.presentation.Permission
import org.organiza.presentation.ProtectionItem
import org.organiza.sync.BackendKind
import org.organiza.storage.RpgKind
import org.organiza.android.ui.CalendarMode
import java.time.LocalDate

/** Records what the screens ask for, by name, for UI tests. */
class RecordingActions : Actions {
    val calls = mutableListOf<String>()

    override fun startSession(apps: String, sites: String, minutes: String, strict: Boolean, shortForm: Boolean) {
        calls += "startSession"
    }
    override fun stopSession() { calls += "stopSession" }
    override fun selectDay(day: LocalDate, then: CalendarMode?) { calls += "selectDay $day $then" }
    override fun showCalendar(mode: CalendarMode) { calls += "showCalendar $mode" }
    override fun saveFocusBlock(replacing: String?, day: LocalDate, startMinute: Int, minutes: Int) {
        calls += "saveFocusBlock $replacing $day $startMinute $minutes"
    }
    override fun deleteFocusBlock(id: String) { calls += "deleteFocusBlock $id" }
    override fun addQuest(kind: RpgKind, title: String) { calls += "addQuest $kind $title" }
    override fun tapHabit(id: String, up: Boolean) { calls += "tapHabit $id $up" }
    override fun checkDaily(id: String, done: Boolean) { calls += "checkDaily $id $done" }
    override fun completeTodo(id: String) { calls += "completeTodo $id" }
    override fun deleteQuest(id: String) { calls += "deleteQuest $id" }
    override fun fix(item: ProtectionItem) { calls += "fix ${item.kind}" }
    override fun addHabit(name: String) { calls += "addHabit $name" }
    override fun markToday(habitId: String, name: String, done: Boolean) { calls += "markToday $name" }
    override fun startTalking() { calls += "startTalking" }
    override fun stopTalking() { calls += "stopTalking" }
    override fun confirmCommand() { calls += "confirmCommand" }
    override fun discardCommand() { calls += "discardCommand" }
    override fun importCalendar() { calls += "importCalendar" }
    override fun weeklyReview() { calls += "weeklyReview" }
    override fun syncArena() { calls += "syncArena" }
    override fun shareInvite() { calls += "shareInvite" }
    override fun createChallenge(habit: String, title: String, days: String, freezes: String, relay: String, name: String) {
        calls += "createChallenge"
    }
    override fun joinChallenge(invite: String, habit: String, name: String) { calls += "joinChallenge" }
    override fun saveKey(key: String) { calls += "saveKey" }
    override fun removeKey() { calls += "removeKey" }
    override fun setVoiceConsent(on: Boolean) { calls += "setVoiceConsent $on" }
    override fun runCopilot() { calls += "runCopilot" }
    override fun grant(permission: Permission) { calls += "grant $permission" }
    override fun finishOnboarding() { calls += "finishOnboarding" }
    override fun restartOnboarding() { calls += "restartOnboarding" }
    override fun chooseBackend(kind: BackendKind?, url: String, key: String) { calls += "chooseBackend $kind $url" }
    override fun signIn(email: String, password: String) { calls += "signIn $email" }
    override fun signUp(email: String, password: String) { calls += "signUp $email" }
    override fun signOut() { calls += "signOut" }
    override fun startFocus(minutes: Int, strict: Boolean, shortForm: Boolean, tier: FocusTier) { calls += "startFocus $minutes $tier" }
    override fun summarizeMonitor() { calls += "summarizeMonitor" }
    override fun logMinutes(habitId: String, minutes: Int) { calls += "logMinutes $habitId $minutes" }
    override fun sendChat(text: String) { calls += "sendChat $text" }
    override fun startChatVoice() { calls += "startChatVoice" }
    override fun stopChatVoice() { calls += "stopChatVoice" }
    override fun clearChat() { calls += "clearChat" }
    override fun deleteAccount() { calls += "deleteAccount" }
    override fun setFocusApp(pkg: String, on: Boolean) { calls += "setFocusApp $pkg $on" }
    override fun addFocusSite(raw: String) { calls += "addFocusSite $raw" }
    override fun removeFocusSite(domain: String) { calls += "removeFocusSite $domain" }
    override fun setLimit(pkg: String, minutes: Long?) { calls += "setLimit $pkg $minutes" }
    override fun setFeed(feed: ShortFormFeed, on: Boolean) { calls += "setFeed $feed $on" }
    override fun addHabitPlan(name: String, frequency: HabitFrequency, target: Int, reminderMinute: Int?, goalMinutes: Int?, linkedPackage: String?) {
        calls += "addHabitPlan $name $frequency $target $reminderMinute $goalMinutes $linkedPackage"
    }
    override fun nudge(challengeId: String, memberId: String) { calls += "nudge $memberId" }
    override fun pickTimetablePhoto() { calls += "pickTimetablePhoto" }
    override fun setImportText(text: String) { calls += "setImportText" }
    override fun extractSchedule() { calls += "extractSchedule" }
    override fun addScheduleBlocks() { calls += "addScheduleBlocks" }
    override fun clearImport() { calls += "clearImport" }
    override fun startDictation() { calls += "startDictation" }
    override fun stopDictation() { calls += "stopDictation" }
    override fun dictationLevel(): Float = 0f
}
