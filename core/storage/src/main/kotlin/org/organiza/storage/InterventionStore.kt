package org.organiza.storage

import java.time.Duration
import java.time.Instant
import org.organiza.domain.DomainName
import org.organiza.domain.OverrideReason
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.port.BlockTarget

public enum class InterventionChoice { STAYED, OVERRODE }

/** One intervention and what the person chose (ADR-0016). [reason] and [passMinutes] are set for overrides. */
public data class StoredIntervention(
    val sessionId: String?,
    val target: BlockTarget,
    val choice: InterventionChoice,
    val reason: OverrideReason?,
    val note: String?,
    val phraseId: String?,
    val pause: Duration,
    val passMinutes: Int?,
    val strict: Boolean,
    val reflected: Boolean,
    val at: Instant,
)

public class InterventionStore(private val db: Database) {
    public fun record(event: StoredIntervention) {
        val (kind, site) = when (val target = event.target) {
            is BlockTarget.App -> "app" to null
            is BlockTarget.Site -> "site" to target.entry.value
            is BlockTarget.ShortForm -> "short_form" to null
            is BlockTarget.Guard -> "guard" to null
        }
        db.execute(
            "INSERT INTO intervention_event (session_id, kind, app, site, outcome, reason, note, phrase_id, pause_s, " +
                "pass_minutes, strict, reflected, at_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(
                event.sessionId,
                kind,
                event.target.app.value,
                site,
                event.choice.name.lowercase(),
                event.reason?.name,
                event.note,
                event.phraseId,
                event.pause.seconds,
                event.passMinutes?.toLong(),
                if (event.strict) 1L else 0L,
                if (event.reflected) 1L else 0L,
                event.at.toEpochMilli(),
            ),
        )
    }

    /** Interventions in [from, to), oldest first. */
    public fun between(from: Instant, to: Instant): List<StoredIntervention> = db.query(
        "SELECT session_id, kind, app, site, outcome, reason, note, phrase_id, pause_s, pass_minutes, strict, reflected, " +
            "at_ms FROM intervention_event WHERE at_ms >= ? AND at_ms < ? ORDER BY at_ms, id",
        listOf(from.toEpochMilli(), to.toEpochMilli()),
    ) { row ->
        StoredIntervention(
            sessionId = row.string(0),
            target = target(row.string(1).orEmpty(), row.string(2).orEmpty(), row.string(3)),
            choice = InterventionChoice.valueOf(row.string(4).orEmpty().uppercase()),
            reason = row.string(5)?.let(OverrideReason::valueOf),
            note = row.string(6),
            phraseId = row.string(7),
            pause = Duration.ofSeconds(row.long(8) ?: 0),
            passMinutes = row.long(9)?.toInt(),
            strict = row.long(10) == 1L,
            reflected = row.long(11) == 1L,
            at = Instant.ofEpochMilli(row.long(12) ?: 0),
        )
    }

    public fun overridesSince(from: Instant): Int = db.query(
        "SELECT count(*) FROM intervention_event WHERE outcome = 'overrode' AND at_ms >= ?",
        listOf(from.toEpochMilli()),
    ) { it.long(0) }.single()?.toInt() ?: 0

    private fun target(kind: String, app: String, site: String?): BlockTarget {
        // Rows were validated on the way in, so a failed parse means a damaged file.
        val pkg = (PackageName.parse(app) as? Parsed.Valid)?.value ?: error("stored package is invalid")
        return when (kind) {
            "app" -> BlockTarget.App(pkg)
            "site" -> BlockTarget.Site(pkg, (DomainName.parse(site.orEmpty()) as? Parsed.Valid)?.value ?: error("stored site is invalid"))
            "short_form" -> BlockTarget.ShortForm(pkg)
            else -> BlockTarget.Guard(pkg)
        }
    }
}
