package com.boratreino.core.model

import kotlin.math.abs

/** A personal best reached by a set. */
sealed interface Record {
    data class HeaviestWeight(val weightKg: Double) : Record

    data class MostReps(val weightKg: Double, val reps: Int) : Record
}

/** A set that was just logged, with the record it set, if any. */
data class LoggedSet(val set: WorkoutSet, val record: Record?)

object RecordRules {
    private const val EPSILON = 0.001

    /**
     * Compares a new set with every earlier set of the same machine (any session, including earlier sets of
     * this one). Cardio sets are never records. No earlier sets -> null (the first time is not a record). Heavier than every earlier weight
     * (by more than 0.001) -> HeaviestWeight. Otherwise more reps than every earlier set at the same weight,
     * with at least one earlier set at that weight -> MostReps. Otherwise null. Null weight counts as 0.
     */
    fun recordFor(newSet: WorkoutSet, earlier: List<WorkoutSet>): Record? {
        if (CardioRules.isCardio(newSet) || earlier.isEmpty()) return null
        val w = newSet.weightKg ?: 0.0
        val reps = newSet.reps ?: 0
        if (earlier.all { w - (it.weightKg ?: 0.0) > EPSILON }) return Record.HeaviestWeight(w)
        val same = earlier.filter { abs((it.weightKg ?: 0.0) - w) <= EPSILON }
        if (same.isNotEmpty() && same.all { reps > (it.reps ?: 0) }) return Record.MostReps(w, reps)
        return null
    }

    /**
     * How many of sessionSets (in loggedAt order) were records when logged, against earlier + the
     * session's own previous sets, per machine.
     */
    fun countRecords(sessionSets: List<WorkoutSet>, earlier: List<WorkoutSet>): Int {
        val history = earlier.groupBy { it.machineId }.mapValues { it.value.toMutableList() }.toMutableMap()
        var count = 0
        for (set in sessionSets.sortedWith(compareBy({ it.loggedAt }, { it.position }))) {
            val prior = history.getOrPut(set.machineId) { mutableListOf() }
            if (CardioRules.isCardio(set)) continue
            if (recordFor(set, prior) != null) count++
            prior.add(set)
        }
        return count
    }
}
