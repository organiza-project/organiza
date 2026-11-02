package org.organiza.presentation

import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

public object Format {
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

    /** Rounds up, so a session with seconds left never reads as over. */
    public fun duration(duration: Duration): String {
        if (duration.isNegative || duration.isZero) return "0 m"
        val minutes = (duration.seconds + 59) / 60
        return if (minutes < 60) "$minutes m" else "${minutes / 60} h %02d m".format(minutes % 60)
    }

    public fun count(count: Int, one: String, many: String): String = "$count ${if (count == 1) one else many}"

    public fun dayMonth(date: LocalDate): String = date.format(DAY_MONTH)
}
