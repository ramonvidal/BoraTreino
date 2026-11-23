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
    /** Cardio only: the bike console's intensity (a level or a percentage; no unit), null = not logged. */
    val intensity: Int? = null,
    /** Cardio only: average power in watts as the console shows it, 0..[CardioRules.MAX_WATTS]; null = not logged. */
    val watts: Int? = null,
    /** Cardio only: average heart rate in bpm, [CardioRules.MIN_HEART_RATE]..[CardioRules.MAX_HEART_RATE]; null = not logged. */
    val heartRate: Int? = null,
    /** Cardio only: watts per kg as the console shows it (never derived), 0.0..[CardioRules.MAX_WATTS_PER_KG]; null = not logged. */
    val wattsPerKg: Double? = null,
    /** Cardio only: training stress score, 0..[CardioRules.MAX_TSS]; null = not logged. */
    val tss: Int? = null,
    /** Cardio only: intensity factor (IF), 0.0..[CardioRules.MAX_INTENSITY_FACTOR]; null = not logged. */
    val intensityFactor: Double? = null,
) {
    init {
        require(weightKg == null || weightKg >= 0) { "weightKg must not be negative" }
        require(reps == null || reps >= 0) { "reps must not be negative" }
        require(seconds == null || seconds >= 0) { "seconds must not be negative" }
        require(calories == null || calories >= 0) { "calories must not be negative" }
        require(distanceM == null || distanceM >= 0) { "distanceM must not be negative" }
        require(intensity == null || intensity in 0..CardioRules.MAX_INTENSITY) {
            "intensity must be in 0..${CardioRules.MAX_INTENSITY}"
        }
        require(watts == null || watts in 0..CardioRules.MAX_WATTS) { "watts must be in 0..${CardioRules.MAX_WATTS}" }
        require(heartRate == null || heartRate in CardioRules.MIN_HEART_RATE..CardioRules.MAX_HEART_RATE) {
            "heartRate must be in ${CardioRules.MIN_HEART_RATE}..${CardioRules.MAX_HEART_RATE}"
        }
        require(wattsPerKg == null || wattsPerKg in 0.0..CardioRules.MAX_WATTS_PER_KG) {
            "wattsPerKg must be in 0.0..${CardioRules.MAX_WATTS_PER_KG}"
        }
        require(tss == null || tss in 0..CardioRules.MAX_TSS) { "tss must be in 0..${CardioRules.MAX_TSS}" }
        require(intensityFactor == null || intensityFactor in 0.0..CardioRules.MAX_INTENSITY_FACTOR) {
            "intensityFactor must be in 0.0..${CardioRules.MAX_INTENSITY_FACTOR}"
        }
    }
}
