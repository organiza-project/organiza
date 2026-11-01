package org.organiza.storage

import java.time.Instant
import org.organiza.domain.port.Capability

public data class StoredTamperEvent(val sessionId: String?, val capability: Capability, val at: Instant)

public class TamperStore(private val db: Database) {
    public fun record(sessionId: String?, capability: Capability, at: Instant) {
        db.execute(
            "INSERT INTO tamper_event (session_id, capability, at_ms) VALUES (?, ?, ?)",
            listOf(sessionId, capability.name, at.toEpochMilli()),
        )
    }

    public fun all(): List<StoredTamperEvent> =
        db.query("SELECT session_id, capability, at_ms FROM tamper_event ORDER BY id") { row ->
            StoredTamperEvent(
                row.string(0),
                Capability.valueOf(row.string(1).orEmpty()),
                Instant.ofEpochMilli(row.long(2) ?: 0),
            )
        }
}
