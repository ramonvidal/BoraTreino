package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Repeating a session carries the bike console values of last time's cardio sets (phase 42). */
class RepeatCardioValuesTest {
    private val at = Instant.parse("2026-11-16T18:00:00Z")

    private fun item(machineId: Long, position: Int) =
        RoutineItem(routineId = 1L, machineId = machineId, position = position, targetSets = 1, repsMin = 8, repsMax = 12)

    private fun cardioSet(
        id: Long,
        machineId: Long,
        position: Int,
        watts: Int?,
        heartRate: Int?,
        wattsPerKg: Double?,
        tss: Int?,
        intensityFactor: Double?,
    ) = WorkoutSet(
        id = id,
        sessionId = 9L,
        machineId = machineId,
        position = position,
        weightKg = null,
        reps = null,
        seconds = 1200,
        calories = 180,
        distanceM = 5000,
        loggedAt = at,
        watts = watts,
        heartRate = heartRate,
        wattsPerKg = wattsPerKg,
        tss = tss,
        intensityFactor = intensityFactor,
    )

    @Test
    fun `a repeated cardio set keeps all of last time's console values`() {
        val last = listOf(cardioSet(1L, machineId = 5L, position = 0, watts = 160, heartRate = 142, wattsPerKg = 2.4, tss = 48, intensityFactor = 0.79))

        val plan = RepeatSessionRules.plan(listOf(item(5L, 0)), last, emptySet(), RepeatMode.SAME, emptyMap())

        assertEquals(
            listOf(PlannedSet(5L, null, null, 1200, 180, 5000, null, 160, 142, 2.4, 48, 0.79)),
            plan,
        )
    }

    @Test
    fun `a repeated set with no console values repeats with none`() {
        val last = listOf(cardioSet(1L, machineId = 5L, position = 0, watts = null, heartRate = null, wattsPerKg = null, tss = null, intensityFactor = null))

        val plan = RepeatSessionRules.plan(listOf(item(5L, 0)), last, emptySet(), RepeatMode.SAME, emptyMap())

        assertEquals(null, plan.single().watts)
        assertEquals(null, plan.single().intensityFactor)
    }

    @Test
    fun `suggested repeat keeps the console values of a cardio machine`() {
        val last = listOf(cardioSet(1L, machineId = 5L, position = 0, watts = 160, heartRate = null, wattsPerKg = null, tss = 48, intensityFactor = 0.79))

        val plan = RepeatSessionRules.plan(listOf(item(5L, 0)), last, emptySet(), RepeatMode.SUGGESTED, emptyMap())

        assertEquals(160, plan.single().watts)
        assertEquals(48, plan.single().tss)
    }
}
