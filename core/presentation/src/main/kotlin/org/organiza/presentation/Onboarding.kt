package org.organiza.presentation

public enum class OnboardingStep { WELCOME, PERMISSIONS, BACKEND, AI, DONE }

/** What each permission is for, and what to do when it cannot be granted. */
public enum class Permission(public val title: String, public val why: String, public val fallback: String) {
    ACCESSIBILITY(
        "App blocking",
        "Lets Organiza see which app is open, so it can pause you before a blocked one. It reads web addresses only " +
            "in browsers, and screen text only on system settings pages during strict sessions. Nothing is stored or sent.",
        "On Android 13 and later, an app installed outside a store may need \"Allow restricted settings\" first: open " +
            "Organiza's App info, tap the menu in the corner, allow it, then come back.",
    ),
    OVERLAY(
        "Pause screen",
        "The screen that asks you to pause before a blocked app. It is drawn by the app blocking service.",
        "Nothing extra to allow. It comes with app blocking.",
    ),
    BATTERY(
        "Keep running",
        "Stops Android putting Organiza to sleep, so scheduled sessions start on time.",
        "Some phones add their own battery limits. Look for Organiza under background activity or app launch in Settings.",
    ),
    VPN(
        "Website blocking",
        "A DNS filter that runs on this phone. Blocked sites get no answer, and no traffic leaves through it.",
        "Only one VPN can run at a time. Without it, sites are still blocked in supported browsers through the address bar.",
    ),
    USAGE(
        "Usage access",
        "Lets habits such as \"under 30 minutes of YouTube\" be judged on this phone, from daily totals for the apps you name.",
        "Without it, habits are marked by hand.",
    ),
    DEVICE_ADMIN(
        "Uninstall protection",
        "Means Organiza cannot be removed in one step. You can always turn it off in Settings.",
        "Optional. Blocking works without it.",
    ),
    TRACKING(
        "Usage tracking",
        "Lets Organiza see which app is in front, so it can count screen time and pause you before a blocked app. " +
            "It reads the app's name only, never window titles, and nothing leaves this computer.",
        "On GNOME this adds a small shell extension, which starts after you log out and back in once.",
    ),
    AUTOSTART(
        "Start at login",
        "Runs Organiza in the background from login, so scheduled sessions start on time. If it is closed by force, it starts again.",
        "Without it, sessions only start while Organiza is open.",
    ),
    TRAY(
        "Keep running when closed",
        "Closing the window leaves Organiza in the top bar, so tracking and blocking carry on.",
        "On GNOME the top bar icon needs the AppIndicator extension, which Ubuntu includes.",
    ),
    SHORTCUT(
        "Copilot shortcut",
        "Super+Shift+Space opens the copilot from anywhere, so you can plan without leaving what you are doing.",
        "Outside GNOME, bind organiza --copilot to a key in your keyboard settings.",
    ),
}

/** The permissions and protections a kind of device offers, in the order they are shown (ADR-0025). */
public enum class Device(public val permissions: List<Permission>, public val protections: List<Protection>) {
    PHONE(
        listOf(Permission.ACCESSIBILITY, Permission.OVERLAY, Permission.BATTERY, Permission.VPN, Permission.USAGE, Permission.DEVICE_ADMIN),
        listOf(Protection.ACCESSIBILITY, Protection.VPN_CONSENT, Protection.BATTERY, Protection.DEVICE_ADMIN, Protection.USAGE),
    ),
    DESKTOP(listOf(Permission.TRACKING, Permission.AUTOSTART, Permission.TRAY, Permission.SHORTCUT), listOf(Protection.TRACKING, Protection.AUTOSTART)),
    ;

    /** Blocking needs this one; the rest are optional. */
    public val required: Permission get() = permissions.first()
}

/** [grantable] is false for a row that comes with another permission and has no button. */
public data class PermissionRow(val permission: Permission, val granted: Boolean, val required: Boolean, val grantable: Boolean)

/** The first-run flow (ADR-0014): welcome, permissions, backend, optional AI key. */
public object Onboarding {
    private val VISIBLE = OnboardingStep.entries - OnboardingStep.DONE

    public fun next(step: OnboardingStep): OnboardingStep =
        OnboardingStep.entries.getOrElse(step.ordinal + 1) { OnboardingStep.DONE }

    public fun back(step: OnboardingStep): OnboardingStep =
        OnboardingStep.entries.getOrElse(step.ordinal - 1) { OnboardingStep.WELCOME }

    public fun progress(step: OnboardingStep): String = "Step ${VISIBLE.indexOf(step) + 1} of ${VISIBLE.size}"

    public fun permissions(granted: Set<Permission>, device: Device = Device.PHONE): List<PermissionRow> = device.permissions.map { permission ->
        val on = if (permission == Permission.OVERLAY) Permission.ACCESSIBILITY in granted else permission in granted
        PermissionRow(permission, on, required = permission == device.required, grantable = permission != Permission.OVERLAY)
    }

    public fun continueLabel(step: OnboardingStep, granted: Set<Permission>, device: Device = Device.PHONE): String = when (step) {
        OnboardingStep.WELCOME -> "Start"
        OnboardingStep.PERMISSIONS -> if (device.required in granted) "Continue" else "Continue without blocking"
        OnboardingStep.BACKEND -> "Continue"
        OnboardingStep.AI -> "Skip for now"
        OnboardingStep.DONE -> "Done"
    }
}
