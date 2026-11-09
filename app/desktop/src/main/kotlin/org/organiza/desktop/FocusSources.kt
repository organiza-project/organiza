package org.organiza.desktop

import com.sun.jna.platform.win32.User32
import com.sun.jna.ptr.IntByReference
import java.io.IOException
import java.lang.ProcessBuilder.Redirect
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** The app in front, by desktop file ID, and the process of its window when the desktop says. */
data class Focus(val app: String, val pid: Long?)

/** Reports the app in front, or null when nothing is (ADR-0024, ADR-0025). */
fun interface FocusSource {
    /** Starts reporting, or returns null when this desktop cannot say which app is in front. */
    fun start(onChange: (Focus?) -> Unit): AutoCloseable?

    /** Sets up what [start] needs, and says what happens next. */
    fun enable(): String = UNSUPPORTED

    companion object {
        const val UNSUPPORTED = "Usage tracking does not work on this desktop yet. It works on Windows and on GNOME, Hyprland, Sway and X11 desktops."

        /** The first source this session supports, checked in order: Windows, GNOME, Hyprland, Sway, then X11. */
        fun forSession(env: Map<String, String> = System.getenv(), apps: List<LinuxApp> = LinuxApps.installed): FocusSource = when {
            WINDOWS -> WindowsFocus(apps)
            "GNOME" in env["XDG_CURRENT_DESKTOP"].orEmpty().split(':') -> GnomeFocus
            !env["HYPRLAND_INSTANCE_SIGNATURE"].isNullOrEmpty() -> HyprlandFocus(apps)
            !env["SWAYSOCK"].isNullOrEmpty() -> SwayFocus(apps)
            env["XDG_SESSION_TYPE"] == "x11" -> X11Focus(apps)
            else -> FocusSource { null }
        }

        /** The entry a window belongs to: its exact ID first, then its WM_CLASS-style names. */
        fun match(apps: List<LinuxApp>, exact: String?, vararg names: String): String? {
            apps.firstOrNull { it.id == exact }?.let { return it.id }
            return names.filter(String::isNotEmpty).firstNotNullOfOrNull { name ->
                apps.firstOrNull { it.wmClass.equals(name, ignoreCase = true) || it.id.equals(name, ignoreCase = true) }
            }?.id
        }
    }
}

/** GNOME keeps the focused app to itself, so the bundled shell extension reports it over D-Bus. */
object GnomeFocus : FocusSource {
    const val UUID = "organiza-focus@organiza.org"
    private const val PATH = "/org/organiza/Focus"
    private val REPLY = Regex("""\((['"])(.*?)\1(?:, (\d+))?,?\)""")

    override fun start(onChange: (Focus?) -> Unit): AutoCloseable? {
        // Listening starts before the first question, so no change falls between the two.
        val monitor = follow(listOf("gdbus", "monitor", "--session", "--dest", "org.gnome.Shell", "--object-path", PATH), onChange) { line ->
            if ("org.organiza.Focus.Changed" in line) focus(line)?.let { onChange(it.takeIf { it.app.isNotEmpty() }) }
        } ?: return null
        val current = output("gdbus", "call", "--session", "--dest", "org.gnome.Shell", "--object-path", PATH, "--method", "org.organiza.Focus.Current")
            ?.let(::focus)
        if (current == null) {
            monitor.close()
            return null
        }
        onChange(current.takeIf { it.app.isNotEmpty() })
        return monitor
    }

    override fun enable(): String {
        install(installDir())
        return if (turnOn()) {
            "The Organiza extension for GNOME is installed. Log out and back in once, and usage tracking starts."
        } else {
            "The extension is installed but could not be turned on. Turn on Organiza Focus in the Extensions app."
        }
    }

    private val FILES = listOf("metadata.json", "extension.js")

    fun installDir(): Path = LinuxApps.dataDirs().first().resolve("gnome-shell/extensions/$UUID")

