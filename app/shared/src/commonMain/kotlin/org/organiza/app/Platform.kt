package org.organiza.app

import java.time.Duration
import java.time.Instant
import org.organiza.ai.ApiKeyStore
import org.organiza.arena.SecretStore
import org.organiza.domain.PackageName
import org.organiza.domain.port.Capability
import org.organiza.domain.port.UsageHistory
import org.organiza.enforcement.SessionPolicy
import org.organiza.presentation.Device
import org.organiza.presentation.Permission
import org.organiza.storage.Database
import org.organiza.storage.HabitReminder
import org.organiza.voice.VoiceCommand

/**
 * Storage that could not be opened. The database is never deleted or recreated (ADR-0008).
 * Unchecked, so a keyring that fails later still reaches the storage thread's error handler.
 */
class StorageUnavailable(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Foreground time per app, and whether the OS lets this app read it. */
interface AppUsage : UsageHistory {
    fun permitted(): Boolean

    /** Every app's foreground time in the window, the sum of which is screen time. */
    fun perApp(from: Instant, to: Instant): Map<PackageName, Duration>

    override fun foregroundTime(app: PackageName, from: Instant, to: Instant): Duration = perApp(from, to)[app] ?: Duration.ZERO
}

/** Small values outside the database, such as whether onboarding is done. */
interface Prefs {
    fun string(key: String): String?

    fun flag(key: String): Boolean = string(key) == "true"

    /** Writes every pair; a null value removes the key. */
    fun put(values: Map<String, String?>)

    fun clear()
}

/** An app the person can launch: package, label and category. */
data class InstalledApp(val pkg: String, val label: String, val category: String)

/**
 * What [OrganizaCore] needs from the OS it runs on (ADR-0025). Android and the Linux desktop each
 * implement it; every decision stays in the core.
 */
interface Platform {
    /** Which permissions and protections this OS offers. */
    val device: Device

    /** Debug builds may use plain HTTP relays. */
    val debug: Boolean

    /** This app's own package, which never counts as screen time. */
    val selfPackage: String

    /** The hosted Supabase project of this build, or blanks when it has none. */
    val hostedUrl: String
    val hostedKey: String

    val usage: AppUsage
    val secrets: SecretStore
    val apiKeys: ApiKeyStore

    fun prefs(name: String): Prefs

    /** Throws [StorageUnavailable]. Called once, on the storage thread. */
    fun openDatabase(): Database

    fun launchable(): List<InstalledApp>

    /** The installed app's name, or its package when it is not installed. */
    fun appLabel(app: PackageName): String

    fun granted(): Set<Capability>

    /** The onboarding permissions this OS has granted. */
    fun permissions(): Set<Permission>

    /** Whether site blocking has been allowed, apart from whether it runs now. */
    fun siteBlockingAllowed(): Boolean

    fun batteryExempt(): Boolean

    /** What lockdown leaves open besides the always-exempt apps (ADR-0007). */
    fun lockdownApps(): Set<PackageName>

    /** Puts the policy in force outside the app's own process, such as the DNS layer. */
    fun applyPolicy(policy: SessionPolicy)

    fun scheduleReminders(reminders: List<HabitReminder>, previous: Set<String>)

    /** Hands a meeting to the person's calendar app and says what happened. */
    fun openCalendar(event: VoiceCommand.AddEvent): String

    /** Runs [work] on the UI thread. */
    fun onMain(work: () -> Unit)

    /** An error line for the local log. It must never hold user data. */
    fun log(message: String)

    /** A state line for the debug checks. */
    fun debugLine(line: String)
}
