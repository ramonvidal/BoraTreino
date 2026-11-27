package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The stall candidate of "What matters now" (C6 contract, section 1.4). "Today" is Wednesday 2026-11-11. */
class InsightStallTest {
    private val today = LocalDate.of(2026, 11, 11)
    private var nextId = 1000L

    private fun machine(
        id: Long,
        name: String,
        group: MuscleGroup = MuscleGroup.SHOULDERS,
        step: Double = 2.5,
        archived: Boolean = false,
    ) = Machine(id, name, group, MachineKind.WEIGHT_REPS, step, null, null, archived)

    private fun report(
        history: List<MachineSeries> = emptyList(),
        jumps: List<SuspiciousJump> = emptyList(),
        groupSets: Map<MuscleGroup, Double> = emptyMap(),
    ): StatsReport = StatsReport(
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
        oneRmHistory = history,
        suspiciousJumps = jumps,
        recentGroupSets = RecentGroupSets(4, groupSets),
    )

    /**
     * One point per value, oldest first, three days apart. The last session is [lastAgo] days before today; its top set
     * is [lastKg] x [lastReps] and the others are 10 kg x 12.
     */
    private fun series(
        machine: Machine,
        vararg values: Double,
        lastAgo: Long = 1,
        lastKg: Double = 10.0,
        lastReps: Int = 12,
    ): MachineSeries {
        val points = values.mapIndexed { i, value ->
            val last = i == values.size - 1
            val id = nextId++
            val set = WorkoutSet(
                id = id, sessionId = id, machineId = machine.id, position = 0,
                weightKg = if (last) lastKg else 10.0, reps = if (last) lastReps else 12,
                seconds = null, calories = null, distanceM = null, loggedAt = Instant.EPOCH,
            )
            val date = today.minusDays(lastAgo + (values.size - 1 - i) * 3L)
            SessionPoint(id, date, value, set, 1)
        }
        return MachineSeries(machine, points)
    }

    private fun stalled(vararg series: MachineSeries, jumps: List<SuspiciousJump> = emptyList(), groups: Map<MuscleGroup, Double> = emptyMap()) =
        InsightRules.stalled(report(series.toList(), jumps, groups))

    // Candidates and the first maximum

    @Test
    fun `a stall needs four points of history`() {
        val m = machine(1, "Lateral raise")
        assertNull(stalled(series(m, 14.0, 14.0, 14.0)))
        assertEquals("Lateral raise", stalled(series(m, 12.0, 14.0, 14.0, 14.0))!!.machine.name)
    }

    @Test
    fun `the stall counts the sessions from the first best one to the last`() {
        val m = machine(1, "Lateral raise")
        val finding = stalled(series(m, 12.0, 14.0, 14.0, 14.0, 14.0))!!
        assertEquals(4, finding.sessions)
    }

    @Test
    fun `a best session only two sessions back is not a stall`() {
        val m = machine(1, "Lateral raise")
        assertNull(stalled(series(m, 12.0, 12.0, 14.0, 14.0)))
    }

    @Test
    fun `a best session at the start of the history counts all sessions`() {
        val m = machine(1, "Lateral raise")
        assertEquals(4, stalled(series(m, 14.0, 12.0, 12.0, 12.0))!!.sessions)
    }

    @Test
    fun `the stalled finding carries the best and last estimates, the last top set and the machine`() {
        val m = machine(1, "Lateral raise")
        val s = series(m, 12.0, 14.0, 14.0, 14.0, 14.0)
        val finding = stalled(s)!!
        assertEquals(m, finding.machine)
        assertEquals(14.0, finding.bestEstimate, 0.0)
        assertEquals(14.0, finding.lastEstimate, 0.0)
        assertEquals(s.points.last().topSet, finding.lastTop)
    }

    // Direction and hint

    @Test
    fun `a last session at ninety five percent of the best is falling with no hint`() {
        val m = machine(1, "Lateral raise")
        val finding = stalled(series(m, 150.0, 200.0, 200.0, 190.0))!!
        assertEquals(StallDirection.FALLING, finding.direction)
        assertNull(finding.hint)
        assertEquals(200.0, finding.bestEstimate, 0.0)
        assertEquals(190.0, finding.lastEstimate, 0.0)
    }

    @Test
    fun `a last session just above ninety five percent of the best is flat`() {
        val m = machine(1, "Lateral raise")
        val finding = stalled(series(m, 150.0, 200.0, 200.0, 191.0))!!
        assertEquals(StallDirection.FLAT, finding.direction)
        assertEquals(StallHint.MoreWeight(12.5), finding.hint)
    }

