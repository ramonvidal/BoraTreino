package com.boratreino.core.model

/** A ready-made machine the user can add with one tap. */
data class StarterMachine(
    val name: String,
    val muscleGroup: MuscleGroup,
    val kind: MachineKind,
    val stepKg: Double,
)

/** The built-in list of common gym machines (no cardio yet). */
object StarterMachines {
    private fun w(name: String, group: MuscleGroup, step: Double = 2.5) =
        StarterMachine(name, group, MachineKind.WEIGHT_REPS, step)

    private fun bw(name: String, group: MuscleGroup) =
        StarterMachine(name, group, MachineKind.BODYWEIGHT_REPS, 2.5)

    val all: List<StarterMachine> = listOf(
        w("Leg press", MuscleGroup.LEGS, 5.0),
        w("Leg extension", MuscleGroup.LEGS),
        w("Lying leg curl", MuscleGroup.LEGS),
        w("Seated leg curl", MuscleGroup.LEGS),
        w("Hip abductor", MuscleGroup.LEGS),
        w("Hip adductor", MuscleGroup.LEGS),
        w("Calf raise", MuscleGroup.LEGS, 5.0),
        w("Hack squat", MuscleGroup.LEGS, 5.0),
        w("Smith machine squat", MuscleGroup.LEGS, 5.0),
        w("Glute kickback", MuscleGroup.LEGS),
        w("Chest press", MuscleGroup.CHEST),
        w("Incline chest press", MuscleGroup.CHEST),
        w("Pec deck", MuscleGroup.CHEST),
        w("Cable crossover", MuscleGroup.CHEST),
        bw("Dips", MuscleGroup.CHEST),
        w("Lat pulldown", MuscleGroup.BACK),
        w("Seated row", MuscleGroup.BACK),
        w("Assisted pull-up", MuscleGroup.BACK),
        w("Back extension", MuscleGroup.BACK),
        bw("Pull-ups", MuscleGroup.BACK),
        w("Shoulder press", MuscleGroup.SHOULDERS),
        w("Lateral raise machine", MuscleGroup.SHOULDERS),
        w("Rear delt fly", MuscleGroup.SHOULDERS),
        w("Shrugs", MuscleGroup.SHOULDERS),
        w("Biceps curl machine", MuscleGroup.ARMS),
        w("Cable biceps curl", MuscleGroup.ARMS),
        w("Triceps pushdown", MuscleGroup.ARMS),
        w("Triceps extension machine", MuscleGroup.ARMS),
        w("Ab crunch machine", MuscleGroup.CORE),
        w("Rotary torso", MuscleGroup.CORE),
    )
}
