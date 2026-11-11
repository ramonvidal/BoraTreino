package com.boratreino.feature.setup

import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.StarterMachines
import java.time.DayOfWeek

/** Fixed setup states for the step screenshots and the content tests: real starter names, nothing that changes. */
internal object SetupSamples {
    private fun starter(name: String) = StarterMachines.all.first { it.name == name }

    val legPress = starter("Leg press")
    val chestPress = starter("Chest press")

    /** Step 1's catalogue: a few starters by muscle group. */
    val groups: List<SetupGroup> = listOf(
        SetupGroup(MuscleGroup.LEGS, listOf(legPress, starter("Leg extension"))),
        SetupGroup(MuscleGroup.CHEST, listOf(chestPress)),
        SetupGroup(MuscleGroup.BACK, listOf(starter("Lat pulldown"), starter("Seated row"))),
    )

    /** Step 1 with two starters ticked. */
    val gym = SetupUiState(step = 0, groups = groups, selected = setOf("Leg press", "Chest press"))

    /** Step 1 with nothing ticked. */
    val gymEmpty = SetupUiState(step = 0, groups = groups)

    /** Step 2 with the two exercises from step 1, both ticked, the default name. */
    val chosen: List<SetupExercise> = listOf(
        SetupExercise(101L, "Leg press", legPress),
        SetupExercise(102L, "Chest press", chestPress),
    )

    val routine = SetupUiState(step = 1, groups = groups, chosen = chosen)

    /** Step 2 with nothing chosen in step 1: the explanation, no name field. */
    val routineNone = SetupUiState(step = 1, groups = groups)

    /** Step 3 with Monday, Wednesday and Friday picked. */
    val days = SetupUiState(step = 2, days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
}