    private fun bundled(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/gnome-extension/$name")) { "$name is missing" }.use { it.readAllBytes() }

    fun install(dir: Path) {
        Files.createDirectories(dir)
        FILES.forEach { Files.write(dir.resolve(it), bundled(it)) }
    }

    /**
     * Replaces an installed extension older than this app's, and says so. GNOME loads the new one at
     * the next login. Nothing happens when none is installed: that waits for the person to allow it.
     */
    fun updateIfOld(dir: Path = installDir()): String? {
        if (!Files.exists(dir.resolve("extension.js"))) return null
        if (FILES.all { Files.exists(dir.resolve(it)) && Files.readAllBytes(dir.resolve(it)).contentEquals(bundled(it)) }) return null
        install(dir)
        return "The GNOME extension was updated so pauses can stop blocked apps. Log out and back in once to finish."
    }

    // GNOME on Wayland finds a new extension only at login, so `enable` can fail until then.
    private fun turnOn(): Boolean {
        if (output("gnome-extensions", "enable", UUID) != null) return true
        val enabled = output("gsettings", "get", "org.gnome.shell", "enabled-extensions") ?: return false
        if ("'$UUID'" in enabled) return true
        val list = Regex("'([^']*)'").findAll(enabled).map { it.groupValues[1] }.toList() + UUID
        return output("gsettings", "set", "org.gnome.shell", "enabled-extensions", list.joinToString(", ", "[", "]") { "'$it'" }) != null
    }

    /** The desktop ID and process in a `gdbus` reply or signal, with "" for nothing in front. */
    fun focus(line: String): Focus? {
        val match = REPLY.find(line) ?: return null
        val id = match.groupValues[2]
        val pid = match.groupValues[3].toLongOrNull()?.takeIf { it > 0 }
        // GNOME names a window it cannot match to a desktop entry `window:N`.
        return if (id.startsWith("window:")) Focus("", null) else Focus(id.removeSuffix(".desktop"), pid)
    }
}

/** Hyprland announces each focus change on its event socket; `hyprctl` names the window's app. */
class HyprlandFocus(private val apps: List<LinuxApp>) : FocusSource {
    override fun start(onChange: (Focus?) -> Unit): AutoCloseable? {
        val runtime = System.getenv("XDG_RUNTIME_DIR") ?: return null
        val socket = Path.of(runtime, "hypr", System.getenv("HYPRLAND_INSTANCE_SIGNATURE").orEmpty(), ".socket2.sock")
        val channel = try {
            SocketChannel.open(UnixDomainSocketAddress.of(socket))
        } catch (_: IOException) {
            return null
        }
        thread(isDaemon = true, name = "hyprland-events") {
            runCatching {
                Channels.newReader(channel, Charsets.UTF_8).buffered().forEachLine { line ->
                    if (line.startsWith("activewindowv2>>")) onChange(output("hyprctl", "-j", "activewindow")?.let { focus(it, apps) })
                }
            }
            onChange(null)
        }
        return channel
    }

    override fun enable(): String = "Usage tracking on Hyprland needs hyprctl, which comes with Hyprland. Open Organiza from your Hyprland session."

    companion object {
        private val CLASS = Regex(""""class":\s*"([^"]*)"""")
        private val PID = Regex(""""pid":\s*(\d+)""")

        fun focus(json: String, apps: List<LinuxApp>): Focus? {
            val id = FocusSource.match(apps, null, CLASS.find(json)?.groupValues?.get(1).orEmpty()) ?: return null
            return Focus(id, PID.find(json)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 })
        }
    }
}

/** Sway reports window events on its IPC socket; native windows carry an app ID, X11 ones a class. */
class SwayFocus(private val apps: List<LinuxApp>) : FocusSource {
    override fun start(onChange: (Focus?) -> Unit): AutoCloseable? =
        follow(listOf("swaymsg", "-t", "subscribe", "-m", "[\"window\"]"), onChange) { line ->
            if (CHANGE.find(line)?.groupValues?.get(1) == "focus") onChange(focus(line, apps))
        }

    override fun enable(): String = "Usage tracking on Sway needs swaymsg, which comes with Sway. Open Organiza from your Sway session."

