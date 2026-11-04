package org.organiza.storage

public sealed interface MigrationResult {
    public data class Migrated(val from: Int, val to: Int) : MigrationResult

    public data class Current(val version: Int) : MigrationResult

    /** The file was written by a newer app. It is left untouched. */
    public data class TooNew(val version: Int) : MigrationResult
}

public object Migrations {
    public const val LATEST: Int = 7

    public fun migrate(db: Database, target: Int = LATEST): MigrationResult {
        val current = db.query("PRAGMA user_version") { it.long(0) }.single()?.toInt() ?: 0
        if (current > LATEST) return MigrationResult.TooNew(current)
        if (current >= target) return MigrationResult.Current(current)
        for (version in current + 1..target) {
            val statements = statements(load(version))
            db.transaction { statements.forEach { db.execute(it) } }
        }
        return MigrationResult.Migrated(current, target)
    }

    private fun load(version: Int): String =
        checkNotNull(javaClass.getResource("/org/organiza/storage/schema/v$version.sql")) {
            "schema v$version is missing"
        }.readText()

    /** Splits a script, because Android's execSQL runs only one statement per call. */
    public fun statements(sql: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var at = 0
        while (at < sql.length) {
            val char = sql[at]
            when {
                !quoted && sql.startsWith("--", at) -> {
                    at = sql.indexOf('\n', at).takeIf { it >= 0 } ?: sql.length
                    continue
                }
                !quoted && char == ';' -> {
                    statements += current.toString()
                    current.clear()
                }
                else -> {
                    if (char == '\'') quoted = !quoted
                    current.append(char)
                }
            }
            at++
        }
        statements += current.toString()
        return statements.map(String::trim).filter(String::isNotEmpty)
    }
}
