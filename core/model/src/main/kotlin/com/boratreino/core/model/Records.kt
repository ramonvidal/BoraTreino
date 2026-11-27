package com.boratreino.core.model

import kotlin.math.abs

/** A personal best reached by a set. */
sealed interface Record {
    data class HeaviestWeight(val weightKg: Double) : Record

    data class MostReps(val weightKg: Double, val reps: Int) : Record

    /** Longest hold or carry time at [weightKg] (0.0 = no load). */
    data class LongestTime(val weightKg: Double, val seconds: Int) : Record

    /** Farthest distance at [weightKg] (0.0 = no load). */
    data class LongestDistance(val weightKg: Double, val distanceM: Int) : Record
}

/** A set that was just logged, with the record it set, if any. */
data class LoggedSet(val set: WorkoutSet, val record: Record?)

object RecordRules {
    private const val EPSILON = 0.001

    /** reps if non-null, else seconds, else distanceM, else 0. */
    private fun WorkoutSet.amount(): Int = reps ?: seconds ?: distanceM ?: 0

    /**
     * Compares a new set with every earlier set of the same machine (any session, including earlier sets of
     * this one). A warm-up set is never a record and earlier warm-ups are ignored. Cardio sets are never records. No earlier sets -> null (the first time is not a record).
     * Heavier than every earlier weight (by more than 0.001) -> HeaviestWeight. Otherwise more reps than every earlier set at the same weight,
     * with at least one earlier set at that weight -> MostReps. Otherwise null. Null weight counts as 0.
     * The "amount" of a set is its own measure: reps if non-null, else seconds, else distanceM (null counts as 0
     * in earlier sets). More amount than every earlier set at the same weight -> MostReps for a reps set,
     * LongestTime for a seconds set, LongestDistance for a distance set. A TIME set has weight 0.0, so it is
     * never HeaviestWeight; only a longer time at 0 kg is a record.
     */
    fun recordFor(newSet: WorkoutSet, earlier: List<WorkoutSet>): Record? {
        if (!newSet.isWorking || CardioRules.isCardio(newSet)) return null
        val prior = earlier.working()
        if (prior.isEmpty()) return null
        val w = newSet.weightKg ?: 0.0
        if (prior.all { w - (it.weightKg ?: 0.0) > EPSILON }) return Record.HeaviestWeight(w)
        val same = prior.filter { abs((it.weightKg ?: 0.0) - w) <= EPSILON }
        if (same.isEmpty()) return null
        val amount = newSet.amount()
        if (!same.all { amount > it.amount() }) return null
        return when {
            newSet.reps != null -> Record.MostReps(w, amount)
            newSet.seconds != null -> Record.LongestTime(w, amount)
            else -> Record.LongestDistance(w, amount)
        }
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
            if (!set.isWorking) {
                prior.add(set)
                continue
            }
            if (recordFor(set, prior) != null) count++
            prior.add(set)
        }
        return count
    }
}
