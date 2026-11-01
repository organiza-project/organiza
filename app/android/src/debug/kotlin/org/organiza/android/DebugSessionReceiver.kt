package org.organiza.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.organiza.enforcement.SessionPolicy

/** Lets scripts/verify-android.sh drive sessions and read tamper state over adb. */
class DebugSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = OrganizaApp.from(context)
        when (intent.getStringExtra("command")) {
            "start" -> {
                val input = SessionInput.parse(
                    intent.getStringExtra("packages").orEmpty(),
                    intent.getStringExtra("domains").orEmpty(),
                )
                app.startSession(
                    SessionPolicy(
                        input.blockList,
                        intent.getBooleanExtra("strict", false),
                        intent.getBooleanExtra("shortForm", false),
                    ),
                )
            }
            "stop" -> app.stopSession()
        }
        val capabilities = app.granted().sorted().joinToString(",")
        val tamper = app.tamperEvents().joinToString(",") { it.capability.name }
        Log.i("OrganizaDebug", "capabilities=$capabilities tamper=$tamper")
    }
}
