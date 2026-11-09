package org.organiza.feature.rpg

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.organiza.app.OrganizaCore
import org.organiza.ui.RpgTaskView
import org.organiza.ui.RpgView
import org.organiza.presentation.Badge
import org.organiza.presentation.DailyInput
import org.organiza.presentation.HeroModel
import org.organiza.presentation.Rpg
import org.organiza.presentation.RpgEntry
import org.organiza.storage.InterventionChoice
import org.organiza.storage.RpgKind
import org.organiza.storage.RpgReason
import org.organiza.storage.Setting

/** Habits, dailies and to-dos with health and XP (ADR-0019). The rules live in core's [Rpg]. */
class RpgController(private val app: OrganizaCore) {
    fun add(kind: RpgKind, title: String) = edit { stores, _ ->
        val clean = title.trim().take(MAX_TITLE)
        if (clean.isNotEmpty()) stores.rpg.addTask(UUID.randomUUID().toString(), kind, clean, app.clock.now())
    }

    fun habit(id: String, up: Boolean) = edit { stores, today ->
        if (up) log(stores, RpgReason.HABIT_UP, id, today, Rpg.HABIT_UP) else log(stores, RpgReason.HABIT_DOWN, id, today, Rpg.HABIT_DOWN)
    }

    fun daily(id: String, done: Boolean) = edit { stores, today ->
        val checked = today in stores.rpg.dailyChecks()[id].orEmpty()
        if (done && !checked) log(stores, RpgReason.DAILY, id, today, Rpg.DAILY)
        if (!done && checked) stores.rpg.uncheck(id, today)
    }

    fun complete(id: String) = edit { stores, today ->
        if (stores.rpg.tasks().any { it.id == id && it.kind == RpgKind.TODO && it.doneAt == null }) {
            stores.rpg.complete(id, app.clock.now())
            log(stores, RpgReason.TODO, id, today, Rpg.TODO)
        }
    }

    fun delete(id: String) = edit { stores, _ -> stores.rpg.delete(id) }

    /**
     * Charges the days since the last settlement for undone dailies and broken focus blocks, then
     * faints the hero at zero health. Storage thread, on every refresh; a day is charged once.
     */
    fun settle(stores: OrganizaCore.Storage, today: LocalDate, zone: ZoneId, baseXp: Int) {
        val settled = stores.settings.text(Setting.RPG_SETTLED)?.let(LocalDate::parse)
        if (settled != null && settled < today.minusDays(1)) {
            val from = maxOf(settled.plusDays(1), today.minusDays(MAX_CATCH_UP))
            val checks = stores.rpg.dailyChecks()
            val dailies = stores.rpg.tasks().filter { it.kind == RpgKind.DAILY }.map {
                DailyInput(it.id, it.createdAt.atZone(zone).toLocalDate(), checks[it.id].orEmpty())
            }
            // An override inside a session breaks that focus block; limits outside sessions do not.
            val broken = stores.interventions.between(from.atStartOfDay(zone).toInstant(), today.atStartOfDay(zone).toInstant())
                .filter { it.choice == InterventionChoice.OVERRODE && it.sessionId != null }
                .groupingBy { it.at.atZone(zone).toLocalDate() }
                .eachCount()
            Rpg.missed(from, today, dailies, broken).forEach { (day, entry) ->
                val reason = if (entry == Rpg.MISSED_DAILY) RpgReason.MISSED_DAILY else RpgReason.BROKE_BLOCK
                log(stores, reason, null, day, entry)
            }
        }
        if (settled == null || settled < today.minusDays(1)) stores.settings.setText(Setting.RPG_SETTLED, today.minusDays(1).toString())
        val entries = entries(stores)
        if (Rpg.hp(entries) == 0) {
            log(stores, RpgReason.FAINTED, null, today, Rpg.faint(baseXp + entries.sumOf { it.xp }))
            app.showMessage("You fainted. Health is full again, but you dropped a level")
        }
    }

    fun hero(stores: OrganizaCore.Storage, baseXp: Int, badges: List<Badge>): HeroModel = Rpg.hero(baseXp, entries(stores), badges)

    fun view(stores: OrganizaCore.Storage, today: LocalDate, hero: HeroModel): RpgView {
        val checks = stores.rpg.dailyChecks()
        val tasks = stores.rpg.tasks().filter { it.doneAt == null }.map { task ->
            RpgTaskView(task.id, task.kind, task.title, task.kind == RpgKind.DAILY && today in checks[task.id].orEmpty())
        }
        val progression = hero.progression
        return RpgView(
            hero.hp, hero.maxHp, hero.badge, progression.fraction, "${progression.toNext} XP to level ${progression.level + 1}", tasks,
            progression.level, progression.rank, progression.rankLine,
        )
    }

    private fun entries(stores: OrganizaCore.Storage) = stores.rpg.entries().map { (xp, hp) -> RpgEntry(xp, hp) }

    private fun log(stores: OrganizaCore.Storage, reason: RpgReason, id: String?, day: LocalDate, entry: RpgEntry) =
        stores.rpg.log(reason, id, day, entry.xp, entry.hp, app.clock.now())

    private fun edit(work: (OrganizaCore.Storage, LocalDate) -> Unit) = app.onStores { stores ->
        work(stores, app.clock.now().atZone(ZoneId.systemDefault()).toLocalDate())
        app.requestUiRefresh()
    }

    private companion object {
        const val MAX_TITLE = 80
        // Health bottoms out at zero long before a week of misses, so older days change nothing.
        const val MAX_CATCH_UP = 7L
    }
}
