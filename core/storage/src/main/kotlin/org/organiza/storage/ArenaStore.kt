package org.organiza.storage

public data class StoredChallenge(
    val id: String,
    val habitId: String,
    val title: String,
    val startsOn: String,
    val lengthDays: Int,
    val freezeAllowance: Int,
    val maxFailures: Int,
    val strict: Boolean,
    val state: String,
    val memberId: String,
    val displayName: String,
    val relayUrl: String,
    val syncCursor: Long,
    val joinSent: Boolean,
    val revealSent: Boolean,
)

/** One of this device's closed days. The salt stays here until the reveal (ADR-0006). */
public data class StoredDayLog(
    val date: String,
    val outcome: String,
    val streak: Int,
    val salt: ByteArray,
    val commitment: ByteArray,
    val previous: ByteArray?,
    val signature: ByteArray,
    val synced: Boolean,
)

public data class StoredEntry(
    val memberId: String,
    val date: String,
    val outcome: String,
    val streak: Int,
    val commitment: ByteArray,
    val previous: ByteArray?,
    val signature: ByteArray,
)

public enum class EntryResult { ADDED, DUPLICATE, CONFLICT }

public class ArenaStore(private val db: Database) {
    public fun createChallenge(challenge: StoredChallenge) {
        db.execute(
            "INSERT INTO challenge (id, habit_id, title, starts_on, length_days, freeze_allowance, max_failures, strict, " +
                "state, member_id, display_name, relay_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(
                challenge.id,
                challenge.habitId,
                challenge.title,
                challenge.startsOn,
                challenge.lengthDays.toLong(),
                challenge.freezeAllowance.toLong(),
                challenge.maxFailures.toLong(),
                if (challenge.strict) 1L else 0L,
                challenge.state,
                challenge.memberId,
                challenge.displayName,
                challenge.relayUrl,
            ),
        )
    }

    public fun challenge(id: String): StoredChallenge? = challenges("WHERE id = ?", listOf(id)).firstOrNull()

    public fun allChallenges(): List<StoredChallenge> = challenges("", emptyList())

    public fun setState(id: String, state: String) {
        db.execute("UPDATE challenge SET state = ? WHERE id = ?", listOf(state, id))
    }

    public fun setCursor(id: String, cursor: Long) {
        db.execute("UPDATE challenge SET sync_cursor = ? WHERE id = ?", listOf(cursor, id))
    }

    public fun markJoinSent(id: String) {
        db.execute("UPDATE challenge SET join_sent = 1 WHERE id = ?", listOf(id))
    }

    public fun markRevealSent(id: String) {
        db.execute("UPDATE challenge SET reveal_sent = 1 WHERE id = ?", listOf(id))
    }

    public fun addLog(challengeId: String, log: StoredDayLog) {
        db.execute(
            "INSERT INTO challenge_log (challenge_id, local_date, outcome, streak, salt, commitment, previous_commitment, signature) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(challengeId, log.date, log.outcome, log.streak.toLong(), log.salt, log.commitment, log.previous, log.signature),
        )
    }

    public fun logs(challengeId: String): List<StoredDayLog> = db.query(
        "SELECT local_date, outcome, streak, salt, commitment, previous_commitment, signature, synced_at_ms " +
            "FROM challenge_log WHERE challenge_id = ? ORDER BY local_date",
        listOf(challengeId),
    ) { row ->
        StoredDayLog(
            date = row.string(0).orEmpty(),
            outcome = row.string(1).orEmpty(),
            streak = (row.long(2) ?: 0).toInt(),
            salt = row.blob(3) ?: ByteArray(0),
            commitment = row.blob(4) ?: ByteArray(0),
            previous = row.blob(5),
            signature = row.blob(6) ?: ByteArray(0),
            synced = row.long(7) != null,
        )
    }

    public fun markSynced(challengeId: String, date: String, atMillis: Long) {
        db.execute(
            "UPDATE challenge_log SET synced_at_ms = ? WHERE challenge_id = ? AND local_date = ?",
            listOf(atMillis, challengeId, date),
        )
    }

    public fun putMember(challengeId: String, memberId: String, name: String, replaceName: Boolean) {
        val conflict = if (replaceName) "DO UPDATE SET display_name = excluded.display_name" else "DO NOTHING"
        db.execute(
            "INSERT INTO arena_member (challenge_id, member_id, display_name) VALUES (?, ?, ?) " +
                "ON CONFLICT (challenge_id, member_id) $conflict",
            listOf(challengeId, memberId, name),
        )
    }

    /** Other members as ID to display name. */
    public fun members(challengeId: String): Map<String, String> = db.query(
        "SELECT member_id, display_name FROM arena_member WHERE challenge_id = ?",
        listOf(challengeId),
    ) { row -> row.string(0).orEmpty() to row.string(1).orEmpty() }.toMap()

    /** Keeps the first answer for a day. A different second answer flags the member. */
    public fun putEntry(challengeId: String, entry: StoredEntry): EntryResult {
        val existing = db.query(
            "SELECT commitment FROM arena_entry WHERE challenge_id = ? AND member_id = ? AND local_date = ?",
            listOf(challengeId, entry.memberId, entry.date),
        ) { row -> row.blob(0) ?: ByteArray(0) }.firstOrNull()
        if (existing != null) {
            if (existing.contentEquals(entry.commitment)) return EntryResult.DUPLICATE
            db.execute(
                "INSERT INTO arena_conflict (challenge_id, member_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                listOf(challengeId, entry.memberId),
            )
            return EntryResult.CONFLICT
        }
        db.execute(
            "INSERT INTO arena_entry (challenge_id, member_id, local_date, outcome, streak, commitment, previous_commitment, signature) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(
                challengeId,
                entry.memberId,
                entry.date,
                entry.outcome,
                entry.streak.toLong(),
                entry.commitment,
                entry.previous,
                entry.signature,
            ),
        )
        return EntryResult.ADDED
    }

    public fun entries(challengeId: String, memberId: String): List<StoredEntry> = db.query(
        "SELECT local_date, outcome, streak, commitment, previous_commitment, signature FROM arena_entry " +
            "WHERE challenge_id = ? AND member_id = ? ORDER BY local_date",
        listOf(challengeId, memberId),
    ) { row ->
        StoredEntry(
            memberId,
            row.string(0).orEmpty(),
            row.string(1).orEmpty(),
            (row.long(2) ?: 0).toInt(),
            row.blob(3) ?: ByteArray(0),
            row.blob(4),
            row.blob(5) ?: ByteArray(0),
        )
    }

    public fun hasConflict(challengeId: String, memberId: String): Boolean = db.query(
        "SELECT 1 FROM arena_conflict WHERE challenge_id = ? AND member_id = ?",
        listOf(challengeId, memberId),
    ) { true }.isNotEmpty()

    public fun putReveal(challengeId: String, memberId: String, message: ByteArray) {
        db.execute(
            "INSERT INTO arena_reveal (challenge_id, member_id, message) VALUES (?, ?, ?) " +
                "ON CONFLICT (challenge_id, member_id) DO NOTHING",
            listOf(challengeId, memberId, message),
        )
    }

    public fun reveal(challengeId: String, memberId: String): ByteArray? = db.query(
        "SELECT message FROM arena_reveal WHERE challenge_id = ? AND member_id = ?",
        listOf(challengeId, memberId),
    ) { row -> row.blob(0) }.firstOrNull()

    private fun challenges(where: String, args: List<Any?>): List<StoredChallenge> = db.query(
        "SELECT id, habit_id, title, starts_on, length_days, freeze_allowance, max_failures, strict, state, member_id, " +
            "display_name, relay_url, sync_cursor, join_sent, reveal_sent FROM challenge $where ORDER BY starts_on, id",
        args,
    ) { row ->
        StoredChallenge(
            id = row.string(0).orEmpty(),
            habitId = row.string(1).orEmpty(),
            title = row.string(2).orEmpty(),
            startsOn = row.string(3).orEmpty(),
            lengthDays = (row.long(4) ?: 0).toInt(),
            freezeAllowance = (row.long(5) ?: 0).toInt(),
            maxFailures = (row.long(6) ?: 0).toInt(),
            strict = row.long(7) == 1L,
            state = row.string(8).orEmpty(),
            memberId = row.string(9).orEmpty(),
            displayName = row.string(10).orEmpty(),
            relayUrl = row.string(11).orEmpty(),
            syncCursor = row.long(12) ?: 0,
            joinSent = row.long(13) == 1L,
            revealSent = row.long(14) == 1L,
        )
    }
}
