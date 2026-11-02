package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RecordRulesTest {
    private val t0 = Instant.parse("2026-10-30T18:00:00Z")

    private fun set(
        id: Long,
        weightKg: Double?,
        reps: Int?,
        machineId: Long = 1L,
        position: Int = 0,
        sessionId: Long = 1L,
        at: Instant = t0.plusSeconds(id * 60),
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
        loggedAt = at,
    )

    // recordFor

    @Test
    fun `the first ever set on a machine is not a record`() {
        assertNull(RecordRules.recordFor(set(1, 120.0, 12), earlier = emptyList()))
    }

    @Test
    fun `a heavier weight than every earlier set is a heaviest-weight record`() {
        val earlier = listOf(set(1, 120.0, 12), set(2, 115.0, 12))

        assertEquals(
            Record.HeaviestWeight(125.0),
            RecordRules.recordFor(set(3, 125.0, 6), earlier),
        )
    }

    @Test
    fun `a heavier weight wins even with fewer reps`() {
        val earlier = listOf(set(1, 120.0, 12))

        assertEquals(Record.HeaviestWeight(125.0), RecordRules.recordFor(set(2, 125.0, 3), earlier))
    }

    @Test
    fun `a weight only 0 001 kg heavier is not a heaviest-weight record`() {
        val earlier = listOf(set(1, 120.0, 12))

        assertNull(RecordRules.recordFor(set(2, 120.0005, 12), earlier))
    }

    @Test
    fun `more reps at the same weight is a most-reps record`() {
        val earlier = listOf(set(1, 120.0, 10), set(2, 120.0, 11))

        assertEquals(Record.MostReps(120.0, 12), RecordRules.recordFor(set(3, 120.0, 12), earlier))
    }

    @Test
    fun `equal reps at the same weight are not a record`() {
        assertNull(RecordRules.recordFor(set(2, 120.0, 12), listOf(set(1, 120.0, 12))))
    }

    @Test
    fun `fewer reps at the same weight are not a record`() {
        assertNull(RecordRules.recordFor(set(2, 120.0, 10), listOf(set(1, 120.0, 12))))
    }

    @Test
    fun `reps must beat every earlier set at that weight, not just one of them`() {
        val earlier = listOf(set(1, 120.0, 10), set(2, 120.0, 12))

        assertNull(RecordRules.recordFor(set(3, 120.0, 11), earlier))
    }

    @Test
    fun `sets at a lighter weight are not compared for reps`() {
        val earlier = listOf(set(1, 100.0, 20), set(2, 120.0, 10))

        assertEquals(Record.MostReps(120.0, 12), RecordRules.recordFor(set(3, 120.0, 12), earlier))
    }

    @Test
    fun `a null weight counts as 0 kg and a null reps as 0`() {
        val earlier = listOf(set(1, null, 10))

        assertEquals(Record.MostReps(0.0, 11), RecordRules.recordFor(set(2, null, 11), earlier))
    }

    @Test
    fun `a null weight is lighter than any loaded set`() {
        val earlier = listOf(set(1, 10.0, 5))

        assertNull(RecordRules.recordFor(set(2, null, 20), earlier))
    }

    @Test
    fun `any loaded set is heavier than an earlier null weight`() {
        val earlier = listOf(set(1, null, 10))

        assertEquals(Record.HeaviestWeight(5.0), RecordRules.recordFor(set(2, 5.0, 10), earlier))
    }

    @Test
    fun `a null reps set counts as 0 reps when it is compared`() {
        val earlier = listOf(set(1, 40.0, null))

        assertEquals(Record.MostReps(40.0, 1), RecordRules.recordFor(set(2, 40.0, 1), earlier))
    }

    // countRecords

    @Test
    fun `a single set in a session with no earlier sets is not a record`() {
        assertEquals(0, RecordRules.countRecords(listOf(set(1, 120.0, 12)), earlier = emptyList()))
    }

    @Test
    fun `a later heavier set in the same session is counted`() {
        val session = listOf(set(1, 120.0, 12, position = 0), set(2, 125.0, 8, position = 1))

        assertEquals(1, RecordRules.countRecords(session, earlier = emptyList()))
    }

    @Test
    fun `a session's sets are compared with the earlier sets of the machine`() {
        val earlier = listOf(set(1, 120.0, 12, sessionId = 0L))

        assertEquals(1, RecordRules.countRecords(listOf(set(2, 125.0, 5, sessionId = 1L)), earlier))
        assertEquals(0, RecordRules.countRecords(listOf(set(3, 120.0, 12, sessionId = 1L)), earlier))
    }

    @Test
    fun `a session's own earlier set at the same weight counts for the next one`() {
        val session = listOf(
            set(1, 100.0, 10, position = 0, at = t0),
            set(2, 100.0, 11, position = 1, at = t0.plusSeconds(60)),
            set(3, 100.0, 11, position = 2, at = t0.plusSeconds(120)),
        )

        assertEquals(1, RecordRules.countRecords(session, earlier = emptyList()))
    }

    @Test
    fun `sets are compared in logged order, then position`() {
        val sameTime = listOf(
            set(2, 100.0, 11, position = 1, at = t0),
            set(1, 100.0, 10, position = 0, at = t0),
        )

        assertEquals(1, RecordRules.countRecords(sameTime, earlier = emptyList()))
    }

    @Test
    fun `each machine is compared only with its own sets`() {
        val earlier = listOf(set(1, 200.0, 1, machineId = 2L, sessionId = 0L))
        val session = listOf(
            set(2, 125.0, 5, machineId = 1L, sessionId = 1L),
            set(3, 100.0, 10, machineId = 2L, sessionId = 1L),
        )

        assertEquals(0, RecordRules.countRecords(session, earlier))
    }

    @Test
    fun `every record in a session is counted`() {
        val earlier = listOf(set(1, 100.0, 12, sessionId = 0L))
        val session = listOf(
            set(2, 100.0, 13, position = 0, sessionId = 1L, at = t0.plusSeconds(120)),
            set(3, 105.0, 5, position = 1, sessionId = 1L, at = t0.plusSeconds(180)),
            set(4, 105.0, 5, position = 2, sessionId = 1L, at = t0.plusSeconds(240)),
        )

        assertEquals(2, RecordRules.countRecords(session, earlier))
    }
}
