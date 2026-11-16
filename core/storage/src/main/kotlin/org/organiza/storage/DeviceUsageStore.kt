package org.organiza.storage

import java.time.LocalDate

public data class DeviceAppMinutes(val app: String, val category: String, val minutes: Long)

/** Other devices' daily minutes per app (ADR-0031). Each report replaces its device's whole day. */
public class DeviceUsageStore(private val db: Database) {
    public fun put(device: String, day: LocalDate, apps: List<DeviceAppMinutes>) {
        db.transaction {
            db.execute("DELETE FROM device_usage WHERE device = ? AND day = ?", listOf(device, day.toString()))
            apps.forEach { db.execute("INSERT INTO device_usage (device, day, app, category, minutes) VALUES (?, ?, ?, ?, ?)", listOf(device, day.toString(), it.app, it.category, it.minutes)) }
        }
    }

    /** Every device's minutes from [from] to [to], by day. */
    public fun between(from: LocalDate, to: LocalDate): Map<LocalDate, List<DeviceAppMinutes>> = db.query(
        "SELECT day, app, category, minutes FROM device_usage WHERE day BETWEEN ? AND ? ORDER BY day DESC, minutes DESC, app",
        listOf(from.toString(), to.toString()),
    ) { row -> LocalDate.parse(row.string(0)) to DeviceAppMinutes(row.string(1).orEmpty(), row.string(2).orEmpty(), row.long(3) ?: 0) }
        .groupBy({ it.first }, { it.second })
}
