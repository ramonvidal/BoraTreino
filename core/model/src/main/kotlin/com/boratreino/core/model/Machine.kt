package com.boratreino.core.model

/** A gym machine or exercise the user can log sets on. */
data class Machine(
    val id: Long,
    val name: String,
    val muscleGroup: MuscleGroup,
    val kind: MachineKind,
    val stepKg: Double,
    val setupNote: String?,
    val restSeconds: Int?,
    val archived: Boolean,
) {
    init {
        require(name.isNotBlank()) { "Machine name must not be blank" }
        require(stepKg > 0) { "stepKg must be positive" }
        require(restSeconds == null || restSeconds > 0) { "restSeconds must be null or positive" }
    }
}
