package com.boratreino.core.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/** What to try next on a machine. */
sealed interface Suggestion {
    val weightKg: Double
    val reps: Int

    data class Up(override val weightKg: Double, override val reps: Int) : Suggestion

    data class Down(override val weightKg: Double, override val reps: Int) : Suggestion
}

object ProgressionRules {
    private const val EPSILON = 0.001

    private fun round3(x: Double) = (x * 1000.0).roundToLong() / 1000.0

    private fun WorkoutSet.w() = weightKg ?: 0.0

    private fun WorkoutSet.r() = reps ?: 0

    /** The first [n] sets by position, or null when fewer exist. */
    private fun firstSets(sets: List<WorkoutSet>, n: Int): List<WorkoutSet>? =
        sets.sortedBy { it.position }.take(n).takeIf { it.size == n }

    private fun sharedWeight(sets: List<WorkoutSet>): Double? {
        val w = sets.first().w()
        return if (sets.all { abs(it.w() - w) <= EPSILON }) w else null
    }

    /**
     * history: this machine's sets per earlier session, most recent session first (each list by position).
     * Up: the most recent session's first targetSets sets all exist, share one weight W (within 0.001) and all
     *     have reps >= repsMax -> Up(W + stepKg, repsMin).
     * Down: in each of the two most recent sessions, the first targetSets sets share one weight W (the same W
     *     in both) and at least one has reps < repsMin -> Down(max(0, W - stepKg), repsMin).
     * Otherwise null. Null when target is null, history is empty or the sets are cardio. Weights rounded like LoggingRules.stepWeight.
     */
    fun suggest(target: RoutineItem?, stepKg: Double, history: List<List<WorkoutSet>>): Suggestion? {
        if (target == null || history.isEmpty()) return null
        val n = target.targetSets
        val latest = firstSets(history[0], n) ?: return null
        if (latest.any { CardioRules.isCardio(it) }) return null
        val w1 = sharedWeight(latest) ?: return null

        if (latest.all { it.r() >= target.repsMax }) {
            return Suggestion.Up(round3(w1 + stepKg), target.repsMin)
        }
        if (history.size >= 2) {
            val previous = firstSets(history[1], n) ?: return null
            val w2 = sharedWeight(previous) ?: return null
            if (abs(w1 - w2) <= EPSILON &&
                latest.any { it.r() < target.repsMin } &&
                previous.any { it.r() < target.repsMin }
            ) {
                return Suggestion.Down(round3(max(0.0, w1 - stepKg)), target.repsMin)
            }
        }
        return null
    }
}
