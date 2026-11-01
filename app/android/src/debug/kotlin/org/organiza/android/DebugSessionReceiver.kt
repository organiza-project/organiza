package org.organiza.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import java.time.Duration
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
            "ics" -> app.importCalendar(
                Base64.decode(intent.getStringExtra("base64").orEmpty(), Base64.DEFAULT).decodeToString(),
            )
        }
        app.logStatus()
    }
}
