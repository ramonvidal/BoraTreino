package com.boratreino.core.model

import java.time.LocalDate
import java.util.Locale

/**
 * The specific muscles the body map paints. Enum order is display order. Each muscle belongs to one of the six big
 * [MuscleGroup]s (which stay the unit of hard sets, balance, recency and records); [MuscleGroup.CARDIO] has none.
 */
enum class Muscle(val group: MuscleGroup) {
    CHEST(MuscleGroup.CHEST),
    SHOULDERS(MuscleGroup.SHOULDERS),
    BICEPS(MuscleGroup.ARMS),
    TRICEPS(MuscleGroup.ARMS),
    FOREARMS(MuscleGroup.ARMS),
    ABS(MuscleGroup.CORE),
    QUADRICEPS(MuscleGroup.LEGS),
    HAMSTRINGS(MuscleGroup.LEGS),
    GLUTES(MuscleGroup.LEGS),
    CALVES(MuscleGroup.LEGS),

    /** Adductors and abductors. */
    HIPS(MuscleGroup.LEGS),

    /** Lats and middle back. */
    UPPER_BACK(MuscleGroup.BACK),

    /** Trapezius and neck. */
    TRAPS(MuscleGroup.BACK),
    LOWER_BACK(MuscleGroup.BACK);

    companion object {
        /** The muscles of [group], in enum order; empty for [MuscleGroup.CARDIO]. */
        fun ofGroup(group: MuscleGroup): List<Muscle> = entries.filter { it.group == group }
    }
}

/**
 * The specific muscle of a library muscle name (trimmed, case-insensitive): quadriceps -> QUADRICEPS,
 * hamstrings -> HAMSTRINGS, glutes -> GLUTES, calves -> CALVES, adductors / abductors -> HIPS, chest -> CHEST,
 * lats / "middle back" -> UPPER_BACK, traps / neck -> TRAPS, "lower back" -> LOWER_BACK, shoulders -> SHOULDERS,
 * biceps -> BICEPS, triceps -> TRICEPS, forearms -> FOREARMS, abdominals -> ABS. Null for an unknown name.
 */
fun libraryMuscle(name: String): Muscle? = when (name.trim().lowercase(Locale.ROOT)) {
    "quadriceps" -> Muscle.QUADRICEPS
    "hamstrings" -> Muscle.HAMSTRINGS
    "glutes" -> Muscle.GLUTES
    "calves" -> Muscle.CALVES
    "adductors", "abductors" -> Muscle.HIPS
    "chest" -> Muscle.CHEST
    "lats", "middle back" -> Muscle.UPPER_BACK
    "traps", "neck" -> Muscle.TRAPS
    "lower back" -> Muscle.LOWER_BACK
    "shoulders" -> Muscle.SHOULDERS
    "biceps" -> Muscle.BICEPS
    "triceps" -> Muscle.TRICEPS
    "forearms" -> Muscle.FOREARMS
    "abdominals" -> Muscle.ABS
    else -> null
}

/**
 * Sets of a 7-day block ([weekStart] = its first day) for one [muscle]: [directSets] are fractional (a set is split
 * equally among the muscles it works directly), [indirectSets] count one per set that works the muscle as a secondary.
 */
data class MuscleDetailWeek(
    val weekStart: LocalDate,
    val muscle: Muscle,
    val directSets: Double,
    val indirectSets: Double,
)
