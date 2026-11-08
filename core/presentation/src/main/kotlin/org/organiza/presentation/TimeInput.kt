package org.organiza.presentation

/** Reads typed lengths and clock times, and writes them back the way people say them. */
public object TimeInput {
    private val DIGITS = Regex("\\d{1,4}")

    /** Whole minutes typed on the number pad, or null when empty or outside [range]. */
    public fun minutes(raw: String, range: IntRange): Int? =
        raw.trim().takeIf(DIGITS::matches)?.toInt()?.takeIf { it in range }

    /**
     * The minute of the day from an hour and a minute box. [pm] is null on a 24 hour clock; on a
     * 12 hour clock hours run 1 to 12 and 12 AM is midnight. An empty minute box means on the hour.
     */
    public fun clock(hour: String, minute: String, pm: Boolean?): Int? {
        val h = hour.trim().takeIf(DIGITS::matches)?.toInt() ?: return null
        val m = if (minute.isBlank()) 0 else minute.trim().takeIf(DIGITS::matches)?.toInt() ?: return null
        if (m !in 0..59) return null
        val hours = when (pm) {
            null -> h.takeIf { it in 0..23 }
            else -> h.takeIf { it in 1..12 }?.let { it % 12 + if (pm) 12 else 0 }
        } ?: return null
        return hours * 60 + m
    }

    public fun label(minuteOfDay: Int, twelveHour: Boolean): String {
        val h = minuteOfDay / 60
        val m = minuteOfDay % 60
        if (!twelveHour) return "%02d:%02d".format(h, m)
        return "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
    }

    public fun length(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        val parts = listOfNotNull(
            Format.count(hours, "hour", "hours").takeIf { hours > 0 },
            Format.count(rest, "minute", "minutes").takeIf { rest > 0 || hours == 0 },
        )
        return parts.joinToString(" ")
    }

    /**
     * What a box holds after the first keystroke since it took focus: only what was typed, so a
     * new number replaces the old one wherever the tap left the cursor. A deletion empties it, and
     * a paste or whole replacement is kept as it is.
     */
    public fun firstKeystroke(old: String, new: String): String {
        val prefix = old.commonPrefixWith(new).length
        val suffix = old.substring(prefix).commonSuffixWith(new.substring(prefix)).length
        val inserted = new.substring(prefix, new.length - suffix)
        val replaced = old.length - suffix > prefix
        return if (replaced && inserted.isNotEmpty()) new else inserted
    }
}
