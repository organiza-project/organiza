package org.organiza.desktop

import java.nio.file.Files
import java.nio.file.Path
import javax.swing.filechooser.FileSystemView
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension

/** Apps on Windows: the Start menu's shortcuts to programs (ADR-0027). */
object WindowsApps {
    /** The Start menu for everyone, then this user's. */
    fun startMenus(env: Map<String, String> = System.getenv()): List<Path> =
        listOfNotNull(env["ProgramData"], env["APPDATA"]).map { Path.of(it, "Microsoft", "Windows", "Start Menu", "Programs") }

    /** One app per program a shortcut opens, named after its first shortcut. Uninstallers are left out. */
    fun scan(menus: List<Path>, target: (Path) -> String? = ::shortcutTarget): List<LinuxApp> {
        val apps = LinkedHashMap<String, LinuxApp>()
        for (menu in menus.filter { it.isDirectory() }) {
            // A folder this login may not read is skipped rather than stopping Organiza from starting.
            runCatching {
                Files.walk(menu).use { paths ->
                    paths.filter { it.isRegularFile() && it.extension.equals("lnk", ignoreCase = true) }.sorted().forEach { link ->
                        if ("uninstall" in link.nameWithoutExtension.lowercase()) return@forEach
                        val exe = runCatching { target(link) }.getOrNull()?.takeIf { it.endsWith(".exe", ignoreCase = true) } ?: return@forEach
                        apps.putIfAbsent(id(exe), LinuxApp(id(exe), link.nameWithoutExtension, exe, "Other", null))
                    }
                }
            }
        }
        return apps.values.toList()
    }

    /**
     * A program's ID is its file name in lower case: `C:\...\Discord.exe` is `discord`, as its process says.
     * Squirrel installs (Discord, Slack, Teams) start through `<App>\Update.exe`, so that is the folder's name.
     */
    fun id(exe: String): String {
        val parts = exe.split('\\', '/')
        val file = parts.last().lowercase()
        return if (file == "update.exe" && parts.size > 1) parts[parts.size - 2].lowercase() else file.substringBeforeLast('.')
    }

    // The Windows shell reads the .lnk file; it throws for a shortcut whose target is gone.
    private fun shortcutTarget(link: Path): String? = FileSystemView.getFileSystemView().getLinkLocation(link.toFile())?.path
}
