package com.boratreino.feature.progress

import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.boratreino.feature.progress.ProgressSamples.date
import com.boratreino.feature.progress.ProgressSamples.machine
import com.boratreino.feature.progress.ProgressSamples.point
import com.boratreino.feature.progress.ProgressSamples.points
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.MachineKind

class ProgressStateTest {
    private val today = ProgressSamples.today

    private fun progress(
        id: Long,
        name: String,
        rows: List<Pair<java.time.LocalDate, Double>>,
        metric: ProgressMetric = ProgressMetric.WEIGHT,
    ): MachineProgress = MachineProgress(
        machine(id, name, MuscleGroup.LEGS, if (metric == ProgressMetric.REPS) MachineKind.BODYWEIGHT_REPS else MachineKind.WEIGHT_REPS),
        metric,
        points(id * 100, rows, metric),
    )

    /** Earlier best [from] (before 3 Oct), recent best [to] (after 3 Oct). */
    private fun improved(id: Long, name: String, from: Double, to: Double) = progress(
        id,
        name,
        listOf(date(9, 1) to from, date(10, 20) to to),
    )

    private fun overview(vararg machines: MachineProgress, trainings: Int = 0, records: Int = 0) =
        ProgressOverview(machines.toList(), trainings, records)

    @Test
    fun `the state is loaded and keeps the day it was built for`() {
        val state = buildProgressState(overview(), today)
        assertTrue(state.loaded)
        assertEquals(today, state.today)
    }

    @Test
    fun `an overview without machines is loaded and empty`() {
        val state = buildProgressState(overview(), today)
        assertTrue(state.machines.isEmpty())
        assertTrue(state.improved.isEmpty())
    }

    @Test
    fun `only machines whose recent best beats their earlier best are most improved`() {
        val state = buildProgressState(
            overview(
                improved(1, "Rising", 100.0, 110.0),
                improved(2, "Flat", 100.0, 100.0),
                improved(3, "Falling", 100.0, 90.0),
                progress(4, "Old only", listOf(date(9, 1) to 100.0)),
                progress(5, "Recent only", listOf(date(10, 20) to 110.0)),
            ),
            today,
        )
        assertEquals(listOf("Rising"), state.improved.map { it.machine.name })
    }

    @Test
    fun `most improved is ranked by percent first`() {
        val state = buildProgressState(
            overview(
                improved(1, "Press", 100.0, 110.0),      // 10 %
                improved(2, "Dip", 50.0, 60.0),          // 20 %, +10
                improved(3, "Curl", 100.0, 120.0),       // 20 %, +20
                improved(4, "Row", 200.0, 240.0),        // 20 %, +40
            ),
            today,
        )
        assertEquals(listOf("Row", "Curl", "Dip", "Press"), state.improved.map { it.machine.name })
    }

    @Test
    fun `equal percent and delta are ranked by name`() {
        val state = buildProgressState(
            overview(
                improved(1, "Biceps", 100.0, 110.0),
                improved(2, "Arm curl", 100.0, 110.0),
            ),
            today,
        )
        assertEquals(listOf("Arm curl", "Biceps"), state.improved.map { it.machine.name })
    }

    @Test
    fun `at most five machines are most improved`() {
        val state = buildProgressState(
            overview(
                improved(1, "A", 100.0, 101.0),
                improved(2, "B", 100.0, 102.0),
                improved(3, "C", 100.0, 103.0),
                improved(4, "D", 100.0, 104.0),
                improved(5, "E", 100.0, 105.0),
                improved(6, "F", 100.0, 106.0),
            ),
            today,
        )
        assertEquals(5, state.improved.size)
        assertEquals(listOf("F", "E", "D", "C", "B"), state.improved.map { it.machine.name })
    }

    @Test
    fun `an improved row carries its from, to and trend`() {
        val row = buildProgressState(overview(improved(1, "Press", 100.0, 110.0)), today).improved.single()
        assertEquals(100.0, row.improvement.from, 1e-9)
        assertEquals(110.0, row.improvement.to, 1e-9)
        assertEquals(ProgressMetric.WEIGHT, row.metric)
        assertEquals(listOf(100.0, 110.0), row.trend)
    }

    @Test
    fun `an improvement older than 30 days is not most improved`() {
        // The recent side (after 3 Oct) has no point, so nothing improved in the last 30 days.
        val state = buildProgressState(
            overview(progress(1, "Old", listOf(date(8, 1) to 100.0, date(9, 1) to 120.0))),
            today,
        )
        assertTrue(state.improved.isEmpty())
    }

    @Test
    fun `trainings and records this month are passed through`() {
        val state = buildProgressState(overview(trainings = 9, records = 4), today)
        assertEquals(9, state.trainingsThisMonth)
        assertEquals(4, state.recordsThisMonth)
    }

    @Test
    fun `a machine row carries its best, last trained date, metric and trend`() {
        val state = buildProgressState(
            overview(progress(1, "Leg press", listOf(date(9, 3) to 100.0, date(9, 20) to 120.0, date(10, 30) to 115.0))),
            today,
        )
        val row = state.machines.single()
        assertEquals("Leg press", row.machine.name)
        assertEquals(ProgressMetric.WEIGHT, row.metric)
        assertEquals(120.0, row.best, 1e-9)
        assertEquals(date(10, 30), row.lastTrained)
        assertEquals(listOf(100.0, 120.0, 115.0), row.trend)
    }

    @Test
    fun `a reps machine row reports its best in reps`() {
        val state = buildProgressState(
            overview(progress(1, "Pull-ups", listOf(date(9, 2) to 8.0, date(10, 30) to 12.0), ProgressMetric.REPS)),
            today,
        )
        val row = state.machines.single()
        assertEquals(ProgressMetric.REPS, row.metric)
        assertEquals(12.0, row.best, 1e-9)
    }

    @Test
    fun `the trend holds the last twelve values, oldest first`() {
        val rows = (1..15).map { date(9, 1).plusDays(it.toLong()) to it.toDouble() }
        val state = buildProgressState(overview(progress(1, "Long", rows)), today)
        assertEquals((4..15).map { it.toDouble() }, state.machines.single().trend)
    }

    @Test
    fun `machines without sessions are not listed`() {
        val state = buildProgressState(
            overview(
                progress(1, "Empty", emptyList()),
                progress(2, "Used", listOf(date(10, 1) to 50.0)),
            ),
            today,
        )
        assertEquals(listOf("Used"), state.machines.map { it.machine.name })
    }

    @Test
    fun `machines keep the order the overview gives them`() {
        val state = buildProgressState(
            overview(
                progress(1, "Zed", listOf(date(10, 31) to 50.0)),
                progress(2, "Alpha", listOf(date(10, 1) to 50.0)),
            ),
            today,
        )
        assertEquals(listOf("Zed", "Alpha"), state.machines.map { it.machine.name })
    }

    @Test
    fun `the sample overview is most improved in three rows`() {
        val state = buildProgressState(ProgressSamples.overview, today)
        assertEquals(3, state.improved.size)
        assertFalse(state.machines.isEmpty())
        assertEquals(6, state.machines.size)
    }
}
