package org.organiza.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

/**
 * The friction of a desktop pause (ADR-0025): a blocked app's processes are stopped with SIGSTOP
 * while it stays blocked, and continued with SIGCONT on an override, when the session ends, when
 * Organiza quits, or at its next start. Stopping keeps the app's memory, so no work is lost.
 */
class Freezer(private val record: Path) {
    /** Each stopped process and when it started, so a reused process ID is never continued. */
    private val frozen = linkedMapOf<Long, Long?>()

    /**
     * Stops [pid] and every process it started, unless it is Organiza, its parents or the session
     * itself. Organiza is never among the processes started: those would make it one of its parents.
     */
    @Synchronized
    fun freeze(pid: Long) {
        val root = ProcessHandle.of(pid).orElse(null) ?: return
        if (!freezable(root)) return
        val tree = listOf(root) + root.descendants().toList()
        signal("STOP", tree.map { it.pid() })
        tree.forEach { frozen[it.pid()] = started(it) }
        save()
    }

    @Synchronized
    fun thawAll() {
        signal("CONT", frozen.keys.toList())
        frozen.clear()
        save()
    }

    /** Continues whatever a crash left stopped, if those processes still run. Call once at start. */
    @Synchronized
    fun recover() {
        if (!record.exists()) return
        val alive = record.readLines().mapNotNull { line ->
            val (pid, start) = line.split(' ').map(String::toLongOrNull).takeIf { it.size == 2 } ?: return@mapNotNull null
            pid?.takeIf { start != null && ProcessHandle.of(it).map(::started).orElse(null) == start }
        }
        signal("CONT", alive)
        record.deleteIfExists()
    }

    private fun freezable(process: ProcessHandle): Boolean {
        val protected = generateSequence(ProcessHandle.current()) { it.parent().orElse(null) }.map { it.pid() }.toSet()
        val name = process.info().command().orElse("").substringAfterLast('/')
        return process.pid() > 1 && process.pid() !in protected && name !in SESSION
    }

    private fun started(process: ProcessHandle): Long? = process.info().startInstant().map { it.toEpochMilli() }.orElse(null)

    private fun save() {
        if (frozen.isEmpty()) {
            record.deleteIfExists()
        } else {
            Files.createDirectories(record.parent)
            record.writeLines(frozen.map { (pid, start) -> "$pid $start" })
        }
    }

    private fun signal(name: String, pids: List<Long>) {
        if (pids.isEmpty()) return
        ProcessBuilder(listOf("kill", "-$name") + pids.map(Long::toString)).redirectErrorStream(true).start().waitFor()
    }

    private companion object {
        // Stopping any of these would freeze the whole desktop.
        val SESSION = setOf("gnome-shell", "Xwayland", "Xorg", "kwin_wayland", "kwin_x11", "Hyprland", "sway", "systemd", "dbus-daemon")
    }
}
