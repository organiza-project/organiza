package org.organiza.android

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.io.IOException
import org.organiza.android.feature.onboarding.Permissions
import org.organiza.android.feature.schedule.TimetableOcr
import org.organiza.android.feature.voice.VoiceRecorder
import org.organiza.app.CoreActions
import org.organiza.app.Recorder
import org.organiza.presentation.Permission
import org.organiza.storage.HabitFrequency
import org.organiza.ui.OrganizaRoot
import org.organiza.ui.OrganizaTheme

class MainActivity : ComponentActivity() {
    private val app get() = OrganizaApp.from(this)
    private val core get() = app.core

    private val calendarPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openInputStream(uri)?.use { core.importCalendar(it.readBytes().decodeToString()) }
        } catch (_: IOException) {
            core.showMessage("Could not read that file")
        }
    }
    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { core.requestUiRefresh() }
    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) core.showMessage("Voice needs the microphone permission")
    }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) core.showMessage("Reminders need notifications. Turn them on in Settings to see them")
    }
    // The system photo picker needs no storage permission, and hands over only the chosen photo.
    private val timetablePhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { core.schedule.readPhoto { TimetableOcr(this).recognise(it) } }
    }

    private val actions by lazy { AndroidActions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OrganizaTheme { OrganizaRoot(core.uiState.value, actions) } }
    }

    override fun onResume() {
        super.onResume()
        core.requestUiRefresh()
        if (core.onboarded) core.copilot.runIfDue()
    }

    override fun onPause() {
        actions.stopAllRecording()
        super.onPause()
    }

    /** The shared actions, with Android's pickers, dialogs, settings screens and microphone. */
    private inner class AndroidActions : CoreActions(core) {
        override fun record(): Recorder? {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                microphone.launch(Manifest.permission.RECORD_AUDIO)
                return null
            }
            return VoiceRecorder().takeIf(VoiceRecorder::start) ?: null.also { core.showMessage("Could not start the microphone") }
        }

        override fun askVoiceConsent() {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Send recordings to Groq?")
                .setMessage(VOICE_NOTICE)
                .setPositiveButton("Allow") { _, _ -> setVoiceConsent(true) }
                .setNegativeButton("Not now", null)
                .show()
        }

        override fun grant(permission: Permission) {
            if (permission == Permission.VPN) {
                VpnService.prepare(this@MainActivity)?.let(vpnConsent::launch)
                return
            }
            val intent = Permissions.settingsIntent(this@MainActivity, permission) ?: return
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                if (permission == Permission.BATTERY) startActivity(Permissions.batteryFallback) else core.showMessage("This phone has no screen for that")
            }
        }

        override fun deleteAccount() {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Delete account and cloud data?")
                .setMessage(DELETE_NOTICE)
                .setPositiveButton("Delete") { _, _ -> core.sync.deleteAccount() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        override fun addHabitPlan(name: String, frequency: HabitFrequency, target: Int, reminderMinute: Int?, goalMinutes: Int?, linkedPackage: String?) {
            super.addHabitPlan(name, frequency, target, reminderMinute, goalMinutes, linkedPackage)
            val needs = reminderMinute != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needs) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        override fun pickTimetablePhoto() = timetablePhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

        override fun importCalendar() = calendarPicker.launch(arrayOf("text/calendar", "*/*"))

        override fun openAppInfo() {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }

        override fun shareDeviceCode() {
            val code = core.devices.code() ?: return core.showMessage("Start sharing usage first")
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, code)
            startActivity(Intent.createChooser(send, "Send the device code to yourself"))
        }

        override fun shareInvite() = core.inviteToShare { invite ->
            if (invite == null) {
                core.showMessage("Create or join a challenge first")
            } else {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, invite)
                startActivity(Intent.createChooser(send, "Share the invite"))
            }
        }
    }
}
