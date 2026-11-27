package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * "What matters now" from the C6 contract (section 1.4): frequency drop, low volume, new record, and the
 * selection in [InsightRules.findings]. The stall rule has its own test class. "Today" is Wednesday 2026-11-11.
 */
class InsightRulesTest {
    private val today = LocalDate.of(2026, 11, 11)

    private fun d(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    /** [days] consecutive days starting on the Monday [weekStart]. */
    private fun block(weekStart: LocalDate, days: Int): Set<LocalDate> =
        (0 until days).map { weekStart.plusDays(it.toLong()) }.toSet()

    private fun machine(id: Long, group: MuscleGroup, name: String = "Machine $id", archived: Boolean = false) =
        Machine(id, name, group, MachineKind.WEIGHT_REPS, 2.5, null, null, archived)

    private fun routine(id: Long, position: Int) = Routine(id, 1, "Routine $id", "R$id", position, emptySet())

    private fun item(routineId: Long, machineId: Long) = RoutineItem(routineId, machineId, 0, 3, 8, 12)

    private fun report(
        period: StatsPeriod = StatsPeriod.FOUR_WEEKS,
        groupSets: RecentGroupSets = RecentGroupSets(0, emptyMap()),
        history: List<MachineSeries> = emptyList(),
        records: List<RecordEvent> = emptyList(),
        jumps: List<SuspiciousJump> = emptyList(),
    ): StatsReport = StatsReport(
        period = period,
        from = today.minusDays(27),
        to = today,
        weeks = emptyList(),
        muscleWeeks = emptyList(),
        recency = emptyList(),
        consistency = Consistency(4, 0, 3, 0.0, null, 0),
        strength = emptyList(),
        overallStrengthPercent = null,
        records = records,
        plateaus = emptyList(),
        repRanges = emptyList(),
        times = SessionTimes(0, null, null),
        oneRmHistory = history,
        suspiciousJumps = jumps,
        recentGroupSets = groupSets,
    )

    /** Three training days in each of the four complete weeks before the recent two, and none in the current week. */
    private val steady: Set<LocalDate> =
        block(d(9, 28), 3) + block(d(10, 5), 3) + block(d(10, 12), 3) + block(d(10, 19), 3)

    // frequencyDrop

    @Test
    fun `a drop reports the recent and usual weekly training days`() {
        val drop = InsightRules.frequencyDrop(steady + block(d(10, 26), 1) + block(d(11, 2), 1), 3, today, DayOfWeek.MONDAY)
        assertEquals(Finding.FrequencyDrop(recentPerWeek = 1.0, usualPerWeek = 3.0, goal = 3), drop)
    }

    @Test
    fun `a drop needs the earliest training on or before the oldest of the six weeks`() {
        val onStart = steady + block(d(10, 26), 1) + block(d(11, 2), 1)
        assertTrue(InsightRules.frequencyDrop(onStart, 3, today, DayOfWeek.MONDAY) != null)

        // Same weeks without the training on the first day (Monday 28 Sept): the earliest is now Tuesday 29 Sept.
        val afterStart = onStart - d(9, 28)
        assertNull(InsightRules.frequencyDrop(afterStart, 3, today, DayOfWeek.MONDAY))
    }

    @Test
    fun `a drop of exactly one day a week is reported`() {
        val dates = steady + block(d(10, 26), 2) + block(d(11, 2), 2)
        val drop = InsightRules.frequencyDrop(dates, 3, today, DayOfWeek.MONDAY)
        assertEquals(Finding.FrequencyDrop(recentPerWeek = 2.0, usualPerWeek = 3.0, goal = 3), drop)
    }

    @Test
    fun `a drop of less than one day a week is not reported`() {
        // Recent weeks 3 and 2 days (mean 2.5), usual 3.0: the difference is 0.5.
        val dates = steady + block(d(10, 26), 2) + block(d(11, 2), 3)
        assertNull(InsightRules.frequencyDrop(dates, 3, today, DayOfWeek.MONDAY))
    }

    @Test
    fun `recent weeks at the goal are not a drop`() {
        val usualSix = block(d(9, 28), 6) + block(d(10, 5), 6) + block(d(10, 12), 6) + block(d(10, 19), 6)
        assertNull(InsightRules.frequencyDrop(usualSix + block(d(10, 26), 3) + block(d(11, 2), 3), 3, today, DayOfWeek.MONDAY))
    }

    @Test
    fun `recent weeks just under the goal are a drop`() {
        val usualSix = block(d(9, 28), 6) + block(d(10, 5), 6) + block(d(10, 12), 6) + block(d(10, 19), 6)
        val drop = InsightRules.frequencyDrop(usualSix + block(d(10, 26), 2) + block(d(11, 2), 2), 3, today, DayOfWeek.MONDAY)
        assertEquals(Finding.FrequencyDrop(recentPerWeek = 2.0, usualPerWeek = 6.0, goal = 3), drop)
    }

    @Test
    fun `a goal of zero is treated as one`() {
        val drop = InsightRules.frequencyDrop(steady, 0, today, DayOfWeek.MONDAY)
        assertEquals(Finding.FrequencyDrop(recentPerWeek = 0.0, usualPerWeek = 3.0, goal = 1), drop)
    }

    @Test
    fun `the current week does not count as a recent week`() {
        val dates = steady + block(d(10, 26), 1) + block(d(11, 2), 1) + block(d(11, 9), 2)
        val drop = InsightRules.frequencyDrop(dates, 3, today, DayOfWeek.MONDAY)
        assertEquals(Finding.FrequencyDrop(recentPerWeek = 1.0, usualPerWeek = 3.0, goal = 3), drop)
    }

    @Test
    fun `no drop without six weeks of history`() {
        val recent = block(d(10, 19), 3) + block(d(10, 26), 1) + block(d(11, 2), 1)
        assertNull(InsightRules.frequencyDrop(recent, 3, today, DayOfWeek.MONDAY))
    }

    // lowVolume

    @Test
    fun `low volume needs two weeks of sets`() {
        val r = report(groupSets = RecentGroupSets(1, mapOf(MuscleGroup.SHOULDERS to 4.0)))
        assertNull(InsightRules.lowVolume(r, emptyList(), emptyMap()))
    }

    @Test
    fun `a group under ten sets a week is reported`() {
        val r = report(groupSets = RecentGroupSets(2, mapOf(MuscleGroup.SHOULDERS to 4.0, MuscleGroup.CHEST to 12.0)))
        assertEquals(Finding.LowVolume(MuscleGroup.SHOULDERS, 4.0, null), InsightRules.lowVolume(r, emptyList(), emptyMap()))
    }

    @Test
    fun `exactly ten sets a week is not low`() {
        val r = report(groupSets = RecentGroupSets(2, mapOf(MuscleGroup.CHEST to 10.0)))
        assertNull(InsightRules.lowVolume(r, emptyList(), emptyMap()))
    }

    @Test
    fun `just under ten sets a week is low`() {
        val r = report(groupSets = RecentGroupSets(2, mapOf(MuscleGroup.CHEST to 9.5)))
        assertEquals(Finding.LowVolume(MuscleGroup.CHEST, 9.5, null), InsightRules.lowVolume(r, emptyList(), emptyMap()))
    }

    @Test
    fun `a group with no sets is not reported`() {
        val r = report(groupSets = RecentGroupSets(2, mapOf(MuscleGroup.SHOULDERS to 0.0)))
        assertNull(InsightRules.lowVolume(r, emptyList(), emptyMap()))
    }

    @Test
    fun `cardio is never reported as low volume`() {
        val r = report(groupSets = RecentGroupSets(2, mapOf(MuscleGroup.CARDIO to 2.0)))
        assertNull(InsightRules.lowVolume(r, emptyList(), emptyMap()))
    }

    @Test
    fun `the group with the fewest sets is chosen`() {
        val r = report(groupSets = RecentGroupSets(2, mapOf(MuscleGroup.CHEST to 8.0, MuscleGroup.SHOULDERS to 4.0)))
        assertEquals(MuscleGroup.SHOULDERS, (InsightRules.lowVolume(r, emptyList(), emptyMap()) as Finding.LowVolume).group)
    }

    @Test
    fun `ties go to the group earlier in enum order`() {
        val r = report(groupSets = RecentGroupSets(2, mapOf(MuscleGroup.ARMS to 5.0, MuscleGroup.SHOULDERS to 5.0)))
        assertEquals(MuscleGroup.SHOULDERS, (InsightRules.lowVolume(r, emptyList(), emptyMap()) as Finding.LowVolume).group)
    }

    @Test
    fun `the suggested routine is the one with the group, when there is a single routine`() {
        val r = report(groupSets = RecentGroupSets(2, mapOf(MuscleGroup.SHOULDERS to 4.0)))
        val routines = listOf(RoutineWithItems(routine(3, 0), listOf(item(3, 10L))))
        val finding = InsightRules.lowVolume(r, routines, mapOf(10L to MuscleGroup.SHOULDERS)) as Finding.LowVolume
        assertEquals(3L, finding.routine!!.id)
    }

    @Test
    fun `ties between routines go to the lowest position`() {
        val r = report(groupSets = RecentGroupSets(2, mapOf(MuscleGroup.SHOULDERS to 4.0)))
        // Given in reverse plan order: the routine at position 1 must still lose to the one at position 0.
        val routines = listOf(
            RoutineWithItems(routine(2, 1), listOf(item(2, 11L))),
            RoutineWithItems(routine(1, 0), listOf(item(1, 10L))),
        )
        val groups = mapOf(10L to MuscleGroup.SHOULDERS, 11L to MuscleGroup.SHOULDERS)
        val finding = InsightRules.lowVolume(r, routines, groups) as Finding.LowVolume
        assertEquals(1L, finding.routine!!.id)
    }

    // newRecord

    private fun heaviest(machine: Machine, date: LocalDate, kg: Double) =
        RecordEvent(machine, date, Record.HeaviestWeight(kg))

    private fun mostReps(machine: Machine, date: LocalDate, kg: Double, reps: Int) =
        RecordEvent(machine, date, Record.MostReps(kg, reps))

    @Test
    fun `no record in the last seven days gives no finding`() {
        val calf = machine(1, MuscleGroup.LEGS)
        assertNull(InsightRules.newRecord(report(records = listOf(heaviest(calf, d(11, 4), 120.0)))))
    }

    @Test
    fun `a record six days ago is in the window and a record seven days ago is not`() {
        val calf = machine(1, MuscleGroup.LEGS)
        val inside = InsightRules.newRecord(report(records = listOf(heaviest(calf, d(11, 5), 120.0))))
        assertEquals(Finding.NewRecord(RecordEvent(calf, d(11, 5), Record.HeaviestWeight(120.0)), 0), inside)
        assertNull(InsightRules.newRecord(report(records = listOf(heaviest(calf, d(11, 4), 120.0)))))
    }

    @Test
    fun `the heaviest weight of the window is named`() {
        val calf = machine(1, MuscleGroup.LEGS)
        val row = machine(2, MuscleGroup.BACK)
        val finding = InsightRules.newRecord(
            report(records = listOf(heaviest(row, d(11, 9), 100.0), heaviest(calf, d(11, 8), 120.0), mostReps(row, d(11, 10), 60.0, 12))),
        )!!
        assertEquals(calf, finding.event!!.machine)
        assertEquals(Record.HeaviestWeight(120.0), finding.event!!.record)
        assertEquals(2, finding.others)
    }

    @Test
    fun `ties on weight go to the newer record`() {
        val calf = machine(1, MuscleGroup.LEGS)
        val row = machine(2, MuscleGroup.BACK)
        val finding = InsightRules.newRecord(
            report(records = listOf(heaviest(calf, d(11, 5), 120.0), heaviest(row, d(11, 9), 120.0))),
        )!!
        assertEquals(row, finding.event!!.machine)
        assertEquals(d(11, 9), finding.event!!.date)
    }

    @Test
    fun `a record on a flagged machine is not named`() {
        val flagged = machine(1, MuscleGroup.ARMS, name = "Biceps curl")
        val calf = machine(2, MuscleGroup.LEGS)
        val jump = SuspiciousJump(flagged, d(11, 1), d(11, 4), 30.0, 45.0)
        val finding = InsightRules.newRecord(
            report(
                records = listOf(heaviest(flagged, d(11, 10), 200.0), heaviest(calf, d(11, 9), 120.0)),
                jumps = listOf(jump),
            ),
        )!!
        assertEquals(calf, finding.event!!.machine)
        assertEquals(1, finding.others)
    }

    @Test
    fun `without a heaviest weight the newest record is named`() {
        val row = machine(1, MuscleGroup.BACK)
        val finding = InsightRules.newRecord(
            report(records = listOf(mostReps(row, d(11, 6), 60.0, 10), mostReps(row, d(11, 9), 60.0, 11))),
        )!!
        assertEquals(d(11, 9), finding.event!!.date)
        assertEquals(1, finding.others)
    }

    @Test
    fun `when every record of the window is flagged no record is named`() {
        val flagged = machine(1, MuscleGroup.ARMS, name = "Biceps curl")
        val jump = SuspiciousJump(flagged, d(11, 1), d(11, 4), 30.0, 45.0)
        val finding = InsightRules.newRecord(
            report(
                records = listOf(heaviest(flagged, d(11, 10), 200.0), mostReps(flagged, d(11, 9), 45.0, 12)),
                jumps = listOf(jump),
            ),
        )!!
        assertNull(finding.event)
        assertEquals(2, finding.others)
        assertTrue(finding.isPositive)
    }

    // findings

    /** The owner's week: shoulders at 4 sets a week, lateral raise flat for 4 sessions at 10 kg, calf raise record. */
    private fun ownerReport(): StatsReport {
        val shoulders = machine(10, MuscleGroup.SHOULDERS, name = "Lateral raise")
        val calf = machine(20, MuscleGroup.LEGS, name = "Calf raise")
        val history = MachineSeries(shoulders, flatHistory(shoulders, 12.0, 14.0, 14.0, 14.0, 14.0))
        val records = listOf(heaviest(calf, d(11, 9), 120.0)) +
            (1..10).map { mostReps(machine(100L + it, MuscleGroup.CHEST, name = "Other $it"), d(11, 5), 20.0, 8) }
        return report(
            groupSets = RecentGroupSets(4, mapOf(MuscleGroup.SHOULDERS to 4.0, MuscleGroup.CHEST to 14.0, MuscleGroup.LEGS to 16.0)),
            history = listOf(history),
            records = records,
        )
    }

    /** Points on consecutive sessions, the last one yesterday; each is a 10 kg x 12 set (estimate 14.0) unless given. */
    private fun flatHistory(machine: Machine, vararg values: Double): List<SessionPoint> =
        values.mapIndexed { i, value ->
            val date = today.minusDays((values.size - 1 - i) * 3L + 1)
            val set = WorkoutSet(
                id = 500L + i, sessionId = 500L + i, machineId = machine.id, position = 0,
                weightKg = 10.0, reps = 12, seconds = null, calories = null, distanceM = null,
                loggedAt = Instant.EPOCH,
            )
            SessionPoint(500L + i, date, value, set, 1)
        }

    @Test
    fun `the owner's week gives low volume, the stall and the record in that order`() {
        val routines = listOf(RoutineWithItems(routine(1, 0), listOf(item(1, 10L))))
        val groups = mapOf(10L to MuscleGroup.SHOULDERS, 20L to MuscleGroup.LEGS)
        val findings = InsightRules.findings(
            ownerReport(), steady + block(d(10, 26), 3) + block(d(11, 2), 3), 3, DayOfWeek.MONDAY, routines, groups,
        )
        assertEquals(3, findings.size)

        val low = findings[0] as Finding.LowVolume
        assertEquals(MuscleGroup.SHOULDERS, low.group)
        assertEquals(4.0, low.setsPerWeek, 0.0)
        assertEquals(1L, low.routine!!.id)

        val stalled = findings[1] as Finding.Stalled
        assertEquals("Lateral raise", stalled.machine.name)
        assertEquals(StallDirection.FLAT, stalled.direction)
        assertEquals(4, stalled.sessions)
        assertEquals(StallHint.MoreWeight(12.5), stalled.hint)

        val record = findings[2] as Finding.NewRecord
        assertEquals("Calf raise", record.event!!.machine.name)
        assertEquals(Record.HeaviestWeight(120.0), record.event!!.record)
        assertEquals(10, record.others)
    }

    @Test
    fun `the record is left out when all three problems exist`() {
        val frequencyDrop = steady + block(d(10, 26), 1) + block(d(11, 2), 1)
        val findings = InsightRules.findings(
            ownerReport(), frequencyDrop, 3, DayOfWeek.MONDAY,
            listOf(RoutineWithItems(routine(1, 0), listOf(item(1, 10L)))),
            mapOf(10L to MuscleGroup.SHOULDERS, 20L to MuscleGroup.LEGS),
        )
        assertEquals(3, findings.size)
        assertTrue(findings[0] is Finding.FrequencyDrop)
        assertTrue(findings[1] is Finding.LowVolume)
        assertTrue(findings[2] is Finding.Stalled)
    }

    @Test
    fun `the record fills the room left by two problems`() {
        val report = report(
            groupSets = RecentGroupSets(2, mapOf(MuscleGroup.CHEST to 4.0)),
            records = listOf(heaviest(machine(1, MuscleGroup.LEGS), d(11, 9), 120.0)),
        )
        val frequencyDrop = steady + block(d(10, 26), 1) + block(d(11, 2), 1)
        val findings = InsightRules.findings(report, frequencyDrop, 3, DayOfWeek.MONDAY, emptyList(), emptyMap())
        assertEquals(3, findings.size)
        assertTrue(findings[0] is Finding.FrequencyDrop)
        assertTrue(findings[1] is Finding.LowVolume)
        assertTrue(findings[2] is Finding.NewRecord)
    }

    /** Training on target every recent week, so no frequency drop. */
    private val onGoal: Set<LocalDate> = steady + block(d(10, 26), 3) + block(d(11, 2), 3)

    @Test
    fun `a problem comes before the good news`() {
        val report = report(
            records = listOf(heaviest(machine(1, MuscleGroup.LEGS), d(11, 9), 120.0)),
            groupSets = RecentGroupSets(2, mapOf(MuscleGroup.CHEST to 4.0)),
        )
        val findings = InsightRules.findings(report, onGoal, 3, DayOfWeek.MONDAY, emptyList(), emptyMap())
        assertEquals(2, findings.size)
        assertTrue(findings[0] is Finding.LowVolume)
        assertTrue(findings[1] is Finding.NewRecord)
        assertTrue(findings.last().isPositive)
    }

    @Test
    fun `a positive finding is never before a problem`() {
        val findings = InsightRules.findings(ownerReport(), onGoal, 3, DayOfWeek.MONDAY, emptyList(), emptyMap())
        assertEquals(3, findings.size)
        assertTrue(findings.dropLast(1).none { it.isPositive })
        assertTrue(findings.last().isPositive)
    }

    @Test
    fun `findings are at most three`() {
        val frequencyDrop = steady + block(d(10, 26), 1) + block(d(11, 2), 1)
        val findings = InsightRules.findings(ownerReport(), frequencyDrop, 3, DayOfWeek.MONDAY, emptyList(), emptyMap())
        assertEquals(InsightRules.MAX_FINDINGS, findings.size)
        assertEquals(3, InsightRules.MAX_FINDINGS)
    }

    @Test
    fun `an empty report gives no findings`() {
        assertEquals(
            emptyList<Finding>(),
            InsightRules.findings(report(), emptySet(), 3, DayOfWeek.MONDAY, emptyList(), emptyMap()),
        )
    }
}
