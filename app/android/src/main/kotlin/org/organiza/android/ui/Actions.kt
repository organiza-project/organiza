package org.organiza.android.ui

import org.organiza.presentation.Permission
import org.organiza.presentation.ProtectionItem
import org.organiza.sync.BackendKind

/** Every user action the screens can ask for. The activity maps these to the app (ADR-0012). */
interface Actions {
    // Focus
    fun startSession(apps: String, sites: String, minutes: String, strict: Boolean, shortForm: Boolean)
    fun stopSession()
    fun fix(item: ProtectionItem)

    // Habits, voice and calendar
    fun addHabit(name: String)
    fun markToday(habitId: String, name: String, done: Boolean)
    fun startTalking()
    fun stopTalking()
    fun confirmCommand()
    fun discardCommand()
    fun importCalendar()
    fun weeklyReview()

    // Arena
    fun syncArena()
    fun shareInvite()
    fun createChallenge(habit: String, title: String, days: String, freezes: String, relay: String, name: String)
    fun joinChallenge(invite: String, habit: String, name: String)

    // AI
    fun saveKey(key: String)
    fun removeKey()
    fun setVoiceConsent(on: Boolean)
    fun runCopilot()

    // Onboarding
    fun grant(permission: Permission)
    fun finishOnboarding()
    fun restartOnboarding()

    // Backend and account. A null kind means local only.
    fun chooseBackend(kind: BackendKind?, url: String, key: String)
    fun signIn(email: String, password: String)
    fun signUp(email: String, password: String)
    fun signOut()
}
