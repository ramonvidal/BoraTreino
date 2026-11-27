package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The Strength section of Overview (C6 contract, section 1.5). "Today" is Wednesday 2026-11-11. */
class StrengthSummaryRulesTest {
    private val today = LocalDate.of(2026, 11, 11)

    private fun machine(id: Long, name: String, group: MuscleGroup = MuscleGroup.CHEST) =
        Machine(id, name, group, MachineKind.WEIGHT_REPS, 2.5, null, null, false)

    private fun trend(machine: Machine, changePercent: Double) = StrengthTrend(
        machine = machine,
        before = 100.0,
        after = 100.0 + changePercent,
        changeKg = changePercent,
        changePercent = changePercent,
    )

    private fun report(
        period: StatsPeriod = StatsPeriod.FOUR_WEEKS,
        strength: List<StrengthTrend> = emptyList(),
        history: List<MachineSeries> = emptyList(),
        jumps: List<SuspiciousJump> = emptyList(),
    ): StatsReport {
        val from = when (period) {
            StatsPeriod.ONE_WEEK -> today.minusDays(6)
            else -> today.minusDays(27)
        }
        return StatsReport(
            period = period,
            from = from,
            to = today,
            weeks = emptyList(),
            muscleWeeks = emptyList(),
            recency = emptyList(),
            consistency = Consistency(4, 0, 3, 0.0, null, 0),
            strength = strength,
            overallStrengthPercent = null,
            records = emptyList(),
            plateaus = emptyList(),
            repRanges = emptyList(),
            times = SessionTimes(0, null, null),
            oneRmHistory = history,
            suspiciousJumps = jumps,
        )
    }

    /** One point per value, the last one yesterday, one day apart; the top set weighs the value so it can be told apart. */
    private fun history(machine: Machine, values: List<Double>, extraBefore: Double? = null): MachineSeries {
        val points = values.mapIndexed { i, value ->
            val id = machine.id * 1000 + i
            val set = WorkoutSet(
                id = id, sessionId = id, machineId = machine.id, position = 0,
                weightKg = value, reps = 5, seconds = null, calories = null, distanceM = null,
                loggedAt = Instant.EPOCH,
            )
            SessionPoint(id, today.minusDays((values.size - i).toLong()), value, set, 1)
        }
        val before = extraBefore?.let {
            val id = machine.id * 1000 + 999
            val set = WorkoutSet(
                id = id, sessionId = id, machineId = machine.id, position = 0,
                weightKg = it, reps = 5, seconds = null, calories = null, distanceM = null,
                loggedAt = Instant.EPOCH,
            )
            listOf(SessionPoint(id, today.minusDays(40), it, set, 1))
        }.orEmpty()
        return MachineSeries(machine, before + points)
    }

    private fun jump(machine: Machine) = SuspiciousJump(machine, today.minusDays(10), today.minusDays(5), 30.0, 45.0)

    // Direction thresholds

    @Test
    fun `a change of exactly plus one percent is rising`() {
        val m = machine(1, "Bench")
        val row = StrengthSummaryRules.summary(report(strength = listOf(trend(m, 1.0)))).risers.single()
        assertEquals(StrengthDirection.RISING, row.direction)
    }

    @Test
    fun `a change just under plus one percent is flat`() {
        val m = machine(1, "Bench")
        val summary = StrengthSummaryRules.summary(report(strength = listOf(trend(m, 0.99))))
        assertTrue(summary.risers.isEmpty())
        assertEquals(StrengthDirection.FLAT, summary.flatOrFalling.single().direction)
    }

    @Test
    fun `a change of exactly minus one percent is falling`() {
        val m = machine(1, "Bench")
        val summary = StrengthSummaryRules.summary(report(strength = listOf(trend(m, -1.0))))
        assertTrue(summary.risers.isEmpty())
        assertEquals(StrengthDirection.FALLING, summary.flatOrFalling.single().direction)
    }

    @Test
    fun `a change just above minus one percent is flat`() {
        val m = machine(1, "Bench")
        val summary = StrengthSummaryRules.summary(report(strength = listOf(trend(m, -0.99))))
        assertEquals(StrengthDirection.FLAT, summary.flatOrFalling.single().direction)
    }

    @Test
    fun `a row carries the before, after and percent of its trend`() {
        val m = machine(1, "Bench")
        val row = StrengthSummaryRules.summary(report(strength = listOf(trend(m, 5.0)))).risers.single()
        assertEquals(m, row.machine)
        assertEquals(100.0, row.before, 0.0)
        assertEquals(105.0, row.after, 0.0)
        assertEquals(5.0, row.changePercent, 0.0)
    }

    // Top three

