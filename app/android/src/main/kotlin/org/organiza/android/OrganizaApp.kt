package org.organiza.android

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.time.Instant
import org.organiza.domain.port.Capability
import org.organiza.domain.port.Clock
import org.organiza.domain.port.EnforcementCapabilities
import org.organiza.enforcement.ActiveSession
import org.organiza.enforcement.SessionPolicy
import org.organiza.enforcement.TamperEvent
import org.organiza.enforcement.TamperMonitor

class OrganizaApp : Application(), EnforcementCapabilities {
    val session = ActiveSession()
    val clock = Clock(Instant::now)

    @Volatile var vpnRunning = false

    private val tamper by lazy { TamperMonitor(this, clock) }

    override fun onCreate() {
        super.onCreate()
        // The system does not reliably call onUnbind when the service is switched off, so
        // the setting itself is watched and the OS is asked directly in granted().
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = checkTamper()
            },
        )
    }

    private fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
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
    }

    fun deviceAdminActive(): Boolean =
        getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent(this))

    fun startSession(policy: SessionPolicy) {
        session.set(policy)
        synchronized(tamper) { tamper.arm() }
        if (policy.blockList.domains.isNotEmpty() && VpnService.prepare(this) == null) {
            startService(Intent(this, DnsVpnService::class.java))
        }
    }

    fun stopSession() {
        synchronized(tamper) { tamper.disarm() }
        session.set(SessionPolicy.IDLE)
        startService(Intent(this, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_STOP))
    }

    /** Called whenever the OS takes a capability away. */
    fun checkTamper() {
        synchronized(tamper) { tamper.check() }
    }

    fun tamperEvents(): List<TamperEvent> = synchronized(tamper) { tamper.events }

    companion object {
        fun from(context: Context): OrganizaApp = context.applicationContext as OrganizaApp

        fun adminComponent(context: Context) = ComponentName(context, OrganizaDeviceAdminReceiver::class.java)
    }
}
