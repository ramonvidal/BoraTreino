package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** Training frequency, from the C6 contract (section 1.3). "Today" is Wednesday 2026-11-11. */
class FrequencyRulesTest {
    private val today = LocalDate.of(2026, 11, 11)

    private fun d(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    // weekCounts

    @Test
    fun `weekCounts with Monday first lists the weeks oldest first from Monday`() {
        val weeks = FrequencyRules.weekCounts(emptySet(), today, DayOfWeek.MONDAY, 4)
        assertEquals(
            listOf(d(10, 19), d(10, 26), d(11, 2), d(11, 9)),
            weeks.map { it.weekStart },
        )
    }

    @Test
    fun `weekCounts with Sunday first starts each week on a Sunday`() {
        val weeks = FrequencyRules.weekCounts(emptySet(), today, DayOfWeek.SUNDAY, 4)
        assertEquals(
            listOf(d(10, 18), d(10, 25), d(11, 1), d(11, 8)),
            weeks.map { it.weekStart },
        )
    }

    @Test
    fun `only the last week of weekCounts is the current week`() {
        val weeks = FrequencyRules.weekCounts(emptySet(), today, DayOfWeek.MONDAY, 4)
        assertEquals(listOf(false, false, false, true), weeks.map { it.isCurrent })
    }

    @Test
    fun `weekCounts counts the distinct training days of each week`() {
        val dates = setOf(d(11, 2), d(11, 9), d(11, 11))
        val weeks = FrequencyRules.weekCounts(dates, today, DayOfWeek.MONDAY, 4)
        assertEquals(listOf(0, 0, 1, 2), weeks.map { it.days })
    }

    @Test
    fun `a Sunday training counts in the earlier week with Monday first`() {
        val weeks = FrequencyRules.weekCounts(setOf(d(11, 8)), today, DayOfWeek.MONDAY, 4)
        assertEquals(listOf(0, 0, 1, 0), weeks.map { it.days })
    }

    @Test
    fun `a Sunday training counts in the current week with Sunday first`() {
        val weeks = FrequencyRules.weekCounts(setOf(d(11, 8)), today, DayOfWeek.SUNDAY, 4)
        assertEquals(listOf(0, 0, 0, 1), weeks.map { it.days })
    }

    @Test
    fun `weekCounts with no trainings is zero for every week`() {
        val weeks = FrequencyRules.weekCounts(emptySet(), today, DayOfWeek.MONDAY, 4)
        assertEquals(listOf(0, 0, 0, 0), weeks.map { it.days })
    }

    @Test
    fun `weekCounts with one week is only the current week`() {
        val weeks = FrequencyRules.weekCounts(setOf(d(11, 9)), today, DayOfWeek.MONDAY, 1)
        assertEquals(1, weeks.size)
        assertEquals(d(11, 9), weeks.single().weekStart)
        assertEquals(1, weeks.single().days)
        assertTrue(weeks.single().isCurrent)
    }

    // summary

    @Test
    fun `summary shows the days trained this week`() {
        val s = FrequencyRules.summary(setOf(d(11, 9), d(11, 11), d(11, 2)), 3, today, DayOfWeek.MONDAY, null)
        assertEquals(2, s.thisWeek)
    }

    @Test
    fun `summary has four weeks with the current one last`() {
        val s = FrequencyRules.summary(setOf(d(11, 9)), 3, today, DayOfWeek.MONDAY, null)
        assertEquals(FrequencyRules.SHOWN_WEEKS, s.weeks.size)
        assertEquals(4, FrequencyRules.SHOWN_WEEKS)
        assertTrue(s.weeks.last().isCurrent)
        assertEquals(d(11, 9), s.weeks.last().weekStart)
    }

    @Test
    fun `summary keeps a goal inside the allowed range`() {
        assertEquals(3, FrequencyRules.summary(emptySet(), 3, today, DayOfWeek.MONDAY, null).goal)
        assertEquals(1, FrequencyRules.summary(emptySet(), 0, today, DayOfWeek.MONDAY, null).goal)
        assertEquals(7, FrequencyRules.summary(emptySet(), 9, today, DayOfWeek.MONDAY, null).goal)
    }

    @Test
    fun `summary passes the next workout through and keeps null`() {
        val routine = Routine(1, 1, "Legs", "Legs", 0, setOf(DayOfWeek.THURSDAY))
        val next = ProjectedWorkout(d(11, 12), routine)
        assertEquals(next, FrequencyRules.summary(emptySet(), 3, today, DayOfWeek.MONDAY, next).next)
        assertNull(FrequencyRules.summary(emptySet(), 3, today, DayOfWeek.MONDAY, null).next)
    }

    // streak

    @Test
    fun `an unfinished current week does not break the streak`() {
        // Goal 2: the current week has 1 day so far, the three weeks before each met the goal.
        val dates = setOf(
            d(11, 9), // current week, one day
            d(11, 2), d(11, 3), // met
            d(10, 26), d(10, 27), // met
            d(10, 19), d(10, 20), // met, the earliest week
        )
        assertEquals(3, FrequencyRules.summary(dates, 2, today, DayOfWeek.MONDAY, null).streak)
    }

    @Test
    fun `the streak stops at the first earlier week below the goal`() {
        val dates = setOf(
            d(11, 2), d(11, 3), // met
            d(10, 26), // one day only: below goal 2
            d(10, 19), d(10, 20), // met, but beyond the break
        )
        assertEquals(1, FrequencyRules.summary(dates, 2, today, DayOfWeek.MONDAY, null).streak)
    }

    @Test
    fun `a current week that meets the goal counts toward the streak`() {
        val dates = setOf(d(11, 9), d(11, 10), d(11, 2), d(11, 3))
        assertEquals(2, FrequencyRules.summary(dates, 2, today, DayOfWeek.MONDAY, null).streak)
    }

    @Test
    fun `no trainings give a streak of zero`() {
        assertEquals(0, FrequencyRules.summary(emptySet(), 2, today, DayOfWeek.MONDAY, null).streak)
    }
}
