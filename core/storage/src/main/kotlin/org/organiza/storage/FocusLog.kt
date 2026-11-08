package org.organiza.storage

import java.time.Instant
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed

/** [app] came to the front at [at]; null means nothing is in front. */
public data class FocusChange(val at: Instant, val app: PackageName?)

/** The desktop's own record of the app in front (ADR-0024). It stays on this device. */
public class FocusLog(private val db: Database) {
    /** Stores a change, and nothing when [app] is already the one in front. */
    public fun record(at: Instant, app: PackageName?) {
        val last = last()
        if (last != null && last.app == app) return
        db.execute("INSERT INTO focus_change (at_ms, package) VALUES (?, ?)", listOf(at.toEpochMilli(), app?.value))
    }

    public fun last(): FocusChange? =
        db.query("SELECT at_ms, package FROM focus_change ORDER BY at_ms DESC, rowid DESC LIMIT 1", map = ::change).firstOrNull()

    /** Changes in the window, led by the last one before [from], which tells what was in front then. */
    public fun changes(from: Instant, to: Instant): List<FocusChange> {
        val before = db.query(
            "SELECT at_ms, package FROM focus_change WHERE at_ms < ? ORDER BY at_ms DESC, rowid DESC LIMIT 1",
            listOf(from.toEpochMilli()),
            ::change,
        )
        val within = db.query(
            "SELECT at_ms, package FROM focus_change WHERE at_ms >= ? AND at_ms < ? ORDER BY at_ms, rowid",
            listOf(from.toEpochMilli(), to.toEpochMilli()),
            ::change,
        )
        return before + within
    }

    private fun change(row: Row) = FocusChange(
        Instant.ofEpochMilli(row.long(0) ?: 0),
        row.string(1)?.let { (PackageName.parse(it) as? Parsed.Valid)?.value },
    )
}