    @Test
    fun `the risers are the three biggest rises, best first`() {
        val strength = listOf(
            trend(machine(1, "A"), 5.0),
            trend(machine(2, "B"), 10.0),
            trend(machine(3, "C"), 2.0),
            trend(machine(4, "D"), 8.0),
        )
        val risers = StrengthSummaryRules.summary(report(strength = strength)).risers
        assertEquals(listOf("B", "D", "A"), risers.map { it.machine.name })
    }

    @Test
    fun `the flat or falling rows are the three biggest falls, worst first`() {
        val strength = listOf(
            trend(machine(1, "A"), -6.0),
            trend(machine(2, "B"), -3.0),
            trend(machine(3, "C"), -0.5),
            trend(machine(4, "D"), -12.0),
            trend(machine(5, "E"), -2.0),
        )
        val rows = StrengthSummaryRules.summary(report(strength = strength)).flatOrFalling
        assertEquals(listOf("D", "A", "B"), rows.map { it.machine.name })
    }

    @Test
    fun `ties in change are ordered by name ignoring case`() {
        val strength = listOf(trend(machine(1, "zeta"), 5.0), trend(machine(2, "Alpha"), 5.0))
        val risers = StrengthSummaryRules.summary(report(strength = strength)).risers
        assertEquals(listOf("Alpha", "zeta"), risers.map { it.machine.name })
    }

    // Flagged machines

    @Test
    fun `a flagged machine is left out of the lists and shown to check`() {
        val flagged = machine(1, "Biceps curl", MuscleGroup.ARMS)
        val other = machine(2, "Bench")
        val summary = StrengthSummaryRules.summary(
            report(strength = listOf(trend(flagged, 20.0), trend(other, 2.0)), jumps = listOf(jump(flagged))),
        )
        assertEquals(listOf("Bench"), summary.risers.map { it.machine.name })
        assertEquals(listOf(flagged), summary.toCheck.map { it.machine })
    }

    @Test
    fun `a flagged machine that is falling is left out of the flat or falling rows`() {
        val flagged = machine(1, "Biceps curl", MuscleGroup.ARMS)
        val summary = StrengthSummaryRules.summary(
            report(strength = listOf(trend(flagged, -5.0)), jumps = listOf(jump(flagged))),
        )
        assertTrue(summary.flatOrFalling.isEmpty())
        assertEquals(1, summary.toCheck.size)
    }

    @Test
    fun `at most three jumps are shown to check`() {
        val machines = (1L..4L).map { machine(it, "Machine $it") }
        val summary = StrengthSummaryRules.summary(report(jumps = machines.map(::jump)))
        assertEquals(3, summary.toCheck.size)
    }

    // ONE_WEEK

    @Test
    fun `one week has no risers and no flat or falling rows`() {
        val summary = StrengthSummaryRules.summary(report(period = StatsPeriod.ONE_WEEK))
        assertTrue(summary.risers.isEmpty())
        assertTrue(summary.flatOrFalling.isEmpty())
    }

    // Sparkline and session counts

    @Test
    fun `the sparkline is the last eight estimates of the period, oldest first`() {
        val m = machine(1, "Bench")
        val values = (1..10).map { it.toDouble() }
        val row = StrengthSummaryRules.summary(
            report(strength = listOf(trend(m, 5.0)), history = listOf(history(m, values))),
        ).risers.single()
        assertEquals(listOf(3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0), row.sparkline)
    }

    @Test
    fun `the sparkline leaves out estimates from before the period`() {
        val m = machine(1, "Bench")
        val values = (1..3).map { it.toDouble() }
        val row = StrengthSummaryRules.summary(
            report(strength = listOf(trend(m, 5.0)), history = listOf(history(m, values, extraBefore = 99.0))),
        ).risers.single()
        assertEquals(listOf(1.0, 2.0, 3.0), row.sparkline)
    }

    @Test
    fun `sessions in the period count only the points inside it`() {
        val m = machine(1, "Bench")
        val values = (1..10).map { it.toDouble() }
        val row = StrengthSummaryRules.summary(
            report(strength = listOf(trend(m, 5.0)), history = listOf(history(m, values, extraBefore = 99.0))),
        ).risers.single()
        assertEquals(10, row.sessionsInPeriod)
    }

    @Test
    fun `the last top set is the top set of the last point in the period`() {
        val m = machine(1, "Bench")
        val values = listOf(60.0, 62.5, 65.0)
        val series = history(m, values, extraBefore = 99.0)
        val row = StrengthSummaryRules.summary(
            report(strength = listOf(trend(m, 5.0)), history = listOf(series)),
        ).risers.single()
        assertEquals(series.points.last().topSet, row.lastTop)
        assertEquals(65.0, row.lastTop!!.weightKg!!, 0.0)
    }
}
