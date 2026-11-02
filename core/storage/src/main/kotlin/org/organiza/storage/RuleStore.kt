package org.organiza.storage

import java.time.Instant
import org.organiza.domain.DomainName
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.ProcessName

public data class StoredRule(
    val id: String,
    val name: String,
    val calendarTag: String?,
    val strict: Boolean,
    val blockShortForm: Boolean,
    val enabled: Boolean,
    val packages: Set<PackageName>,
    val domains: Set<DomainName>,
    val processes: Set<ProcessName> = emptySet(),
)

public class RuleStore(private val db: Database) {
    public fun save(rule: StoredRule, at: Instant) {
        db.transaction {
            db.execute(
                "INSERT INTO rule (id, name, calendar_tag, strict, block_short_form, enabled, created_at_ms, updated_at_ms) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET name = excluded.name, " +
                    "calendar_tag = excluded.calendar_tag, strict = excluded.strict, " +
                    "block_short_form = excluded.block_short_form, enabled = excluded.enabled, " +
                    "updated_at_ms = excluded.updated_at_ms",
                listOf(
                    rule.id,
                    rule.name,
                    rule.calendarTag,
                    rule.strict.toLong(),
                    rule.blockShortForm.toLong(),
                    rule.enabled.toLong(),
                    at.toEpochMilli(),
                    at.toEpochMilli(),
                ),
            )
            db.execute("DELETE FROM rule_target WHERE rule_id = ?", listOf(rule.id))
            rule.packages.forEach { insertTarget(rule.id, KIND_PACKAGE, it.value) }
            rule.domains.forEach { insertTarget(rule.id, KIND_DOMAIN, it.value) }
            rule.processes.forEach { insertTarget(rule.id, KIND_PROCESS, it.value) }
        }
    }

    public fun find(id: String): StoredRule? = select("WHERE id = ?", listOf(id)).firstOrNull()

    public fun all(): List<StoredRule> = select("", emptyList())

    public fun delete(id: String) {
        db.execute("DELETE FROM rule WHERE id = ?", listOf(id))
    }

    private fun insertTarget(ruleId: String, kind: String, value: String) {
        db.execute("INSERT INTO rule_target (rule_id, kind, value) VALUES (?, ?, ?)", listOf(ruleId, kind, value))
    }

    private fun select(where: String, args: List<Any?>): List<StoredRule> =
        db.query(
            "SELECT id, name, calendar_tag, strict, block_short_form, enabled FROM rule $where ORDER BY id",
            args,
        ) { row ->
            StoredRule(
                id = row.string(0).orEmpty(),
                name = row.string(1).orEmpty(),
                calendarTag = row.string(2),
                strict = row.long(3) == 1L,
                blockShortForm = row.long(4) == 1L,
                enabled = row.long(5) == 1L,
                packages = emptySet(),
                domains = emptySet(),
            )
        }.map { rule ->
            rule.copy(
                packages = targets(rule.id, KIND_PACKAGE, PackageName::parse),
                domains = targets(rule.id, KIND_DOMAIN, DomainName::parse),
                processes = targets(rule.id, KIND_PROCESS, ProcessName::parse),
            )
        }

    /** Values are validated before they are saved, so a row that no longer parses is skipped. */
    private fun <T> targets(ruleId: String, kind: String, parse: (String) -> Parsed<T>): Set<T> =
        db.query("SELECT value FROM rule_target WHERE rule_id = ? AND kind = ?", listOf(ruleId, kind)) { it.string(0) }
            .mapNotNull { value -> (value?.let(parse) as? Parsed.Valid)?.value }
            .toSet()

    private fun Boolean.toLong(): Long = if (this) 1 else 0

    private companion object {
        const val KIND_PACKAGE = "package"
        const val KIND_DOMAIN = "domain"
        const val KIND_PROCESS = "process"
    }
}
