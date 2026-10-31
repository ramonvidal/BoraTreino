package com.boratreino.core.model

import java.time.Instant

/** One logged set on a machine within a session. */
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
) {
    init {
        require(weightKg == null || weightKg >= 0) { "weightKg must not be negative" }
        require(reps == null || reps >= 0) { "reps must not be negative" }
        require(seconds == null || seconds >= 0) { "seconds must not be negative" }
        require(calories == null || calories >= 0) { "calories must not be negative" }
        require(distanceM == null || distanceM >= 0) { "distanceM must not be negative" }
    }
}
