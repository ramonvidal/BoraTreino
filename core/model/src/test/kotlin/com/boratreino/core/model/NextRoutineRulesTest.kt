package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
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

    private fun session(id: Long, routineId: Long?, startedAt: String, endedAt: String? = startedAt) = Session(
        id = id,
        startedAt = Instant.parse(startedAt),
        endedAt = endedAt?.let { Instant.parse(it) },
        routineId = routineId,
        note = null,
    )

    private fun nextRoutine(
        routines: List<Routine>,
        lastRoutineId: Long?,
        today: DayOfWeek,
        trainingDays: Set<DayOfWeek> = emptySet(),
        planned: List<Routine> = emptyList(),
        doneToday: Set<Long> = emptySet(),
    ): Routine? = NextRoutineRules.next(
        ScheduleMode.IN_TURN,
        routines,
        lastRoutineId,
        today,
        trainingDays,
        planned,
        doneToday,
    )

    private fun upcomingFrom(
        routines: List<Routine>,
        lastRoutineId: Long?,
        trainingDays: Set<DayOfWeek>,
        after: LocalDate,
        planned: List<PlannedWorkout> = emptyList(),
    ): UpcomingTraining? = NextRoutineRules.upcoming(
        ScheduleMode.IN_TURN,
        routines,
        lastRoutineId,
        trainingDays,
        after,
        planned,
    )

    // Rotation routines (no weekdays)

    private val a = routine(1L, 0, "A")
    private val b = routine(2L, 1, "B")
    private val c = routine(3L, 2, "C")
    private val abc = listOf(a, b, c)

    // A fixed routine on Tuesday, between the rotation routines by position.
    private val fixedC = routine(3L, 2, "C", setOf(DayOfWeek.TUESDAY))
    private val mixed = listOf(a, b, fixedC)

    private val trainingDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

    private val monday = LocalDate.of(2026, 11, 2)
    private val tuesday = LocalDate.of(2026, 11, 3)
    private val wednesday = LocalDate.of(2026, 11, 4)
    private val thursday = LocalDate.of(2026, 11, 5)
    private val friday = LocalDate.of(2026, 11, 6)
    private val saturday = LocalDate.of(2026, 11, 7)

    // Rotation

    @Test
    fun `rotation, the routine after the last one trained is next`() {
        assertEquals(b, nextRoutine(abc, lastRoutineId = a.id, today = DayOfWeek.TUESDAY))
        assertEquals(c, nextRoutine(abc, lastRoutineId = b.id, today = DayOfWeek.TUESDAY))
    }

    @Test
    fun `rotation, after the last routine the first one comes back`() {
        assertEquals(a, nextRoutine(abc, lastRoutineId = c.id, today = DayOfWeek.TUESDAY))
    }

    @Test
    fun `rotation, a single routine is always next`() {
        assertEquals(a, nextRoutine(listOf(a), lastRoutineId = a.id, today = DayOfWeek.TUESDAY))
    }

    @Test
    fun `rotation, with no last routine the first by position is next`() {
        assertEquals(a, nextRoutine(abc, lastRoutineId = null, today = DayOfWeek.TUESDAY))
    }

    @Test
    fun `rotation, a last routine that no longer exists starts the rotation again`() {
        assertEquals(a, nextRoutine(abc, lastRoutineId = 999L, today = DayOfWeek.TUESDAY))
    }

    @Test
    fun `rotation, routines are ordered by position not by list order`() {
        val shuffled = listOf(c, a, b)

        assertEquals(a, nextRoutine(shuffled, lastRoutineId = null, today = DayOfWeek.TUESDAY))
        assertEquals(b, nextRoutine(shuffled, lastRoutineId = a.id, today = DayOfWeek.TUESDAY))
        assertEquals(a, nextRoutine(shuffled, lastRoutineId = c.id, today = DayOfWeek.TUESDAY))
    }

    @Test
    fun `rotation, with no training days every day is a training day when there are no fixed routines`() {
        for (day in DayOfWeek.entries) {
            assertEquals(b, nextRoutine(abc, lastRoutineId = a.id, today = day, trainingDays = emptySet()))
        }
    }

    @Test
    fun `rotation, a missed routine stays the next one`() {
        // Trained A on Monday, skipped the Wednesday session: on Friday B is still next, not C.
        assertEquals(b, nextRoutine(abc, lastRoutineId = a.id, today = DayOfWeek.FRIDAY, trainingDays = trainingDays))
    }

    @Test
    fun `rotation, with training days the rotation wraps after the last routine`() {
        assertEquals(a, nextRoutine(abc, lastRoutineId = c.id, today = DayOfWeek.MONDAY, trainingDays = trainingDays))
    }

    @Test
    fun `rotation, on a training day the next routine is offered`() {
        assertEquals(b, nextRoutine(abc, lastRoutineId = a.id, today = DayOfWeek.WEDNESDAY, trainingDays = trainingDays))
    }

    @Test
    fun `rotation, on a day that is not a training day is a rest day`() {
        assertNull(nextRoutine(abc, lastRoutineId = a.id, today = DayOfWeek.TUESDAY, trainingDays = trainingDays))
        assertNull(nextRoutine(abc, lastRoutineId = null, today = DayOfWeek.SUNDAY, trainingDays = trainingDays))
    }

    // Fixed routines (weekdays set)

    @Test
    fun `fixed, the routine on its day wins over the rotation`() {
        assertEquals(fixedC, nextRoutine(mixed, lastRoutineId = a.id, today = DayOfWeek.TUESDAY, trainingDays = trainingDays))
    }

    @Test
    fun `fixed, the routine on its day wins even when the rotation is offered that day`() {
        assertEquals(
            fixedC,
            nextRoutine(mixed, lastRoutineId = a.id, today = DayOfWeek.TUESDAY, trainingDays = setOf(DayOfWeek.TUESDAY)),
        )
    }

    @Test
    fun `fixed, a routine with several days is next on each of them`() {
        val twoDays = routine(3L, 2, "C", setOf(DayOfWeek.THURSDAY, DayOfWeek.SATURDAY))
        val routines = listOf(a, b, twoDays)

        assertEquals(twoDays, nextRoutine(routines, lastRoutineId = a.id, today = DayOfWeek.THURSDAY, trainingDays = trainingDays))
        assertEquals(twoDays, nextRoutine(routines, lastRoutineId = a.id, today = DayOfWeek.SATURDAY, trainingDays = trainingDays))
    }

    @Test
    fun `fixed, two routines on one day the first by position wins`() {
        val routines = listOf(
            routine(5L, 1, "Y", setOf(DayOfWeek.FRIDAY)),
            routine(4L, 0, "X", setOf(DayOfWeek.FRIDAY)),
        )

        assertEquals(4L, nextRoutine(routines, lastRoutineId = null, today = DayOfWeek.FRIDAY)?.id)
    }

    @Test
    fun `fixed, a fixed routine is never picked by the rotation`() {
        assertEquals(b, nextRoutine(mixed, lastRoutineId = a.id, today = DayOfWeek.WEDNESDAY, trainingDays = trainingDays))
    }

    @Test
    fun `fixed, the rotation skips a fixed routine between two rotation routines`() {
        // B is last: the rotation goes to A, past the fixed C that sits between them by position.
        assertEquals(a, nextRoutine(mixed, lastRoutineId = b.id, today = DayOfWeek.MONDAY, trainingDays = trainingDays))
    }

    @Test
    fun `fixed, a fixed routine that is the last routine gives the first rotation routine`() {
        assertEquals(a, nextRoutine(mixed, lastRoutineId = fixedC.id, today = DayOfWeek.MONDAY, trainingDays = trainingDays))
    }

    @Test
    fun `fixed, only fixed routines and a day with no routine is a rest day`() {
        assertNull(nextRoutine(listOf(fixedC), lastRoutineId = null, today = DayOfWeek.MONDAY, trainingDays = trainingDays))
    }

    @Test
    fun `fixed, only fixed routines and their day gives the routine`() {
        assertEquals(fixedC, nextRoutine(listOf(fixedC), lastRoutineId = null, today = DayOfWeek.TUESDAY))
    }

    @Test
    fun `fixed, a rotation with no training days is offered on no day when a fixed routine exists`() {
        assertNull(nextRoutine(mixed, lastRoutineId = b.id, today = DayOfWeek.MONDAY, trainingDays = emptySet()))
        assertNull(nextRoutine(mixed, lastRoutineId = b.id, today = DayOfWeek.WEDNESDAY, trainingDays = emptySet()))
    }

    @Test
    fun `fixed, with training days the fixed routines do not add rotation days`() {
        assertEquals(a, nextRoutine(mixed, lastRoutineId = b.id, today = DayOfWeek.MONDAY, trainingDays = trainingDays))
        assertEquals(a, nextRoutine(mixed, lastRoutineId = b.id, today = DayOfWeek.FRIDAY, trainingDays = trainingDays))
        assertNull(nextRoutine(mixed, lastRoutineId = b.id, today = DayOfWeek.THURSDAY, trainingDays = trainingDays))
    }

    @Test
    fun `fixed, a last routine that is not in rotation starts the rotation again`() {
        // 999 is not a routine any more: the rotation starts again with A.
        assertEquals(a, nextRoutine(mixed, lastRoutineId = 999L, today = DayOfWeek.WEDNESDAY, trainingDays = trainingDays))
    }

    // Mode is ignored

    @Test
    fun `the schedule mode does not change the next routine`() {
        for (mode in ScheduleMode.entries) {
            assertEquals(
                a,
                NextRoutineRules.next(mode, mixed, lastRoutineId = b.id, today = DayOfWeek.MONDAY, trainingDays = trainingDays),
            )
            assertEquals(
                fixedC,
                NextRoutineRules.next(mode, mixed, lastRoutineId = b.id, today = DayOfWeek.TUESDAY, trainingDays = trainingDays),
            )
            assertEquals(
                b,
                NextRoutineRules.next(mode, abc, lastRoutineId = a.id, today = DayOfWeek.WEDNESDAY, trainingDays = trainingDays),
            )
        }
    }

    // No routines

    @Test
    fun `with no routines there is nothing next in either mode`() {
        for (mode in ScheduleMode.entries) {
            assertNull(NextRoutineRules.next(mode, emptyList(), lastRoutineId = null, today = DayOfWeek.TUESDAY))
        }
    }

    @Test
    fun `with no routines there is no upcoming training in either mode`() {
        for (mode in ScheduleMode.entries) {
            assertNull(
                NextRoutineRules.upcoming(mode, emptyList(), lastRoutineId = null, trainingDays = trainingDays, after = monday),
            )
        }
    }

    // Planned routines

    @Test
    fun `a planned routine beats the fixed day`() {
        assertEquals(a, nextRoutine(mixed, lastRoutineId = null, today = DayOfWeek.TUESDAY, planned = listOf(a)))
    }

    @Test
    fun `a planned routine makes a rest day a training day`() {
        assertEquals(c, nextRoutine(abc, lastRoutineId = a.id, today = DayOfWeek.TUESDAY, trainingDays = trainingDays, planned = listOf(c)))
    }

    @Test
    fun `planned routines are taken in order skipping the ones done today`() {
        val planned = listOf(b, c)

        assertEquals(b, nextRoutine(abc, lastRoutineId = a.id, today = DayOfWeek.TUESDAY, planned = planned))
        assertEquals(
            c,
            nextRoutine(abc, lastRoutineId = a.id, today = DayOfWeek.TUESDAY, planned = planned, doneToday = setOf(b.id)),
        )
    }

    @Test
    fun `once every planned routine is done the schedule decides again`() {
        val planned = listOf(b, c)
        val bothDone = setOf(b.id, c.id)

        assertNull(
            nextRoutine(
                abc,
                lastRoutineId = a.id,
                today = DayOfWeek.TUESDAY,
                trainingDays = trainingDays,
                planned = planned,
                doneToday = bothDone,
            ),
        )
        assertEquals(
            b,
            nextRoutine(
                abc,
                lastRoutineId = a.id,
                today = DayOfWeek.WEDNESDAY,
                trainingDays = trainingDays,
                planned = planned,
                doneToday = bothDone,
            ),
        )
    }

    @Test
    fun `a routine done today that is not planned does not change the planned pick`() {
        assertEquals(b, nextRoutine(abc, lastRoutineId = null, today = DayOfWeek.TUESDAY, planned = listOf(b), doneToday = setOf(c.id)))
    }

    // upcoming

    @Test
    fun `upcoming, the next training day after a date comes with the routine suggested then`() {
        assertEquals(
            UpcomingTraining(wednesday, b),
            upcomingFrom(abc, lastRoutineId = a.id, trainingDays = trainingDays, after = monday),
        )
    }

    @Test
    fun `upcoming, the date itself is not counted`() {
        assertEquals(
            UpcomingTraining(LocalDate.of(2026, 11, 9), b),
            upcomingFrom(abc, lastRoutineId = a.id, trainingDays = setOf(DayOfWeek.MONDAY), after = monday),
        )
    }

    @Test
    fun `upcoming, the search wraps over the end of the week`() {
        assertEquals(
            UpcomingTraining(LocalDate.of(2026, 11, 9), a),
            upcomingFrom(abc, lastRoutineId = c.id, trainingDays = trainingDays, after = saturday),
        )
    }

    @Test
    fun `upcoming, with no training days tomorrow is the next training`() {
        assertEquals(
            UpcomingTraining(saturday, b),
            upcomingFrom(abc, lastRoutineId = a.id, trainingDays = emptySet(), after = friday),
        )
    }

    @Test
    fun `upcoming, with no last routine the first routine is the upcoming one`() {
        assertEquals(
            UpcomingTraining(wednesday, a),
            upcomingFrom(abc, lastRoutineId = null, trainingDays = trainingDays, after = monday),
        )
    }

    @Test
    fun `upcoming, a fixed day comes before the rotation`() {
        assertEquals(
            UpcomingTraining(tuesday, fixedC),
            upcomingFrom(mixed, lastRoutineId = b.id, trainingDays = trainingDays, after = monday),
        )
    }

    @Test
    fun `upcoming, after a fixed day the rotation continues`() {
        assertEquals(
            UpcomingTraining(wednesday, a),
            upcomingFrom(mixed, lastRoutineId = b.id, trainingDays = trainingDays, after = tuesday),
        )
    }

    @Test
    fun `upcoming, with no training days the rotation waits for the fixed day`() {
        assertEquals(
            UpcomingTraining(LocalDate.of(2026, 11, 10), fixedC),
            upcomingFrom(mixed, lastRoutineId = b.id, trainingDays = emptySet(), after = wednesday),
        )
    }

    @Test
    fun `upcoming, a planned routine on a day off is the upcoming training`() {
        assertEquals(
            UpcomingTraining(thursday, c),
            upcomingFrom(
                abc,
                lastRoutineId = a.id,
                trainingDays = trainingDays,
                after = wednesday,
                planned = listOf(PlannedWorkout(thursday, c.id)),
            ),
        )
    }

    @Test
    fun `upcoming, a planned routine beats the schedule on the same day`() {
        assertEquals(
            UpcomingTraining(wednesday, c),
            upcomingFrom(
                abc,
                lastRoutineId = a.id,
                trainingDays = trainingDays,
                after = monday,
                planned = listOf(PlannedWorkout(wednesday, c.id)),
            ),
        )
    }

    @Test
    fun `upcoming, a planned routine more than 7 days ahead is ignored`() {
        assertEquals(
            UpcomingTraining(tuesday, fixedC),
            upcomingFrom(
                mixed,
                lastRoutineId = b.id,
                trainingDays = trainingDays,
                after = monday,
                planned = listOf(PlannedWorkout(LocalDate.of(2026, 11, 10), a.id)),
            ),
        )
    }

    @Test
    fun `upcoming, several planned routines on one date the lowest position wins`() {
        assertEquals(
            UpcomingTraining(wednesday, b),
            upcomingFrom(
                abc,
                lastRoutineId = null,
                trainingDays = trainingDays,
                after = monday,
                planned = listOf(
                    PlannedWorkout(wednesday, c.id, position = 1),
                    PlannedWorkout(wednesday, b.id, position = 0),
                ),
            ),
        )
    }

    @Test
    fun `upcoming, a planned routine that no longer exists is ignored`() {
        assertEquals(
            UpcomingTraining(wednesday, a),
            upcomingFrom(
                abc,
                lastRoutineId = null,
                trainingDays = trainingDays,
                after = monday,
                planned = listOf(PlannedWorkout(wednesday, 999L)),
            ),
        )
    }

    // lastRotationRoutineId

    private val history = listOf(
        session(1L, a.id, "2026-11-02T08:00:00Z", "2026-11-02T09:00:00Z"),
        session(2L, b.id, "2026-11-04T08:00:00Z", "2026-11-04T09:00:00Z"),
        session(3L, fixedC.id, "2026-11-05T08:00:00Z", "2026-11-05T09:00:00Z"),
    )

    @Test
    fun `last rotation routine, no sessions gives null`() {
        assertNull(NextRoutineRules.lastRotationRoutineId(emptyList(), mixed))
    }

    @Test
    fun `last rotation routine, the latest finished rotation session wins over a later fixed one`() {
        assertEquals(b.id, NextRoutineRules.lastRotationRoutineId(history, mixed))
    }

    @Test
    fun `last rotation routine, an open session is ignored`() {
        val withOpen = history + session(4L, a.id, "2026-11-06T08:00:00Z", endedAt = null)

        assertEquals(b.id, NextRoutineRules.lastRotationRoutineId(withOpen, mixed))
    }

    @Test
    fun `last rotation routine, a session with no routine is ignored`() {
        val withFree = history + session(4L, null, "2026-11-06T08:00:00Z", "2026-11-06T09:00:00Z")

        assertEquals(b.id, NextRoutineRules.lastRotationRoutineId(withFree, mixed))
    }

    @Test
    fun `last rotation routine, a session of a routine that no longer exists is ignored`() {
        val withDeleted = history + session(4L, 999L, "2026-11-06T08:00:00Z", "2026-11-06T09:00:00Z")

        assertEquals(b.id, NextRoutineRules.lastRotationRoutineId(withDeleted, mixed))
    }

    @Test
    fun `last rotation routine, only fixed routines gives null`() {
        assertNull(NextRoutineRules.lastRotationRoutineId(listOf(history[2]), listOf(fixedC)))
    }

    @Test
    fun `last rotation routine, the latest by start wins whatever the list order`() {
        val reversed = history.reversed()

        assertEquals(b.id, NextRoutineRules.lastRotationRoutineId(reversed, mixed))
    }

    @Test
    fun `last rotation routine, a tie on the start goes to the higher session id`() {
        val tied = listOf(
            session(10L, a.id, "2026-11-02T08:00:00Z", "2026-11-02T09:00:00Z"),
            session(11L, b.id, "2026-11-02T08:00:00Z", "2026-11-02T09:00:00Z"),
        )

        assertEquals(b.id, NextRoutineRules.lastRotationRoutineId(tied, mixed))
    }

    @Test
    fun `last rotation routine feeds next so the rotation continues after it`() {
        val last = NextRoutineRules.lastRotationRoutineId(history, mixed)

        assertEquals(a, nextRoutine(mixed, lastRoutineId = last, today = DayOfWeek.MONDAY, trainingDays = trainingDays))
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
