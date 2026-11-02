package org.organiza.android.feature.ai

import android.content.Context
import java.time.Duration
import java.time.ZoneId
import org.organiza.ai.AiResult
import org.organiza.ai.CopilotAdvice
import org.organiza.ai.CopilotFacts
import org.organiza.ai.DistractionWindow
import org.organiza.ai.DistractionWindows
import org.organiza.ai.Sanitizer
import org.organiza.android.OrganizaApp
import org.organiza.android.ui.CopilotView
import org.organiza.storage.InterventionChoice
import org.organiza.storage.InterventionStore
import org.organiza.storage.StoredIntervention

/**
 * The background copilot (ADR-0017). The distraction windows are worked out locally on every
 * refresh. The AI advice runs at most once a day on its own, or when asked, and only sends counts.
 */
class CopilotController(private val app: OrganizaApp) {
    private val prefs = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Volatile private var advice: CopilotAdvice? = null
    @Volatile private var busy = false

    fun view(): CopilotView = CopilotView(advice, prefs.getString(RAN_ON, null)?.let { "Last run $it" }, busy)

    /** The hardest hours over the last 14 days. Storage thread only. */
    fun windows(store: InterventionStore, zone: ZoneId): List<DistractionWindow> {
        val (attempts, overrides) = byHour(recent(store), zone)
        return DistractionWindows.find(attempts, overrides)
    }

    /** Runs once per day when a key is saved and the app is opened. */
    fun runIfDue() {
        val today = app.clock.now().atZone(ZoneId.systemDefault()).toLocalDate().toString()
        if (prefs.getString(RAN_ON, null) == today || !app.hasApiKey()) return
        run()
    }

    fun run() {
        if (busy) return
        busy = true
        app.requestUiRefresh()
        app.onStores { stores ->
            val zone = ZoneId.systemDefault()
            val now = app.clock.now()
            val today = now.atZone(zone).toLocalDate()
            val (attempts, overrides) = byHour(recent(stores.interventions), zone)
            val habits = stores.aiFacts.habits(today.minusDays(DAYS), today.plusDays(1), Sanitizer())
            val facts = CopilotFacts(
                days = DAYS.toInt(),
                attemptsByHour = attempts,
                overridesByHour = overrides,
                focusMinutes = stores.aiFacts.weekly(now.minus(Duration.ofDays(DAYS)), now, habits).focusMinutes,
                habits = habits,
                busy = stores.aiFacts.busy(now, now.plus(Duration.ofDays(7)), zone),
            )
            app.onAi {
                try {
                    when (val result = app.coach().copilot(facts)) {
                        is AiResult.Ok -> {
                            advice = result.value
                            prefs.edit().putString(RAN_ON, today.toString()).apply()
                        }
                        is AiResult.Failed -> app.showMessage("Copilot failed: ${result.failure}")
                    }
                } finally {
                    busy = false
                    app.requestUiRefresh()
                }
            }
        }
    }

    private fun recent(store: InterventionStore): List<StoredIntervention> {
        val now = app.clock.now()
        return store.between(now.minus(Duration.ofDays(DAYS)), now.plusSeconds(1))
    }

    private fun byHour(events: List<StoredIntervention>, zone: ZoneId): Pair<List<Int>, List<Int>> =
        DistractionWindows.byHour(events.map { it.at }, zone) to
            DistractionWindows.byHour(events.filter { it.choice == InterventionChoice.OVERRODE }.map { it.at }, zone)

    private companion object {
        const val PREFERENCES = "organiza_copilot"
        const val RAN_ON = "ran_on"
        const val DAYS = 14L
    }
}
