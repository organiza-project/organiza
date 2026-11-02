package org.organiza.linux

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import org.organiza.domain.DomainName

/** Keeps one marked block in /etc/hosts and never edits a line outside it (ADR-0013). */
object HostsFile {
    const val BEGIN = "# BEGIN organiza block (managed by the organiza daemon, do not edit)"
    const val END = "# END organiza block"
    private val PREFIXES = listOf("www.", "m.")

    fun render(current: String, domains: Set<DomainName>): String {
        val start = current.indexOf(BEGIN)
        val base = if (start < 0) current else current.substring(0, start)
        if (domains.isEmpty()) return base
        val prefix = if (base.isEmpty() || base.endsWith("\n")) base else "$base\n"
        val hosts = domains.map { it.value }.sorted().flatMap { domain ->
            listOf(domain) + PREFIXES.filterNot { domain.startsWith(it) }.map { it + domain }
        }
        return buildString {
            append(prefix)
            appendLine(BEGIN)
            hosts.forEach { appendLine("0.0.0.0 $it") }
            hosts.forEach { appendLine(":: $it") }
            appendLine(END)
        }
    }

    /** Rewrites the file only when the block changes, replacing it in one step. */
    fun apply(path: Path, domains: Set<DomainName>) {
        val current = if (Files.exists(path)) Files.readString(path) else ""
        val next = render(current, domains)
        if (next == current) return
        val temporary = path.resolveSibling(path.fileName.toString() + ".organiza")
        Files.writeString(temporary, next)
        Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-r--r--"))
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
