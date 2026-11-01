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
import org.organiza.enforcement.SessionPolicy

/** Plain permission and test screen for Phase 1. Phase 5 replaces it (ADR-0007). */
class SetupActivity : Activity() {
    private val app get() = OrganizaApp.from(this)
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this)
        val packages = EditText(this).apply { hint = "Packages, comma separated" }
        val domains = EditText(this).apply { hint = "Domains, comma separated" }
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
            addView(strict)
            addView(shortForm)
            addView(button("Start session") {
                val input = SessionInput.parse(packages.text.toString(), domains.text.toString())
                app.startSession(SessionPolicy(input.blockList, strict.isChecked, shortForm.isChecked))
                refresh(input.rejected)
            })
            addView(button("Stop session") {
                app.stopSession()
                refresh()
            })
        }
        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
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
            appendLine("Tamper events: ${app.tamperEvents().size}")
            if (rejected.isNotEmpty()) appendLine("Could not read: ${rejected.joinToString()}")
        }
    }

    private companion object {
        const val PADDING = 48
    }
}
