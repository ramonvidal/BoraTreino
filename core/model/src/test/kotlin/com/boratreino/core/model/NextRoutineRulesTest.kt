package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

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

    // IN_TURN with training days

    private val trainingDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

    @Test
    fun `in turn with training days, a training day gives the in-turn next routine`() {
        assertEquals(b, NextRoutineRules.next(ScheduleMode.IN_TURN, abc, lastRoutineId = a.id, today = DayOfWeek.WEDNESDAY, trainingDays = trainingDays))
    }

    @Test
    fun `in turn with training days, a day that is not a training day has no routine`() {
        assertNull(NextRoutineRules.next(ScheduleMode.IN_TURN, abc, lastRoutineId = a.id, today = DayOfWeek.TUESDAY, trainingDays = trainingDays))
        assertNull(NextRoutineRules.next(ScheduleMode.IN_TURN, abc, lastRoutineId = null, today = DayOfWeek.SUNDAY, trainingDays = trainingDays))
    }

    @Test
    fun `in turn with empty training days, every day is a training day`() {
        for (day in DayOfWeek.entries) {
            assertEquals(b, NextRoutineRules.next(ScheduleMode.IN_TURN, abc, lastRoutineId = a.id, today = day, trainingDays = emptySet()))
        }
    }

    @Test
    fun `in turn with training days, a missed routine stays the next one`() {
        // Trained A on Monday, skipped the Wednesday session: on Friday B is still next, not C.
        assertEquals(b, NextRoutineRules.next(ScheduleMode.IN_TURN, abc, lastRoutineId = a.id, today = DayOfWeek.FRIDAY, trainingDays = trainingDays))
    }

    @Test
    fun `in turn with training days, the rotation wraps after the last routine`() {
        assertEquals(a, NextRoutineRules.next(ScheduleMode.IN_TURN, abc, lastRoutineId = c.id, today = DayOfWeek.MONDAY, trainingDays = trainingDays))
    }

    // upcoming

    private val monday = LocalDate.of(2026, 11, 2)
    private val friday = LocalDate.of(2026, 11, 6)
    private val saturday = LocalDate.of(2026, 11, 7)

    @Test
    fun `in turn, the next training day after a date is found with the routine suggested then`() {
        val upcoming = NextRoutineRules.upcoming(ScheduleMode.IN_TURN, abc, lastRoutineId = a.id, trainingDays = trainingDays, after = monday)

        assertEquals(UpcomingTraining(LocalDate.of(2026, 11, 4), b), upcoming)
    }

    @Test
    fun `in turn, the date itself is not counted`() {
        val onlyMondays = setOf(DayOfWeek.MONDAY)

        val upcoming = NextRoutineRules.upcoming(ScheduleMode.IN_TURN, abc, lastRoutineId = a.id, trainingDays = onlyMondays, after = monday)

        assertEquals(UpcomingTraining(LocalDate.of(2026, 11, 9), b), upcoming)
    }

    @Test
    fun `in turn, the search wraps over the end of the week`() {
        val upcoming = NextRoutineRules.upcoming(ScheduleMode.IN_TURN, abc, lastRoutineId = c.id, trainingDays = trainingDays, after = saturday)

        assertEquals(UpcomingTraining(LocalDate.of(2026, 11, 9), a), upcoming)
    }

    @Test
    fun `in turn, with empty training days tomorrow is the next training`() {
        val upcoming = NextRoutineRules.upcoming(ScheduleMode.IN_TURN, abc, lastRoutineId = a.id, trainingDays = emptySet(), after = friday)

        assertEquals(UpcomingTraining(saturday, b), upcoming)
    }

    @Test
    fun `in turn, with no last routine the first routine is the upcoming one`() {
        val upcoming = NextRoutineRules.upcoming(ScheduleMode.IN_TURN, abc, lastRoutineId = null, trainingDays = trainingDays, after = monday)

        assertEquals(UpcomingTraining(LocalDate.of(2026, 11, 4), a), upcoming)
    }

    @Test
    fun `with no routines there is no upcoming training in either mode`() {
        assertNull(NextRoutineRules.upcoming(ScheduleMode.IN_TURN, emptyList(), lastRoutineId = null, trainingDays = trainingDays, after = monday))
        assertNull(NextRoutineRules.upcoming(ScheduleMode.WEEKDAYS, emptyList(), lastRoutineId = null, trainingDays = emptySet(), after = monday))
    }

    @Test
    fun `on weekdays, the upcoming training is the next day that has a routine, wrapping the week`() {
        val scheduled = listOf(
            routine(1L, 0, "A", setOf(DayOfWeek.MONDAY)),
            routine(2L, 1, "B", setOf(DayOfWeek.WEDNESDAY)),
            routine(3L, 2, "C", setOf(DayOfWeek.FRIDAY)),
        )

        assertEquals(
            UpcomingTraining(LocalDate.of(2026, 11, 4), scheduled[1]),
            NextRoutineRules.upcoming(ScheduleMode.WEEKDAYS, scheduled, lastRoutineId = null, trainingDays = emptySet(), after = monday),
        )
        assertEquals(
            UpcomingTraining(LocalDate.of(2026, 11, 9), scheduled[0]),
            NextRoutineRules.upcoming(ScheduleMode.WEEKDAYS, scheduled, lastRoutineId = null, trainingDays = emptySet(), after = friday),
        )
    }

    @Test
    fun `on weekdays, when two routines share the upcoming day the first by position wins`() {
        val shuffled = listOf(
            routine(2L, 1, "B", setOf(DayOfWeek.WEDNESDAY)),
            routine(1L, 0, "A", setOf(DayOfWeek.WEDNESDAY)),
        )

        assertEquals(
            UpcomingTraining(LocalDate.of(2026, 11, 4), shuffled[1]),
            NextRoutineRules.upcoming(ScheduleMode.WEEKDAYS, shuffled, lastRoutineId = null, trainingDays = emptySet(), after = monday),
        )
    }

    @Test
    fun `on weekdays, routines with no weekdays have no upcoming training`() {
        assertNull(NextRoutineRules.upcoming(ScheduleMode.WEEKDAYS, abc, lastRoutineId = null, trainingDays = emptySet(), after = monday))
    }

    // Planned routines: next

    @Test
    fun `on weekdays, a planned routine beats the routine scheduled for the day`() {
        val scheduled = listOf(
            routine(1L, 0, "A", setOf(DayOfWeek.MONDAY)),
            routine(2L, 1, "B", setOf(DayOfWeek.WEDNESDAY)),
        )

        assertEquals(
            scheduled[1],
            NextRoutineRules.next(ScheduleMode.WEEKDAYS, scheduled, lastRoutineId = null, today = DayOfWeek.MONDAY, planned = scheduled[1]),
        )
    }

    @Test
    fun `on weekdays, a planned routine makes a rest day a training day`() {
        val scheduled = listOf(routine(1L, 0, "A", setOf(DayOfWeek.MONDAY)))

        assertEquals(
            scheduled[0],
            NextRoutineRules.next(ScheduleMode.WEEKDAYS, scheduled, lastRoutineId = null, today = DayOfWeek.TUESDAY, planned = scheduled[0]),
        )
    }

    @Test
    fun `in turn, a planned routine beats the rotation`() {
        assertEquals(c, NextRoutineRules.next(ScheduleMode.IN_TURN, abc, lastRoutineId = a.id, today = anyDay, planned = c))
    }

    @Test
    fun `in turn with training days, a planned routine makes a day off a training day`() {
        assertEquals(
            b,
            NextRoutineRules.next(
                ScheduleMode.IN_TURN,
                abc,
                lastRoutineId = a.id,
                today = DayOfWeek.TUESDAY,
                trainingDays = trainingDays,
                planned = b,
            ),
        )
    }

    @Test
    fun `without a planned routine the schedule still decides`() {
        assertNull(
            NextRoutineRules.next(
                ScheduleMode.IN_TURN,
                abc,
                lastRoutineId = a.id,
                today = DayOfWeek.TUESDAY,
                trainingDays = trainingDays,
                planned = null,
            ),
        )
    }

    // Planned routines: upcoming

    private val weekScheduled = listOf(
        routine(1L, 0, "A", setOf(DayOfWeek.MONDAY)),
        routine(2L, 1, "B", setOf(DayOfWeek.WEDNESDAY)),
        routine(3L, 2, "C", setOf(DayOfWeek.FRIDAY)),
    )

    @Test
    fun `on weekdays, a planned routine on a scheduled day replaces that day's routine`() {
        val upcoming = NextRoutineRules.upcoming(
            ScheduleMode.WEEKDAYS,
            weekScheduled,
            lastRoutineId = null,
            trainingDays = emptySet(),
            after = monday,
            planned = listOf(PlannedWorkout(LocalDate.of(2026, 11, 4), 3L)),
        )

        assertEquals(UpcomingTraining(LocalDate.of(2026, 11, 4), weekScheduled[2]), upcoming)
    }

    @Test
    fun `on weekdays, a planned routine on a day with no schedule is the upcoming training`() {
        val upcoming = NextRoutineRules.upcoming(
            ScheduleMode.WEEKDAYS,
            weekScheduled,
            lastRoutineId = null,
            trainingDays = emptySet(),
            after = monday,
            planned = listOf(PlannedWorkout(LocalDate.of(2026, 11, 3), 2L)),
        )

        assertEquals(UpcomingTraining(LocalDate.of(2026, 11, 3), weekScheduled[1]), upcoming)
    }

    @Test
    fun `a planned routine more than 7 days ahead is ignored`() {
        val upcoming = NextRoutineRules.upcoming(
            ScheduleMode.WEEKDAYS,
            weekScheduled,
            lastRoutineId = null,
            trainingDays = emptySet(),
            after = monday,
            planned = listOf(PlannedWorkout(LocalDate.of(2026, 11, 10), 1L)),
        )

        assertEquals(UpcomingTraining(LocalDate.of(2026, 11, 4), weekScheduled[1]), upcoming)
    }

    @Test
    fun `a planned routine on the last day of the 7 day window counts`() {
        val unscheduled = listOf(routine(1L, 0, "A"))

        val upcoming = NextRoutineRules.upcoming(
            ScheduleMode.WEEKDAYS,
            unscheduled,
            lastRoutineId = null,
            trainingDays = emptySet(),
            after = monday,
            planned = listOf(PlannedWorkout(LocalDate.of(2026, 11, 9), 1L)),
        )

        assertEquals(UpcomingTraining(LocalDate.of(2026, 11, 9), unscheduled[0]), upcoming)
    }

    @Test
    fun `a planned routine that no longer exists is ignored`() {
        val upcoming = NextRoutineRules.upcoming(
            ScheduleMode.WEEKDAYS,
            weekScheduled,
            lastRoutineId = null,
            trainingDays = emptySet(),
            after = monday,
            planned = listOf(PlannedWorkout(LocalDate.of(2026, 11, 4), 999L)),
        )

        assertEquals(UpcomingTraining(LocalDate.of(2026, 11, 4), weekScheduled[1]), upcoming)
    }

    @Test
    fun `in turn, a planned routine beats the rotation in the upcoming training`() {
        val upcoming = NextRoutineRules.upcoming(
            ScheduleMode.IN_TURN,
            abc,
            lastRoutineId = a.id,
            trainingDays = trainingDays,
            after = monday,
            planned = listOf(PlannedWorkout(LocalDate.of(2026, 11, 4), c.id)),
        )

        assertEquals(UpcomingTraining(LocalDate.of(2026, 11, 4), c), upcoming)
    }

    @Test
    fun `in turn, a planned routine on a day off is the upcoming training`() {
        val upcoming = NextRoutineRules.upcoming(
            ScheduleMode.IN_TURN,
            abc,
            lastRoutineId = a.id,
            trainingDays = trainingDays,
            after = monday,
            planned = listOf(PlannedWorkout(LocalDate.of(2026, 11, 3), b.id)),
        )

        assertEquals(UpcomingTraining(LocalDate.of(2026, 11, 3), b), upcoming)
    }
}
