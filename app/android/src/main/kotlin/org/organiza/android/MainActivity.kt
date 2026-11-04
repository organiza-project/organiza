package org.organiza.android

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.IOException
import java.time.Duration
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import org.organiza.android.feature.arena.ArenaScreen
import org.organiza.android.feature.blocker.BlockerHubScreen
import org.organiza.android.feature.focus.SessionInput
import org.organiza.android.feature.habits.HabitsScreen
import org.organiza.android.feature.habits.NewHabitScreen
import org.organiza.android.feature.home.HomeScreen
import org.organiza.android.feature.metrics.MetricsScreen
import org.organiza.android.feature.onboarding.OnboardingScreen
import org.organiza.android.feature.onboarding.Permissions
import org.organiza.android.feature.profile.ProfileScreen
import org.organiza.android.feature.schedule.CalendarScreen
import org.organiza.android.feature.schedule.DictationScreen
import org.organiza.android.feature.schedule.ImportScreen
import org.organiza.android.ui.Route
import org.organiza.android.ui.Tab
import org.organiza.enforcement.ShortFormFeed
import org.organiza.storage.HabitFrequency
import org.organiza.storage.HabitPlan
import org.organiza.android.feature.voice.VoiceRecorder
import org.organiza.android.ui.Actions
import org.organiza.android.ui.BottomBar
import org.organiza.android.ui.OrganizaTheme
import org.organiza.android.ui.Space
import org.organiza.android.ui.Theme
import org.organiza.android.ui.UiSnapshot
import org.organiza.arena.ChallengeRules
import org.organiza.presentation.Permission
import org.organiza.presentation.ProtectionItem
import org.organiza.scheduler.ManualRequest
import org.organiza.sync.BackendKind
import org.organiza.storage.RpgKind
import org.organiza.android.ui.CalendarMode
import java.time.LocalDate

class MainActivity : ComponentActivity(), Actions {
    private val app get() = OrganizaApp.from(this)
    private var recorder: VoiceRecorder? = null

