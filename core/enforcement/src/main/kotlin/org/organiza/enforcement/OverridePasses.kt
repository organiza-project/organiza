package org.organiza.enforcement

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.organiza.domain.DomainName
import org.organiza.domain.PackageName
import org.organiza.domain.port.BlockTarget
import org.organiza.domain.port.Clock

/**
 * Short passes granted by an override (ADR-0016). Held in memory only, so a restart blocks again.
 * Safe to use from several threads: the accessibility layer grants them and the DNS layer reads them.
 */
public class OverridePasses(private val clock: Clock) {
    private sealed interface Key {
        data class App(val app: PackageName) : Key

        data class Site(val entry: DomainName) : Key

        data class ShortForm(val app: PackageName) : Key

        data class Limit(val app: PackageName) : Key
    }

    private val passes = ConcurrentHashMap<Key, Instant>()

    /** Grants [target] for [length], at most [MAX_PASS]. The settings guard is never granted. */
    public fun grant(target: BlockTarget, length: Duration) {
        val key = key(target) ?: return
        passes[key] = clock.now().plus(minOf(length, MAX_PASS))
    }

    public fun allows(target: BlockTarget): Boolean = key(target)?.let(::live) ?: false

    public fun allowsShortForm(app: PackageName): Boolean = live(Key.ShortForm(app))

    /** [list] without the apps and site entries that hold a live pass. */
    public fun apply(list: BlockList): BlockList {
        val live = passes.keys.filter(::live)
        if (live.isEmpty()) return list
        val apps = live.filterIsInstance<Key.App>().map { it.app }.toSet()
        val sites = live.filterIsInstance<Key.Site>().map { it.entry }.toSet()
        return list.copy(packages = list.packages - apps, domains = list.domains - sites)
    }

    /** When the soonest live pass ends, so the block can be put back on time. */
    public fun nextExpiry(): Instant? {
        val now = clock.now()
        return passes.values.filter { it.isAfter(now) }.minOrNull()
    }

    public fun clear() {
        passes.clear()
    }

    private fun live(key: Key): Boolean {
        val until = passes[key] ?: return false
        if (until.isAfter(clock.now())) return true
        passes.remove(key, until)
        return false
    }

    private fun key(target: BlockTarget): Key? = when (target) {
        is BlockTarget.App -> Key.App(target.app)
        is BlockTarget.Site -> Key.Site(target.entry)
        is BlockTarget.ShortForm -> Key.ShortForm(target.app)
        is BlockTarget.Limit -> Key.Limit(target.app)
        is BlockTarget.Guard -> null
    }

    public companion object {
        public val MAX_PASS: Duration = Duration.ofMinutes(15)
    }
}
