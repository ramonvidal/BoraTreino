package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The three StatsReport fields added for the Overview tab (C6 contract, section 1.2): oneRmHistory, suspiciousJumps and
 * recentGroupSets. Built through [StatsRules.report] on explicit dates. "Today" is Wednesday 2026-11-11.
 */
class StatsRulesC6Test {
    private val today = LocalDate.of(2026, 11, 11)

    private val machines = mutableListOf<Machine>()
    private val sessions = mutableListOf<Session>()
    private val sets = mutableListOf<WorkoutSet>()

    private fun machine(
        name: String,
        group: MuscleGroup = MuscleGroup.CHEST,
        kind: MachineKind = MachineKind.WEIGHT_REPS,
    ): Machine {
        val m = Machine(machines.size + 1L, name, group, kind, 2.5, null, null, false)
        machines += m
        return m
    }

    /** A session on [daysAgo] days before today, at 10:00 UTC. */
    private fun session(daysAgo: Long): Long {
        val id = sessions.size + 1L
        val start = today.minusDays(daysAgo).atTime(10, 0).toInstant(ZoneOffset.UTC)
        sessions += Session(id, start, null, null, null)
        return id
    }

    private fun strength(
        sessionId: Long,
        machine: Machine,
        kg: Double,
        reps: Int,
        type: SetType = SetType.NORMAL,
    ) {
        val start = sessions.first { it.id == sessionId }.startedAt
        sets += WorkoutSet(
            id = sets.size + 1L, sessionId = sessionId, machineId = machine.id, position = sets.size,
            weightKg = kg, reps = reps, seconds = null, calories = null, distanceM = null,
            loggedAt = start.plusSeconds(60L * sets.size), type = type,
        )
    }

    private fun cardio(sessionId: Long, machine: Machine) {
        val start = sessions.first { it.id == sessionId }.startedAt
        sets += WorkoutSet(
            id = sets.size + 1L, sessionId = sessionId, machineId = machine.id, position = sets.size,
            weightKg = null, reps = null, seconds = 600, calories = null, distanceM = 2000,
            loggedAt = start.plusSeconds(60L * sets.size),
        )
    }

    private fun report(period: StatsPeriod = StatsPeriod.FOUR_WEEKS): StatsReport = StatsRules.report(
        period = period,
        today = today,
        firstDayOfWeek = DayOfWeek.MONDAY,
        weeklyGoal = 3,
        machines = machines,
        sessions = sessions,
        sets = sets,
        zone = ZoneOffset.UTC,
    )

    // oneRmHistory

    @Test
    fun `one rep max history lists strength machines with their whole history, by name ignoring case`() {
        val beta = machine("beta", MuscleGroup.CHEST)
        val alpha = machine("Alpha", MuscleGroup.BACK)
        strength(session(60), alpha, 10.0, 10)
        strength(session(3), beta, 20.0, 8)
        val history = report().oneRmHistory
        assertEquals(listOf("Alpha", "beta"), history.map { it.machine.name })
        assertEquals(1, history.first().points.size)
    }

    @Test
    fun `a machine whose only sets are above twelve reps has no one rep max history`() {
        val gamma = machine("Gamma")
        strength(session(2), gamma, 10.0, 15)
        assertTrue(report().oneRmHistory.isEmpty())
    }

    @Test
    fun `warm-up sets are not in one rep max history`() {
        val delta = machine("Delta")
        strength(session(2), delta, 10.0, 8, SetType.WARMUP)
        assertTrue(report().oneRmHistory.isEmpty())
    }

    @Test
    fun `cardio machines are not in one rep max history`() {
        val run = machine("Run", MuscleGroup.CARDIO, MachineKind.CARDIO)
        cardio(session(2), run)
        assertTrue(report().oneRmHistory.isEmpty())
    }

    // suspiciousJumps

    @Test
    fun `a jump inside the period is listed for its machine`() {
        val curl = machine("Biceps curl", MuscleGroup.ARMS)
        strength(session(10), curl, 30.0, 10)
        strength(session(5), curl, 45.0, 10)
        val jump = report().suspiciousJumps.single()
        assertEquals("Biceps curl", jump.machine.name)
        assertEquals(30.0, jump.fromKg, 0.0)
        assertEquals(45.0, jump.toKg, 0.0)
        assertEquals(today.minusDays(5), jump.toDate)
    }

    @Test
    fun `a jump before the period is listed only for a period that reaches back to it`() {
        val curl = machine("Biceps curl", MuscleGroup.ARMS)
        strength(session(70), curl, 30.0, 10)
        strength(session(65), curl, 45.0, 10)
        assertTrue(report(StatsPeriod.FOUR_WEEKS).suspiciousJumps.isEmpty())
        assertEquals(1, report(StatsPeriod.ALL).suspiciousJumps.size)
    }

