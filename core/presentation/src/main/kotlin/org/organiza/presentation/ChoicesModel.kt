package org.organiza.presentation

import java.time.ZoneId
import org.organiza.ai.DistractionWindow
import org.organiza.domain.OverrideReason
import org.organiza.domain.PackageName
import org.organiza.domain.port.BlockTarget
import org.organiza.storage.InterventionChoice
import org.organiza.storage.StoredIntervention

public data class ChoiceRow(val time: String, val target: String, val outcome: String, val detail: String?)

/** Today's interventions as conscious choices (ADR-0016), and the hardest hours (ADR-0017). */
public data class ChoicesModel(
    val headline: String,
    val stayed: Int,
    val overrode: Int,
    val rows: List<ChoiceRow>,
    val windows: List<String>,
) {
    public companion object {
        public const val MAX_ROWS: Int = 10

        /** [today] holds today's events, oldest first. [appName] names a package for people. */
        public fun build(
            today: List<StoredIntervention>,
            zone: ZoneId,
            appName: (PackageName) -> String,
            windows: List<DistractionWindow>,
        ): ChoicesModel {
            val stayed = today.count { it.choice == InterventionChoice.STAYED }
            val headline = when {
                today.isEmpty() -> "No blocked attempts today"
                today.size == 1 && stayed == 1 -> "You stayed focused the 1 time today"
                else -> "You stayed focused $stayed of ${today.size} times today"
            }
            val rows = today.asReversed().take(MAX_ROWS).map { event ->
                ChoiceRow(
                    time = event.at.atZone(zone).toLocalTime().toString().take(5),
                    target = label(event.target, appName),
                    outcome = if (event.choice == InterventionChoice.STAYED) "Stayed focused" else "Opened for ${event.passMinutes} min",
                    detail = event.reason?.let { reason ->
                        val note = event.note?.takeIf(String::isNotBlank)
                        if (note == null) Reasons.label(reason) else "${Reasons.label(reason)}: $note"
                    },
                )
            }
            val hard = windows.map { window ->
                val opened = if (window.overrides == 0) "none opened" else "${window.overrides} opened"
                "${window.label}: ${Format.count(window.attempts, "attempt", "attempts")}, $opened"
            }
            return ChoicesModel(headline, stayed, today.size - stayed, rows, hard)
        }

        public fun label(target: BlockTarget, appName: (PackageName) -> String): String = when (target) {
            is BlockTarget.App -> appName(target.app)
            is BlockTarget.Site -> target.entry.value
            is BlockTarget.ShortForm -> "Short videos in ${appName(target.app)}"
            is BlockTarget.Guard -> "Organiza's settings"
            is BlockTarget.Limit -> "${appName(target.app)}, daily limit"
        }
    }
}

public object Reasons {
    public fun label(reason: OverrideReason): String = when (reason) {
        OverrideReason.WORK -> "Work"
        OverrideReason.COMMUNICATION -> "A message"
        OverrideReason.ERRAND -> "An errand"
        OverrideReason.BOREDOM -> "Boredom"
        OverrideReason.HABIT -> "Habit"
        OverrideReason.OTHER -> "Something else"
    }
}
