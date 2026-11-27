package com.boratreino.core.model

import java.time.Instant

/** What kind of set it was. Only [WARMUP] is left out of records, progression, volume and counts. */
enum class SetType { NORMAL, WARMUP, DROP, FAILURE }

/** A working set counts for performance: every type except [SetType.WARMUP]. */
val WorkoutSet.isWorking: Boolean get() = type != SetType.WARMUP

/** The working sets, order kept. */
fun List<WorkoutSet>.working(): List<WorkoutSet> = filter { it.isWorking }

/**
 * One logged set on a machine within a session.
 *
 * Shapes by machine kind (invariant): WEIGHT_REPS and BODYWEIGHT_REPS have a weight and reps; WEIGHT_TIME has a
 * weight and [seconds] (no reps, no distance); WEIGHT_DISTANCE has a weight and [distanceM] (no reps, no seconds);
 * TIME has weight 0.0 and [seconds]; CARDIO has no weight and no reps. Every strength set has a non-null weight
 * (0.0 = no load), which is what [CardioRules.isCardio] relies on.
 */
data class WorkoutSet(
    val id: Long,
    val sessionId: Long,
    val machineId: Long,
    val position: Int,
    val weightKg: Double?,
    val reps: Int?,
    val seconds: Int?,
    val calories: Int?,
    val distanceM: Int?,
    val loggedAt: Instant,
    val type: SetType = SetType.NORMAL,
    /** Cardio only: the console's intensity (no unit, e.g. 0.79), 0.0..[CardioRules.MAX_INTENSITY], 2 decimals; null = not logged. */
    val intensity: Double? = null,
) {
    init {
        require(weightKg == null || weightKg >= 0) { "weightKg must not be negative" }
        require(reps == null || reps >= 0) { "reps must not be negative" }
        require(seconds == null || seconds >= 0) { "seconds must not be negative" }
        require(calories == null || calories >= 0) { "calories must not be negative" }
        require(distanceM == null || distanceM >= 0) { "distanceM must not be negative" }
        require(intensity == null || intensity in 0.0..CardioRules.MAX_INTENSITY) {
            "intensity must be in 0.0..${CardioRules.MAX_INTENSITY}"
        }
    }
}