    @Test
    fun `suspicious jumps are listed newest first`() {
        val curl = machine("Biceps curl", MuscleGroup.ARMS)
        val row = machine("Row", MuscleGroup.BACK)
        strength(session(10), curl, 30.0, 10)
        strength(session(5), curl, 45.0, 10)
        strength(session(4), row, 40.0, 10)
        strength(session(2), row, 60.0, 10)
        assertEquals(listOf("Row", "Biceps curl"), report().suspiciousJumps.map { it.machine.name })
    }

    @Test
    fun `a jump confirmed by a later session is not listed`() {
        val curl = machine("Biceps curl", MuscleGroup.ARMS)
        strength(session(10), curl, 30.0, 10)
        strength(session(5), curl, 45.0, 10)
        strength(session(1), curl, 45.0, 10)
        assertTrue(report().suspiciousJumps.isEmpty())
    }

    // recentGroupSets

    @Test
    fun `recent group sets without any session are empty`() {
        machine("Chest press")
        assertEquals(0, report().recentGroupSets.weeks)
        assertTrue(report().recentGroupSets.setsPerWeek.isEmpty())
    }

    @Test
    fun `recent group sets on the day of the first session are one week`() {
        val chest = machine("Chest press")
        strength(session(0), chest, 50.0, 10)
        strength(session(0), chest, 50.0, 10)
        strength(session(0), chest, 50.0, 10)
        strength(session(0), chest, 50.0, 10)
        val recent = report().recentGroupSets
        assertEquals(1, recent.weeks)
        assertEquals(4.0, recent.setsPerWeek[MuscleGroup.CHEST]!!, 0.0)
    }

    @Test
    fun `recent group sets span the weeks since the first session`() {
        val chest = machine("Chest press")
        val row = machine("Row", MuscleGroup.BACK)
        strength(session(10), row, 40.0, 10)
        strength(session(10), row, 40.0, 10)
        repeat(4) { strength(session(2), chest, 50.0, 10) }
        val recent = report().recentGroupSets
        assertEquals(2, recent.weeks)
        assertEquals(2.0, recent.setsPerWeek[MuscleGroup.CHEST]!!, 0.0)
        assertEquals(1.0, recent.setsPerWeek[MuscleGroup.BACK]!!, 0.0)
    }

    @Test
    fun `recent group sets stop at four weeks and leave out sets before the window`() {
        val chest = machine("Chest press")
        strength(session(60), chest, 50.0, 10)
        strength(session(60), chest, 50.0, 10)
        repeat(8) { strength(session(0), chest, 50.0, 10) }
        val recent = report().recentGroupSets
        assertEquals(4, recent.weeks)
        assertEquals(2.0, recent.setsPerWeek[MuscleGroup.CHEST]!!, 0.0)
    }

    @Test
    fun `warm-up sets are not counted in recent group sets`() {
        val chest = machine("Chest press")
        val s = session(0)
        strength(s, chest, 50.0, 10)
        strength(s, chest, 50.0, 10)
        repeat(3) { strength(s, chest, 20.0, 10, SetType.WARMUP) }
        assertEquals(2.0, report().recentGroupSets.setsPerWeek[MuscleGroup.CHEST]!!, 0.0)
    }

    @Test
    fun `cardio is not counted in recent group sets`() {
        val chest = machine("Chest press")
        val run = machine("Run", MuscleGroup.CARDIO, MachineKind.CARDIO)
        val s = session(0)
        cardio(s, run)
        strength(s, chest, 50.0, 10)
        val setsPerWeek = report().recentGroupSets.setsPerWeek
        assertTrue(!setsPerWeek.containsKey(MuscleGroup.CARDIO))
        assertEquals(1.0, setsPerWeek[MuscleGroup.CHEST]!!, 0.0)
    }

    @Test
    fun `every group except cardio is present, with zero where nothing was done`() {
        val chest = machine("Chest press")
        strength(session(0), chest, 50.0, 10)
        val setsPerWeek = report().recentGroupSets.setsPerWeek
        assertEquals(MuscleGroup.entries.filter { it != MuscleGroup.CARDIO }.toSet(), setsPerWeek.keys)
        assertEquals(0.0, setsPerWeek[MuscleGroup.ARMS]!!, 0.0)
    }

    // defaults

    @Test
    fun `a report built without the new fields has empty defaults`() {
        val minimal = StatsReport(
            period = StatsPeriod.FOUR_WEEKS,
            from = today.minusDays(27),
            to = today,
            weeks = emptyList(),
            muscleWeeks = emptyList(),
            recency = emptyList(),
            consistency = Consistency(4, 0, 3, 0.0, null, 0),
            strength = emptyList(),
            overallStrengthPercent = null,
            records = emptyList(),
            plateaus = emptyList(),
            repRanges = emptyList(),
            times = SessionTimes(0, null, null),
        )
        assertTrue(minimal.oneRmHistory.isEmpty())
        assertTrue(minimal.suspiciousJumps.isEmpty())
        assertEquals(0, minimal.recentGroupSets.weeks)
        assertTrue(minimal.recentGroupSets.setsPerWeek.isEmpty())
    }
}
