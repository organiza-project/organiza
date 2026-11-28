package org.organiza.ui

import org.organiza.enforcement.ShortFormFeed
import org.organiza.presentation.Permission
import org.organiza.storage.FocusTier
import org.organiza.storage.HabitFrequency
import org.organiza.presentation.ProtectionItem
import org.organiza.sync.BackendKind
import org.organiza.storage.RpgKind
import java.time.LocalDate

/** Every user action the screens can ask for. The activity maps these to the app (ADR-0012). */
interface Actions {
    // Focus
    fun startSession(apps: String, sites: String, minutes: String, strict: Boolean, shortForm: Boolean)
    fun stopSession()
    fun fix(item: ProtectionItem)

    // The focus list, daily limits and short video feeds (ADR-0018)
    fun startFocus(minutes: Int, strict: Boolean, shortForm: Boolean, tier: FocusTier)
    fun summarizeMonitor()
    fun setFocusApp(pkg: String, on: Boolean)
    fun addFocusSite(raw: String)
    fun removeFocusSite(domain: String)
    fun setLimit(pkg: String, minutes: Long?)
    fun setFeed(feed: ShortFormFeed, on: Boolean)

    // Habits, voice and calendar
    fun addHabit(name: String)
    fun addHabitPlan(name: String, frequency: HabitFrequency, target: Int, reminderMinute: Int?, goalMinutes: Int?, linkedPackage: String?)
    fun logMinutes(habitId: String, minutes: Int)
    fun markToday(habitId: String, name: String, done: Boolean)
    fun startTalking()
    fun stopTalking()
    fun confirmCommand()
    fun discardCommand()
    fun importCalendar()
    fun weeklyReview()

    // Calendar (ADR-0019). [then] switches the view once the day is chosen.
    fun selectDay(day: LocalDate, then: CalendarMode?)
    fun showCalendar(mode: CalendarMode)
    fun saveFocusBlock(replacing: String?, day: LocalDate, startMinute: Int, minutes: Int)
    fun deleteFocusBlock(id: String)

    fun addEvent(day: LocalDate, startMinute: Int, minutes: Int, title: String, remindBefore: Int?)

    fun deleteEvent(id: String)

    // Habits, dailies and to-dos with health and XP (ADR-0019)
    fun addQuest(kind: RpgKind, title: String)
    fun tapHabit(id: String, up: Boolean)
    fun checkDaily(id: String, done: Boolean)
    fun completeTodo(id: String)
    fun deleteQuest(id: String)


    // Schedule import
    fun pickTimetablePhoto()
    fun setImportText(text: String)
    fun extractSchedule()
    fun addScheduleBlocks()
    fun clearImport()
    fun startDictation()
    fun stopDictation()
    fun dictationLevel(): Float

    // AI
    fun saveKey(key: String)
    fun removeKey()
    fun setVoiceConsent(on: Boolean)
    fun runCopilot()
    fun sendChat(text: String)
    fun startChatVoice()
    fun stopChatVoice()
    fun clearChat()

    // Onboarding
    fun grant(permission: Permission)
    fun finishOnboarding()
    fun restartOnboarding()

    // Backend and account. A null kind means local only.
    fun chooseBackend(kind: BackendKind?, url: String, key: String)
    fun signIn(email: String, password: String)
    fun signUp(email: String, password: String)
    fun signOut()
    fun deleteAccount()

    /** The copilot's memory of the person, in their own words (ADR-0033). */
    fun saveAboutMe(text: String)

    // The lockout (ADR-0032)
    fun setLockout(on: Boolean)
    fun unlock(typed: String)
    fun cancelUnlock()

    // The usage report (ADR-0031)
    fun setUsageSpan(span: UsageSpan)
    fun shiftUsage(step: Int)
    fun shiftUsageEdge(start: Boolean, days: Int)
    fun setUsageAllDevices(on: Boolean)

    // Notes (ADR-0030)
    /** [remindDay]: 0 today, 1 tomorrow, null for no reminder; [minute] of that day. */
    fun addNote(title: String, body: String, subject: String, remindDay: Int?, minute: Int)

    fun showOlderNotes()
    fun deleteNote(id: String)
    fun organizeNote(id: String)
    fun filterNotes(subject: String?)
    fun startNoteDictation()
    fun stopNoteDictation()

    // Usage across this person's devices (ADR-0028)
    /** Joins the devices behind [code], or starts a new set when it is blank. */
    fun linkDevices(code: String)
    fun unlinkDevices()
    fun shareDeviceCode()

    /** Android's App info for Organiza, where "Allow restricted settings" lives. */
    fun openAppInfo()
}
