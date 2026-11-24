package org.organiza.presentation

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageReportTest {
    private val monday = LocalDate.parse("2026-11-16")
    private val days = mapOf(
        monday to listOf(AppMinutes("Instagram", "Social", 50), AppMinutes("WhatsApp", "Communication", 30)),
        monday.minusDays(1) to listOf(AppMinutes("Instagram", "Social", 70), AppMinutes("YouTube", "Video", 20)),
        monday.minusDays(5) to listOf(AppMinutes("YouTube", "Video", 40)),
    )

    @Test
    fun `a range is one bar a day, with categories and apps ranked by time`() {
        val report = UsageReport.build(days, monday.minusDays(6), monday, monday)
        assertEquals("10 Nov – 16 Nov", report.title)
        assertEquals("3h 30m", report.total)
        assertEquals(7, report.bars.size)
        assertEquals(listOf(0L, 40, 0, 0, 0, 90, 80), report.bars.map { it.minutes })
        assertEquals("Tue" to "Mon", report.bars.first().label to report.bars.last().label)
        assertEquals(listOf("Social" to 120L, "Video" to 60L, "Communication" to 30L), report.categories.map { it.category to it.minutes })
        assertEquals("Instagram", report.apps.first().label)
        assertEquals(true, report.bars.last().today)
    }

    @Test
    fun `one day is titled by its date and counts only that day`() {
        val report = UsageReport.build(days, monday, monday, monday)
        assertEquals("Today", report.title)
        assertEquals("1h 20m", report.total)
        assertEquals(listOf("Instagram", "WhatsApp"), report.apps.map { it.label })
    }

    @Test
    fun `a range shows its daily average, counting only days up to today`() {
        // 210 minutes over the seven days of the week that have happened.
        assertEquals("30m a day", UsageReport.build(days, monday.minusDays(6), monday, monday).average)
        // A week still running divides by the days so far: Monday alone is 80 minutes.
        assertEquals("1h 20m a day", UsageReport.build(days, monday, monday.plusDays(6), monday).average)
    }

    @Test
    fun `the chart's gridlines fall on round times above the tallest bar`() {
        assertEquals(listOf(15L, 30L), UsageReport.ticks(25))
        assertEquals(listOf(30L, 60L, 90L), UsageReport.ticks(90))
        assertEquals(listOf(120L, 240L, 360L), UsageReport.ticks(330))
        assertEquals(listOf(15L), UsageReport.ticks(0))
    }
}
