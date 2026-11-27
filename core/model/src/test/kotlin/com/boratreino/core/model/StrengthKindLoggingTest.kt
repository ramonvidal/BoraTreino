package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StrengthKindLoggingTest {
    private val at = Instant.parse("2026-11-20T09:00:00Z")

    private fun set(
        position: Int,
        weightKg: Double?,
        reps: Int?,
        seconds: Int? = null,
        distanceM: Int? = null,
        type: SetType = SetType.NORMAL,
    ) = WorkoutSet(
        id = position + 1L, sessionId = 1L, machineId = 1L, position = position,
        weightKg = weightKg, reps = reps, seconds = seconds, calories = null, distanceM = distanceM,
        loggedAt = at, type = type,
    )

    // draftOf

    @Test
    fun `a timed set becomes a draft with its seconds and no reps`() {
        assertEquals(
            SetDraft(47.5, 0, seconds = 45),
            LoggingRules.draftOf(set(0, 47.5, null, seconds = 45), MachineKind.WEIGHT_TIME),
        )
    }

    @Test
    fun `a timed set with no weight counts 0 kg and a missing time is the default time`() {
        assertEquals(
            SetDraft(0.0, 0, seconds = LoggingRules.DEFAULT_SECONDS),
            LoggingRules.draftOf(set(0, null, null), MachineKind.TIME),
        )
    }

    @Test
    fun `a distance set becomes a draft with its metres and no reps`() {
        assertEquals(
            SetDraft(40.0, 0, distanceM = 25),
            LoggingRules.draftOf(set(0, 40.0, null, distanceM = 25), MachineKind.WEIGHT_DISTANCE),
        )
    }

    @Test
    fun `a distance set with no metres drafts the default distance`() {
        assertEquals(
            SetDraft(0.0, 0, distanceM = LoggingRules.DEFAULT_DISTANCE_M),
            LoggingRules.draftOf(set(0, null, null), MachineKind.WEIGHT_DISTANCE),
        )
    }

    @Test
    fun `a reps set still drafts its reps`() {
        assertEquals(SetDraft(40.0, 8), LoggingRules.draftOf(set(0, 40.0, 8), MachineKind.WEIGHT_REPS))
    }

    // prefill and sameAgain

    @Test
    fun `prefill with no history is 0 kg and the default for each measure`() {
        assertEquals(SetDraft(0.0, 10), LoggingRules.prefill(emptyList(), emptyList(), kind = MachineKind.WEIGHT_REPS))
        assertEquals(
            SetDraft(0.0, 0, seconds = 30),
            LoggingRules.prefill(emptyList(), emptyList(), kind = MachineKind.WEIGHT_TIME),
        )
        assertEquals(
            SetDraft(0.0, 0, seconds = 30),
            LoggingRules.prefill(emptyList(), emptyList(), kind = MachineKind.TIME),
        )
        assertEquals(
            SetDraft(0.0, 0, distanceM = 20),
            LoggingRules.prefill(emptyList(), emptyList(), kind = MachineKind.WEIGHT_DISTANCE),
        )
    }

    @Test
    fun `prefill starts from this session's last timed set`() {
        val lastTime = listOf(set(0, 40.0, null, seconds = 30))
        val thisSession = listOf(set(0, 40.0, null, seconds = 40), set(1, 42.5, null, seconds = 45))

        assertEquals(
            SetDraft(42.5, 0, seconds = 45),
            LoggingRules.prefill(lastTime, thisSession, kind = MachineKind.WEIGHT_TIME),
        )
    }

    @Test
    fun `prefill starts from last time's first distance set when nothing is logged this session`() {
        val lastTime = listOf(set(0, 40.0, null, distanceM = 25), set(1, 40.0, null, distanceM = 30))

        assertEquals(
            SetDraft(40.0, 0, distanceM = 25),
            LoggingRules.prefill(lastTime, emptyList(), kind = MachineKind.WEIGHT_DISTANCE),
        )
    }

    @Test
    fun `prefill ignores a reps suggestion for a timed kind`() {
        val suggestion = Suggestion.Up(60.0, 8)

        assertEquals(
            SetDraft(0.0, 0, seconds = 30),
            LoggingRules.prefill(emptyList(), emptyList(), suggestion, MachineKind.TIME),
        )
    }

    @Test
    fun `prefill for a timed kind ignores warm-ups in this session`() {
        val thisSession = listOf(set(0, 20.0, null, seconds = 10, type = SetType.WARMUP))

        assertEquals(
            SetDraft(0.0, 0, seconds = 30),
            LoggingRules.prefill(emptyList(), thisSession, kind = MachineKind.TIME),
        )
    }

    @Test
    fun `same again is last time's timed set at the index of the next set`() {
        val lastTime = listOf(set(0, 40.0, null, seconds = 30), set(1, 40.0, null, seconds = 45))
        val thisSession = listOf(set(0, 40.0, null, seconds = 30))

        assertEquals(
            SetDraft(40.0, 0, seconds = 45),
            LoggingRules.sameAgain(lastTime, thisSession, MachineKind.WEIGHT_TIME),
        )
    }

    @Test
    fun `same again is last time's distance set at the index of the next set`() {
        val lastTime = listOf(set(0, 40.0, null, distanceM = 25))

        assertEquals(
            SetDraft(40.0, 0, distanceM = 25),
            LoggingRules.sameAgain(lastTime, emptyList(), MachineKind.WEIGHT_DISTANCE),
        )
    }

    @Test
    fun `same again is absent for a timed kind when last time has no set at that index`() {
        val lastTime = listOf(set(0, 40.0, null, seconds = 30))
        val thisSession = listOf(set(0, 40.0, null, seconds = 30))

        assertNull(LoggingRules.sameAgain(lastTime, thisSession, MachineKind.TIME))
    }

    // steps

    @Test
    fun `seconds step by 5 on the grid`() {
        assertEquals(35, LoggingRules.stepSeconds(30, up = true))
        assertEquals(25, LoggingRules.stepSeconds(30, up = false))
    }

    @Test
    fun `an off-grid time snaps to the grid in the direction of the step`() {
        assertEquals(35, LoggingRules.stepSeconds(32, up = true))
        assertEquals(30, LoggingRules.stepSeconds(32, up = false))
    }

    @Test
    fun `seconds stay between 1 and the maximum`() {
        assertEquals(1, LoggingRules.stepSeconds(1, up = false))
        assertEquals(1, LoggingRules.stepSeconds(3, up = false))
        assertEquals(5, LoggingRules.stepSeconds(0, up = true))
        assertEquals(LoggingRules.MAX_SECONDS, LoggingRules.stepSeconds(LoggingRules.MAX_SECONDS, up = true))
        assertEquals(LoggingRules.MAX_SECONDS, LoggingRules.stepSeconds(3598, up = true))
    }

    @Test
    fun `metres step by 5 on the grid`() {
        assertEquals(25, LoggingRules.stepDistance(20, up = true))
        assertEquals(15, LoggingRules.stepDistance(20, up = false))
    }

    @Test
    fun `an off-grid distance snaps to the grid in the direction of the step`() {
        assertEquals(25, LoggingRules.stepDistance(22, up = true))
        assertEquals(20, LoggingRules.stepDistance(22, up = false))
    }

    @Test
    fun `metres stay between 1 and the maximum`() {
        assertEquals(1, LoggingRules.stepDistance(1, up = false))
        assertEquals(5, LoggingRules.stepDistance(0, up = true))
        assertEquals(LoggingRules.MAX_DISTANCE_M, LoggingRules.stepDistance(LoggingRules.MAX_DISTANCE_M, up = true))
        assertEquals(LoggingRules.MAX_DISTANCE_M, LoggingRules.stepDistance(4998, up = true))
    }

    // canLog

    @Test
    fun `a reps draft can always be logged`() {
        assertTrue(LoggingRules.canLog(SetDraft(0.0, 0), MachineKind.WEIGHT_REPS))
    }

    @Test
    fun `a timed draft needs seconds`() {
        assertFalse(LoggingRules.canLog(SetDraft(40.0, 0, seconds = 0), MachineKind.WEIGHT_TIME))
        assertTrue(LoggingRules.canLog(SetDraft(0.0, 0, seconds = 1), MachineKind.TIME))
    }

    @Test
    fun `a distance draft needs metres`() {
        assertFalse(LoggingRules.canLog(SetDraft(40.0, 0, distanceM = 0), MachineKind.WEIGHT_DISTANCE))
        assertTrue(LoggingRules.canLog(SetDraft(40.0, 0, distanceM = 1), MachineKind.WEIGHT_DISTANCE))
    }

    @Test
    fun `cardio can never be logged from the strength draft`() {
        assertFalse(LoggingRules.canLog(SetDraft(0.0, 10, seconds = 60, distanceM = 100), MachineKind.CARDIO))
    }

    // toSet shapes

    @Test
    fun `a time only set is logged with weight 0 kg, its seconds and no reps`() {
        val logged = LoggingRules.toSet(
            id = 7L, sessionId = 2L, machineId = 3L, position = 1,
            draft = SetDraft(47.5, 0, seconds = 45), kind = MachineKind.TIME, loggedAt = at,
        )

        assertEquals(7L, logged.id)
        assertEquals(2L, logged.sessionId)
        assertEquals(3L, logged.machineId)
        assertEquals(1, logged.position)
        assertEquals(0.0, logged.weightKg!!, 0.0)
        assertNull(logged.reps)
        assertEquals(45, logged.seconds)
        assertNull(logged.distanceM)
        assertNull(logged.calories)
        assertEquals(at, logged.loggedAt)
    }

    @Test
    fun `a weight and time set keeps its weight, its seconds and no reps`() {
        val logged = LoggingRules.toSet(1L, 1L, 1L, 0, SetDraft(47.5, 0, seconds = 30), MachineKind.WEIGHT_TIME, at)

        assertEquals(47.5, logged.weightKg!!, 0.0)
        assertNull(logged.reps)
        assertEquals(30, logged.seconds)
        assertNull(logged.distanceM)
    }

    @Test
    fun `a weight and distance set keeps its weight, its metres and no reps or seconds`() {
        val logged = LoggingRules.toSet(1L, 1L, 1L, 0, SetDraft(40.0, 0, distanceM = 25), MachineKind.WEIGHT_DISTANCE, at)

        assertEquals(40.0, logged.weightKg!!, 0.0)
        assertNull(logged.reps)
        assertNull(logged.seconds)
        assertEquals(25, logged.distanceM)
    }

    @Test
    fun `a reps set keeps its weight and reps and has no time or distance`() {
        val logged = LoggingRules.toSet(1L, 1L, 1L, 0, SetDraft(40.0, 8), MachineKind.WEIGHT_REPS, at)

        assertEquals(40.0, logged.weightKg!!, 0.0)
        assertEquals(8, logged.reps)
        assertNull(logged.seconds)
        assertNull(logged.distanceM)
    }

    @Test
    fun `a bodyweight set keeps its added weight`() {
        val logged = LoggingRules.toSet(1L, 1L, 1L, 0, SetDraft(10.0, 12), MachineKind.BODYWEIGHT_REPS, at)

        assertEquals(10.0, logged.weightKg!!, 0.0)
        assertEquals(12, logged.reps)
    }

    @Test
    fun `a time only set drops any weight in the draft`() {
        val logged = LoggingRules.toSet(1L, 1L, 1L, 0, SetDraft(30.0, 0, seconds = 20), MachineKind.TIME, at)

        assertEquals(0.0, logged.weightKg!!, 0.0)
    }

    @Test
    fun `the amounts and weight of a logged set are clamped to their ranges`() {
        val reps = LoggingRules.toSet(1L, 1L, 1L, 0, SetDraft(2000.0, 500), MachineKind.WEIGHT_REPS, at)
        assertEquals(1000.0, reps.weightKg!!, 0.0)
        assertEquals(LoggingRules.MAX_REPS, reps.reps)

        val time = LoggingRules.toSet(1L, 1L, 1L, 0, SetDraft(2000.0, 0, seconds = 9999), MachineKind.WEIGHT_TIME, at)
        assertEquals(1000.0, time.weightKg!!, 0.0)
        assertEquals(LoggingRules.MAX_SECONDS, time.seconds)

        val distance = LoggingRules.toSet(1L, 1L, 1L, 0, SetDraft(40.0, 0, distanceM = 9999), MachineKind.WEIGHT_DISTANCE, at)
        assertEquals(LoggingRules.MAX_DISTANCE_M, distance.distanceM)
    }

    @Test
    fun `the warm-up or drop type of the draft is kept on the logged set`() {
        val logged = LoggingRules.toSet(
            1L, 1L, 1L, 0, SetDraft(20.0, 0, SetType.WARMUP, seconds = 30), MachineKind.WEIGHT_TIME, at,
        )

        assertEquals(SetType.WARMUP, logged.type)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cardio is not a strength set and toSet refuses it`() {
        LoggingRules.toSet(1L, 1L, 1L, 0, SetDraft(0.0, 10), MachineKind.CARDIO, at)
    }

    @Test
    fun `every strength kind logs a set whose weight is not null`() {
        val draft = SetDraft(0.0, 10, seconds = 30, distanceM = 20)
        for (kind in MachineKind.entries.filter { it.isStrength }) {
            val logged = LoggingRules.toSet(1L, 1L, 1L, 0, draft, kind, at)
            assertTrue(kind.name, logged.weightKg != null)
        }
    }

    // applyDraft

    @Test
    fun `applying a draft keeps the id, session, position and time of the set`() {
        val original = set(2, 40.0, 8)

        val edited = LoggingRules.applyDraft(original, SetDraft(45.0, 0, seconds = 40), MachineKind.WEIGHT_TIME)

        assertEquals(original.id, edited.id)
        assertEquals(original.sessionId, edited.sessionId)
        assertEquals(original.machineId, edited.machineId)
        assertEquals(original.position, edited.position)
        assertEquals(original.loggedAt, edited.loggedAt)
        assertEquals(45.0, edited.weightKg!!, 0.0)
        assertNull(edited.reps)
        assertEquals(40, edited.seconds)
    }

    @Test
    fun `applying a distance draft to a reps set gives metres and no reps`() {
        val edited = LoggingRules.applyDraft(set(0, 40.0, 8), SetDraft(40.0, 0, distanceM = 30), MachineKind.WEIGHT_DISTANCE)

        assertNull(edited.reps)
        assertNull(edited.seconds)
        assertEquals(30, edited.distanceM)
    }

    @Test
    fun `applying a timed draft clears the distance of the set`() {
        val edited = LoggingRules.applyDraft(set(0, 0.0, null, distanceM = 20), SetDraft(0.0, 0, seconds = 30), MachineKind.TIME)

        assertNull(edited.distanceM)
        assertEquals(30, edited.seconds)
    }

    @Test
    fun `applying a draft to a time only set forces the weight to 0 kg`() {
        val edited = LoggingRules.applyDraft(set(0, 0.0, null, seconds = 30), SetDraft(25.0, 0, seconds = 30), MachineKind.TIME)

        assertEquals(0.0, edited.weightKg!!, 0.0)
    }
}
