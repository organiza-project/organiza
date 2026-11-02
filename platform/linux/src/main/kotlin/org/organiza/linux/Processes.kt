package org.organiza.linux

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.organiza.domain.ProcessName

/** One process as the scanner sees it: every name it goes by, plus a Flatpak ID if it has one. */
data class ProcessInfo(val pid: Int, val uid: Int, val names: Set<String>, val flatpakId: String?)

/** Reads processes from a /proc tree. Takes the root as a parameter so tests can use a fake one. */
class ProcessTable(private val root: Path = Path.of("/proc")) {
    fun list(): List<ProcessInfo> {
        val entries = try {
            Files.list(root).use { stream -> stream.toList() }
        } catch (_: IOException) {
            return emptyList()
        }
        return entries.mapNotNull { dir -> dir.fileName.toString().toIntOrNull()?.let { read(it, dir) } }.sortedBy { it.pid }
    }

    /** Null when the process ended while it was being read, which is normal. */
    private fun read(pid: Int, dir: Path): ProcessInfo? = try {
        val uid = Files.readAllLines(dir.resolve("status"))
            .firstOrNull { it.startsWith("Uid:") }
            ?.split(Regex("\\s+"))?.getOrNull(1)?.toIntOrNull() ?: return null
        val comm = Files.readString(dir.resolve("comm")).trim()
        val argv0 = Files.readAllBytes(dir.resolve("cmdline")).takeWhile { it != 0.toByte() }.toByteArray().decodeToString()
        val exe = try {
            Files.readSymbolicLink(dir.resolve("exe")).fileName?.toString()
        } catch (_: IOException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }
        val cgroup = try {
            Files.readString(dir.resolve("cgroup"))
        } catch (_: IOException) {
            ""
        }
        val names = listOfNotNull(comm, argv0.substringAfterLast('/'), exe).filter(String::isNotEmpty).toSet()
        ProcessInfo(pid, uid, names, FLATPAK.find(cgroup)?.groupValues?.get(1))
    } catch (_: IOException) {
        null
    }

    private companion object {
        val FLATPAK = Regex("""app-flatpak-([A-Za-z0-9._-]+?)-\d+\.scope""")
    }
}

/** Decides which processes to end. Lockout protection lives here (ADR-0013). */
class ProcessGuard(private val selfPid: Int) {
    fun targets(processes: List<ProcessInfo>, blocked: Set<ProcessName>): List<Int> {
        if (blocked.isEmpty()) return emptyList()
        val wanted = blocked.map { it.value }.toSet()
        return processes.filter { process ->
            process.uid >= FIRST_USER_UID &&
                process.pid != selfPid &&
                process.pid != 1 &&
                process.names.none { it in PROTECTED } &&
                (process.names.any { name -> name in wanted || wanted.any { truncatedComm(it) == name } } ||
                    process.flatpakId in wanted)
        }.map { it.pid }
    }

    /** The kernel cuts `comm` to 15 characters, so "telegram-desktop" appears as "telegram-deskto". */
    private fun truncatedComm(name: String) = if (name.length > COMM_LENGTH) name.take(COMM_LENGTH) else null

    private companion object {
        const val FIRST_USER_UID = 1000
        const val COMM_LENGTH = 15

        // Ending any of these could lock a person out of their own session.
        val PROTECTED = setOf(
            "systemd", "init", "login", "sudo", "su", "sshd", "ssh", "bash", "zsh", "sh", "dash", "fish",
            "gnome-shell", "gnome-session-binary", "Xwayland", "Xorg", "kwin_wayland", "kwin_x11", "plasmashell",
            "gdm-wayland-session", "gdm-x-session", "sddm", "lightdm", "dbus-daemon", "pipewire", "java",
        )
    }
}