    @Test
    fun `a flat stall at ten reps or fewer suggests two more reps`() {
        val m = machine(1, "Lateral raise")
        val finding = stalled(series(m, 12.0, 14.0, 14.0, 14.0, lastKg = 10.0, lastReps = 10))!!
        assertEquals(StallHint.MoreReps(12), finding.hint)
    }

    @Test
    fun `a flat stall at eleven reps suggests one machine step more weight`() {
        val m = machine(1, "Lateral raise", step = 5.0)
        val finding = stalled(series(m, 12.0, 14.0, 14.0, 14.0, lastKg = 10.0, lastReps = 11))!!
        assertEquals(StallHint.MoreWeight(15.0), finding.hint)
    }

    @Test
    fun `a flat stall at twelve reps suggests a machine step more weight`() {
        val m = machine(1, "Lateral raise", step = 2.5)
        val finding = stalled(series(m, 12.0, 14.0, 14.0, 14.0, lastKg = 10.0, lastReps = 12))!!
        assertEquals(StallHint.MoreWeight(12.5), finding.hint)
    }

    // Window and exclusions

    @Test
    fun `a last session twenty days ago is active`() {
        val m = machine(1, "Lateral raise")
        assertEquals("Lateral raise", stalled(series(m, 12.0, 14.0, 14.0, 14.0, lastAgo = 20))!!.machine.name)
    }

    @Test
    fun `a last session twenty one days ago is not active`() {
        val m = machine(1, "Lateral raise")
        assertNull(stalled(series(m, 12.0, 14.0, 14.0, 14.0, lastAgo = 21)))
    }

    @Test
    fun `an archived machine is never stalled`() {
        val m = machine(1, "Lateral raise", archived = true)
        assertNull(stalled(series(m, 12.0, 14.0, 14.0, 14.0)))
    }

    @Test
    fun `a flagged machine is never stalled`() {
        val m = machine(1, "Biceps curl", group = MuscleGroup.ARMS)
        val jump = SuspiciousJump(m, today.minusDays(10), today.minusDays(5), 30.0, 45.0)
        assertNull(stalled(series(m, 12.0, 14.0, 14.0, 14.0), jumps = listOf(jump)))
    }

    // Ordering

    @Test
    fun `the stall with more sessions is chosen`() {
        val three = machine(1, "Lateral raise")
        val four = machine(2, "Front raise")
        assertEquals("Front raise", stalled(series(three, 12.0, 14.0, 14.0, 14.0), series(four, 12.0, 14.0, 14.0, 14.0, 14.0))!!.machine.name)
    }

    @Test
    fun `ties on sessions go to the group with fewer sets`() {
        val chest = machine(1, "Chest press", group = MuscleGroup.CHEST)
        val shoulders = machine(2, "Lateral raise", group = MuscleGroup.SHOULDERS)
        val finding = stalled(
            series(chest, 12.0, 14.0, 14.0, 14.0),
            series(shoulders, 12.0, 14.0, 14.0, 14.0),
            groups = mapOf(MuscleGroup.CHEST to 12.0, MuscleGroup.SHOULDERS to 4.0),
        )!!
        assertEquals("Lateral raise", finding.machine.name)
    }

    @Test
    fun `a group with no sets counts as zero in the tie-break`() {
        val chest = machine(1, "Chest press", group = MuscleGroup.CHEST)
        val arms = machine(2, "Curl", group = MuscleGroup.ARMS)
        val finding = stalled(
            series(chest, 12.0, 14.0, 14.0, 14.0),
            series(arms, 12.0, 14.0, 14.0, 14.0),
            groups = mapOf(MuscleGroup.CHEST to 12.0),
        )!!
        assertEquals("Curl", finding.machine.name)
    }

    @Test
    fun `ties on sessions and sets go to the name ignoring case`() {
        val beta = machine(1, "beta", group = MuscleGroup.ARMS)
        val alpha = machine(2, "Alpha", group = MuscleGroup.BACK)
        val finding = stalled(series(beta, 12.0, 14.0, 14.0, 14.0), series(alpha, 12.0, 14.0, 14.0, 14.0))!!
        assertEquals("Alpha", finding.machine.name)
    }

    @Test
    fun `no history gives no stall`() {
        assertNull(stalled())
    }
}
