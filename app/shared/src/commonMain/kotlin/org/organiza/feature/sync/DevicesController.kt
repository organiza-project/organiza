package org.organiza.feature.sync

import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.organiza.app.OrganizaCore
import org.organiza.storage.DeviceAppMinutes
import org.organiza.domain.PackageName
import org.organiza.sync.AuthState
import org.organiza.sync.DeviceLink
import org.organiza.sync.DeviceUsage

/**
 * Today's minutes from this person's other devices, so a daily limit counts every device (ADR-0028).
 * Reports are sealed end to end and go through the backend's relay; local only shares nothing.
 */
class DevicesController(private val app: OrganizaCore) {
    private val prefs = app.platform.prefs(PREFERENCES)
    private val secrets = app.platform.secrets
    private val network = Executors.newSingleThreadScheduledExecutor { Thread(it, "organiza-devices").apply { isDaemon = true } }

    /**
     * This device's name inside the sealed reports; it never leaves them. Taken from the machine, hashed, so a
     * reinstall keeps it and the old reports are not counted as another device; random where there is none.
     */
    val deviceId: String = app.platform.machineId?.let(::hashed)
        ?: prefs.string(DEVICE) ?: UUID.randomUUID().toString().also { prefs.put(mapOf(DEVICE to it)) }

    // Read from the keyring once: on Linux each read starts a helper process, and screens ask often.
    @Volatile private var current: DeviceLink? = null
    @Volatile private var loaded = false

    /** The exchange for the current relay and link, under a key naming both. */
    @Volatile private var exchange: Pair<String, DeviceUsage>? = null

    // shortcut: a report every 20 minutes keeps up to six devices inside the relay's 500 messages a day,
    // so another device's minutes can be 20 minutes behind; shorten it if limits feel late.
    fun start() {
        network.scheduleWithFixedDelay(::run, FIRST_S, INTERVAL_S, TimeUnit.SECONDS)
    }

    val linked: Boolean get() = link() != null

    fun code(): String? = link()?.code

    /** Joins the devices behind [code], or starts a new set when it is blank. */
    fun link(code: String) {
        val link = if (code.isBlank()) DeviceLink.create() else DeviceLink.fromCode(code) ?: return app.showMessage("That is not a device code from Organiza")
        secrets.put(SECRET, link.code.toByteArray())
        current = link
        loaded = true
        prefs.put(mapOf(CURSOR to null, POSTED to null))
        exchange = null
        app.showMessage(if (code.isBlank()) "Usage sharing is on. Copy the code to your other devices" else "This device now shares usage with your others")
        network.execute(::run)
    }

    fun unlink() {
        secrets.remove(SECRET)
        current = null
        loaded = true
        prefs.put(mapOf(CURSOR to null))
        exchange = null
        app.showMessage("This device no longer shares usage")
    }

    /** Today's time in [pkg], by its name, on the other devices; zero when none share. */
    fun elsewhere(pkg: PackageName): Duration {
        val usage = exchange?.second ?: return Duration.ZERO
        return Duration.ofMinutes(usage.minutes(app.platform.appLabel(pkg), today()))
    }

    /** What the last exchange did, in words, for the account panel; null before the first. */
    @Volatile var lastResult: String? = null
        private set

    /** How many other devices have reported today. */
    fun others(): Int = exchange?.second?.devices(today()) ?: 0

    // A repeating task stops for good the first time it throws, so nothing may escape.
    private fun run() = try {
        exchangeNow()
    } catch (failure: RuntimeException) {
        app.platform.log("device usage exchange failed: ${failure.javaClass.simpleName}")
    }

    private fun exchangeNow() {
        val backend = app.sync.backend
        val relay = backend.sync.relay()
        val link = link()
        if (relay == null || link == null || backend.auth.state() !is AuthState.SignedIn) {
            exchange = null
            return
        }
        val key = backend.sync.backend.toString() + link.mailboxId
        val usage = exchange?.takeIf { it.first == key }?.second
            ?: DeviceUsage(link, deviceId, relay, prefs.string(CURSOR)?.toLongOrNull() ?: 0).also { exchange = key to it }
        // Usage is read where storage lives, then sent from here.
        app.onStores { stores ->
            val today = today()
            // Right after linking, two weeks of history go out once; later, every day since the last post,
            // so days spent offline are filled in too, the last one with its final count.
            val days = DeviceUsage.daysToPost(prefs.string(POSTED)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, today)
            val saved = stores.deviceUsage.between(days.last(), today, mine = true)
            val reports = days.associateWith { app.usageReport.mine(it, today, ZoneId.systemDefault(), saved) }.filterValues { it.isNotEmpty() }
            network.execute { trade(usage, today, reports, stores) }
        }
    }

    private fun trade(usage: DeviceUsage, today: LocalDate, reports: Map<LocalDate, List<DeviceAppMinutes>>, stores: OrganizaCore.Storage) {
        try {
            reports.forEach { (day, apps) -> usage.post(day, apps.associate { it.app to it.minutes }, apps.associate { it.app to it.category }) }
            prefs.put(mapOf(POSTED to today.toString()))
            val received = usage.pull()
            prefs.put(mapOf(CURSOR to usage.cursor.toString()))
            lastResult = "Last shared at ${java.time.LocalTime.now().toString().take(5)}"
            // Kept in the encrypted database, so Metrics can show every device's history (ADR-0031).
            if (received.isNotEmpty()) {
                app.onStores {
                    received.forEach { report ->
                        stores.deviceUsage.put(report.device, report.day, report.minutes.map { (name, minutes) -> DeviceAppMinutes(name, report.categories[name] ?: OTHER, minutes) })
                    }
                    app.requestUiRefresh()
                }
            }
            app.requestUiRefresh()
        } catch (failure: IOException) {
            app.platform.log("device usage exchange failed: ${failure.javaClass.simpleName}")
            fail("The backend could not be reached. Usage is kept and shared on the next try")
        } catch (failure: RuntimeException) {
            app.platform.log("device usage exchange failed: ${failure.javaClass.simpleName}")
            fail("Sharing usage failed. Usage is kept and shared on the next try")
        }
    }

    private fun fail(text: String) {
        lastResult = text
        app.requestUiRefresh()
    }

    private fun link(): DeviceLink? {
        if (!loaded) {
            // A keyring that cannot be reached means not linked for now; storage reports that problem itself.
            current = runCatching { secrets.get(SECRET) }.getOrElse { return null }?.decodeToString()?.let(DeviceLink::fromCode)
            loaded = true
        }
        return current
    }

    private fun hashed(raw: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest("organiza-device:$raw".toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

    private fun today(): LocalDate = app.clock.now().atZone(ZoneId.systemDefault()).toLocalDate()

    private companion object {
        const val PREFERENCES = "organiza_devices"
        const val DEVICE = "device"
        const val CURSOR = "cursor"
        const val POSTED = "posted"
        const val OTHER = "Other"
        const val SECRET = "devices-secret"
        const val FIRST_S = 30L
        const val INTERVAL_S = 20 * 60L
    }
}
