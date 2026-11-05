package com.boratreino.core.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** The optional warm-up suggestion: a few lighter sets before the working weight. */
object WarmupRules {
    /** Weights closer than this are the same weight. */
    private const val EPSILON = 0.001

    /** Below this many steps of working weight there is nothing sensible to suggest. */
    private const val MIN_STEPS = 4

    /** Percent of the working weight and reps: 50 x 8, 70 x 5, 85 x 3. */
    val SCHEME: List<Pair<Int, Int>> = listOf(50 to 8, 70 to 5, 85 to 3)

    /**
     * Warm-up sets for [workingWeightKg] on a machine that moves in [stepKg] steps. Empty when the weight is under 4 steps,
     * or [stepKg] / the weight is not positive. Each weight is rounded DOWN to the step (at least one step); entries equal to
     * an earlier one or not below the working weight are dropped. Ascending by weight, type WARMUP.
     */
    fun plan(workingWeightKg: Double, stepKg: Double): List<SetDraft> {
        if (stepKg <= 0.0 || workingWeightKg <= 0.0) return emptyList()
        if (workingWeightKg / stepKg < MIN_STEPS - EPSILON / stepKg) return emptyList()
        val result = mutableListOf<SetDraft>()
        for ((percent, reps) in SCHEME) {
            val weight = roundDown(workingWeightKg * percent / 100.0, stepKg)
            if (weight >= workingWeightKg - EPSILON) continue
            if (result.any { abs(it.weightKg - weight) <= EPSILON }) continue
            result += SetDraft(weight, reps, SetType.WARMUP)
        }
        return result.sortedBy { it.weightKg }
    }

    private fun roundDown(weightKg: Double, stepKg: Double): Double {
        val units = weightKg / stepKg
        val nearest = units.roundToLong()
        val onGrid = abs(weightKg - nearest * stepKg) <= EPSILON
        val steps = maxOf(1L, if (onGrid) nearest else floor(units).toLong())
        return ((steps * stepKg) * 1000.0).roundToLong() / 1000.0
    }
}
