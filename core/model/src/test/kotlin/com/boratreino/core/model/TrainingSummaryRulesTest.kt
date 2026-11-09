package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TrainingSummaryRulesTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val start: Instant = Instant.parse("2026-11-09T18:00:00Z")

    private val legPress = machine(1L, "Leg press", MachineKind.WEIGHT_REPS)
    private val row = machine(2L, "Seated row", MachineKind.WEIGHT_REPS)
    private val bike = machine(3L, "Bike", MachineKind.CARDIO)
    private val machines = listOf(legPress, row, bike)

    private fun machine(id: Long, name: String, kind: MachineKind) = Machine(
        id = id,
        name = name,
        muscleGroup = MuscleGroup.LEGS,
        kind = kind,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private fun session(
        id: Long = 10L,
        startedAt: Instant = start,
        endedAt: Instant? = start.plusSeconds(62 * 60L),
        routineId: Long? = 5L,
    ) = Session(id = id, startedAt = startedAt, endedAt = endedAt, routineId = routineId, note = null)

    /** A logged set; [at] seconds after [start] unless given. */
    private fun set(
        id: Long,
        machineId: Long = 1L,
        weightKg: Double? = 100.0,
        reps: Int? = 10,
        type: SetType = SetType.NORMAL,
        at: Long = id,
        seconds: Int? = null,
        position: Int = 0,
        sessionId: Long = 10L,
    ) = WorkoutSet(
        id = id,
        sessionId = sessionId,
        machineId = machineId,
        position = position,
        weightKg = weightKg,
        reps = reps,
        seconds = seconds,
        calories = null,
        distanceM = null,
        loggedAt = start.plusSeconds(at),
        type = type,
    )

    private fun build(
        session: Session = session(),
        routineName: String? = "Routine A",
        sets: List<WorkoutSet> = emptyList(),
        earlier: Map<Long, List<WorkoutSet>> = emptyMap(),
        previous: Pair<Session, List<WorkoutSet>>? = null,
        zoneId: ZoneId = zone,
    ): TrainingSummary = TrainingSummaryRules.build(
        session = session,
        routineName = routineName,
        sets = sets,
        machines = machines,
        earlierSetsByMachine = earlier,
        previous = previous,
        zone = zoneId,
    )

    // Working sets, volume and machines

    @Test
    fun `warm-up sets are left out of the set count, the volume and the machines`() {
        val summary = build(
            sets = listOf(
                set(1, machineId = 1L, weightKg = 60.0, reps = 12, type = SetType.WARMUP),
                set(2, machineId = 1L, weightKg = 100.0, reps = 10),
                set(3, machineId = 2L, weightKg = 40.0, reps = 8, type = SetType.WARMUP),
            ),
        )

        assertEquals(1, summary.workingSets)
        assertEquals(1000.0, summary.volumeKg, 0.001)
        assertEquals(1, summary.machineCount)
    }

    @Test
    fun `drop and failure sets are working sets`() {
        val summary = build(
            sets = listOf(
                set(1, weightKg = 100.0, reps = 10, type = SetType.NORMAL),
                set(2, weightKg = 80.0, reps = 6, type = SetType.DROP),
                set(3, weightKg = 80.0, reps = 4, type = SetType.FAILURE),
            ),
        )

        assertEquals(3, summary.workingSets)
        assertEquals(1000.0 + 480.0 + 320.0, summary.volumeKg, 0.001)
    }

    @Test
    fun `volume is weight times reps summed over the working sets`() {
        val summary = build(
            sets = listOf(
                set(1, weightKg = 42.5, reps = 12),
                set(2, weightKg = 50.0, reps = 8),
            ),
        )

        assertEquals(42.5 * 12 + 50.0 * 8, summary.volumeKg, 0.001)
    }

    @Test
    fun `a null weight or a null reps count as zero volume`() {
        val summary = build(
            sets = listOf(
                set(1, weightKg = null, reps = 12),
                set(2, weightKg = 30.0, reps = null),
                set(3, weightKg = 20.0, reps = 5),
            ),
        )

        assertEquals(100.0, summary.volumeKg, 0.001)
        assertEquals(3, summary.workingSets)
    }

    @Test
    fun `cardio adds nothing to the volume`() {
        val summary = build(
            sets = listOf(
                set(1, machineId = 3L, weightKg = 50.0, reps = 10, seconds = 600),
                set(2, machineId = 1L, weightKg = 100.0, reps = 10),
            ),
        )

        assertEquals(1000.0, summary.volumeKg, 0.001)
    }

    @Test
    fun `a cardio-only session has no volume`() {
        val summary = build(
            sets = listOf(set(1, machineId = 3L, weightKg = null, reps = null, seconds = 1200)),
        )

        assertEquals(0.0, summary.volumeKg, 0.001)
        assertEquals(1, summary.machineCount)
    }

    @Test
    fun `machine count is the number of distinct machines with working sets`() {
        val summary = build(
            sets = listOf(
                set(1, machineId = 1L),
                set(2, machineId = 1L),
                set(3, machineId = 2L),
                set(4, machineId = 2L, type = SetType.WARMUP),
            ),
        )

        assertEquals(2, summary.machineCount)
        assertEquals(3, summary.workingSets)
    }

    @Test
    fun `an empty session has no sets, no volume, no machines and no records`() {
        val summary = build(sets = emptyList())

        assertEquals(0, summary.workingSets)
        assertEquals(0.0, summary.volumeKg, 0.001)
        assertEquals(0, summary.machineCount)
        assertTrue(summary.records.isEmpty())
    }

    // Duration

    @Test
    fun `duration is the time between start and end in whole minutes`() {
        val summary = build(session = session(endedAt = start.plusSeconds(62 * 60L)))

        assertEquals(62, summary.durationMinutes)
    }

    @Test
    fun `duration rounds to the nearest minute`() {
        assertEquals(61, build(session = session(endedAt = start.plusSeconds(61 * 60L + 29))).durationMinutes)
        assertEquals(62, build(session = session(endedAt = start.plusSeconds(61 * 60L + 30))).durationMinutes)
    }

    @Test
    fun `an open session has no duration`() {
        assertNull(build(session = session(endedAt = null)).durationMinutes)
    }

    @Test
    fun `a session added by hand with the same start and end has no duration`() {
        assertNull(build(session = session(endedAt = start)).durationMinutes)
    }

    @Test
    fun `a session shorter than half a minute is shown as one minute`() {
        assertEquals(1, build(session = session(endedAt = start.plusSeconds(10))).durationMinutes)
    }

    @Test
    fun `a session longer than six hours is capped at 360 minutes`() {
        val longer = Duration.ofHours(9).toSeconds()
        assertEquals(360, build(session = session(endedAt = start.plusSeconds(longer))).durationMinutes)
    }

    @Test
    fun `a session of exactly six hours is 360 minutes`() {
        assertEquals(360, build(session = session(endedAt = start.plusSeconds(6 * 3600L))).durationMinutes)
    }

    // Records

    @Test
    fun `a first ever set on a machine is not a record`() {
        val summary = build(sets = listOf(set(1, weightKg = 100.0, reps = 10)))

        assertTrue(summary.records.isEmpty())
    }

    @Test
    fun `a heavier set than every earlier one is a heaviest-weight record with its machine and date`() {
        val earlier = mapOf(1L to listOf(set(-5, machineId = 1L, weightKg = 100.0, reps = 10, sessionId = 1L)))
        val summary = build(sets = listOf(set(1, machineId = 1L, weightKg = 105.0, reps = 5)), earlier = earlier)

        assertEquals(listOf(RecordEvent(legPress, LocalDate.of(2026, 11, 9), Record.HeaviestWeight(105.0))), summary.records)
    }

    @Test
    fun `records are replayed in order and listed newest first`() {
        val earlier = mapOf(1L to listOf(set(-5, machineId = 1L, weightKg = 100.0, reps = 10, sessionId = 1L)))
        val summary = build(
            sets = listOf(
                set(1, machineId = 1L, weightKg = 105.0, reps = 5, at = 1),
                set(2, machineId = 1L, weightKg = 105.0, reps = 8, at = 2),
            ),
            earlier = earlier,
        )

        assertEquals(
            listOf(
                RecordEvent(legPress, LocalDate.of(2026, 11, 9), Record.MostReps(105.0, 8)),
                RecordEvent(legPress, LocalDate.of(2026, 11, 9), Record.HeaviestWeight(105.0)),
            ),
            summary.records,
        )
    }

    @Test
    fun `a set in this session can be a record against an earlier set of the same session`() {
        // Nothing earlier in history: the first set is not a record, the second, heavier, is.
        val summary = build(
            sets = listOf(
                set(1, machineId = 2L, weightKg = 40.0, reps = 10, at = 1),
                set(2, machineId = 2L, weightKg = 45.0, reps = 10, at = 2),
            ),
        )

        assertEquals(listOf(RecordEvent(row, LocalDate.of(2026, 11, 9), Record.HeaviestWeight(45.0))), summary.records)
    }

    @Test
    fun `a set that is not heavier and not more reps is no record`() {
        val earlier = mapOf(1L to listOf(set(-5, machineId = 1L, weightKg = 100.0, reps = 10, sessionId = 1L)))
        val summary = build(sets = listOf(set(1, machineId = 1L, weightKg = 90.0, reps = 10)), earlier = earlier)

        assertTrue(summary.records.isEmpty())
    }

    @Test
    fun `warm-up sets are never records and do not count against later sets`() {
        val earlier = mapOf(1L to listOf(set(-5, machineId = 1L, weightKg = 100.0, reps = 10, sessionId = 1L)))
        val summary = build(
            sets = listOf(
                set(1, machineId = 1L, weightKg = 150.0, reps = 3, type = SetType.WARMUP, at = 1),
                set(2, machineId = 1L, weightKg = 110.0, reps = 5, at = 2),
            ),
            earlier = earlier,
        )

        assertEquals(listOf(RecordEvent(legPress, LocalDate.of(2026, 11, 9), Record.HeaviestWeight(110.0))), summary.records)
    }

    @Test
    fun `cardio sets are never records`() {
        val earlier = mapOf(3L to listOf(set(-5, machineId = 3L, weightKg = null, reps = null, seconds = 600, sessionId = 1L)))
        val summary = build(
            sets = listOf(set(1, machineId = 3L, weightKg = null, reps = null, seconds = 1500)),
            earlier = earlier,
        )

        assertTrue(summary.records.isEmpty())
    }

    @Test
    fun `records of this session come from earlier sets only, not from other machines`() {
        val earlier = mapOf(2L to listOf(set(-5, machineId = 2L, weightKg = 200.0, reps = 10, sessionId = 1L)))
        val summary = build(
            sets = listOf(set(1, machineId = 1L, weightKg = 100.0, reps = 10)),
            earlier = earlier,
        )

        assertTrue(summary.records.isEmpty())
    }

    // Comparison with the previous session of the routine

    @Test
    fun `a session without a previous one has no comparison`() {
        val summary = build(sets = listOf(set(1)), previous = null)

        assertNull(summary.previous)
    }

    @Test
    fun `a higher volume than the previous session is a positive percent`() {
        val previousSession = session(id = 4L, startedAt = start.minusSeconds(7 * 86_400L))
        val previousSets = listOf(set(-1, weightKg = 100.0, reps = 10, sessionId = 4L))
        val summary = build(
            sets = listOf(set(1, weightKg = 100.0, reps = 12), set(2, weightKg = 100.0, reps = 8)),
            previous = previousSession to previousSets,
        )

        val previous = requireNotNull(summary.previous)
        assertEquals(2000.0, summary.volumeKg, 0.001)
        assertEquals(1000.0, previous.volumeKg, 0.001)
        assertEquals(100.0, previous.volumeChangePercent!!, 0.001)
        assertEquals(1, previous.workingSets)
        assertEquals(1, previous.setsChange)
    }

    @Test
    fun `a lower volume than the previous session is a negative percent`() {
        val previousSession = session(id = 4L, startedAt = start.minusSeconds(7 * 86_400L))
        val previousSets = listOf(set(-2, weightKg = 100.0, reps = 10, sessionId = 4L), set(-1, weightKg = 100.0, reps = 10, sessionId = 4L))
        val summary = build(
            sets = listOf(set(1, weightKg = 90.0, reps = 10)),
            previous = previousSession to previousSets,
        )

        val previous = requireNotNull(summary.previous)
        assertEquals(-55.0, previous.volumeChangePercent!!, 0.001)
        assertEquals(-1, previous.setsChange)
    }

    @Test
    fun `the same volume is a zero percent change and the same number of sets is no change`() {
        val previousSession = session(id = 4L, startedAt = start.minusSeconds(7 * 86_400L))
        val previousSets = listOf(set(-1, weightKg = 80.0, reps = 10, sessionId = 4L))
        val summary = build(
            sets = listOf(set(1, weightKg = 40.0, reps = 20)),
            previous = previousSession to previousSets,
        )

        val previous = requireNotNull(summary.previous)
        assertEquals(0.0, previous.volumeChangePercent!!, 0.001)
        assertEquals(0, previous.setsChange)
    }

    @Test
    fun `a previous volume of zero gives no percent, but the set change is still there`() {
        val previousSession = session(id = 4L, startedAt = start.minusSeconds(7 * 86_400L))
        val previousSets = listOf(set(-1, weightKg = 60.0, reps = 10, type = SetType.WARMUP, sessionId = 4L))
        val summary = build(
            sets = listOf(set(1, weightKg = 100.0, reps = 10), set(2, weightKg = 100.0, reps = 10)),
            previous = previousSession to previousSets,
        )

        val previous = requireNotNull(summary.previous)
        assertEquals(0.0, previous.volumeKg, 0.001)
        assertNull(previous.volumeChangePercent)
        assertEquals(0, previous.workingSets)
        assertEquals(2, previous.setsChange)
    }

    // Plain values

    @Test
    fun `the session id, routine name and date are carried over`() {
        val summary = build(
            session = session(id = 77L, startedAt = Instant.parse("2026-11-10T01:30:00Z"), endedAt = Instant.parse("2026-11-10T02:30:00Z")),
            routineName = "Routine B",
            zoneId = ZoneId.of("America/Sao_Paulo"),
        )

        assertEquals(77L, summary.sessionId)
        assertEquals("Routine B", summary.routineName)
        assertEquals(LocalDate.of(2026, 11, 9), summary.date)
    }

    @Test
    fun `a free session has no routine name`() {
        assertNull(build(routineName = null).routineName)
    }
}
