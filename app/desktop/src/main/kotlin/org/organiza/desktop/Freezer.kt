package org.organiza.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

/**
 * The friction of a desktop pause (ADR-0025): a blocked app's processes are stopped with SIGSTOP
 * (suspended, on Windows: ADR-0027) while it stays blocked, and continued with SIGCONT on an
 * override, when the session ends, when Organiza quits, or at its next start. Stopping keeps the
 * app's memory, so no work is lost. [signal] sends "STOP" or "CONT" to processes.
 */
class Freezer(private val record: Path, private val signal: (String, List<Long>) -> Unit = ::send) {
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
        // Windows counts suspensions and needs as many resumes, so a frozen process is never suspended twice.
        val tree = (listOf(root) + root.descendants().toList()).filter { it.pid() !in frozen }
        if (tree.isEmpty()) return
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
        return process.pid() > 1 && process.pid() !in protected && freezable(process.info().command().orElse(""))
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

    companion object {
        // Stopping any of these would freeze the whole desktop.
        private val SESSION = setOf(
            "gnome-shell", "xwayland", "xorg", "kwin_wayland", "kwin_x11", "hyprland", "sway", "systemd", "dbus-daemon",
            "explorer.exe", "dwm.exe", "csrss.exe", "winlogon.exe", "wininit.exe", "services.exe", "lsass.exe", "smss.exe",
            "svchost.exe", "sihost.exe", "ctfmon.exe", "fontdrvhost.exe", "applicationframehost.exe", "searchhost.exe",
            "startmenuexperiencehost.exe", "shellexperiencehost.exe", "textinputhost.exe",
        )

        /** False for the desktop session's own programs, given a program path from either OS. */
        fun freezable(command: String): Boolean = command.substringAfterLast('/').substringAfterLast('\\').lowercase() !in SESSION

        private fun send(name: String, pids: List<Long>) {
            if (pids.isEmpty()) return
            if (WINDOWS) return pids.forEach { suspend(it, name == "STOP") }
            ProcessBuilder(listOf("kill", "-$name") + pids.map(Long::toString)).redirectErrorStream(true).start().waitFor()
        }

        private fun suspend(pid: Long, stop: Boolean) {
            val process = Kernel32.INSTANCE.OpenProcess(PROCESS_SUSPEND_RESUME, false, pid.toInt()) ?: return
            try {
                if (stop) NtDll.INSTANCE.NtSuspendProcess(process) else NtDll.INSTANCE.NtResumeProcess(process)
            } finally {
                Kernel32.INSTANCE.CloseHandle(process)
            }
        }

        private const val PROCESS_SUSPEND_RESUME = 0x0800
    }
}

/** The undocumented but long-stable calls that suspend and resume every thread of a process. */
@Suppress("FunctionName")
private interface NtDll : Library {
    fun NtSuspendProcess(process: WinNT.HANDLE): Int
    fun NtResumeProcess(process: WinNT.HANDLE): Int

    companion object {
        val INSTANCE: NtDll by lazy { Native.load("ntdll", NtDll::class.java) }
    }
}
