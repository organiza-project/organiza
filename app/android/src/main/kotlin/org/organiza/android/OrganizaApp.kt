package org.organiza.android

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.database.ContentObserver
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import java.time.Duration
import org.organiza.ai.ApiKeyStore
import org.organiza.android.data.AndroidUsageHistory
import org.organiza.android.data.DatabaseKey
import org.organiza.android.data.KeystoreApiKeyStore
import org.organiza.android.data.KeystoreSecretStore
import org.organiza.android.data.SqlCipherDatabase
import org.organiza.android.data.StorageException
import org.organiza.android.feature.blocker.DnsVpnService
import org.organiza.android.feature.blocker.OrganizaAccessibilityService
import org.organiza.android.feature.blocker.OrganizaDeviceAdminReceiver
import org.organiza.android.feature.habits.Reminders
import org.organiza.android.feature.onboarding.Permissions
import org.organiza.app.InstalledApp
import org.organiza.app.OrganizaCore
import org.organiza.app.Platform
import org.organiza.app.Prefs
import org.organiza.app.StorageUnavailable
import org.organiza.arena.SecretStore
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.port.Capability
import org.organiza.enforcement.SessionPolicy
import org.organiza.presentation.Device
import org.organiza.presentation.Permission
import org.organiza.storage.Database
import org.organiza.storage.HabitReminder
import org.organiza.voice.VoiceCommand

/** The Android process: it holds the shared [core] and is its [Platform] (ADR-0025). */
class OrganizaApp : Application(), Platform {
    val core by lazy { OrganizaCore(this) }

    @Volatile var vpnRunning = false

    private val main by lazy { Handler(Looper.getMainLooper()) }

    override val device: Device = Device.PHONE
    override val debug: Boolean get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    override val selfPackage: String get() = packageName
    override val hostedUrl: String = BuildConfig.SUPABASE_URL
    override val hostedKey: String = BuildConfig.SUPABASE_ANON_KEY
    override val usage by lazy { AndroidUsageHistory(this) }
    override val secrets: SecretStore by lazy { KeystoreSecretStore(this) }
    override val apiKeys: ApiKeyStore by lazy { KeystoreApiKeyStore(this) }

    override fun onCreate() {
        super.onCreate()
        // The system does not reliably call onUnbind when the service is switched off, so
        // the setting itself is watched and the OS is asked directly in granted().
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) = core.checkTamper()
            },
        )
        core.start()
    }

    override fun prefs(name: String): Prefs {
        val shared = getSharedPreferences(name, MODE_PRIVATE)
        return object : Prefs {
            override fun string(key: String): String? = shared.all[key]?.toString()

            override fun put(values: Map<String, String?>) {
                val editor = shared.edit()
                values.forEach { (key, value) -> if (value == null) editor.remove(key) else editor.putString(key, value) }
                editor.apply()
            }

            override fun clear() = shared.edit().clear().apply()
        }
    }

    override fun openDatabase(): Database {
        val file = getDatabasePath(DATABASE_NAME)
        file.parentFile?.mkdirs()
        return try {
            val key = DatabaseKey(this, file).load()
            try {
                SqlCipherDatabase.open(file, key)
            } finally {
                key.fill(0)
            }
        } catch (failure: StorageException) {
            throw StorageUnavailable(failure.failure.name, failure)
        }
    }

    private var launchableCache: List<InstalledApp>? = null

    /** Package, label and the store category each app declares, or Other. */
    override fun launchable(): List<InstalledApp> = launchableCache ?: run {
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(main, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val category = ApplicationInfo.getCategoryTitle(this, info.activityInfo.applicationInfo.category)?.toString() ?: "Other"
                InstalledApp(info.activityInfo.packageName, info.loadLabel(pm).toString(), category)
            }
            .also { launchableCache = it }
    }

    override fun appLabel(app: PackageName): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(app.value, 0)).toString()
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        app.value
    }

    private fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val self = ComponentName(this, OrganizaAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
    }

    override fun granted(): Set<Capability> = buildSet {
        if (accessibilityEnabled()) {
            add(Capability.ACCESSIBILITY)
            add(Capability.OVERLAY)
        }
        if (vpnRunning) add(Capability.VPN)
        if (deviceAdminActive()) add(Capability.DEVICE_ADMIN)
        if (usage.permitted()) add(Capability.USAGE_STATS)
    }

    override fun permissions(): Set<Permission> = Permissions.granted(this)

    override fun siteBlockingAllowed(): Boolean = VpnService.prepare(this) == null

    override fun batteryExempt(): Boolean = Permissions.batteryExempt(this)

    fun deviceAdminActive(): Boolean = getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent(this))

    override fun lockdownApps(): Set<PackageName> =
        listOfNotNull(Telephony.Sms.getDefaultSmsPackage(this)).mapNotNull { (PackageName.parse(it) as? Parsed.Valid)?.value }.toSet()

    override fun applyPolicy(policy: SessionPolicy) {
        val wanted = policy.blockList.domains.isNotEmpty() && siteBlockingAllowed()
        try {
            if (wanted && !vpnRunning) {
                startService(Intent(this, DnsVpnService::class.java))
            } else if (!wanted && vpnRunning) {
                startService(Intent(this, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_STOP))
            }
        } catch (_: IllegalStateException) {
            // Android refused a background start. The next tick tries again.
            core.showMessage("Could not change the VPN yet")
        }
    }

    override fun scheduleReminders(reminders: List<HabitReminder>, previous: Set<String>) = Reminders.schedule(this, reminders, previous)

    /** Hands a meeting to the phone's calendar app, which shows it for saving. Nothing is stored here. */
    override fun openCalendar(event: VoiceCommand.AddEvent): String {
        val end = event.start.plus(Duration.ofMinutes(event.minutes.toLong()))
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, event.title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start.toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.toEpochMilli())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            "Opened your calendar. Save the event there"
        } catch (_: ActivityNotFoundException) {
            "No calendar app on this phone can take the event"
        }
    }

    override fun onMain(work: () -> Unit) {
        main.post(work)
    }

    override fun log(message: String) {
        Log.e(TAG, message)
    }

    override fun debugLine(line: String) {
        Log.i("OrganizaDebug", line)
    }

    companion object {
        private const val TAG = "Organiza"
        private const val DATABASE_NAME = "organiza.db"

        fun from(context: Context): OrganizaApp = context.applicationContext as OrganizaApp

        fun adminComponent(context: Context) = ComponentName(context, OrganizaDeviceAdminReceiver::class.java)
    }
}
