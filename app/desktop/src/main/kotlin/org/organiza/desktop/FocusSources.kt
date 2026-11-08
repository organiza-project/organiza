package org.organiza.desktop

import java.io.IOException
import java.lang.ProcessBuilder.Redirect
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.concurrent.thread

/** Reports the app in front by its desktop file ID, or null when nothing is (ADR-0024). */
fun interface FocusSource {
    /** Starts reporting, or returns null when this desktop cannot say which app is in front. */
    fun start(onChange: (String?) -> Unit): AutoCloseable?

    /** Sets up what [start] needs, and says what happens next. */
    fun enable(): String = "Usage tracking does not work on this desktop yet. It works on GNOME and on X11 desktops."

    companion object {
        fun forSession(env: Map<String, String> = System.getenv()): FocusSource = when {
            "GNOME" in env["XDG_CURRENT_DESKTOP"].orEmpty().split(':') -> GnomeFocus
            env["XDG_SESSION_TYPE"] == "x11" -> X11Focus(LinuxApps.installed)
            else -> FocusSource { null }
        }
    }
}

/** GNOME keeps the focused app to itself, so the bundled shell extension reports it over D-Bus. */
object GnomeFocus : FocusSource {
    const val UUID = "organiza-focus@organiza.org"
    private const val PATH = "/org/organiza/Focus"
    private val ID = Regex("""\((['"])(.*)\1,\)""")

    override fun start(onChange: (String?) -> Unit): AutoCloseable? {
        // Listening starts before the first question, so no change falls between the two.
        val monitor = follow(listOf("gdbus", "monitor", "--session", "--dest", "org.gnome.Shell", "--object-path", PATH), onChange) { line ->
            appId(line)?.let { onChange(it.ifEmpty { null }) }
        } ?: return null
        val current = output("gdbus", "call", "--session", "--dest", "org.gnome.Shell", "--object-path", PATH, "--method", "org.organiza.Focus.Current")
            ?.let(::appId)
        if (current == null) {
            monitor.close()
            return null
        }
        onChange(current.ifEmpty { null })
        return monitor
    }

    override fun enable(): String {
        val dir = LinuxApps.dataDirs().first().resolve("gnome-shell/extensions/$UUID")
        Files.createDirectories(dir)
        for (name in listOf("metadata.json", "extension.js")) {
            checkNotNull(javaClass.getResourceAsStream("/gnome-extension/$name")) { "$name is missing" }
                .use { Files.copy(it, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING) }
        }
        return if (turnOn()) {
            "The Organiza extension for GNOME is installed. Log out and back in once, and usage tracking starts."
        } else {
            "The extension is installed but could not be turned on. Turn on Organiza Focus in the Extensions app."
        }
    }

    // GNOME on Wayland finds a new extension only at login, so `enable` can fail until then.
    private fun turnOn(): Boolean {
        if (output("gnome-extensions", "enable", UUID) != null) return true
        val enabled = output("gsettings", "get", "org.gnome.shell", "enabled-extensions") ?: return false
        if ("'$UUID'" in enabled) return true
        val list = Regex("'([^']*)'").findAll(enabled).map { it.groupValues[1] }.toList() + UUID
        return output("gsettings", "set", "org.gnome.shell", "enabled-extensions", list.joinToString(", ", "[", "]") { "'$it'" }) != null
    }

    /** The desktop ID in a `gdbus` reply or signal line, "" for nothing in front, null for other lines. */
    fun appId(line: String): String? {
        val id = ID.find(line)?.groupValues?.get(2) ?: return null
        // GNOME names a window it cannot match to a desktop entry `window:N`.
        return if (id.startsWith("window:")) "" else id.removeSuffix(".desktop")
    }
}

/** X11 publishes the active window on the root window, and `xprop -spy` reports each change. */
class X11Focus(private val apps: List<LinuxApp>) : FocusSource {
    override fun start(onChange: (String?) -> Unit): AutoCloseable? =
        follow(listOf("xprop", "-root", "-spy", "_NET_ACTIVE_WINDOW"), onChange) { line ->
            onChange(window(line)?.let { output("xprop", "-id", it, "_GTK_APPLICATION_ID", "WM_CLASS") }?.let { appId(it, apps) })
        }

    override fun enable(): String = "Usage tracking on X11 needs xprop. Install the x11-utils package and open Organiza again."

    companion object {
        private val WINDOW = Regex("""window id # 0x([0-9a-fA-F]+)""")
        private val GTK = Regex("""_GTK_APPLICATION_ID\(UTF8_STRING\) = "([^"]*)"""")
        private val CLASS = Regex("""WM_CLASS\(STRING\) = "([^"]*)", "([^"]*)"""")

        fun window(line: String): String? =
            WINDOW.find(line)?.groupValues?.get(1)?.takeIf { it.trimStart('0').isNotEmpty() }?.let { "0x$it" }

        /** The entry a window belongs to: its GTK application ID, else its WM_CLASS instance or class. */
        fun appId(props: String, apps: List<LinuxApp>): String? {
            val gtk = GTK.find(props)?.groupValues?.get(1)
            apps.firstOrNull { it.id == gtk }?.let { return it.id }
            val classes = CLASS.find(props)?.groupValues?.drop(1).orEmpty()
            return classes.firstNotNullOfOrNull { name ->
                apps.firstOrNull { it.wmClass.equals(name, ignoreCase = true) || it.id.equals(name, ignoreCase = true) }
            }?.id
        }
    }
}

/** Hands each line [command] prints to [onLine] until closed. When it ends, nothing is known to be in front. */
private fun follow(command: List<String>, onChange: (String?) -> Unit, onLine: (String) -> Unit): AutoCloseable? {
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
private fun output(vararg command: String): String? = try {
    val process = ProcessBuilder(*command).redirectError(Redirect.DISCARD).start()
    val out = process.inputStream.bufferedReader().readText()
    out.takeIf { process.waitFor() == 0 }
} catch (_: IOException) {
    null
}
