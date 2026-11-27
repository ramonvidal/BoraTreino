package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RotationProjectionTest {
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

    // Fixed on Thursday, between the rotation routines by position.
    private val fixedThursday = routine(4L, 2, "T", setOf(DayOfWeek.THURSDAY))

    private val monday = LocalDate.of(2026, 11, 2)
    private val tuesday = LocalDate.of(2026, 11, 3)
    private val wednesday = LocalDate.of(2026, 11, 4)
    private val friday = LocalDate.of(2026, 11, 6)
    private val nextMonday = LocalDate.of(2026, 11, 9)
    private val monWedFri = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

    /** (date, routine short name) pairs, to read the results at a glance. */
    private fun project(
        routines: List<Routine> = abc,
        lastRoutineId: Long? = null,
        trainingDays: Set<DayOfWeek> = emptySet(),
        planned: List<PlannedWorkout> = emptyList(),
        trainedToday: Boolean = false,
        today: LocalDate = monday,
        daysAhead: Int = RotationProjection.DAYS_AHEAD,
        doneToday: Set<Long> = emptySet(),
    ): List<Pair<LocalDate, String>> = RotationProjection.project(
        today = today,
        routines = routines,
        lastRoutineId = lastRoutineId,
        trainingDays = trainingDays,
        planned = planned,
        trainedToday = trainedToday,
        daysAhead = daysAhead,
        doneToday = doneToday,
    ).map { it.date to it.routine.shortName }

    // Today and the coming days

    @Test
    fun `untrained today gets the routine after the last one trained`() {
        val result = project(lastRoutineId = a.id, trainingDays = monWedFri)

        assertEquals(monday, result.first().first)
        assertEquals("B", result.first().second)
    }

    @Test
    fun `with nothing trained yet today gets the first routine`() {
        val result = project(lastRoutineId = null, trainingDays = monWedFri)

        assertEquals(listOf(monday to "A", wednesday to "B", friday to "C", nextMonday to "A"), result)
    }

    @Test
    fun `a trained today has no entry and the rotation goes on from the last one`() {
        val result = project(lastRoutineId = a.id, trainingDays = monWedFri, trainedToday = true)

        assertEquals(listOf(wednesday to "B", friday to "C", nextMonday to "A"), result)
    }

    @Test
    fun `the projection covers today and the 7 days after it, 8 dates at most`() {
        val result = project(lastRoutineId = null)

        assertEquals(RotationProjection.DAYS_AHEAD, 7)
        assertEquals((0L..7L).map { monday.plusDays(it) }, result.map { it.first })
    }

    @Test
    fun `a custom number of days ahead stops there`() {
        val result = project(lastRoutineId = null, daysAhead = 2)

        assertEquals(listOf(monday to "A", tuesday to "B", wednesday to "C"), result)
    }

    @Test
    fun `entries are ordered by date`() {
        val dates = project(lastRoutineId = null, trainingDays = monWedFri).map { it.first }

        assertEquals(dates.sorted(), dates)
    }

    // Training days

    @Test
    fun `only the training days get an entry`() {
        val dates = project(lastRoutineId = null, trainingDays = monWedFri).map { it.first }

        assertEquals(listOf(monday, wednesday, friday, nextMonday), dates)
    }

    @Test
    fun `with no training days and no fixed routine every day gets the rotation`() {
        val result = project(lastRoutineId = null, trainingDays = emptySet())

        assertEquals(
            listOf("A", "B", "C", "A", "B", "C", "A", "B"),
            result.map { it.second },
        )
    }

    @Test
    fun `a day that is not a training day is skipped and the routine due stays due`() {
        // Last trained A; Tuesday is not a training day, so B is still due on Wednesday.
        val result = project(lastRoutineId = a.id, trainingDays = monWedFri, today = tuesday)

        assertEquals(listOf(wednesday to "B", friday to "C", nextMonday to "A"), result)
    }

    @Test
    fun `with a fixed routine and no training days the rotation has no entry`() {
        val result = project(routines = listOf(a, b, fixedThursday), lastRoutineId = null, trainingDays = emptySet())

        assertEquals(listOf(LocalDate.of(2026, 11, 5) to "T"), result)
    }

    // Manual plans

    @Test
    fun `a manual plan hides the entry of its date and its rotation routine moves the pointer`() {
        val planned = listOf(PlannedWorkout(monday, b.id))

        val result = project(lastRoutineId = null, planned = planned)

        assertTrue(result.none { it.first == monday })
        assertEquals(
            listOf(
                tuesday to "C",
                wednesday to "A",
                LocalDate.of(2026, 11, 5) to "B",
                friday to "C",
                LocalDate.of(2026, 11, 7) to "A",
                LocalDate.of(2026, 11, 8) to "B",
                nextMonday to "C",
            ),
            result,
        )
    }

    @Test
    fun `a manual plan of a fixed routine does not hide the rotation and does not move its pointer`() {
        // Monday is planned for the fixed routine: no entry for it, but the rotation still gives Monday its B.
        val planned = listOf(PlannedWorkout(monday, fixedThursday.id))

        val result = project(
            routines = listOf(a, b, fixedThursday),
            lastRoutineId = a.id,
            trainingDays = monWedFri,
            planned = planned,
        )

        // Monday B, Wednesday A, Thursday is the fixed routine (no slot), Friday B, next Monday A.
        assertEquals(
            listOf(monday to "B", wednesday to "A", LocalDate.of(2026, 11, 5) to "T", friday to "B", nextMonday to "A"),
            result,
        )
    }

    @Test
    fun `a manual plan on a training day keeps the rotation for the next day`() {
        val planned = listOf(PlannedWorkout(wednesday, c.id))

        val result = project(lastRoutineId = a.id, trainingDays = monWedFri, planned = planned)

        // Monday gets B; Wednesday is planned C (C moves the pointer to C); Friday gets A.
        assertEquals(listOf(monday to "B", friday to "A", nextMonday to "B"), result)
    }

    // Fixed routines

    @Test
    fun `a fixed routine on its weekday comes after that day's rotation routine and does not use up a rotation slot`() {
        val fixedWednesday = routine(4L, 2, "W", setOf(DayOfWeek.WEDNESDAY))

        val result = project(routines = listOf(a, b, fixedWednesday), lastRoutineId = null, trainingDays = monWedFri)

        // Monday A, Wednesday B then the fixed W (no slot used), Friday A, next Monday B.
        assertEquals(
            listOf(monday to "A", wednesday to "B", wednesday to "W", friday to "A", nextMonday to "B"),
            result,
        )
    }

    @Test
    fun `a fixed routine of today that is done gets no entry and the rotation of today still shows`() {
        val fixedMonday = routine(4L, 2, "W", setOf(DayOfWeek.MONDAY))

        val result = project(
            routines = listOf(a, b, fixedMonday),
            lastRoutineId = a.id,
            trainingDays = monWedFri,
            trainedToday = true,
            doneToday = setOf(fixedMonday.id),
        )

        assertEquals(listOf(monday to "B"), result.filter { it.first == monday })
        assertTrue(result.none { it.first == monday && it.second == "W" })
    }

    @Test
    fun `a rotation routine done today fills today's slot and gets no entry, and the rotation goes on from it`() {
        val result = project(
            lastRoutineId = b.id,
            trainingDays = monWedFri,
            trainedToday = true,
            doneToday = setOf(b.id),
        )

        // Monday's slot is filled by the B done today; Wednesday C, Friday A, next Monday B.
        assertEquals(listOf(wednesday to "C", friday to "A", nextMonday to "B"), result)
    }

    @Test
    fun `several entries on one date are listed rotation first and then the fixed routine`() {
        val fixedMonday = routine(4L, 2, "W", setOf(DayOfWeek.MONDAY))

        val result = project(routines = listOf(a, b, fixedMonday), lastRoutineId = null, trainingDays = monWedFri)

        assertEquals(
            listOf(monday to "A", monday to "W", wednesday to "B", friday to "A", nextMonday to "B", nextMonday to "W"),
            result,
        )
    }

    @Test
    fun `a fixed routine on a day with no training day still gets an entry`() {
        val fixedTuesday = routine(4L, 2, "T", setOf(DayOfWeek.TUESDAY))

        val result = project(routines = listOf(a, b, fixedTuesday), lastRoutineId = a.id, trainingDays = monWedFri)

        // Monday B, Tuesday T (no slot), then the rotation goes on from B: Wednesday A, Friday B, next Monday A.
        assertEquals(listOf(monday to "B", tuesday to "T", wednesday to "A", friday to "B", nextMonday to "A"), result)
    }

    // Rotation pointer and edge cases

    @Test
    fun `the rotation continues from the last routine given`() {
        val result = project(lastRoutineId = b.id, trainingDays = monWedFri)

        assertEquals(listOf(monday to "C", wednesday to "A", friday to "B", nextMonday to "C"), result)
    }

    @Test
    fun `a last routine that is the last of the rotation wraps to the first`() {
        val result = project(lastRoutineId = c.id, trainingDays = monWedFri, trainedToday = true)

        assertEquals("A", result.first().second)
    }

    @Test
    fun `with no routines there is nothing to project`() {
        assertEquals(emptyList<Pair<LocalDate, String>>(), project(routines = emptyList(), lastRoutineId = null))
    }

    @Test
    fun `with one routine it is projected on every day`() {
        val result = project(routines = listOf(a), lastRoutineId = a.id)

        assertEquals((0L..7L).map { monday.plusDays(it) to "A" }, result)
    }

    @Test
    fun `the order of the routine list does not change the projection`() {
        val shuffled = project(routines = listOf(c, a, b), lastRoutineId = null, trainingDays = monWedFri)

        assertEquals(project(lastRoutineId = null, trainingDays = monWedFri), shuffled)
    }
}
