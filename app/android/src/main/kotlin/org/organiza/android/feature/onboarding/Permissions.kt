package org.organiza.android.feature.onboarding

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.PowerManager
import android.provider.Settings
import org.organiza.android.OrganizaApp
import org.organiza.domain.port.Capability
import org.organiza.presentation.Permission
import org.organiza.presentation.Protection

/** Reads which permissions are granted, and names the screen that grants each one. */
object Permissions {
    fun granted(app: OrganizaApp): Set<Permission> = buildSet {
        val capabilities = app.granted()
        if (Capability.ACCESSIBILITY in capabilities) add(Permission.ACCESSIBILITY)
        if (Capability.USAGE_STATS in capabilities) add(Permission.USAGE)
        if (Capability.DEVICE_ADMIN in capabilities) add(Permission.DEVICE_ADMIN)
        if (VpnService.prepare(app) == null) add(Permission.VPN)
        if (batteryExempt(app)) add(Permission.BATTERY)
    }

    fun batteryExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** The settings screen for [permission], or null for VPN consent and the pause screen, which have none. */
    fun settingsIntent(context: Context, permission: Permission): Intent? = when (permission) {
        Permission.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        Permission.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        Permission.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        Permission.DEVICE_ADMIN -> Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, OrganizaApp.adminComponent(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, Permission.DEVICE_ADMIN.why)
        Permission.VPN, Permission.OVERLAY -> null
    }

    /** Some phones refuse the direct battery request, so the general list is the fallback. */
    val batteryFallback: Intent get() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun forProtection(protection: Protection): Permission = when (protection) {
        Protection.ACCESSIBILITY -> Permission.ACCESSIBILITY
        Protection.VPN_CONSENT -> Permission.VPN
        Protection.BATTERY -> Permission.BATTERY
        Protection.DEVICE_ADMIN -> Permission.DEVICE_ADMIN
        Protection.USAGE -> Permission.USAGE
    }
}
