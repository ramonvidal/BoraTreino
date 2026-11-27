package com.boratreino.core.model

/**
 * What a set on a machine records. New values are appended at the end: the kind is stored by name.
 * Every strength kind logs a weight (0.0 = no load); [CARDIO] logs none.
 */
enum class MachineKind { WEIGHT_REPS, BODYWEIGHT_REPS, CARDIO, WEIGHT_TIME, WEIGHT_DISTANCE, TIME }

/** What a strength set measures after its load. */
enum class SetMeasure { REPS, SECONDS, METRES }

/** REPS for WEIGHT_REPS/BODYWEIGHT_REPS, SECONDS for WEIGHT_TIME/TIME, METRES for WEIGHT_DISTANCE, null for CARDIO. */
val MachineKind.measure: SetMeasure?
    get() = when (this) {
        MachineKind.WEIGHT_REPS, MachineKind.BODYWEIGHT_REPS -> SetMeasure.REPS
        MachineKind.WEIGHT_TIME, MachineKind.TIME -> SetMeasure.SECONDS
        MachineKind.WEIGHT_DISTANCE -> SetMeasure.METRES
        MachineKind.CARDIO -> null
    }

/** True for every kind except CARDIO. */
val MachineKind.isStrength: Boolean get() = this != MachineKind.CARDIO

/** Shows a weight stepper: every strength kind except TIME (BODYWEIGHT_REPS = added weight). */
val MachineKind.hasLoad: Boolean get() = isStrength && this != MachineKind.TIME
