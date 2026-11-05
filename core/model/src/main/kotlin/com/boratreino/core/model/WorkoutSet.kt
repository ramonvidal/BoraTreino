package com.boratreino.core.model

import java.time.Instant

/** What kind of set it was. Only [WARMUP] is left out of records, progression, volume and counts. */
enum class SetType { NORMAL, WARMUP, DROP, FAILURE }

/** A working set counts for performance: every type except [SetType.WARMUP]. */
val WorkoutSet.isWorking: Boolean get() = type != SetType.WARMUP

/** The working sets, order kept. */
fun List<WorkoutSet>.working(): List<WorkoutSet> = filter { it.isWorking }

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
    val type: SetType = SetType.NORMAL,
) {
    init {
        require(weightKg == null || weightKg >= 0) { "weightKg must not be negative" }
        require(reps == null || reps >= 0) { "reps must not be negative" }
        require(seconds == null || seconds >= 0) { "seconds must not be negative" }
        require(calories == null || calories >= 0) { "calories must not be negative" }
        require(distanceM == null || distanceM >= 0) { "distanceM must not be negative" }
    }
}
