package com.boratreino.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** One session for the calendar: its routine and counts. */
data class SessionInfo(val session: Session, val routine: Routine?, val machineCount: Int, val setCount: Int)

/** A session with its sets grouped by machine, machines in order of first set. */
data class SessionDetail(val session: Session, val routine: Routine?, val machines: List<SessionMachine>)

object WeekGoalRules {
    const val MIN_GOAL = 1
    const val MAX_GOAL = 7
    const val DEFAULT_GOAL = 3

    /** The first day of the week (per [firstDayOfWeek]) that contains [date]. */
    fun weekStart(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate {
        val back = Math.floorMod(date.dayOfWeek.value - firstDayOfWeek.value, 7)
        return date.minusDays(back.toLong())
    }

    /** Distinct training dates in the week containing [date]. */
    fun daysTrained(trainingDates: Set<LocalDate>, date: LocalDate, firstDayOfWeek: DayOfWeek): Int {
        val start = weekStart(date, firstDayOfWeek)
        val end = start.plusDays(7)
        return trainingDates.count { !it.isBefore(start) && it.isBefore(end) }
    }

    /**
     * Consecutive weeks meeting the goal, counting back from the current week: the current week counts
     * when it already meets the goal; when it doesn't (yet), counting starts at the previous week, so an
     * unfinished week never breaks the streak. Stops at the first earlier week below the goal.
     */
    fun streak(trainingDates: Set<LocalDate>, goal: Int, today: LocalDate, firstDayOfWeek: DayOfWeek): Int {
        if (trainingDates.isEmpty()) return 0
        val earliest = weekStart(trainingDates.min(), firstDayOfWeek)
        var week = weekStart(today, firstDayOfWeek)
        var count = 0
        if (daysTrained(trainingDates, week, firstDayOfWeek) >= goal) count++
        week = week.minusWeeks(1)
        while (!week.isBefore(earliest) && daysTrained(trainingDates, week, firstDayOfWeek) >= goal) {
            count++
            week = week.minusWeeks(1)
        }
        return count
    }
}

object CalendarRules {
    /** The month's cells, row by row, each row 7 long starting on firstDayOfWeek; null = outside the month. 4-6 rows. */
    fun monthGrid(month: YearMonth, firstDayOfWeek: DayOfWeek): List<LocalDate?> {
        val first = month.atDay(1)
        val lead = Math.floorMod(first.dayOfWeek.value - firstDayOfWeek.value, 7)
        val cells = ArrayList<LocalDate?>()
        repeat(lead) { cells.add(null) }
        for (d in 1..month.lengthOfMonth()) cells.add(month.atDay(d))
        while (cells.size % 7 != 0) cells.add(null)
        return cells
    }

    /** The short name shown on a training day: the first (by startedAt) session's routine that has one; "•" when none does. */
    fun dayLabel(sessions: List<SessionInfo>): String =
        sessions.sortedBy { it.session.startedAt }.firstNotNullOfOrNull { it.routine?.shortName } ?: "•"

    /** Start time for a session added on a past day: that day at 18:00 in the zone of [now]; for today, now minus 1 hour (not before midnight). */
    fun addedSessionStart(day: LocalDate, now: ZonedDateTime): Instant {
        val zone = now.zone
        if (day == now.toLocalDate()) {
            val midnight = day.atStartOfDay(zone).toInstant()
            val hourAgo = now.toInstant().minus(1, ChronoUnit.HOURS)
            return if (hourAgo.isBefore(midnight)) midnight else hourAgo
        }
        return day.atTime(LocalTime.of(18, 0)).atZone(zone).toInstant()
    }
}
