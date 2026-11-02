package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CalendarRulesTest {
    private val monday = DayOfWeek.MONDAY
    private val sunday = DayOfWeek.SUNDAY
    private val november2026 = YearMonth.of(2026, 11) // 1 Nov is a Sunday
    private val february2027 = YearMonth.of(2027, 2) // 1 Feb is a Monday

    private val saoPaulo = ZoneId.of("America/Sao_Paulo") // UTC-3, no daylight saving

    // monthGrid

    @Test
    fun `every row has seven cells`() {
        for (month in listOf(november2026, february2027, YearMonth.of(2026, 3), YearMonth.of(2027, 8))) {
            for (first in listOf(monday, sunday)) {
                assertEquals("$month from $first", 0, CalendarRules.monthGrid(month, first).size % 7)
            }
        }
    }

    @Test
    fun `a month has between four and six rows`() {
        for (year in 2026..2027) {
            for (m in 1..12) {
                for (first in listOf(monday, sunday)) {
                    val rows = CalendarRules.monthGrid(YearMonth.of(year, m), first).size / 7
                    assertTrue("$year-$m from $first has $rows rows", rows in 4..6)
                }
            }
        }
    }

    @Test
    fun `february 2027 from a Monday takes exactly four rows with no trailing row`() {
        val grid = CalendarRules.monthGrid(february2027, monday)

        assertEquals(28, grid.size)
        assertEquals(LocalDate.of(2027, 2, 1), grid.first())
        assertEquals(LocalDate.of(2027, 2, 28), grid.last())
    }

    @Test
    fun `february 2027 from a Sunday takes five rows`() {
        assertEquals(5, CalendarRules.monthGrid(february2027, sunday).size / 7)
    }

    @Test
    fun `november 2026 from a Monday has its Sunday the 1st in the first row's last cell`() {
        val grid = CalendarRules.monthGrid(november2026, monday)

        assertEquals(6, grid.size / 7)
        assertNull(grid[0])
        assertNull(grid[5])
        assertEquals(LocalDate.of(2026, 11, 1), grid[6])
    }

    @Test
    fun `november 2026 from a Sunday starts with the 1st`() {
        val grid = CalendarRules.monthGrid(november2026, sunday)

        assertEquals(5, grid.size / 7)
        assertEquals(LocalDate.of(2026, 11, 1), grid.first())
    }

    @Test
    fun `the days of the month come in order and everything else is null`() {
        for (month in listOf(november2026, february2027, YearMonth.of(2026, 2))) {
            for (first in listOf(monday, sunday)) {
                val grid = CalendarRules.monthGrid(month, first)
                val days = grid.filterNotNull()
                val expected = (1..month.lengthOfMonth()).map { month.atDay(it) }

                assertEquals("$month from $first", expected, days)
                assertTrue(grid.all { it == null || YearMonth.from(it) == month })
            }
        }
    }

    @Test
    fun `each cell is on the weekday its column shows`() {
        for (first in listOf(monday, sunday)) {
            val grid = CalendarRules.monthGrid(november2026, first)
            grid.forEachIndexed { index, date ->
                if (date != null) {
                    assertEquals("cell $index from $first", first.plus((index % 7).toLong()), date.dayOfWeek)
                }
            }
        }
    }

    @Test
    fun `no row is empty`() {
        for (year in 2026..2027) {
            for (m in 1..12) {
                val grid = CalendarRules.monthGrid(YearMonth.of(year, m), monday)
                grid.chunked(7).forEach { row -> assertTrue("$year-$m", row.any { it != null }) }
            }
        }
    }

    // dayLabel

    private val planId = 1L

    private fun routine(id: Long, shortName: String) =
        Routine(id = id, planId = planId, name = "Routine $shortName", shortName = shortName, position = id.toInt(), weekdays = emptySet())

    private fun info(startedAt: String, routine: Routine?): SessionInfo {
        val start = Instant.parse(startedAt)
        val session = Session(id = start.epochSecond, startedAt = start, endedAt = start, routineId = routine?.id, note = null)
        return SessionInfo(session, routine, machineCount = 1, setCount = 1)
    }

    @Test
    fun `the day shows the routine of its only session`() {
        assertEquals("B", CalendarRules.dayLabel(listOf(info("2026-11-02T18:00:00Z", routine(2, "B")))))
    }

    @Test
    fun `the first session by start time gives the label, whatever the list order`() {
        val later = info("2026-11-02T20:00:00Z", routine(3, "C"))
        val earlier = info("2026-11-02T08:00:00Z", routine(1, "A"))

        assertEquals("A", CalendarRules.dayLabel(listOf(later, earlier)))
        assertEquals("A", CalendarRules.dayLabel(listOf(earlier, later)))
    }

    @Test
    fun `a session without a routine is skipped for the label`() {
        val free = info("2026-11-02T08:00:00Z", null)
        val b = info("2026-11-02T18:00:00Z", routine(2, "B"))

        assertEquals("B", CalendarRules.dayLabel(listOf(free, b)))
    }

    @Test
    fun `a day with only free sessions shows a dot`() {
        val free = info("2026-11-02T08:00:00Z", null)

        assertEquals("•", CalendarRules.dayLabel(listOf(free)))
    }

    @Test
    fun `no sessions shows a dot`() {
        assertEquals("•", CalendarRules.dayLabel(emptyList()))
    }

    // addedSessionStart

    private fun local(date: String, time: String, zone: ZoneId = saoPaulo): Instant =
        LocalDate.parse(date).atTime(java.time.LocalTime.parse(time)).atZone(zone).toInstant()

    @Test
    fun `a session added on a past day starts at 18 00 that day`() {
        val now = ZonedDateTime.of(2026, 11, 2, 10, 0, 0, 0, saoPaulo)

        assertEquals(local("2026-10-20", "18:00"), CalendarRules.addedSessionStart(LocalDate.of(2026, 10, 20), now))
        assertEquals(Instant.parse("2026-10-20T21:00:00Z"), CalendarRules.addedSessionStart(LocalDate.of(2026, 10, 20), now))
    }

    @Test
    fun `a session added on a future day also starts at 18 00 that day`() {
        val now = ZonedDateTime.of(2026, 11, 2, 10, 0, 0, 0, saoPaulo)

        assertEquals(Instant.parse("2026-11-10T21:00:00Z"), CalendarRules.addedSessionStart(LocalDate.of(2026, 11, 10), now))
    }

    @Test
    fun `18 00 is in the zone of now`() {
        val now = ZonedDateTime.of(2026, 11, 2, 10, 0, 0, 0, ZoneOffset.UTC)

        assertEquals(Instant.parse("2026-10-20T18:00:00Z"), CalendarRules.addedSessionStart(LocalDate.of(2026, 10, 20), now))
    }

    @Test
    fun `today starts one hour before now`() {
        val now = ZonedDateTime.of(2026, 11, 2, 20, 30, 0, 0, saoPaulo)

        assertEquals(Instant.parse("2026-11-02T22:30:00Z"), CalendarRules.addedSessionStart(LocalDate.of(2026, 11, 2), now))
    }

    @Test
    fun `today just after midnight clamps to midnight`() {
        val now = ZonedDateTime.of(2026, 11, 2, 0, 10, 0, 0, saoPaulo)

        assertEquals(local("2026-11-02", "00:00"), CalendarRules.addedSessionStart(LocalDate.of(2026, 11, 2), now))
        assertEquals(Instant.parse("2026-11-02T03:00:00Z"), CalendarRules.addedSessionStart(LocalDate.of(2026, 11, 2), now))
    }

    @Test
    fun `today at exactly one hour after midnight starts at midnight`() {
        val now = ZonedDateTime.of(2026, 11, 2, 1, 0, 0, 0, saoPaulo)

        assertEquals(Instant.parse("2026-11-02T03:00:00Z"), CalendarRules.addedSessionStart(LocalDate.of(2026, 11, 2), now))
    }

    @Test
    fun `the start of today is never after now`() {
        val now = ZonedDateTime.of(2026, 11, 2, 12, 0, 0, 0, saoPaulo)

        assertFalse(CalendarRules.addedSessionStart(LocalDate.of(2026, 11, 2), now).isAfter(now.toInstant()))
    }
}
