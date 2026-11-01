package org.organiza.enforcement

import kotlin.test.Test
import kotlin.test.assertEquals

class LockoutExemptionsTest {
    private val self = "org.organiza.app"

    private fun exempt(homeApps: List<String> = emptyList(), dialer: String? = null) =
        LockoutExemptions.of(self, homeApps, dialer).map { it.value }.toSet()

    @Test
    fun `launchers dialers and the app itself are exempt`() {
        val exempt = exempt(listOf("com.google.android.apps.nexuslauncher"), "com.acme.dialer")
        assertEquals(true, "com.google.android.apps.nexuslauncher" in exempt)
        assertEquals(true, "com.acme.dialer" in exempt)
        assertEquals(true, self in exempt)
    }

    @Test
    fun `emergency packages are always exempt`() {
        assertEquals(true, "com.android.phone" in exempt())
        assertEquals(true, "com.android.emergency" in exempt())
    }

    @Test
    fun `settings is not exempt despite registering a fallback home activity`() {
        val exempt = exempt(listOf("com.android.settings", "com.google.android.apps.nexuslauncher"))
        assertEquals(false, "com.android.settings" in exempt)
    }
}
