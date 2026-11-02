package org.organiza.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.IOException
import java.time.Duration
import org.organiza.arena.ChallengeRules
import org.organiza.enforcement.SessionPolicy
import org.organiza.scheduler.ManualRequest

/** Plain permission and test screen for Phase 1. Phase 5 replaces it (ADR-0007). */
class SetupActivity : Activity() {
    private val app get() = OrganizaApp.from(this)
    private lateinit var status: TextView
    private var recorder: VoiceRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this)
        val packages = EditText(this).apply { hint = "Packages, comma separated" }
        val domains = EditText(this).apply { hint = "Domains, comma separated" }
        val minutes = EditText(this).apply {
            hint = "Minutes"
            setText("30")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val apiKey = EditText(this).apply {
            hint = "Groq API key"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val goal = EditText(this).apply { hint = "Your goal this week" }
        val reason = EditText(this).apply { hint = "Why do you need to leave this session?" }
        val arenaHabit = EditText(this).apply { hint = "Habit for the challenge" }
        val arenaTitle = EditText(this).apply { hint = "Challenge title" }
        val arenaDays = EditText(this).apply {
            hint = "Days"
            setText("30")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val arenaFreezes = EditText(this).apply {
            hint = "Freeze days"
            setText("2")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val arenaRelay = EditText(this).apply { hint = "Relay address (https)" }
        val arenaName = EditText(this).apply { hint = "Your name in the group" }
        val arenaInvite = EditText(this).apply {
            hint = "Paste an invite"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val strict = CheckBox(this).apply { text = "Strict (guards Organiza's settings pages)" }
        val shortForm = CheckBox(this).apply { text = "Block Shorts and Reels" }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(PADDING, PADDING, PADDING, PADDING)
            addView(status)
            addView(button("Accessibility settings") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            addView(button("Allow VPN") { VpnService.prepare(this@SetupActivity)?.let { startActivityForResult(it, 0) } })
            addView(button("Enable uninstall protection") { requestDeviceAdmin() })
            addView(packages)
            addView(domains)
            addView(minutes)
            addView(strict)
            addView(shortForm)
            addView(button("Start session") {
                val input = SessionInput.parse(packages.text.toString(), domains.text.toString())
                val request = ManualRequest(
                    input.blockList.packages,
                    input.blockList.domains,
                    strict.isChecked,
                    shortForm.isChecked,
                )
                app.startManual(request, Duration.ofMinutes(minutes.text.toString().toLongOrNull() ?: 0))
                refreshSoon(input.rejected)
            })
            addView(button("Stop session") {
                app.stopSession()
                refreshSoon()
            })
            addView(button("Import calendar (.ics)") { pickCalendar() })
            addView(TextView(this@SetupActivity).apply { text = AI_NOTICE })
            addView(apiKey)
            addView(button("Save API key") {
                app.saveApiKey(apiKey.text.toString())
                apiKey.text.clear()
            })
            addView(button("Remove API key") { app.clearApiKey() })
            addView(button("Weekly review") { app.requestWeeklyReview() })
            addView(goal)
            addView(button("Suggest a schedule") { app.requestSchedule(goal.text.toString()) })
            addView(talkButton())
            addView(button("Confirm command") { app.confirmVoiceCommand() })
            addView(button("Discard command") { app.discardVoiceCommand() })
            addView(TextView(this@SetupActivity).apply { text = ARENA_NOTICE })
            addView(arenaHabit)
            addView(button("Mark this habit done today") { app.markHabitDone(arenaHabit.text.toString()) })
            addView(arenaTitle)
            addView(arenaDays)
            addView(arenaFreezes)
            addView(arenaRelay)
            addView(arenaName)
            addView(button("Create a challenge") {
                val rules = ChallengeRules(
                    arenaDays.text.toString().toIntOrNull() ?: 0,
                    arenaFreezes.text.toString().toIntOrNull() ?: 0,
                )
                app.createChallenge(
                    arenaHabit.text.toString(),
                    arenaTitle.text.toString(),
                    rules,
                    arenaRelay.text.toString().trim(),
                    arenaName.text.toString(),
                )
            })
            addView(button("Share the invite") { shareInvite() })
            addView(arenaInvite)
            addView(button("Join with this invite") {
                app.joinChallenge(arenaInvite.text.toString(), arenaHabit.text.toString(), arenaName.text.toString())
                arenaInvite.text.clear()
            })
            addView(button("Sync challenges now") { app.arenaNow() })
            addView(reason)
            addView(button("Ask the gatekeeper") { app.askGatekeeper(reason.text.toString(), 30) })
        }
        setContentView(ScrollView(this).apply { addView(column) })
    }

    private val poll = object : Runnable {
        override fun run() {
            refresh()
            status.postDelayed(this, POLL_MS)
        }
    }

    override fun onResume() {
        super.onResume()
        poll.run()
    }

    override fun onPause() {
        status.removeCallbacks(poll)
        // Recording never continues once the screen is left.
        recorder?.stop()
        recorder = null
        super.onPause()
    }

    /** Storage work is asynchronous, so the result shows a moment later. */
    private fun refreshSoon(rejected: List<String> = emptyList()) {
        status.postDelayed({ refresh(rejected) }, REFRESH_DELAY_MS)
    }

    private fun pickCalendar() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        startActivityForResult(intent, REQUEST_CALENDAR)
    }

    @Deprecated("Deprecated in Android, and adequate for this temporary screen")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQUEST_CALENDAR || resultCode != RESULT_OK || uri == null) return
        try {
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            if (text != null) app.importCalendar(text)
            refreshSoon()
        } catch (_: IOException) {
            status.text = "Could not read that file"
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun talkButton() = Button(this).apply {
        text = "Hold to speak a command"
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> startTalking()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.performClick()
                    stopTalking()
                }
            }
            true
        }
    }

    private fun startTalking() {
        if (!app.voiceConsent) {
            AlertDialog.Builder(this)
                .setTitle("Send recordings to Groq?")
                .setMessage(VOICE_NOTICE)
                .setPositiveButton("Allow") { _, _ -> app.voiceConsent = true }
                .setNegativeButton("Not now", null)
                .show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
            return
        }
        recorder = VoiceRecorder().takeIf(VoiceRecorder::start)
        if (recorder == null) status.text = "Could not start the microphone"
    }

    private fun stopTalking() {
        val active = recorder ?: return
        recorder = null
        app.handleSpeech(active.stop(), VoiceRecorder.SAMPLE_RATE)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener(View.OnClickListener { onClick() })
    }

    private fun shareInvite() {
        val invite = app.latestInvite
        if (invite == null) {
            status.text = "Create a challenge first"
            return
        }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, invite)
        startActivity(Intent.createChooser(send, "Share the invite"))
    }

    private fun requestDeviceAdmin() {
        startActivity(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, OrganizaApp.adminComponent(this)),
        )
    }

    private fun refresh(rejected: List<String> = emptyList()) {
        status.text = buildString {
            appendLine("Granted: ${app.granted().joinToString().ifEmpty { "nothing" }}")
            appendLine("VPN consent: ${if (VpnService.prepare(this@SetupActivity) == null) "yes" else "no"}")
            appendLine("Session: ${if (app.session.current == SessionPolicy.IDLE) "idle" else "running"}")
            appendLine("Tamper events: ${app.tamperCount}")
            app.storageFailure?.let { appendLine("Storage problem: $it. Your data has not been deleted.") }
            app.statusMessage?.let(::appendLine)
            app.arenaSummary.takeIf(String::isNotEmpty)?.let(::appendLine)
            if (rejected.isNotEmpty()) appendLine("Could not read: ${rejected.joinToString()}")
        }
    }

    private companion object {
        const val PADDING = 48
        const val ARENA_NOTICE = "Challenges share only your daily result and streak with your group, encrypted so " +
            "the relay cannot read them. Results are self-reported: \"confirmed\" means they were not rewritten, " +
            "not that they are proven true."
        const val REQUEST_CALENDAR = 1
        const val REQUEST_MICROPHONE = 2
        const val VOICE_NOTICE = "While you hold the button, your voice is recorded and sent to Groq using your own " +
            "key, so they can turn it into text. Unlike typed text, a recording cannot have names removed first. " +
            "Organiza keeps no recordings or transcripts, and silence is never sent."
        const val REFRESH_DELAY_MS = 400L
        const val POLL_MS = 1000L
        const val AI_NOTICE = "AI is optional and uses your own Groq key. Goals and answers you type are sent to " +
            "Groq after names, links and numbers are removed where detected, which may not catch everything. " +
            "App and website history is never sent."
    }
}
