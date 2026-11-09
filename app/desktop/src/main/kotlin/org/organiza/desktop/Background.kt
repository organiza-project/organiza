package org.organiza.desktop

import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread
import kotlin.io.path.exists
import kotlin.io.path.writeText

/** How this process was started, minus the flags that only ask for a window or the copilot. */
fun launcher(): List<String> {
    val info = ProcessHandle.current().info()
    val args = info.arguments().map { it.toList() }.orElse(emptyList()).filterNot { it in FLAGS }
    return listOf(info.command().orElse("organiza")) + args
}

const val BACKGROUND = "--background"
const val COPILOT = "--copilot"
private val FLAGS = setOf(BACKGROUND, COPILOT)

/**
 * Starting at login as a systemd user service (ADR-0025). It starts with the graphical session, in
 * the background, and again if it is killed; quitting from the tray ends it until the next login.
 */
class Autostart(private val configHome: Path) {
    private val unit = configHome.resolve("systemd/user/organiza.service")
    private val wanted = configHome.resolve("systemd/user/graphical-session.target.wants/organiza.service")

    fun enabled(): Boolean = wanted.exists()

    /** Writes the unit and links it the way `systemctl --user enable` would; systemd reads both at the next login. */
    fun enable(command: List<String> = launcher()): String {
        Files.createDirectories(wanted.parent)
        unit.writeText(unitText(command))
        Files.deleteIfExists(wanted)
        Files.createSymbolicLink(wanted, unit)
        return "Organiza now starts in the background when you log in"
    }

    companion object {
        fun unitText(command: List<String>): String = """
            [Unit]
            Description=Organiza focus sessions and usage tracking
            PartOf=graphical-session.target
            After=graphical-session.target

            [Service]
            ExecStart=${(command + BACKGROUND).joinToString(" ", transform = ::quote)}
            Restart=on-failure
            RestartSec=3

            [Install]
            WantedBy=graphical-session.target
        """.trimIndent() + "\n"

        // systemd reads quotes, backslashes, % specifiers and $ variables, so each is escaped.
        private fun quote(arg: String): String =
            "\"" + arg.replace("\\", "\\\\").replace("\"", "\\\"").replace("%", "%%").replace("$", "$$") + "\""
    }
}

/** One Organiza per login: a second start hands its request to the first over a private socket. */
object Instance {
    /** Held while this Organiza runs, so two starts at once cannot both listen. Null when another holds it. */
    fun lock(file: Path): AutoCloseable? {
        val channel = try {
            Files.createDirectories(file.parent)
            FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        } catch (failure: IOException) {
            // With no lock file Organiza still runs, as it does with no socket.
            System.err.println("organiza: no single-instance lock: ${failure.javaClass.simpleName}")
            return AutoCloseable {}
        }
        val lock = try {
            channel.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }
        if (lock == null) channel.close()
        // Closing the channel releases the lock; so does the process ending.
        return channel.takeIf { lock != null }
    }

    /** Sends [command] to a running Organiza. False when none is running. */
    fun handOff(socket: Path, command: String): Boolean = try {
        SocketChannel.open(UnixDomainSocketAddress.of(socket)).use { it.write(ByteBuffer.wrap("$command\n".toByteArray())) }
        true
    } catch (_: IOException) {
        false
    }

    /** Takes requests from later starts. Only [COMMANDS] are passed on. */
    fun listen(socket: Path, onCommand: (String) -> Unit): AutoCloseable {
        // This process holds the lock, so a file left here is from a crash.
        Files.deleteIfExists(socket)
        Files.createDirectories(socket.parent)
        val server = try {
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socket))
        } catch (failure: IOException) {
            // Such as a runtime folder whose path is too long for a socket. Organiza still runs, as more than one copy.
            System.err.println("organiza: no single-instance socket: ${failure.javaClass.simpleName}")
            return AutoCloseable {}
        }
        thread(isDaemon = true, name = "organiza-instance") {
            while (server.isOpen) {
                val line = try {
                    server.accept().use { Channels.newReader(it, Charsets.UTF_8).buffered().readLine() }
                } catch (_: IOException) {
                    break
                }
                line?.trim()?.takeIf { it in COMMANDS }?.let(onCommand)
            }
        }
        return server
    }

    val COMMANDS = setOf("show", "copilot")

    fun socket(env: Map<String, String> = System.getenv()): Path =
        env["XDG_RUNTIME_DIR"]?.let { Path.of(it, "organiza.sock") } ?: DesktopStorage.file(env).resolveSibling("organiza.sock")
}

/** A GNOME keyboard shortcut that opens the copilot from anywhere (ADR-0025). */
object Shortcut {
    const val KEYS = "<Super><Shift>space"
    private const val SCHEMA = "org.gnome.settings-daemon.plugins.media-keys"
    private const val PATH = "/org/gnome/settings-daemon/plugins/media-keys/custom-keybindings/organiza/"

    fun installed(): Boolean = "'$PATH'" in output("gsettings", "get", SCHEMA, "custom-keybindings").orEmpty()

    fun install(command: List<String> = launcher() + COPILOT): String {
        val existing = output("gsettings", "get", SCHEMA, "custom-keybindings")
            ?: return "Shortcuts are set in your desktop's keyboard settings. Bind this command to a key: ${command.joinToString(" ")}"
        val binding = "$SCHEMA.custom-keybinding:$PATH"
        val set = listOf(
            listOf("name", "Organiza copilot"),
            listOf("command", command.joinToString(" ") { if (' ' in it) "'$it'" else it }),
            listOf("binding", KEYS),
        ).all { (key, value) -> output("gsettings", "set", binding, key, value) != null }
        if (!set) return "The shortcut could not be saved"
        if ("'$PATH'" !in existing) {
            val list = Regex("'([^']*)'").findAll(existing).map { it.groupValues[1] }.toList() + PATH
            output("gsettings", "set", SCHEMA, "custom-keybindings", list.joinToString(", ", "[", "]") { "'$it'" })
        }
        return "Super+Shift+Space now opens the copilot"
    }
}
