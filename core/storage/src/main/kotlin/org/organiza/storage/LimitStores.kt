package org.organiza.storage

import java.time.Duration
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed

/** Daily foreground limits per app (ADR-0018). They apply all day, in and out of sessions. */
public class UsageLimitStore(private val db: Database) {
    public fun set(app: PackageName, limit: Duration) {
        db.execute(
            "INSERT INTO usage_limit (package, minutes) VALUES (?, ?) ON CONFLICT (package) DO UPDATE SET minutes = excluded.minutes",
            listOf(app.value, limit.toMinutes()),
        )
    }

    public fun remove(app: PackageName) {
        db.execute("DELETE FROM usage_limit WHERE package = ?", listOf(app.value))
    }

    public fun all(): Map<PackageName, Duration> = db.query("SELECT package, minutes FROM usage_limit ORDER BY package") { row ->
        (PackageName.parse(row.string(0).orEmpty()) as? Parsed.Valid)?.value?.let { it to Duration.ofMinutes(row.long(1) ?: 0) }
    }.filterNotNull().toMap()
}

/** Switches that are not part of any session. Unknown keys read as off. */
public enum class Setting(internal val key: String) {
    BLOCK_REELS("block_reels"),
    BLOCK_SHORTS("block_shorts"),
}

public class SettingsStore(private val db: Database) {
    public fun flag(setting: Setting): Boolean =
        db.query("SELECT value FROM setting WHERE key = ?", listOf(setting.key)) { it.string(0) }.firstOrNull() == "true"

    public fun setFlag(setting: Setting, on: Boolean) {
        db.execute(
            "INSERT INTO setting (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value",
            listOf(setting.key, on.toString()),
        )
    }
}
