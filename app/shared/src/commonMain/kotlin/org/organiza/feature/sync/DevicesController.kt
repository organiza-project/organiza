package org.organiza.feature.sync

import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.organiza.app.OrganizaCore
import org.organiza.domain.PackageName
import org.organiza.sync.AuthState
import org.organiza.sync.DeviceLink
import org.organiza.sync.DeviceUsage

/**
 * Today's minutes from this person's other devices, so a daily limit counts every device (ADR-0028).
 * Reports are sealed end to end and go through the backend's Arena relay; local only shares nothing.
 */
class DevicesController(private val app: OrganizaCore) {
    private val prefs = app.platform.prefs(PREFERENCES)
    private val secrets = app.platform.secrets
    private val network = Executors.newSingleThreadScheduledExecutor { Thread(it, "organiza-devices").apply { isDaemon = true } }

    /** A random name for this device inside the sealed reports; it never leaves them. */
    private val device: String = prefs.string(DEVICE) ?: UUID.randomUUID().toString().also { prefs.put(mapOf(DEVICE to it)) }

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
        prefs.put(mapOf(CURSOR to null))
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
        val url = backend.sync.defaultRelayUrl()
        val link = link()
        if (url == null || link == null || backend.auth.state() !is AuthState.SignedIn) {
            exchange = null
            return
        }
        val key = url + link.mailboxId
        val usage = exchange?.takeIf { it.first == key }?.second
            ?: DeviceUsage(link, device, backend.sync.relayFor(url), prefs.string(CURSOR)?.toLongOrNull() ?: 0).also { exchange = key to it }
        // Usage is read where storage lives, then sent from here.
        app.onStores {
            val day = today()
            val minutes = todayByName(day)
            network.execute { trade(usage, day, minutes) }
        }
    }

    private fun trade(usage: DeviceUsage, day: LocalDate, minutes: Map<String, Long>) {
        try {
            if (minutes.isNotEmpty()) usage.post(day, minutes)
            usage.pull()
            prefs.put(mapOf(CURSOR to usage.cursor.toString()))
            app.requestUiRefresh()
        } catch (failure: IOException) {
            app.platform.log("device usage exchange failed: ${failure.javaClass.simpleName}")
        } catch (failure: RuntimeException) {
            app.platform.log("device usage exchange failed: ${failure.javaClass.simpleName}")
        }
    }

    // Storage thread: the desktop reads its own focus log. [day] is read once, so minutes and their day agree at midnight.
    private fun todayByName(day: LocalDate): Map<String, Long> {
        val usage = app.platform.usage
        if (!usage.permitted()) return emptyMap()
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant()
        val now = maxOf(app.clock.now(), start)
        return usage.perApp(start, minOf(now, day.plusDays(1).atStartOfDay(zone).toInstant()))
            .filterKeys { it.value != app.platform.selfPackage }
            .entries.groupBy({ app.platform.appLabel(it.key) }, { it.value.toMinutes() })
            .mapValues { it.value.sum() }
            .filterValues { it > 0 }
    }

    private fun link(): DeviceLink? {
        if (!loaded) {
            // A keyring that cannot be reached means not linked for now; storage reports that problem itself.
            current = runCatching { secrets.get(SECRET) }.getOrElse { return null }?.decodeToString()?.let(DeviceLink::fromCode)
            loaded = true
        }
        return current
    }

    private fun today(): LocalDate = app.clock.now().atZone(ZoneId.systemDefault()).toLocalDate()

    private companion object {
        const val PREFERENCES = "organiza_devices"
        const val DEVICE = "device"
        const val CURSOR = "cursor"
        const val SECRET = "devices-secret"
        const val FIRST_S = 30L
        const val INTERVAL_S = 20 * 60L
    }
}
