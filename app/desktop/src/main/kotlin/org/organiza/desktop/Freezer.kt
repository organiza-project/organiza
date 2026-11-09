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
    private val frozen = linkedSetOf<Long>()

    /** Stops [pid] and every process it started, unless it is Organiza, its parents or the session itself. */
    @Synchronized
    fun freeze(pid: Long) {
        val root = ProcessHandle.of(pid).orElse(null) ?: return
        if (!freezable(root)) return
        val tree = listOf(root) + root.descendants().toList()
        signal("STOP", tree.map { it.pid() })
        frozen += tree.map { it.pid() }
        save()
    }

    @Synchronized
    fun thawAll() {
        signal("CONT", frozen.toList())
        frozen.clear()
        save()
    }

    /** Continues whatever a crash left stopped. Call once at start. */
    @Synchronized
    fun recover() {
        if (!record.exists()) return
        signal("CONT", record.readLines().mapNotNull(String::toLongOrNull))
        record.deleteIfExists()
    }

    private fun freezable(process: ProcessHandle): Boolean {
        val self = ProcessHandle.current()
        val protected = generateSequence(self) { it.parent().orElse(null) }.map { it.pid() }.toSet()
        val name = process.info().command().orElse("").substringAfterLast('/')
        return process.pid() > 1 && process.pid() !in protected && name !in SESSION
    }

    private fun save() {
        if (frozen.isEmpty()) {
            record.deleteIfExists()
        } else {
            Files.createDirectories(record.parent)
            record.writeLines(frozen.map(Long::toString))
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
