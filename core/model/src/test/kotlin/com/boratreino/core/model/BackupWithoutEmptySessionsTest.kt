package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant

class BackupWithoutEmptySessionsTest {
    private val machine = Machine(1, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, "Seat 4", 120, archived = false)
    private val routine = Routine(1, 1, "Routine A", "A", 0, setOf(DayOfWeek.MONDAY))
    private val item = RoutineItem(routineId = 1, machineId = 1, position = 0, targetSets = 3, repsMin = 8, repsMax = 12)
    private val withSets = Session(1, Instant.parse("2026-10-31T02:30:00Z"), Instant.parse("2026-10-31T03:30:00Z"), 1, "Felt strong")
    private val extraWithSets = Session(3, Instant.parse("2026-10-27T18:05:00Z"), Instant.parse("2026-10-27T19:00:00Z"), null, null, isExtra = true)
    private val emptyOpen = Session(2, Instant.parse("2026-11-02T01:00:00Z"), null, 1, null)
    private val emptyClosed = Session(4, Instant.parse("2026-10-20T18:00:00Z"), Instant.parse("2026-10-20T18:00:00Z"), null, null)
    private val sets = listOf(
        WorkoutSet(10, 1, 1, 0, 120.0, 12, null, null, null, Instant.parse("2026-10-31T02:31:00Z")),
        WorkoutSet(11, 3, 1, 0, 110.0, 8, null, null, null, Instant.parse("2026-10-27T18:10:00Z")),
    )
    private val plan = RoutinePlan(1, ScheduleMode.IN_TURN)

    private fun data(sessions: List<Session>) = BackupData(
        machines = listOf(machine),
        plans = listOf(plan),
        routines = listOf(routine),
        items = listOf(item),
        sessions = sessions,
        sets = sets,
    )

    @Test
    fun `drops the sessions that have no sets`() {
        val cleaned = data(listOf(withSets, emptyOpen, extraWithSets, emptyClosed)).withoutEmptySessions()

        assertEquals(listOf(withSets, extraWithSets), cleaned.sessions)
    }

    @Test
    fun `keeps the ids of the sessions and sets that remain`() {
        val cleaned = data(listOf(emptyOpen, withSets, emptyClosed)).withoutEmptySessions()

        assertEquals(listOf(1L), cleaned.sessions.map { it.id })
        assertEquals(sets, cleaned.sets)
    }

    @Test
    fun `keeps every other table as it is`() {
        val before = data(listOf(withSets, emptyOpen))

        val cleaned = before.withoutEmptySessions()

        assertEquals(before.machines, cleaned.machines)
        assertEquals(before.plans, cleaned.plans)
        assertEquals(before.routines, cleaned.routines)
        assertEquals(before.items, cleaned.items)
        assertEquals(before.sets, cleaned.sets)
        assertEquals(before.plannedWorkouts, cleaned.plannedWorkouts)
        assertEquals(before.measurements, cleaned.measurements)
    }

    @Test
    fun `gives back the same instance when no session is empty`() {
        val before = data(listOf(withSets, extraWithSets))

        assertSame(before, before.withoutEmptySessions())
    }

    @Test
    fun `a backup with no sessions and no sets stays as it is`() {
        val empty = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        assertSame(empty, empty.withoutEmptySessions())
    }
}
