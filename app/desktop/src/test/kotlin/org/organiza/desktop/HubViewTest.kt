package org.organiza.desktop

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.app.InstalledApp
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.feature.blocker.hubView

class HubViewTest {
    private fun pkg(raw: String) = (PackageName.parse(raw) as Parsed.Valid).value

    @Test
    fun `a limit counts time on other devices, while screen time stays this device's`() {
        val apps = listOf(InstalledApp("linux.discord", "Discord", "Social"), InstalledApp("linux.maps", "Maps", "Travel"))
        val today = mapOf("linux.discord" to Duration.ofMinutes(50), "linux.maps" to Duration.ofMinutes(10))
        val view = hubView(
            apps, today, emptyList(), mapOf(pkg("linux.discord") to Duration.ofMinutes(60)), emptySet(), allowed = true,
        ) { Duration.ofMinutes(20) }
        val discord = view.apps.first { it.label == "Discord" }
        assertEquals(70, discord.usedMinutes)
        assertEquals(true, discord.locked)
        // An app without a limit shows only this device's time.
        assertEquals(10, view.apps.first { it.label == "Maps" }.usedMinutes)
        assertEquals("1 h 00 m", view.screenTime)
    }
}
