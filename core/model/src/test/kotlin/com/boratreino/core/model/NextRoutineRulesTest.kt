package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class NextRoutineRulesTest {
    private fun routine(
        id: Long,
        position: Int,
        shortName: String,
        weekdays: Set<DayOfWeek> = emptySet(),
    ) = Routine(
        id = id,
        planId = 1L,
        name = "Routine $shortName",
        shortName = shortName,
        position = position,
        weekdays = weekdays,
    )

    private val a = routine(1L, 0, "A")
    private val b = routine(2L, 1, "B")
    private val c = routine(3L, 2, "C")
    private val abc = listOf(a, b, c)

    private val anyDay = DayOfWeek.TUESDAY

    // IN_TURN

    @Test
    fun `in turn, the routine after the last one done is next`() {
        assertEquals(b, NextRoutineRules.next(ScheduleMode.IN_TURN, abc, lastRoutineId = a.id, today = anyDay))
        assertEquals(c, NextRoutineRules.next(ScheduleMode.IN_TURN, abc, lastRoutineId = b.id, today = anyDay))
    }

    @Test
    fun `in turn, after the last routine the first one comes back`() {
        assertEquals(a, NextRoutineRules.next(ScheduleMode.IN_TURN, abc, lastRoutineId = c.id, today = anyDay))
    }

    @Test
    fun `in turn, with a single routine it is always next`() {
        assertEquals(a, NextRoutineRules.next(ScheduleMode.IN_TURN, listOf(a), lastRoutineId = a.id, today = anyDay))
    }

    @Test
    fun `in turn, with no last routine the first routine is next`() {
        assertEquals(a, NextRoutineRules.next(ScheduleMode.IN_TURN, abc, lastRoutineId = null, today = anyDay))
    }

    @Test
    fun `in turn, a last routine that no longer exists counts as no last routine`() {
        assertEquals(a, NextRoutineRules.next(ScheduleMode.IN_TURN, abc, lastRoutineId = 999L, today = anyDay))
    }

    @Test
    fun `in turn, routines are ordered by position, not by list order`() {
        val shuffled = listOf(c, a, b)

        assertEquals(a, NextRoutineRules.next(ScheduleMode.IN_TURN, shuffled, lastRoutineId = null, today = anyDay))
        assertEquals(b, NextRoutineRules.next(ScheduleMode.IN_TURN, shuffled, lastRoutineId = a.id, today = anyDay))
        assertEquals(a, NextRoutineRules.next(ScheduleMode.IN_TURN, shuffled, lastRoutineId = c.id, today = anyDay))
    }

    @Test
    fun `in turn, the weekday does not change the next routine`() {
        val onWeekdays = listOf(
            routine(1L, 0, "A", setOf(DayOfWeek.MONDAY)),
            routine(2L, 1, "B", setOf(DayOfWeek.WEDNESDAY)),
        )

        assertEquals(
            routine(2L, 1, "B", setOf(DayOfWeek.WEDNESDAY)),
            NextRoutineRules.next(ScheduleMode.IN_TURN, onWeekdays, lastRoutineId = 1L, today = DayOfWeek.SATURDAY),
        )
    }

    // WEEKDAYS

    @Test
    fun `on weekdays, the routine scheduled for today is next`() {
        val scheduled = listOf(
            routine(1L, 0, "A", setOf(DayOfWeek.MONDAY)),
            routine(2L, 1, "B", setOf(DayOfWeek.WEDNESDAY)),
            routine(3L, 2, "C", setOf(DayOfWeek.FRIDAY)),
        )

        assertEquals(scheduled[1], NextRoutineRules.next(ScheduleMode.WEEKDAYS, scheduled, lastRoutineId = null, today = DayOfWeek.WEDNESDAY))
    }

    @Test
    fun `on weekdays, a day with no routine is a rest day`() {
        val scheduled = listOf(
            routine(1L, 0, "A", setOf(DayOfWeek.MONDAY)),
            routine(2L, 1, "B", setOf(DayOfWeek.WEDNESDAY)),
        )

        assertNull(NextRoutineRules.next(ScheduleMode.WEEKDAYS, scheduled, lastRoutineId = null, today = DayOfWeek.TUESDAY))
    }

    @Test
    fun `on weekdays, when two routines share the day the first by position wins`() {
        val shuffled = listOf(
            routine(2L, 1, "B", setOf(DayOfWeek.MONDAY)),
            routine(1L, 0, "A", setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
        )

        assertEquals(1L, NextRoutineRules.next(ScheduleMode.WEEKDAYS, shuffled, lastRoutineId = null, today = DayOfWeek.MONDAY)?.id)
    }

    @Test
    fun `on weekdays, the last routine done does not matter`() {
        val scheduled = listOf(
            routine(1L, 0, "A", setOf(DayOfWeek.MONDAY)),
            routine(2L, 1, "B", setOf(DayOfWeek.MONDAY)),
        )

        assertEquals(
            scheduled[0],
            NextRoutineRules.next(ScheduleMode.WEEKDAYS, scheduled, lastRoutineId = 1L, today = DayOfWeek.MONDAY),
        )
    }

    // No routines

    @Test
    fun `with no routines there is nothing next in either mode`() {
        assertNull(NextRoutineRules.next(ScheduleMode.IN_TURN, emptyList(), lastRoutineId = null, today = anyDay))
        assertNull(NextRoutineRules.next(ScheduleMode.WEEKDAYS, emptyList(), lastRoutineId = null, today = anyDay))
    }

    // nextShortName

    @Test
    fun `the first short name is A`() {
        assertEquals("A", NextRoutineRules.nextShortName(emptyList()))
    }

    @Test
    fun `the next short name follows the used letters`() {
        assertEquals("C", NextRoutineRules.nextShortName(listOf(a, b)))
    }

    @Test
    fun `a short name that is used is skipped even when a letter before it is free`() {
        val withGap = listOf(routine(1L, 0, "A"), routine(2L, 1, "C"))

        assertEquals("B", NextRoutineRules.nextShortName(withGap))
    }

    @Test
    fun `short names are compared ignoring case`() {
        val lowercase = listOf(routine(1L, 0, "a"), routine(2L, 1, "b"))

        assertEquals("C", NextRoutineRules.nextShortName(lowercase))
    }

    @Test
    fun `a longer short name does not use up a letter`() {
        assertEquals("A", NextRoutineRules.nextShortName(listOf(routine(1L, 0, "Push"))))
    }

    @Test
    fun `once Z is used the short name is R followed by the count plus one`() {
        val all = ('A'..'Z').mapIndexed { i, letter -> routine(i + 1L, i, letter.toString()) }

        assertEquals("R27", NextRoutineRules.nextShortName(all))
    }
}
