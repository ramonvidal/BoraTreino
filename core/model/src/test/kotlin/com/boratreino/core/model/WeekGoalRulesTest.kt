package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeekGoalRulesTest {
    private val monday = DayOfWeek.MONDAY
    private val sunday = DayOfWeek.SUNDAY

    /** Wednesday 4 November 2026; its Monday-first week is 2-8 Nov, its Sunday-first week 1-7 Nov. */
    private val wednesday = LocalDate.of(2026, 11, 4)

    private fun day(month: Int, dayOfMonth: Int): LocalDate = LocalDate.of(2026, month, dayOfMonth)

    /** The given training dates, as a set. */
    private fun trainingOn(vararg days: LocalDate): Set<LocalDate> = days.toSet()

    /** Three training dates in each of the given Monday-first weeks, starting from the Monday. */
    private fun threeDaysIn(vararg mondays: LocalDate): Set<LocalDate> =
        mondays.flatMapTo(HashSet()) { monday -> (0L..2L).map { monday.plusDays(it) } }

    // weekStart

    @Test
    fun `a Monday-first week starts on the Monday`() {
        assertEquals(day(11, 2), WeekGoalRules.weekStart(wednesday, monday))
        assertEquals(day(11, 2), WeekGoalRules.weekStart(day(11, 2), monday))
        assertEquals(day(11, 2), WeekGoalRules.weekStart(day(11, 8), monday))
    }

    @Test
    fun `a Sunday on a Monday-first week belongs to the week before`() {
        assertEquals(day(10, 26), WeekGoalRules.weekStart(day(11, 1), monday))
    }

    @Test
    fun `a Sunday-first week starts on the Sunday`() {
        assertEquals(day(11, 1), WeekGoalRules.weekStart(wednesday, sunday))
        assertEquals(day(11, 1), WeekGoalRules.weekStart(day(11, 1), sunday))
        assertEquals(day(11, 1), WeekGoalRules.weekStart(day(11, 7), sunday))
    }

    @Test
    fun `a Monday on a Sunday-first week belongs to the week that started the day before`() {
        assertEquals(day(11, 1), WeekGoalRules.weekStart(day(11, 2), sunday))
    }

    @Test
    fun `any first day of the week works`() {
        assertEquals(day(10, 29), WeekGoalRules.weekStart(wednesday, DayOfWeek.THURSDAY))
        assertEquals(day(10, 29), WeekGoalRules.weekStart(day(11, 4), DayOfWeek.THURSDAY))
    }

    // daysTrained

    @Test
    fun `counts the training dates inside the Monday-first week`() {
        val dates = trainingOn(day(10, 31), day(11, 2), day(11, 4), day(11, 8), day(11, 9))

        assertEquals(3, WeekGoalRules.daysTrained(dates, wednesday, monday))
    }

    @Test
    fun `a Sunday is in the week before on a Monday-first calendar`() {
        val dates = trainingOn(day(11, 1))

        assertEquals(0, WeekGoalRules.daysTrained(dates, wednesday, monday))
        assertEquals(1, WeekGoalRules.daysTrained(dates, wednesday, sunday))
    }

    @Test
    fun `any day of the week gives the same count`() {
        val dates = trainingOn(day(11, 2), day(11, 5), day(11, 8))

        for (offset in 0L..6L) {
            assertEquals(3, WeekGoalRules.daysTrained(dates, day(11, 2).plusDays(offset), monday))
        }
    }

    @Test
    fun `no training dates means zero days`() {
        assertEquals(0, WeekGoalRules.daysTrained(emptySet(), wednesday, monday))
    }

    // streak: counting from the current week (today = Wednesday 4 November 2026, goal from each test)

    @Test
    fun `no training dates means no streak`() {
        for (goal in WeekGoalRules.MIN_GOAL..WeekGoalRules.MAX_GOAL) {
            assertEquals(0, WeekGoalRules.streak(emptySet(), goal, wednesday, monday))
        }
    }

    @Test
    fun `a current week that already meets the goal counts`() {
        // Weeks from 2 Nov back: 3 days, 3 days, 3 days, 1 day (breaks), 3 days.
        val dates = threeDaysIn(day(11, 2), day(10, 26), day(10, 19), day(10, 5)) + day(10, 12)

        assertEquals(3, WeekGoalRules.streak(dates, 3, wednesday, monday))
    }

    @Test
    fun `an unfinished current week does not break the streak`() {
        // This week has one day so far; the two weeks before met the goal.
        val dates = threeDaysIn(day(10, 26), day(10, 19)) + day(11, 2)

        assertEquals(2, WeekGoalRules.streak(dates, 3, wednesday, monday))
    }

    @Test
    fun `an unfinished current week with no days yet does not break the streak`() {
        val dates = threeDaysIn(day(10, 26), day(10, 19))

        assertEquals(2, WeekGoalRules.streak(dates, 3, wednesday, monday))
    }

    @Test
    fun `a week below the goal stops the streak`() {
        // This week met, last week empty, the two weeks before that met.
        val dates = threeDaysIn(day(11, 2), day(10, 19), day(10, 12))

        assertEquals(1, WeekGoalRules.streak(dates, 3, wednesday, monday))
    }

    @Test
    fun `an unfinished current week and an unmet last week mean no streak`() {
        val dates = trainingOn(day(11, 2), day(10, 27), day(10, 28))

        assertEquals(0, WeekGoalRules.streak(dates, 3, wednesday, monday))
    }

    @Test
    fun `a goal of one counts every week with a training day`() {
        val dates = trainingOn(day(10, 19), day(10, 27), day(11, 3))

        assertEquals(3, WeekGoalRules.streak(dates, 1, wednesday, monday))
    }

    @Test
    fun `a goal of seven needs every day of a week`() {
        val full = (0L..6L).map { day(10, 26).plusDays(it) }.toSet()
        val sixDays = full - day(10, 28)

        assertEquals(1, WeekGoalRules.streak(full, 7, wednesday, monday))
        assertEquals(0, WeekGoalRules.streak(sixDays, 7, wednesday, monday))
    }

    @Test
    fun `the first day of the week decides which week a training day counts for`() {
        // Monday-first: 2 Nov and 8 Nov are the current week (2 days, below 3); 1 Nov is the week before.
        // Sunday-first: 1 Nov, 2 Nov and 7 Nov are the current week (3 days, meets the goal).
        val dates = trainingOn(day(11, 1), day(11, 2), day(11, 7))

        assertEquals(0, WeekGoalRules.streak(dates, 3, wednesday, monday))
        assertEquals(1, WeekGoalRules.streak(dates, 3, wednesday, sunday))
    }

    @Test
    fun `the streak never goes back before the first training week`() {
        // Only one training day ever, in the week of 26 Oct; this week has none yet.
        val dates = trainingOn(day(10, 27))

        assertEquals(1, WeekGoalRules.streak(dates, 1, wednesday, monday))
    }
}
