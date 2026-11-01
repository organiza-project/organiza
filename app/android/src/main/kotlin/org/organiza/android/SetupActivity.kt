package org.organiza.android

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.IOException
import java.time.Duration
import org.organiza.enforcement.SessionPolicy
import org.organiza.scheduler.ManualRequest

/** Plain permission and test screen for Phase 1. Phase 5 replaces it (ADR-0007). */
class SetupActivity : Activity() {
    private val app get() = OrganizaApp.from(this)
    private lateinit var status: TextView

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

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener(View.OnClickListener { onClick() })
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
            if (rejected.isNotEmpty()) appendLine("Could not read: ${rejected.joinToString()}")
        }
    }

    private companion object {
        const val PADDING = 48
        const val REQUEST_CALENDAR = 1
        const val REFRESH_DELAY_MS = 400L
        const val POLL_MS = 1000L
        const val AI_NOTICE = "AI is optional and uses your own Groq key. Goals and answers you type are sent to " +
            "Groq after names, links and numbers are removed where detected, which may not catch everything. " +
            "App and website history is never sent."
    }
}
