package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RepeatSessionRulesTest {
    private val at = Instant.parse("2026-11-02T18:00:00Z")
    private var nextId = 1L

    private fun item(machineId: Long, position: Int) =
        RoutineItem(routineId = 1L, machineId = machineId, position = position, targetSets = 3, repsMin = 8, repsMax = 12)

    private fun weightSet(machineId: Long, position: Int, kg: Double, reps: Int) = WorkoutSet(
        id = nextId++, sessionId = 10L, machineId = machineId, position = position,
        weightKg = kg, reps = reps, seconds = null, calories = null, distanceM = null, loggedAt = at,
    )

    private fun cardioSet(machineId: Long, position: Int, seconds: Int, calories: Int?, distanceM: Int?) = WorkoutSet(
        id = nextId++, sessionId = 10L, machineId = machineId, position = position,
        weightKg = null, reps = null, seconds = seconds, calories = calories, distanceM = distanceM, loggedAt = at,
    )

    private fun planned(machineId: Long, kg: Double, reps: Int) = PlannedSet(machineId, kg, reps, null, null, null)

    @Test
    fun sameCopiesSetsPerMachineInFichaOrder() {
        val items = listOf(item(2L, 1), item(1L, 0))
        val last = listOf(
            weightSet(2L, 0, 30.0, 10),
            weightSet(1L, 0, 20.0, 12),
            weightSet(1L, 1, 22.5, 11),
            weightSet(2L, 1, 30.0, 9),
        )
        val plan = RepeatSessionRules.plan(items, last, emptySet(), RepeatMode.SAME, emptyMap())
        assertEquals(
            listOf(planned(1L, 20.0, 12), planned(1L, 22.5, 11), planned(2L, 30.0, 10), planned(2L, 30.0, 9)),
            plan,
        )
    }

    @Test
    fun setsOfAMachineKeepTheirPositionOrder() {
        val items = listOf(item(1L, 0))
        val last = listOf(weightSet(1L, 2, 30.0, 8), weightSet(1L, 0, 20.0, 12), weightSet(1L, 1, 25.0, 10))
        val plan = RepeatSessionRules.plan(items, last, emptySet(), RepeatMode.SAME, emptyMap())
        assertEquals(listOf(20.0, 25.0, 30.0), plan.map { it.weightKg })
    }

    @Test
    fun machinesWithoutLastSetsAreLeftOut() {
        val items = listOf(item(1L, 0), item(2L, 1), item(3L, 2))
        val last = listOf(weightSet(1L, 0, 20.0, 12), weightSet(3L, 0, 40.0, 8))
        val plan = RepeatSessionRules.plan(items, last, emptySet(), RepeatMode.SAME, emptyMap())
        assertEquals(listOf(1L, 3L), plan.map { it.machineId })
    }

    @Test
    fun setsOfMachinesNotInTheFichaAreIgnored() {
        val items = listOf(item(1L, 0))
        val last = listOf(weightSet(1L, 0, 20.0, 12), weightSet(9L, 0, 40.0, 8))
        val plan = RepeatSessionRules.plan(items, last, emptySet(), RepeatMode.SAME, emptyMap())
        assertEquals(listOf(1L), plan.map { it.machineId })
    }

    @Test
    fun machinesAlreadyWithSetsTodayAreLeftOut() {
        val items = listOf(item(1L, 0), item(2L, 1))
        val last = listOf(weightSet(1L, 0, 20.0, 12), weightSet(2L, 0, 30.0, 10))
        val plan = RepeatSessionRules.plan(items, last, setOf(1L), RepeatMode.SAME, emptyMap())
        assertEquals(listOf(planned(2L, 30.0, 10)), plan)
    }

    @Test
    fun suggestedUsesSuggestionForEverySetOfThatMachine() {
        val items = listOf(item(1L, 0), item(2L, 1))
        val last = listOf(
            weightSet(1L, 0, 40.0, 12),
            weightSet(1L, 1, 40.0, 12),
            weightSet(1L, 2, 40.0, 12),
            weightSet(2L, 0, 30.0, 10),
            weightSet(2L, 1, 30.0, 9),
        )
        val plan = RepeatSessionRules.plan(
            items, last, emptySet(), RepeatMode.SUGGESTED, mapOf(1L to Suggestion.Up(42.5, 8)),
        )
        assertEquals(
            listOf(
                planned(1L, 42.5, 8), planned(1L, 42.5, 8), planned(1L, 42.5, 8),
                planned(2L, 30.0, 10), planned(2L, 30.0, 9),
            ),
            plan,
        )
    }

    @Test
    fun suggestedWorksWithDownSuggestions() {
        val items = listOf(item(1L, 0))
        val last = listOf(weightSet(1L, 0, 40.0, 5), weightSet(1L, 1, 40.0, 5))
        val plan = RepeatSessionRules.plan(
            items, last, emptySet(), RepeatMode.SUGGESTED, mapOf(1L to Suggestion.Down(37.5, 8)),
        )
        assertEquals(listOf(planned(1L, 37.5, 8), planned(1L, 37.5, 8)), plan)
    }

    @Test
    fun sameIgnoresSuggestions() {
        val items = listOf(item(1L, 0))
        val last = listOf(weightSet(1L, 0, 40.0, 12))
        val plan = RepeatSessionRules.plan(items, last, emptySet(), RepeatMode.SAME, mapOf(1L to Suggestion.Up(42.5, 8)))
        assertEquals(listOf(planned(1L, 40.0, 12)), plan)
    }

    @Test
    fun suggestionForMachineWithoutLastSetsAddsNothing() {
        val items = listOf(item(1L, 0))
        val plan = RepeatSessionRules.plan(
            items, emptyList(), emptySet(), RepeatMode.SUGGESTED, mapOf(1L to Suggestion.Up(42.5, 8)),
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun cardioCopiesSecondsCaloriesAndDistance() {
        val items = listOf(item(5L, 0))
        val last = listOf(cardioSet(5L, 0, 1200, 150, 4000), cardioSet(5L, 1, 600, null, null))
        val plan = RepeatSessionRules.plan(items, last, emptySet(), RepeatMode.SAME, emptyMap())
        assertEquals(
            listOf(
                PlannedSet(5L, null, null, 1200, 150, 4000),
                PlannedSet(5L, null, null, 600, null, null),
            ),
            plan,
        )
    }

    @Test
    fun cardioIgnoresSuggestions() {
        val items = listOf(item(5L, 0))
        val last = listOf(cardioSet(5L, 0, 1200, 150, 4000))
        val plan = RepeatSessionRules.plan(
            items, last, emptySet(), RepeatMode.SUGGESTED, mapOf(5L to Suggestion.Up(10.0, 8)),
        )
        assertEquals(listOf(PlannedSet(5L, null, null, 1200, 150, 4000)), plan)
    }

    @Test
    fun emptyInputsGiveEmptyPlan() {
        assertTrue(RepeatSessionRules.plan(emptyList(), emptyList(), emptySet(), RepeatMode.SAME, emptyMap()).isEmpty())
        assertTrue(
            RepeatSessionRules.plan(listOf(item(1L, 0)), emptyList(), emptySet(), RepeatMode.SAME, emptyMap()).isEmpty(),
        )
        assertTrue(
            RepeatSessionRules.plan(emptyList(), listOf(weightSet(1L, 0, 20.0, 10)), emptySet(), RepeatMode.SUGGESTED, emptyMap())
                .isEmpty(),
        )
    }

    @Test
    fun machineCountCountsDistinctMachines() {
        assertEquals(0, RepeatSessionRules.machineCount(emptyList()))
        val plan = listOf(planned(1L, 20.0, 10), planned(1L, 20.0, 10), planned(2L, 30.0, 8))
        assertEquals(2, RepeatSessionRules.machineCount(plan))
    }
}
