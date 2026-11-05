package com.boratreino.core.model

/** Estimated one-rep max (Epley), used only to compare working sets in Progress. */
object OneRepMaxRules {
    const val MAX_REPS = 12

    /** null when [weightKg] <= 0, [reps] < 1 or [reps] > [MAX_REPS]. One rep is its own weight; else weight * (1 + reps / 30). */
    fun estimate(weightKg: Double, reps: Int): Double? = when {
        weightKg <= 0.0 || reps < 1 || reps > MAX_REPS -> null
        reps == 1 -> weightKg
        else -> weightKg * (1 + reps / 30.0)
    }

    /** Working sets only (a warm-up gives null); null weight or reps gives null. */
    fun estimate(set: WorkoutSet): Double? {
        if (!set.isWorking) return null
        return estimate(set.weightKg ?: return null, set.reps ?: return null)
    }

    /** The working set with the highest estimate (ties: heavier, then later position); null if none is eligible. */
    fun bestSet(sets: List<WorkoutSet>): WorkoutSet? =
        sets.mapNotNull { set -> estimate(set)?.let { set to it } }
            .maxWithOrNull(compareBy({ it.second }, { it.first.weightKg ?: 0.0 }, { it.first.position }))
            ?.first
}
