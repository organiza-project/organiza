package org.organiza.android

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.organiza.android.data.AndroidUsageHistory
import org.organiza.ai.GroqEndpoint
import org.organiza.feature.focus.SessionInput
import org.organiza.android.feature.voice.VoiceRecorder
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.port.BlockTarget
import org.organiza.arena.ChallengeRules
import org.organiza.scheduler.ManualRequest
import org.organiza.storage.RpgKind
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
            "start" -> app.core.startManual(
                ManualRequest(
                    input.blockList.packages,
                    input.blockList.domains,
                    strict,
                    intent.getBooleanExtra("shortForm", false),
                ),
                Duration.ofMinutes(intent.getLongExtra("minutes", 60)),
            )
            "stop" -> app.core.endAllForTesting()
            "userstop" -> app.core.stopSession()
            // adb can only remove test-only admins, but an admin can always remove itself.
            "removeadmin" -> if (app.deviceAdminActive()) {
                context.getSystemService(DevicePolicyManager::class.java)
                    .removeActiveAdmin(OrganizaApp.adminComponent(context))
            }
            "rule" -> app.core.saveRule(
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
            "aiendpoint" -> app.core.aiEndpoint = GroqEndpoint.forTesting(intent.getStringExtra("url").orEmpty())
            "savekey" -> app.core.saveApiKey(intent.getStringExtra("key").orEmpty())
            "clearkey" -> app.core.clearApiKey()
            "aireview" -> app.core.requestWeeklyReview()
            "aischedule" -> app.core.requestSchedule(decoded(intent))
            "aicopilot" -> app.core.copilot.run()
            "stay" -> app.core.stayFocusedForTesting?.invoke()
            // Grants a pass as an override would, without the typing, for the enforcement checks.
            "pass" -> {
                val length = Duration.ofSeconds(intent.getLongExtra("seconds", 30))
                input.blockList.packages.forEach { app.core.passes.grant(BlockTarget.App(it), length) }
                // A site pass is keyed by the list entry, so the browser named here does not matter.
                val browser = (PackageName.parse("com.android.chrome") as Parsed.Valid).value
                input.blockList.domains.forEach { app.core.passes.grant(BlockTarget.Site(browser, it), length) }
            }
            "onboarded" -> app.core.onboarded = intent.getBooleanExtra("done", true)
            "voicetone" -> app.core.handleSpeech(ShortArray(16_000) { if (it % 2 == 0) 8000 else -8000 }, 16_000)
            "voicesilence" -> app.core.handleSpeech(ShortArray(16_000), 16_000)
            "voicefile" -> app.core.handleSpeech(wavSamples(File(app.filesDir, intent.getStringExtra("name").orEmpty())), 16_000)
            "voicerecord" -> {
                val recorder = VoiceRecorder()
                if (recorder.start()) {
                    Handler(Looper.getMainLooper()).postDelayed(
                        { app.core.handleSpeech(recorder.stop(), VoiceRecorder.SAMPLE_RATE) },
                        2000,
                    )
                }
            }
            "voiceconfirm" -> app.core.confirmVoiceCommand()
            "voicediscard" -> app.core.discardVoiceCommand()
            "arenacreate" -> app.core.createChallenge(
                intent.getStringExtra("habit").orEmpty(),
                intent.getStringExtra("title").orEmpty(),
                ChallengeRules(
                    intent.getIntExtra("days", 1),
                    intent.getIntExtra("freezes", 0),
                    intent.getIntExtra("maxFailures", 0),
                    strict,
                ),
                intent.getStringExtra("relay").orEmpty(),
                intent.getStringExtra("name").orEmpty(),
            )
            "arenahabit" -> app.core.addCriterionHabitForTesting(
                intent.getStringExtra("name").orEmpty(),
                intent.getStringExtra("kind").orEmpty(),
                intent.getStringExtra("target").orEmpty(),
            )
            "habitday" -> app.core.markHabitDaysAgoForTesting(
                intent.getStringExtra("name").orEmpty(),
                intent.getLongExtra("ago", 0),
                intent.getBooleanExtra("done", true),
            )
            "addhabit" -> app.core.addHabit(intent.getStringExtra("name").orEmpty())
            "arenamark" -> app.core.markHabitDone(intent.getStringExtra("name").orEmpty())
            "arenasync" -> app.core.arenaNow()
            "arenainvite" -> Log.i("OrganizaDebug", "invite=${app.core.latestInvite}")
            "clockdays" -> app.core.clockOffsetForTesting = Duration.ofDays(intent.getLongExtra("days", 0))
            // Today's foreground seconds for one package, as the limits and the hub read them.
            "usage" -> {
                val app0 = (PackageName.parse(intent.getStringExtra("package").orEmpty()) as? Parsed.Valid)?.value
                if (app0 != null) {
                    val zone = ZoneId.systemDefault()
                    val now = Instant.now()
                    val used = AndroidUsageHistory(context).foregroundTime(app0, now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant(), now)
                    Log.i("OrganizaDebug", "usage=${used.seconds}")
                }
                return
            }
            "quest" -> app.core.game.add(RpgKind.valueOf(intent.getStringExtra("kind").orEmpty()), intent.getStringExtra("name").orEmpty())
            // Ticks a daily, taps a habit (up unless --ez up false) or completes a to-do, found by its title.
            "questtap" -> app.core.onStores { stores ->
                val task = stores.rpg.tasks().firstOrNull { it.title == intent.getStringExtra("name") } ?: return@onStores
                when (task.kind) {
                    RpgKind.DAILY -> app.core.game.daily(task.id, true)
                    RpgKind.HABIT -> app.core.game.habit(task.id, intent.getBooleanExtra("up", true))
                    RpgKind.TODO -> app.core.game.complete(task.id)
                }
            }
            "limit" -> app.core.hub.setLimit(intent.getStringExtra("package").orEmpty(), intent.getLongExtra("minutes", 60))
            "ics" -> app.core.importCalendar(
                Base64.decode(intent.getStringExtra("base64").orEmpty(), Base64.DEFAULT).decodeToString(),
            )
        }
        app.core.logStatus()
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
