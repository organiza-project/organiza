package org.organiza.enforcement

import org.organiza.domain.DomainName
import org.organiza.domain.PackageName
import org.organiza.domain.ProcessName

/** With [allowOnly], [packages] are the only apps allowed and every other app is blocked (ADR-0020). */
public data class BlockList(
    val packages: Set<PackageName>,
    val domains: Set<DomainName>,
    val processes: Set<ProcessName> = emptySet(),
    val allowOnly: Boolean = false,
) {
    public fun blocks(app: PackageName): Boolean = (app in packages) != allowOnly

    public fun blocks(domain: DomainName): Boolean = entryFor(domain) != null

    /** The listed domain that covers [domain], which an override pass is keyed by. */
    public fun entryFor(domain: DomainName): DomainName? = domains.firstOrNull { it.covers(domain) }

    public fun blocks(process: ProcessName): Boolean = process in processes

    public companion object {
        public val EMPTY: BlockList = BlockList(emptySet(), emptySet())
    }
}
