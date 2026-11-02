package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BackupSummaryTest {
    private val saoPaulo = ZoneId.of("America/Sao_Paulo")

    private val data = BackupData(
        machines = listOf(
            Machine(1, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, archived = false),
            Machine(2, "Seated row", MuscleGroup.BACK, MachineKind.WEIGHT_REPS, 2.5, null, null, archived = false),
        ),
        plans = listOf(RoutinePlan(1, ScheduleMode.IN_TURN)),
        routines = listOf(Routine(1, 1, "Routine A", "A", 0, setOf(DayOfWeek.MONDAY))),
        items = emptyList(),
        sessions = listOf(
            // 2026-10-31 02:30 UTC is still 30 October in Sao Paulo.
            Session(1, Instant.parse("2026-10-31T02:30:00Z"), Instant.parse("2026-10-31T03:30:00Z"), 1, null),
            // 2026-11-02 01:00 UTC is 1 November in Sao Paulo.
            Session(2, Instant.parse("2026-11-02T01:00:00Z"), null, null, null),
            Session(3, Instant.parse("2026-10-27T18:05:00Z"), Instant.parse("2026-10-27T19:00:00Z"), null, null),
        ),
        sets = listOf(
            WorkoutSet(1, 1, 1, 0, 120.0, 12, null, null, null, Instant.parse("2026-10-31T02:31:00Z")),
            WorkoutSet(2, 1, 2, 0, 40.0, 10, null, null, null, Instant.parse("2026-10-31T02:40:00Z")),
            WorkoutSet(3, 3, 1, 0, 110.0, 8, null, null, null, Instant.parse("2026-10-27T18:10:00Z")),
        ),
    )

    @Test
    fun `counts every machine, routine, session and set`() {
        val summary = data.summary(saoPaulo)

        assertEquals(2, summary.machines)
        assertEquals(1, summary.routines)
        assertEquals(3, summary.sessions)
        assertEquals(3, summary.sets)
    }

    @Test
    fun `the first and last days are the session days in the zone`() {
        val summary = data.summary(saoPaulo)

        assertEquals(LocalDate.of(2026, 10, 27), summary.firstDay)
        assertEquals(LocalDate.of(2026, 11, 1), summary.lastDay)
    }

    @Test
    fun `the zone decides which day a session falls on`() {
        val summary = data.summary(ZoneId.of("UTC"))

        assertEquals(LocalDate.of(2026, 10, 27), summary.firstDay)
        assertEquals(LocalDate.of(2026, 11, 2), summary.lastDay)
    }

    @Test
    fun `a single session gives the same first and last day`() {
        val one = data.copy(sessions = listOf(data.sessions[0]))

        val summary = one.summary(saoPaulo)

        assertEquals(LocalDate.of(2026, 10, 30), summary.firstDay)
        assertEquals(LocalDate.of(2026, 10, 30), summary.lastDay)
    }

    @Test
    fun `an empty history has no days`() {
        val empty = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        val summary = empty.summary(saoPaulo)

        assertEquals(BackupSummary(0, 0, 0, 0, null, null), summary)
        assertNull(summary.firstDay)
        assertNull(summary.lastDay)
    }

    @Test
    fun `sets without sessions do not give days`() {
        val setsOnly = data.copy(sessions = emptyList())

        val summary = setsOnly.summary(saoPaulo)

        assertEquals(3, summary.sets)
        assertNull(summary.firstDay)
        assertNull(summary.lastDay)
    }
}
