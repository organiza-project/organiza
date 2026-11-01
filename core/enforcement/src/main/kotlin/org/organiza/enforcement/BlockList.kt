package org.organiza.enforcement

import org.organiza.domain.DomainName
import org.organiza.domain.PackageName

public data class BlockList(
    val packages: Set<PackageName>,
    val domains: Set<DomainName>,
) {
    public fun blocks(app: PackageName): Boolean = app in packages

    public fun blocks(domain: DomainName): Boolean = domains.any { it.covers(domain) }

    public companion object {
        public val EMPTY: BlockList = BlockList(emptySet(), emptySet())
    }
}
