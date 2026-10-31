package com.boratreino.core.model

/** A machine inside a routine, with its target sets and rep range. */
data class RoutineItem(
    val routineId: Long,
    val machineId: Long,
    val position: Int,
    val targetSets: Int,
    val repsMin: Int,
    val repsMax: Int,
) {
    init {
        require(targetSets >= 1) { "targetSets must be at least 1" }
        require(repsMin >= 1) { "repsMin must be at least 1" }
        require(repsMin <= repsMax) { "repsMin must not exceed repsMax" }
    }
}
