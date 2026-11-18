package org.organiza.storage

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.testing.JdbcDatabase

class DeviceUsageStoreTest {
    private val store = DeviceUsageStore(JdbcDatabase().also { Migrations.migrate(it) })
    private val day = LocalDate.parse("2026-11-16")

    @Test
    fun `a later report from a device replaces that day, and ranges read every device`() {
        store.put("phone", day, listOf(DeviceAppMinutes("Instagram", "Social", 40)))
        store.put("phone", day, listOf(DeviceAppMinutes("Instagram", "Social", 55), DeviceAppMinutes("Maps", "Travel", 5)))
        store.put("laptop", day.minusDays(1), listOf(DeviceAppMinutes("Discord", "Communication", 30)))
        store.put("laptop", day.minusDays(9), listOf(DeviceAppMinutes("Discord", "Communication", 99)))
        assertEquals(
            mapOf(
                day to listOf(DeviceAppMinutes("Instagram", "Social", 55), DeviceAppMinutes("Maps", "Travel", 5)),
                day.minusDays(1) to listOf(DeviceAppMinutes("Discord", "Communication", 30)),
            ),
            store.between(day.minusDays(6), day),
        )
    }

    @Test
    fun `this device's own days are kept apart from the other devices'`() {
        store.put(DeviceUsageStore.SELF, day, listOf(DeviceAppMinutes("Chess", "Games", 20)))
        store.put("laptop", day, listOf(DeviceAppMinutes("Discord", "Communication", 30)))
        assertEquals(mapOf(day to listOf(DeviceAppMinutes("Chess", "Games", 20))), store.between(day, day, mine = true))
        assertEquals(mapOf(day to listOf(DeviceAppMinutes("Discord", "Communication", 30))), store.between(day, day))
    }
}
