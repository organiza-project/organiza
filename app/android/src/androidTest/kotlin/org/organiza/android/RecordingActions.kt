package org.organiza.android

import org.organiza.android.ui.Actions
import org.organiza.presentation.Permission
import org.organiza.presentation.ProtectionItem
import org.organiza.sync.BackendKind

/** Records what the screens ask for, by name, for UI tests. */
class RecordingActions : Actions {
    val calls = mutableListOf<String>()

    override fun startSession(apps: String, sites: String, minutes: String, strict: Boolean, shortForm: Boolean) {
        calls += "startSession"
    }
    override fun stopSession() { calls += "stopSession" }
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
}
