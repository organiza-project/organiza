package org.organiza.android.ui

import org.organiza.enforcement.ShortFormFeed
import org.organiza.presentation.Permission
import org.organiza.storage.HabitFrequency
import org.organiza.presentation.ProtectionItem
import org.organiza.sync.BackendKind

/** Every user action the screens can ask for. The activity maps these to the app (ADR-0012). */
interface Actions {
    // Focus
    fun startSession(apps: String, sites: String, minutes: String, strict: Boolean, shortForm: Boolean)
    fun stopSession()
    fun fix(item: ProtectionItem)

    // The focus list, daily limits and short video feeds (ADR-0018)
    fun startFocus(minutes: Int, strict: Boolean, shortForm: Boolean)
    fun setFocusApp(pkg: String, on: Boolean)
    fun addFocusSite(raw: String)
    fun removeFocusSite(domain: String)
    fun setLimit(pkg: String, minutes: Long?)
    fun setFeed(feed: ShortFormFeed, on: Boolean)

    // Habits, voice and calendar
    fun addHabit(name: String)
    fun addHabitPlan(name: String, frequency: HabitFrequency, target: Int, reminderMinute: Int?)
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
    fun nudge(challengeId: String, memberId: String)

    // Schedule import
    fun pickTimetablePhoto()
    fun setImportText(text: String)
    fun extractSchedule()
    fun addScheduleBlocks()
    fun clearImport()
    fun startDictation()
    fun stopDictation()
    fun dictationLevel(): Float
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
