package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class WorkingSetRulesTest {
    private val t0 = Instant.parse("2026-10-30T18:00:00Z")

    private fun set(
        id: Long,
        weightKg: Double?,
        reps: Int?,
        type: SetType = SetType.NORMAL,
        machineId: Long = 1L,
        position: Int = id.toInt(),
        sessionId: Long = 1L,
    ) = WorkoutSet(
        id = id,
        sessionId = sessionId,
        machineId = machineId,
        position = position,
        weightKg = weightKg,
        reps = reps,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = t0.plusSeconds(id * 60),
        type = type,
    )

    private fun warm(id: Long, weightKg: Double?, reps: Int?, machineId: Long = 1L, sessionId: Long = 1L) =
        set(id, weightKg, reps, SetType.WARMUP, machineId, sessionId = sessionId)

    // SetType and working()

    @Test
    fun `a set is normal by default`() {
        assertEquals(SetType.NORMAL, set(1, 40.0, 10).type)
        assertEquals(SetType.NORMAL, SetDraft(40.0, 10).type)
    }

    @Test
    fun `there are four set types`() {
        assertEquals(
            listOf(SetType.NORMAL, SetType.WARMUP, SetType.DROP, SetType.FAILURE),
            SetType.entries.toList(),
        )
    }

    @Test
    fun `only a warm-up is not a working set`() {
        assertTrue(set(1, 40.0, 10, SetType.NORMAL).isWorking)
        assertFalse(set(1, 40.0, 10, SetType.WARMUP).isWorking)
        assertTrue(set(1, 40.0, 10, SetType.DROP).isWorking)
        assertTrue(set(1, 40.0, 10, SetType.FAILURE).isWorking)
    }

    @Test
    fun `working drops warm-ups and keeps the order`() {
        val sets = listOf(
            set(1, 40.0, 10, SetType.DROP),
            warm(2, 20.0, 15),
            set(3, 40.0, 8),
            set(4, 40.0, 6, SetType.FAILURE),
        )

        assertEquals(listOf(1L, 3L, 4L), sets.working().map { it.id })
        assertTrue(emptyList<WorkoutSet>().working().isEmpty())
        assertTrue(listOf(warm(1, 20.0, 15)).working().isEmpty())
    }

    // RecordRules

    @Test
    fun `a warm-up is never a record`() {
        val earlier = listOf(set(1, 40.0, 10))

        assertNull(RecordRules.recordFor(warm(2, 100.0, 20), earlier))
        assertNull(RecordRules.recordFor(warm(2, 40.0, 30), earlier))
    }

    @Test
    fun `earlier warm-ups are ignored when looking for a record`() {
        val earlier = listOf(set(1, 40.0, 10), warm(2, 100.0, 5))

        assertEquals(Record.HeaviestWeight(45.0), RecordRules.recordFor(set(3, 45.0, 8), earlier))
    }

    @Test
    fun `a machine with only earlier warm-ups gives no record`() {
        assertNull(RecordRules.recordFor(set(2, 40.0, 10), listOf(warm(1, 20.0, 10))))
    }

    @Test
    fun `earlier warm-ups at the same weight do not make a reps record`() {
        val earlier = listOf(set(1, 40.0, 10), warm(2, 40.0, 4))

        assertEquals(Record.MostReps(40.0, 11), RecordRules.recordFor(set(3, 40.0, 11), earlier))
        assertNull(RecordRules.recordFor(set(3, 40.0, 10), earlier))
    }

    @Test
    fun `drop and failure sets can be records`() {
        val earlier = listOf(set(1, 40.0, 10))

        assertEquals(Record.HeaviestWeight(50.0), RecordRules.recordFor(set(2, 50.0, 5, SetType.FAILURE), earlier))
        assertEquals(Record.HeaviestWeight(50.0), RecordRules.recordFor(set(2, 50.0, 5, SetType.DROP), earlier))
    }

    @Test
    fun `countRecords skips warm-ups and ignores them as history`() {
        val earlier = listOf(set(1, 40.0, 10))
        val session = listOf(
            warm(2, 100.0, 20),
            set(3, 50.0, 8),
            warm(4, 120.0, 20),
            set(5, 45.0, 8),
        )

        assertEquals(1, RecordRules.countRecords(session, earlier))
    }

    @Test
    fun `countRecords with only warm-ups is zero`() {
        assertEquals(0, RecordRules.countRecords(listOf(warm(2, 100.0, 20)), listOf(set(1, 40.0, 10))))
    }

    // ProgressionRules

    private val target = RoutineItem(routineId = 1, machineId = 1, position = 0, targetSets = 2, repsMin = 8, repsMax = 12)

    @Test
    fun `suggest ignores warm-ups in the history`() {
        val last = listOf(
            set(1, 20.0, 15, SetType.WARMUP, position = 0),
            set(2, 40.0, 12, position = 1),
            set(3, 40.0, 12, position = 2),
        )

        assertEquals(Suggestion.Up(42.5, 8), ProgressionRules.suggest(target, 2.5, listOf(last)))
    }

    @Test
    fun `a session of only warm-ups is not history`() {
        val onlyWarmups = listOf(warm(1, 20.0, 12), warm(2, 20.0, 12))
        val real = listOf(set(3, 40.0, 12, position = 0), set(4, 40.0, 12, position = 1))

        assertEquals(Suggestion.Up(42.5, 8), ProgressionRules.suggest(target, 2.5, listOf(onlyWarmups, real)))
        assertNull(ProgressionRules.suggest(target, 2.5, listOf(onlyWarmups)))
    }

    @Test
    fun `a trailing warm-up does not stop an up suggestion`() {
        val last = listOf(
            set(1, 40.0, 12, position = 0),
            set(2, 40.0, 12, position = 1),
            set(3, 10.0, 3, SetType.WARMUP, position = 2),
        )

        assertEquals(Suggestion.Up(42.5, 8), ProgressionRules.suggest(target, 2.5, listOf(last)))
    }

    // ProgressRules

    private val machine = Machine(1, "Press", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false)
    private val bodyweight = Machine(2, "Pull-ups", MuscleGroup.BACK, MachineKind.BODYWEIGHT_REPS, 2.5, null, null, false)
    private val day1 = LocalDate.of(2026, 10, 1)
    private val day2 = LocalDate.of(2026, 11, 2)
    private val dates = mapOf(1L to day1, 2L to day2)

    @Test
    fun `points leave warm-ups out of the top set and the count`() {
        val sets = listOf(
            warm(1, 100.0, 20, sessionId = 1),
            set(2, 40.0, 10, sessionId = 1),
            set(3, 40.0, 8, sessionId = 1),
        )

        val point = ProgressRules.points(sets, dates, ProgressMetric.WEIGHT).single()

        assertEquals(40.0, point.value, 0.0)
        assertEquals(2L, point.topSet.id)
        assertEquals(2, point.setCount)
    }

    @Test
    fun `a session with only warm-ups gives no point`() {
        val sets = listOf(warm(1, 20.0, 15, sessionId = 1), set(2, 40.0, 10, sessionId = 2))

        val points = ProgressRules.points(sets, dates, ProgressMetric.WEIGHT)

        assertEquals(listOf(2L), points.map { it.sessionId })
    }

    @Test
    fun `reps points ignore warm-up reps`() {
        val sets = listOf(
            warm(1, 0.0, 30, machineId = 2, sessionId = 1),
            set(2, 0.0, 8, machineId = 2, sessionId = 1),
        )

        assertEquals(8.0, ProgressRules.points(sets, dates, ProgressMetric.REPS).single().value, 0.0)
    }

    @Test
    fun `a weighted warm-up does not turn a bodyweight machine into a weight chart`() {
        val onlyWarmupWeighted = listOf(warm(1, 10.0, 10, machineId = 2), set(2, 0.0, 8, machineId = 2))

        assertEquals(ProgressMetric.REPS, ProgressRules.metricFor(bodyweight, onlyWarmupWeighted))
        assertEquals(
            ProgressMetric.WEIGHT,
            ProgressRules.metricFor(bodyweight, listOf(set(3, 10.0, 8, machineId = 2))),
        )
    }

    @Test
    fun `change and improvement follow working sets only`() {
        val sets = listOf(
            set(1, 40.0, 10, sessionId = 1),
            warm(2, 100.0, 5, sessionId = 2),
            set(3, 40.0, 10, sessionId = 2),
        )
        val points = ProgressRules.points(sets, dates, ProgressMetric.WEIGHT)

        assertEquals(0.0, ProgressRules.change(points)!!, 0.0)
        assertNull(ProgressRules.improvement(points, today = day2))
        assertEquals(40.0, ProgressRules.best(points)!!.value, 0.0)
    }

    @Test
    fun `improvement sees a real rise even with a heavier warm-up earlier`() {
        val sets = listOf(
            set(1, 40.0, 10, sessionId = 1),
            warm(2, 100.0, 5, sessionId = 1),
            warm(3, 100.0, 5, sessionId = 2),
            set(4, 50.0, 10, sessionId = 2),
        )
        val points = ProgressRules.points(sets, dates, ProgressMetric.WEIGHT)

        assertEquals(Improvement(40.0, 50.0), ProgressRules.improvement(points, today = day2))
    }

    @Test
    fun `recordsInMonth counts working records and never a warm-up`() {
        val sets = listOf(
            set(1, 40.0, 10, sessionId = 1),
            warm(2, 100.0, 5, sessionId = 2),
            set(3, 45.0, 8, sessionId = 2),
        )

        assertEquals(1, ProgressRules.recordsInMonth(sets, dates, YearMonth.of(2026, 11)))
        assertEquals(0, ProgressRules.recordsInMonth(sets, dates, YearMonth.of(2026, 10)))
    }

    @Test
    fun `a warm-up heavier than the working sets leaves no record`() {
        val sets = listOf(
            set(1, 40.0, 10, sessionId = 1),
            warm(2, 100.0, 5, sessionId = 2),
        )

        assertEquals(0, ProgressRules.recordsInMonth(sets, dates, YearMonth.of(2026, 11)))
    }

    // SetSummary

    @Test
    fun `the summary leaves warm-ups out`() {
        val sets = listOf(warm(1, 20.0, 15), set(2, 40.0, 10), set(3, 40.0, 10))

        assertEquals(SetSummary.Uniform(2, 40.0, 10), SetSummary.of(sets))
    }

    @Test
    fun `the summary is null with only warm-ups`() {
        assertNull(SetSummary.of(listOf(warm(1, 20.0, 15), warm(2, 20.0, 15))))
    }

    @Test
    fun `drop and failure sets are in the summary`() {
        val sets = listOf(set(1, 40.0, 10), set(2, 30.0, 8, SetType.DROP))

        assertEquals(SetSummary.Mixed(listOf(SetDraft(40.0, 10), SetDraft(30.0, 8))), SetSummary.of(sets))
    }

    // LoggingRules

    @Test
    fun `prefill never takes a warm-up from this session`() {
        val thisSession = listOf(set(1, 40.0, 10), warm(2, 20.0, 15))

        assertEquals(SetDraft(40.0, 10), LoggingRules.prefill(emptyList(), thisSession))
    }

    @Test
    fun `prefill with only a warm-up today falls back to last time then the default`() {
        val today = listOf(warm(1, 20.0, 15))
        val lastTime = listOf(set(2, 40.0, 10))

        assertEquals(SetDraft(40.0, 10), LoggingRules.prefill(lastTime, today))
        assertEquals(SetDraft(0.0, LoggingRules.DEFAULT_REPS), LoggingRules.prefill(emptyList(), today))
    }

    @Test
    fun `prefill skips a leading warm-up in last time`() {
        val lastTime = listOf(warm(1, 20.0, 15), set(2, 40.0, 10))

        assertEquals(SetDraft(40.0, 10), LoggingRules.prefill(lastTime, emptyList()))
    }

    @Test
    fun `prefill after a warm-up prefers the suggestion over last time`() {
        val suggestion = Suggestion.Up(42.5, 8)

        assertEquals(
            SetDraft(42.5, 8),
            LoggingRules.prefill(listOf(set(1, 40.0, 12)), listOf(warm(2, 20.0, 15)), suggestion),
        )
    }

    @Test
    fun `sameAgain indexes working sets only`() {
        val lastTime = listOf(warm(1, 20.0, 15), set(2, 40.0, 10), set(3, 40.0, 8))

        assertEquals(SetDraft(40.0, 10), LoggingRules.sameAgain(lastTime, emptyList()))
        assertEquals(SetDraft(40.0, 10), LoggingRules.sameAgain(lastTime, listOf(warm(4, 20.0, 15))))
        assertEquals(SetDraft(40.0, 8), LoggingRules.sameAgain(lastTime, listOf(warm(4, 20.0, 15), set(5, 40.0, 10))))
        assertNull(LoggingRules.sameAgain(lastTime, listOf(set(5, 40.0, 10), set(6, 40.0, 8))))
    }

    @Test
    fun `sameAgain is null when last time had only warm-ups`() {
        assertNull(LoggingRules.sameAgain(listOf(warm(1, 20.0, 15)), emptyList()))
    }

    // RepeatSessionRules

    @Test
    fun `repeating a session repeats working sets only`() {
        val items = listOf(RoutineItem(1, 1, 0, 3, 8, 12), RoutineItem(1, 2, 1, 3, 8, 12))
        val last = listOf(
            warm(1, 20.0, 15),
            set(2, 40.0, 10),
            set(3, 40.0, 8, SetType.FAILURE),
            warm(4, 5.0, 15, machineId = 2),
        )

        val plan = RepeatSessionRules.plan(items, last, emptySet(), RepeatMode.SAME, emptyMap())

        assertEquals(listOf(1L, 1L), plan.map { it.machineId })
        assertEquals(listOf(10, 8), plan.map { it.reps })
        assertEquals(1, RepeatSessionRules.machineCount(plan))
    }

    // HealthExportRules

    private val session = Session(5, Instant.parse("2026-11-02T18:00:00Z"), Instant.parse("2026-11-02T19:00:00Z"), null, null)
    private val press = Machine(9, "Chest press", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false)

    private fun strength(id: Long, reps: Int, type: SetType = SetType.NORMAL) =
        set(id, 40.0, reps, type, machineId = 9, sessionId = 5)

    private fun export(sets: List<WorkoutSet>) = HealthExportRules.export(session, sets, mapOf(9L to press), null)

    @Test
    fun `health strength sets and reps count working sets`() {
        val export = export(listOf(strength(1, 15, SetType.WARMUP), strength(2, 10), strength(3, 6, SetType.FAILURE)))!!

        assertEquals(2, export.strengthSets)
        assertEquals(16, export.strengthReps)
    }

    @Test
    fun `health export with only warm-ups has no strength sets but is still strength training`() {
        val export = export(listOf(strength(1, 15, SetType.WARMUP)))!!

        assertEquals(0, export.strengthSets)
        assertEquals(0, export.strengthReps)
        assertEquals(HealthExerciseKind.STRENGTH_TRAINING, export.kind)
    }

    @Test
    fun `health version changes when a set type changes`() {
        val normal = export(listOf(strength(1, 10, SetType.NORMAL)))!!
        val warmup = export(listOf(strength(1, 10, SetType.WARMUP)))!!
        val drop = export(listOf(strength(1, 10, SetType.DROP)))!!

        assertNotEquals(normal.version, warmup.version)
        assertNotEquals(normal.version, drop.version)
        assertEquals(normal.version, export(listOf(strength(1, 10, SetType.NORMAL)))!!.version)
        assertNotNull(normal)
    }
}
