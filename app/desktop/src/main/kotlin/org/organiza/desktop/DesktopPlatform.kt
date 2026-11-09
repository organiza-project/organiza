package org.organiza.desktop

import java.awt.SystemTray
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import org.organiza.ai.ApiKeyStore
import org.organiza.app.AppUsage
import org.organiza.app.InstalledApp
import org.organiza.app.Platform
import org.organiza.app.Prefs
import org.organiza.arena.SecretStore
import org.organiza.domain.PackageName
import org.organiza.domain.port.Capability
import org.organiza.enforcement.SessionPolicy
import org.organiza.presentation.Device
import org.organiza.presentation.Permission
import org.organiza.storage.Database
import org.organiza.storage.FocusLog
import org.organiza.storage.HabitReminder
import org.organiza.voice.VoiceCommand

/** The Linux desktop as the shared core sees it (ADR-0025). Nothing here leaves the computer. */
class DesktopPlatform(
    private val keyring: SecretStore = Keyring(),
    val apps: List<LinuxApp> = LinuxApps.installed,
    private val env: Map<String, String> = System.getenv(),
    private val database: Path = DesktopStorage.file(env),
    val autostart: Autostart = Autostart(configHome(env)),
) : Platform {
    override val device: Device = Device.DESKTOP
    override val debug: Boolean = System.getProperty("organiza.debug") == "true"
    override val selfPackage: String = SELF.value
    // The desktop build has no hosted project; people choose local only or their own Supabase.
    override val hostedUrl: String = ""
    override val hostedKey: String = ""
    override val secrets: SecretStore = keyring
    override val apiKeys: ApiKeyStore = KeyringApiKeyStore(keyring)

    /** Whether a focus source is reporting, set by the app once it starts one. */
    @Volatile var tracking = false

    /** Whether the copilot shortcut is set, read once since asking GNOME starts a process. */
    @Volatile var shortcut = Shortcut.installed()

    @Volatile private var db: Database? = null

    override val usage: AppUsage = object : AppUsage {
        override fun permitted(): Boolean = tracking

        override fun perApp(from: java.time.Instant, to: java.time.Instant): Map<PackageName, Duration> =
            db?.let { LoggedUsage(FocusLog(it)).perApp(from, to) } ?: emptyMap()
    }

    val dataDir: Path get() = database.parent

    var trayWanted: Boolean
        get() = prefs(PREFS).flag(TRAY)
        set(value) = prefs(PREFS).put(mapOf(TRAY to value.toString()))

    override fun prefs(name: String): Prefs = FilePrefs(dataDir.resolve("$name.properties"))

    override fun openDatabase(): Database = DesktopStorage.open(database, keyring).also { db = it }

    override fun launchable(): List<InstalledApp> =
        apps.mapNotNull { app -> app.pkg?.takeIf { it != SELF }?.let { InstalledApp(it.value, app.name, app.category) } }

    override fun appLabel(app: PackageName): String = apps.firstOrNull { it.pkg == app }?.name ?: app.value

    override fun granted(): Set<Capability> = buildSet {
        if (tracking) add(Capability.USAGE_STATS)
        if (autostart.enabled()) add(Capability.DAEMON)
    }

    override fun permissions(): Set<Permission> = buildSet {
        if (tracking) add(Permission.TRACKING)
        if (autostart.enabled()) add(Permission.AUTOSTART)
        if (trayWanted && SystemTray.isSupported()) add(Permission.TRAY)
        if (shortcut) add(Permission.SHORTCUT)
    }

    // shortcut: no website blocking on the desktop yet; it needs a root helper for /etc/hosts.
    override fun siteBlockingAllowed(): Boolean = false

    override fun batteryExempt(): Boolean = true

    override fun lockdownApps(): Set<PackageName> = emptySet()

    // App blocking reads the session inside this process, so nothing outside needs telling.
    override fun applyPolicy(policy: SessionPolicy) = Unit

    private val reminders = Executors.newSingleThreadScheduledExecutor { Thread(it, "organiza-reminders").apply { isDaemon = true } }
    private var scheduled: List<ScheduledFuture<*>> = emptyList()

    /** Each reminder shows as a desktop notification at its minute of the day, every day. */
    @Synchronized
    override fun scheduleReminders(reminders: List<HabitReminder>, previous: Set<String>) {
        scheduled.forEach { it.cancel(false) }
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        scheduled = reminders.map { reminder ->
            var at = now.toLocalDate().atStartOfDay(now.zone).plusMinutes(reminder.minute.toLong())
            if (!at.isAfter(now)) at = at.plusDays(1)
            this.reminders.scheduleAtFixedRate(
                { output("notify-send", "--app-name=Organiza", reminder.name, "Time for this habit") },
                Duration.between(now, at).toMillis(),
                TimeUnit.DAYS.toMillis(1),
                TimeUnit.MILLISECONDS,
            )
        }
    }

    /** Hands the meeting to the person's calendar app as a one-event file, which it offers to save. */
    override fun openCalendar(event: VoiceCommand.AddEvent): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"))
        val end = event.start.plus(Duration.ofMinutes(event.minutes.toLong()))
        val title = event.title.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", " ")
        val file = Files.createTempFile("organiza-event", ".ics")
        Files.writeString(
            file,
            "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Organiza//EN\r\nBEGIN:VEVENT\r\nUID:${java.util.UUID.randomUUID()}@organiza\r\n" +
                "DTSTAMP:${stamp.format(java.time.Instant.now())}\r\nDTSTART:${stamp.format(event.start)}\r\nDTEND:${stamp.format(end)}\r\n" +
                "SUMMARY:$title\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n",
        )
        return if (output("xdg-open", file.toString()) != null) "Opened your calendar. Save the event there" else "No calendar app could take the event"
    }

    override fun onMain(work: () -> Unit) = SwingUtilities.invokeLater(work)

    override fun log(message: String) = System.err.println("organiza: $message")

    override fun debugLine(line: String) {
        if (debug) println("OrganizaDebug: $line")
    }

    companion object {
        /** Organiza's own identity among desktop apps; its windows never count or get blocked. */
        val SELF: PackageName = checkNotNull(LinuxApps.packageName("organiza"))
        private const val PREFS = "desktop"
        private const val TRAY = "tray"

        fun configHome(env: Map<String, String>): Path =
            env["XDG_CONFIG_HOME"]?.takeIf(String::isNotEmpty)?.let(Path::of) ?: Path.of(env["HOME"].orEmpty(), ".config")
    }
}

/** Preferences in a properties file only this user can read, next to the database. */
class FilePrefs(private val file: Path) : Prefs {
    @Synchronized
    override fun string(key: String): String? = load().getProperty(key)

    @Synchronized
    override fun put(values: Map<String, String?>) {
        val props = load()
        values.forEach { (key, value) -> if (value == null) props.remove(key) else props.setProperty(key, value) }
        save(props)
    }

    @Synchronized
    override fun clear() = save(Properties())

    private fun load() = Properties().also { props -> if (file.exists()) file.inputStream().use(props::load) }

    private fun save(props: Properties) {
        Files.createDirectories(file.parent)
        if (!file.exists()) Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        file.outputStream().use { props.store(it, null) }
    }
}
