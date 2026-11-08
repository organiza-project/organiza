package org.organiza.feature.focus

import org.organiza.domain.DomainName
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.enforcement.BlockList

/** Parses comma-separated lists. Entries that do not parse are returned in [rejected]. */
data class SessionInput(val blockList: BlockList, val rejected: List<String>) {
    companion object {
        fun parse(packages: String, domains: String): SessionInput {
            val rejected = mutableListOf<String>()
            fun <T> parseAll(raw: String, parser: (String) -> Parsed<T>): Set<T> =
                raw.split(',').map(String::trim).filter(String::isNotEmpty).mapNotNull { entry ->
                    when (val parsed = parser(entry)) {
                        is Parsed.Valid -> parsed.value
                        is Parsed.Invalid -> null.also { rejected += entry }
                    }
                }.toSet()
            val list = BlockList(parseAll(packages, PackageName::parse), parseAll(domains, DomainName::parse))
            return SessionInput(list, rejected)
        }
    }
}