    private val calendarPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openInputStream(uri)?.use { app.importCalendar(it.readBytes().decodeToString()) }
        } catch (_: IOException) {
            app.showMessage("Could not read that file")
        }
    }
    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { app.requestUiRefresh() }
    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) app.showMessage("Voice needs the microphone permission")
    }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) app.showMessage("Reminders need notifications. Turn them on in Settings to see them")
    }
    // The system photo picker needs no storage permission, and hands over only the chosen photo.
    private val timetablePhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let(app.schedule::readPhoto)
    }
    private var dictation: VoiceRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OrganizaTheme { Root(app.uiState.value, this) } }
    }

    override fun onResume() {
        super.onResume()
        app.requestUiRefresh()
        if (app.onboarded) app.copilot.runIfDue()
    }

    override fun onPause() {
        // Recording never continues once the screen is left (ADR-0010).
        recorder?.stop()
        recorder = null
        dictation?.stop()
        dictation = null
        super.onPause()
    }

    override fun startSession(apps: String, sites: String, minutes: String, strict: Boolean, shortForm: Boolean) {
        val input = SessionInput.parse(apps, sites)
        val request = ManualRequest(input.blockList.packages, input.blockList.domains, strict, shortForm)
        app.startManual(request, Duration.ofMinutes(minutes.toLongOrNull() ?: 0))
        if (input.rejected.isNotEmpty()) app.showMessage("Could not read: ${input.rejected.joinToString()}")
    }

    override fun stopSession() = app.stopSession()

    override fun fix(item: ProtectionItem) = grant(Permissions.forProtection(item.kind))

    override fun grant(permission: Permission) {
        if (permission == Permission.VPN) {
            VpnService.prepare(this)?.let(vpnConsent::launch)
            return
        }
        val intent = Permissions.settingsIntent(this, permission) ?: return
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            if (permission == Permission.BATTERY) startActivity(Permissions.batteryFallback) else app.showMessage("This phone has no screen for that")
        }
    }

    override fun finishOnboarding() {
        app.onboarded = true
        app.copilot.runIfDue()
    }

    override fun restartOnboarding() {
        app.onboarded = false
    }

    override fun chooseBackend(kind: BackendKind?, url: String, key: String) = app.sync.choose(kind, url, key)

    override fun signIn(email: String, password: String) = app.sync.signIn(email, password)

    override fun signUp(email: String, password: String) = app.sync.signUp(email, password)

    override fun signOut() = app.sync.signOut()

    override fun runCopilot() = app.copilot.run()

    override fun addHabit(name: String) = app.addHabit(name)

    override fun addHabitPlan(name: String, frequency: HabitFrequency, target: Int, reminderMinute: Int?) {
        app.addHabitPlan(name, HabitPlan(frequency, target, reminderMinute))
        val needs = reminderMinute != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun startFocus(minutes: Int, strict: Boolean, shortForm: Boolean) = app.startFocus(minutes, strict, shortForm)

    override fun setFocusApp(pkg: String, on: Boolean) = app.hub.setApp(pkg, on)

    override fun addFocusSite(raw: String) = app.hub.addSite(raw)

    override fun removeFocusSite(domain: String) = app.hub.removeSite(domain)

    override fun setLimit(pkg: String, minutes: Long?) = app.hub.setLimit(pkg, minutes)

    override fun setFeed(feed: ShortFormFeed, on: Boolean) = app.hub.setFeed(feed, on)

    override fun nudge(challengeId: String, memberId: String) = app.nudge(challengeId, memberId)

    override fun selectDay(day: LocalDate, then: CalendarMode?) = app.calendar.select(day, then)

    override fun showCalendar(mode: CalendarMode) = app.calendar.show(mode)

    override fun saveFocusBlock(replacing: String?, day: LocalDate, startMinute: Int, minutes: Int) =
        app.calendar.save(replacing, day, startMinute, minutes)

    override fun deleteFocusBlock(id: String) = app.calendar.delete(id)

    override fun addQuest(kind: RpgKind, title: String) = app.game.add(kind, title)

    override fun tapHabit(id: String, up: Boolean) = app.game.habit(id, up)

    override fun checkDaily(id: String, done: Boolean) = app.game.daily(id, done)

    override fun completeTodo(id: String) = app.game.complete(id)

    override fun deleteQuest(id: String) = app.game.delete(id)

    override fun pickTimetablePhoto() = timetablePhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    override fun setImportText(text: String) = app.schedule.setText(text)

    override fun extractSchedule() = app.schedule.extract()

    override fun addScheduleBlocks() = app.schedule.add()

    override fun clearImport() = app.schedule.clear()

    override fun startDictation() {
        if (!app.voiceConsent) return startTalking()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphone.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        dictation = VoiceRecorder().takeIf(VoiceRecorder::start)
        if (dictation == null) app.showMessage("Could not start the microphone")
    }

    override fun stopDictation() {
        val active = dictation ?: return
        dictation = null
        app.schedule.dictated(active.stop(), VoiceRecorder.SAMPLE_RATE)
    }

    override fun dictationLevel(): Float = dictation?.level ?: 0f

    override fun markToday(habitId: String, name: String, done: Boolean) = app.markHabit(name, done)

    override fun startTalking() {
        if (!app.voiceConsent) {
            AlertDialog.Builder(this)
                .setTitle("Send recordings to Groq?")
                .setMessage(VOICE_NOTICE)
                .setPositiveButton("Allow") { _, _ -> app.voiceConsent = true; app.requestUiRefresh() }
                .setNegativeButton("Not now", null)
                .show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphone.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        recorder = VoiceRecorder().takeIf(VoiceRecorder::start)
        if (recorder == null) app.showMessage("Could not start the microphone")
    }

    override fun stopTalking() {
        val active = recorder ?: return
        recorder = null
        app.handleSpeech(active.stop(), VoiceRecorder.SAMPLE_RATE)
    }

    override fun confirmCommand() = app.confirmVoiceCommand()

    override fun discardCommand() = app.discardVoiceCommand()

    override fun importCalendar() = calendarPicker.launch(arrayOf("text/calendar", "*/*"))

    override fun weeklyReview() = app.requestWeeklyReview()

    override fun syncArena() = app.arenaNow()

    override fun shareInvite() = app.inviteToShare { invite ->
        if (invite == null) {
            app.showMessage("Create or join a challenge first")
        } else {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, invite)
            startActivity(Intent.createChooser(send, "Share the invite"))
        }
    }

    override fun createChallenge(habit: String, title: String, days: String, freezes: String, relay: String, name: String) {
        val rules = ChallengeRules(days.toIntOrNull() ?: 0, freezes.toIntOrNull() ?: 0)
        app.createChallenge(habit, title, rules, relay.trim(), name)
    }

    override fun joinChallenge(invite: String, habit: String, name: String) = app.joinChallenge(invite, habit, name)

    override fun saveKey(key: String) = app.saveApiKey(key)

    override fun removeKey() = app.clearApiKey()

    override fun setVoiceConsent(on: Boolean) {
        app.voiceConsent = on
        app.requestUiRefresh()
    }

    private companion object {
        const val VOICE_NOTICE = "While you hold the button, your voice is recorded and sent to Groq using your own " +
            "key, so they can turn it into text. Unlike typed text, a recording cannot have names removed first. " +
            "Organiza keeps no recordings or transcripts, and silence is never sent."
    }
}

@Composable
private fun Root(ui: UiSnapshot, actions: Actions) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    // Screens opened on top of a tab (ADR-0018). Back closes the top one.
    val stack = remember { mutableStateListOf<Route>() }
    val open: (Route) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.isNotEmpty() && !ui.onboarding) { back() }
    val colors = Theme.colors
    Column(Modifier.fillMaxSize().background(colors.ground).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val route = stack.lastOrNull()
        // Each tab and screen keeps its own scroll position, so switching never opens one halfway down.
        val scroll = rememberSaveable(tab, route, saver = androidx.compose.foundation.ScrollState.Saver) {
            androidx.compose.foundation.ScrollState(0)
        }
        // Content stops at 720dp and centres on a tablet, so short panels never stretch across it.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp)
                .padding(horizontal = Space.margin),
            verticalArrangement = Arrangement.spacedBy(Space.gap),
        ) {
            when {
                ui.onboarding -> OnboardingScreen(ui, actions)
                route == Route.NEW_HABIT -> NewHabitScreen(actions, back)
                route == Route.AGENDA -> CalendarScreen(ui, actions, back, open)
                route == Route.IMPORT -> ImportScreen(ui, actions, back, open)
                route == Route.DICTATION -> DictationScreen(ui, actions, back)
                route == Route.BLOCKER -> BlockerHubScreen(ui, actions, back)
                tab == Tab.HOME -> HomeScreen(ui, actions, open) { tab = it }
                tab == Tab.HABITS -> HabitsScreen(ui, actions, open)
                tab == Tab.ARENA -> ArenaScreen(ui, actions)
                tab == Tab.METRICS -> MetricsScreen(ui, actions)
                else -> ProfileScreen(ui, actions)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (!ui.onboarding && route == null) BottomBar(tab) { tab = it }
    }
}
