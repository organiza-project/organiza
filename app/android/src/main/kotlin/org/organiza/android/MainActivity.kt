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
import org.organiza.android.feature.arena.ArenaScreen
import org.organiza.android.feature.focus.FocusScreen
import org.organiza.android.feature.focus.SessionInput
import org.organiza.android.feature.habits.HabitsScreen
import org.organiza.android.feature.onboarding.OnboardingScreen
import org.organiza.android.feature.onboarding.Permissions
import org.organiza.android.feature.vault.VaultScreen
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val colors = Theme.colors
    Column(Modifier.fillMaxSize().background(colors.ground).windowInsetsPadding(WindowInsets.safeDrawing)) {
        // Each tab keeps its own scroll position, so switching never opens one halfway down.
        val scroll = rememberSaveable(tab, saver = androidx.compose.foundation.ScrollState.Saver) {
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
                tab == 0 -> FocusScreen(ui, actions)
                tab == 1 -> HabitsScreen(ui, actions)
                tab == 2 -> ArenaScreen(ui, actions)
                else -> VaultScreen(ui, actions)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (!ui.onboarding) BottomBar(tab) { tab = it }
    }
}
