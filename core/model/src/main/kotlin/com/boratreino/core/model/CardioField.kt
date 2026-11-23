package com.boratreino.core.model

/** A cardio value the user can type on the number pad; each maps to one [CardioDraft] property. */
enum class CardioField {
    MINUTES,
    DISTANCE_KM,
    CALORIES,
    INTENSITY,
    WATTS,
    HEART_RATE,
    WATTS_PER_KG,
    TSS,
    INTENSITY_FACTOR,
}
