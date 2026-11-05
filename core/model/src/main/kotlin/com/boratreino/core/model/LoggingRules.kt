package com.boratreino.core.model

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** Weight and reps the steppers hold before a set is logged. */
data class SetDraft(val weightKg: Double, val reps: Int, val type: SetType = SetType.NORMAL)

/** Rules for the logging path: what the steppers start at and how they move. */
object LoggingRules {
    const val DEFAULT_REPS = 10
    const val MAX_REPS = 100
    const val MAX_WEIGHT_KG = 1000.0

    /** Weights closer than this are the same weight. */
    private const val EPSILON = 0.001

    private fun WorkoutSet.toDraft() = SetDraft(weightKg ?: 0.0, reps ?: DEFAULT_REPS)

    /**
     * Steppers' starting values (warm-ups never count): this session's last set if any; else [suggestion] if any; else last
     * time's first set; else 0 kg x DEFAULT_REPS.
     */
    fun prefill(
        lastTime: List<WorkoutSet>,
        thisSession: List<WorkoutSet>,
        suggestion: Suggestion? = null,
    ): SetDraft {
        thisSession.working().lastOrNull()?.let { return it.toDraft() }
        suggestion?.let { return SetDraft(it.weightKg, it.reps) }
        return lastTime.working().firstOrNull()?.toDraft() ?: SetDraft(0.0, DEFAULT_REPS)
    }

    /** Last time's working set at index (working sets in thisSession), as a draft; null when there is none. */
    fun sameAgain(lastTime: List<WorkoutSet>, thisSession: List<WorkoutSet>): SetDraft? =
        lastTime.working().getOrNull(thisSession.working().size)?.toDraft()

    /**
     * One step up or down. An off-grid weight first snaps to the next multiple of [stepKg] in that
     * direction (a weight within 0.001 of a multiple counts as on the grid). Clamped to 0..MAX_WEIGHT_KG.
     */
    fun stepWeight(weightKg: Double, stepKg: Double, up: Boolean): Double {
        if (stepKg <= 0.0) return weightKg.coerceIn(0.0, MAX_WEIGHT_KG)
        val units = weightKg / stepKg
        val nearest = units.roundToLong()
        val onGrid = abs(weightKg - nearest * stepKg) <= EPSILON
        val target = when {
            onGrid -> if (up) nearest + 1 else nearest - 1
            up -> ceil(units).toLong()
            else -> floor(units).toLong()
        }
        val stepped = ((target * stepKg) * 1000.0).roundToLong() / 1000.0
        return stepped.coerceIn(0.0, MAX_WEIGHT_KG)
    }

    /** +/-1, clamped to 1..MAX_REPS. */
    fun stepReps(reps: Int, up: Boolean): Int = (if (up) reps + 1 else reps - 1).coerceIn(1, MAX_REPS)
}
