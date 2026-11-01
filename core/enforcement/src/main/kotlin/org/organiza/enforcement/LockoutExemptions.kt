package org.organiza.enforcement

import org.organiza.domain.PackageName
import org.organiza.domain.Parsed

/** Apps that must stay reachable whatever a block list says (ADR-0007). */
public object LockoutExemptions {
    private val EMERGENCY_PACKAGES = listOf(
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.emergency",
    )

    /**
     * Settings registers a fallback HOME activity for use during boot, so it appears among
     * [homeApps] on stock Android. It is removed here, or it could never be blocked or guarded.
     */
    public fun of(self: String, homeApps: List<String>, dialer: String?): Set<PackageName> {
        val launchers = homeApps.filterNot { name ->
            (PackageName.parse(name) as? Parsed.Valid)?.value?.let(ContentPolicy::isGuarded) == true
        }
        return (launchers + listOfNotNull(dialer) + EMERGENCY_PACKAGES + self)
            .mapNotNull { (PackageName.parse(it) as? Parsed.Valid)?.value }
            .toSet()
    }
}
