package org.organiza.android

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.io.File
import java.time.Duration
import org.organiza.ai.GroqEndpoint
import org.organiza.scheduler.ManualRequest
import org.organiza.storage.StoredRule

/** Lets scripts/verify-android.sh drive sessions and read state over adb. */
class DebugSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = OrganizaApp.from(context)
        val input = SessionInput.parse(
            intent.getStringExtra("packages").orEmpty(),
            intent.getStringExtra("domains").orEmpty(),
        )
        val strict = intent.getBooleanExtra("strict", false)
        when (intent.getStringExtra("command")) {
            "start" -> app.startManual(
                ManualRequest(
                    input.blockList.packages,
                    input.blockList.domains,
                    strict,
                    intent.getBooleanExtra("shortForm", false),
                ),
                Duration.ofMinutes(intent.getLongExtra("minutes", 60)),
            )
            "stop" -> app.endAllForTesting()
            "userstop" -> app.stopSession()
            // adb can only remove test-only admins, but an admin can always remove itself.
            "removeadmin" -> if (app.deviceAdminActive()) {
                context.getSystemService(DevicePolicyManager::class.java)
                    .removeActiveAdmin(OrganizaApp.adminComponent(context))
            }
            "rule" -> app.saveRule(
                StoredRule(
                    id = intent.getStringExtra("id").orEmpty(),
                    name = intent.getStringExtra("name").orEmpty(),
                    // Sent without the hash, which adb's remote shell would read as a comment.
                    calendarTag = "#" + intent.getStringExtra("tag").orEmpty(),
                    strict = strict,
                    blockShortForm = false,
                    enabled = true,
                    packages = input.blockList.packages,
                    domains = input.blockList.domains,
                ),
            )
            "aiendpoint" -> app.aiEndpoint = GroqEndpoint.forTesting(intent.getStringExtra("url").orEmpty())
            "savekey" -> app.saveApiKey(intent.getStringExtra("key").orEmpty())
            "clearkey" -> app.clearApiKey()
            "aireview" -> app.requestWeeklyReview()
            "aischedule" -> app.requestSchedule(decoded(intent))
            "aigate" -> app.askGatekeeper(decoded(intent), 30)
            "voicetone" -> app.handleSpeech(ShortArray(16_000) { if (it % 2 == 0) 8000 else -8000 }, 16_000)
            "voicesilence" -> app.handleSpeech(ShortArray(16_000), 16_000)
            "voicefile" -> app.handleSpeech(wavSamples(File(app.filesDir, intent.getStringExtra("name").orEmpty())), 16_000)
            "voicerecord" -> {
                val recorder = VoiceRecorder()
                if (recorder.start()) {
                    Handler(Looper.getMainLooper()).postDelayed(
                        { app.handleSpeech(recorder.stop(), VoiceRecorder.SAMPLE_RATE) },
                        2000,
                    )
                }
            }
            "voiceconfirm" -> app.confirmVoiceCommand()
            "voicediscard" -> app.discardVoiceCommand()
            "ics" -> app.importCalendar(
                Base64.decode(intent.getStringExtra("base64").orEmpty(), Base64.DEFAULT).decodeToString(),
            )
        }
        app.logStatus()
    }

    private fun wavSamples(file: File): ShortArray {
        val bytes = file.readBytes()
        return ShortArray((bytes.size - 44) / 2) { index ->
            ((bytes[45 + index * 2].toInt() shl 8) or (bytes[44 + index * 2].toInt() and 0xff)).toShort()
        }
    }

    private fun decoded(intent: Intent): String =
        Base64.decode(intent.getStringExtra("base64").orEmpty(), Base64.DEFAULT).decodeToString()
}
