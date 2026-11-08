package org.organiza.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed

/** An app a person can launch, from its desktop entry (ADR-0024). */
data class LinuxApp(val id: String, val name: String, val icon: String?, val category: String, val wmClass: String?) {
    val pkg: PackageName? get() = LinuxApps.packageName(id)
}

/** Desktop entries and their icons, read from the XDG data directories. */
object LinuxApps {
    /** Every launchable app on this computer, read once. */
    val installed: List<LinuxApp> by lazy { scan(dataDirs()) }

    private val byPackage by lazy { installed.associateBy { it.pkg?.value } }

    fun find(pkg: String): LinuxApp? = byPackage[pkg]

    /** The app's identity in core: its ID when that fits, otherwise the same ID under `linux.`. */
    fun packageName(desktopId: String): PackageName? {
        (PackageName.parse(desktopId) as? Parsed.Valid)?.let { return it.value }
        val segments = desktopId.replace(Regex("[^A-Za-z0-9_.]"), "_").split('.').filter(String::isNotEmpty)
            .map { if (it.first().isLetter()) it else "x$it" }
        if (segments.isEmpty()) return null
        return (PackageName.parse((listOf("linux") + segments).joinToString(".")) as? Parsed.Valid)?.value
    }

    /** The app an entry describes, or null when it is hidden or not an application. */
    fun parse(id: String, text: String): LinuxApp? {
        val keys = mutableMapOf<String, String>()
        var inEntry = false
        for (line in text.lineSequence().map(String::trim)) {
            when {
                line.startsWith("[") -> inEntry = line == "[Desktop Entry]"
                inEntry && '=' in line -> keys.putIfAbsent(line.substringBefore('=').trim(), line.substringAfter('=').trim())
            }
        }
        if (keys["Type"] != "Application" || keys["NoDisplay"] == "true" || keys["Hidden"] == "true") return null
        val name = keys["Name"]?.takeIf(String::isNotEmpty) ?: return null
        val category = keys["Categories"].orEmpty().split(';').firstNotNullOfOrNull(CATEGORIES::get) ?: "Other"
        return LinuxApp(id, name, keys["Icon"]?.takeIf(String::isNotEmpty), category, keys["StartupWMClass"])
    }

    /** Apps under each directory's `applications/`; an earlier directory wins, as the spec says. */
    fun scan(dataDirs: List<Path>): List<LinuxApp> {
        val seen = LinkedHashMap<String, LinuxApp?>()
        for (dir in dataDirs.map { it.resolve("applications") }.filter { it.isDirectory() }) {
            Files.walk(dir).use { paths ->
                paths.filter { it.isRegularFile() && it.extension == "desktop" }.sorted().forEach { file ->
                    val id = file.relativeTo(dir).toString().removeSuffix(".desktop").replace('/', '-')
                    if (id !in seen) seen[id] = runCatching { parse(id, file.readText()) }.getOrNull()
                }
            }
        }
        return seen.values.filterNotNull()
    }

    /** The file for an `Icon=` value: a path as it is, or a name from hicolor or pixmaps. */
    fun iconFile(icon: String, dataDirs: List<Path> = dataDirs()): Path? {
        if (icon.startsWith("/")) return Path.of(icon).takeIf { it.isRegularFile() }
        // shortcut: hicolor only, not the person's chosen theme; apps install their icons there.
        val candidates = dataDirs.flatMap { dir ->
            SIZES.map { dir.resolve("icons/hicolor/$it/apps/$icon.${if (it == "scalable") "svg" else "png"}") } +
                listOf("png", "svg").map { dir.resolve("pixmaps/$icon.$it") }
        }
        return candidates.firstOrNull { it.isRegularFile() }
    }

    fun dataDirs(env: Map<String, String> = System.getenv()): List<Path> {
        val home = env["XDG_DATA_HOME"]?.takeIf(String::isNotEmpty) ?: "${env["HOME"]}/.local/share"
        val system = env["XDG_DATA_DIRS"]?.takeIf(String::isNotEmpty) ?: "/usr/local/share:/usr/share"
        return (listOf(home) + system.split(':')).filter(String::isNotEmpty).map { Path.of(it.trimEnd('/').ifEmpty { "/" }) }.distinct()
    }

    private val SIZES = listOf("96x96", "128x128", "64x64", "48x48", "256x256", "scalable", "512x512", "32x32")

    /** The main categories of the desktop menu spec, named the way the metrics screen groups apps. */
    private val CATEGORIES = mapOf(
        "AudioVideo" to "Audio & Video", "Audio" to "Audio & Video", "Video" to "Audio & Video",
        "Development" to "Development", "Education" to "Education", "Game" to "Games", "Graphics" to "Graphics",
        "Network" to "Internet", "Office" to "Productivity", "Science" to "Science",
        "Settings" to "System", "System" to "System", "Utility" to "Utilities",
    )
}