    companion object {
        private val CHANGE = Regex(""""change":\s*"([^"]*)"""")
        private val APP_ID = Regex(""""app_id":\s*"([^"]*)"""")
        private val CLASS = Regex(""""class":\s*"([^"]*)"""")
        private val PID = Regex(""""pid":\s*(\d+)""")

        fun focus(json: String, apps: List<LinuxApp>): Focus? {
            val appId = APP_ID.find(json)?.groupValues?.get(1).orEmpty()
            val id = FocusSource.match(apps, appId, appId, CLASS.find(json)?.groupValues?.get(1).orEmpty()) ?: return null
            return Focus(id, PID.find(json)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 })
        }
    }
}

/** X11 publishes the active window on the root window, and `xprop -spy` reports each change. */
class X11Focus(private val apps: List<LinuxApp>) : FocusSource {
    override fun start(onChange: (Focus?) -> Unit): AutoCloseable? =
        follow(listOf("xprop", "-root", "-spy", "_NET_ACTIVE_WINDOW"), onChange) { line ->
            onChange(window(line)?.let { output("xprop", "-id", it, "_GTK_APPLICATION_ID", "WM_CLASS", "_NET_WM_PID") }?.let { focus(it, apps) })
        }

    override fun enable(): String = "Usage tracking on X11 needs xprop. Install the x11-utils package and open Organiza again."

    companion object {
        private val WINDOW = Regex("""window id # 0x([0-9a-fA-F]+)""")
        private val GTK = Regex("""_GTK_APPLICATION_ID\(UTF8_STRING\) = "([^"]*)"""")
        private val CLASS = Regex("""WM_CLASS\(STRING\) = "([^"]*)", "([^"]*)"""")
        private val PID = Regex("""_NET_WM_PID\(CARDINAL\) = (\d+)""")

        fun window(line: String): String? =
            WINDOW.find(line)?.groupValues?.get(1)?.takeIf { it.trimStart('0').isNotEmpty() }?.let { "0x$it" }

        /** The entry a window belongs to: its GTK application ID, else its WM_CLASS instance or class. */
        fun focus(props: String, apps: List<LinuxApp>): Focus? {
            val classes = CLASS.find(props)?.groupValues?.drop(1).orEmpty().toTypedArray()
            val id = FocusSource.match(apps, GTK.find(props)?.groupValues?.get(1), *classes) ?: return null
            return Focus(id, PID.find(props)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 })
        }
    }
}

/** Windows names the foreground window, and its process's program file names the app (ADR-0027). */
class WindowsFocus(private val apps: List<LinuxApp>) : FocusSource {
    override fun start(onChange: (Focus?) -> Unit): AutoCloseable {
        val timer = Executors.newSingleThreadScheduledExecutor { Thread(it, "windows-focus").apply { isDaemon = true } }
        var last: Focus? = null
        // shortcut: polls twice a second; SetWinEventHook needs a message loop, worth it if this shows in battery use.
        timer.scheduleWithFixedDelay({
            val now = runCatching { foreground() }.getOrNull()
            if (now != last) {
                last = now
                onChange(now)
            }
        }, 0, POLL_MS, TimeUnit.MILLISECONDS)
        return AutoCloseable { timer.shutdownNow() }
    }

    override fun enable(): String = "Usage tracking on Windows needs no setup. Open Organiza again to restart it."

    private fun foreground(): Focus? {
        val window = User32.INSTANCE.GetForegroundWindow() ?: return null
        val pid = IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(window, pid)
        // Empty for an elevated program, which this process may not inspect.
        val exe = ProcessHandle.of(pid.value.toLong()).flatMap { it.info().command() }.orElse(null) ?: return null
        return focus(exe, pid.value.toLong(), apps)
    }

    companion object {
        private const val POLL_MS = 500L

        fun focus(exe: String, pid: Long, apps: List<LinuxApp>): Focus? =
            FocusSource.match(apps, WindowsApps.id(exe))?.let { Focus(it, pid.takeIf { it > 0 }) }
    }
}

/** Hands each line [command] prints to [onLine] until closed. When it ends, nothing is known to be in front. */
private fun follow(command: List<String>, onChange: (Focus?) -> Unit, onLine: (String) -> Unit): AutoCloseable? {
    val process = try {
        ProcessBuilder(command).redirectError(Redirect.DISCARD).start()
    } catch (_: IOException) {
        return null
    }
    // shortcut: a source that dies is not restarted, so tracking stops until Organiza opens again.
    thread(isDaemon = true, name = command.first()) {
        process.inputStream.bufferedReader().forEachLine(onLine)
        onChange(null)
    }
    return AutoCloseable { process.destroy() }
}

/** Standard output of a command that succeeded, or null. */
internal fun output(vararg command: String): String? = try {
    val process = ProcessBuilder(*command).redirectError(Redirect.DISCARD).start()
    val out = process.inputStream.bufferedReader().readText()
    out.takeIf { process.waitFor() == 0 }
} catch (_: IOException) {
    null
}
