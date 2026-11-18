package org.organiza.storage

import java.time.LocalDate

public data class DeviceAppMinutes(val app: String, val category: String, val minutes: Long)

/**
 * Daily minutes per app (ADR-0031): other devices' reports, and this device's own finished days under
 * [SELF], kept because Android forgets app usage after about a week. Each put replaces that device's day.
 */
public class DeviceUsageStore(private val db: Database) {
    public fun put(device: String, day: LocalDate, apps: List<DeviceAppMinutes>) {
        db.transaction {
            db.execute("DELETE FROM device_usage WHERE device = ? AND day = ?", listOf(device, day.toString()))
            apps.forEach { db.execute("INSERT INTO device_usage (device, day, app, category, minutes) VALUES (?, ?, ?, ?, ?)", listOf(device, day.toString(), it.app, it.category, it.minutes)) }
        }
    }

    /** Minutes from [from] to [to], by day: the other devices', or with [mine] this device's own. */
    public fun between(from: LocalDate, to: LocalDate, mine: Boolean = false): Map<LocalDate, List<DeviceAppMinutes>> = db.query(
        "SELECT day, app, category, minutes FROM device_usage WHERE day BETWEEN ? AND ? AND (device = ?) = ? " +
            "ORDER BY day DESC, minutes DESC, app",
        listOf(from.toString(), to.toString(), SELF, if (mine) 1L else 0L),
    ) { row -> LocalDate.parse(row.string(0)) to DeviceAppMinutes(row.string(1).orEmpty(), row.string(2).orEmpty(), row.long(3) ?: 0) }
        .groupBy({ it.first }, { it.second })

    public companion object {
        /** The device name of this device's own rows; reports carry random or hashed ids, never this. */
        public const val SELF: String = "this-device"
    }
}
