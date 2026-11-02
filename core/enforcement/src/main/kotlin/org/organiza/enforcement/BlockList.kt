package org.organiza.enforcement

import org.organiza.domain.DomainName
import org.organiza.domain.PackageName
import org.organiza.domain.ProcessName

public data class BlockList(
    val packages: Set<PackageName>,
    val domains: Set<DomainName>,
    val processes: Set<ProcessName> = emptySet(),
) {
    public fun blocks(app: PackageName): Boolean = app in packages

    public fun blocks(domain: DomainName): Boolean = domains.any { it.covers(domain) }

    public fun blocks(process: ProcessName): Boolean = process in processes

    public companion object {
        public val EMPTY: BlockList = BlockList(emptySet(), emptySet())
    }
}
