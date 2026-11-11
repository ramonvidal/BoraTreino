package com.boratreino.feature.routines

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.StarterMachines

/** Fixed gym data for the Routines screenshots: real gym names, nothing that changes between runs. */
internal object GymSamples {
    private fun machine(
        id: Long,
        name: String,
        group: MuscleGroup,
        stepKg: Double = 2.5,
        setupNote: String? = null,
        bodyweight: Boolean = false,
        archived: Boolean = false,
    ) = Machine(
        id = id,
        name = name,
        muscleGroup = group,
        kind = if (bodyweight) MachineKind.BODYWEIGHT_REPS else MachineKind.WEIGHT_REPS,
        stepKg = stepKg,
        setupNote = setupNote,
        restSeconds = null,
        archived = archived,
    )

    val legPress = machine(1L, "Leg press", MuscleGroup.LEGS, 5.0, "Seat 4")
    val legExtension = machine(2L, "Leg extension", MuscleGroup.LEGS)
    val latPulldown = machine(3L, "Lat pulldown", MuscleGroup.BACK, setupNote = "Thigh pad 3")
    val seatedRow = machine(4L, "Seated row", MuscleGroup.BACK)
    val pullUps = machine(5L, "Pull-ups", MuscleGroup.BACK, bodyweight = true)
    val chestPress = machine(6L, "Chest press", MuscleGroup.CHEST)
    val shoulderPress = machine(7L, "Shoulder press", MuscleGroup.SHOULDERS)
    val cableCurl = machine(8L, "Cable biceps curl", MuscleGroup.ARMS)
    val hackSquat = machine(9L, "Hack squat", MuscleGroup.LEGS, 5.0, archived = true)

    /** A machine that is not in the starter list, for the delete dialogs. */
    val legCurl = machine(21L, "Leg curl", MuscleGroup.LEGS)

    /** The gym's active machines, grouped by muscle group as the screen shows them. */
    val machineGroups: List<MachineGroup> = listOf(
        MachineGroup(MuscleGroup.LEGS, listOf(legPress, legExtension)),
        MachineGroup(MuscleGroup.BACK, listOf(latPulldown, seatedRow, pullUps)),
        MachineGroup(MuscleGroup.CHEST, listOf(chestPress)),
        MachineGroup(MuscleGroup.SHOULDERS, listOf(shoulderPress)),
        MachineGroup(MuscleGroup.ARMS, listOf(cableCurl)),
    )

    private val gymNames: Set<String> = machineGroups.flatMap { it.machines }.map { it.name }.toSet()

    /** Starters the gym does not have yet. */
    val startersNotAdded: List<StarterMachine> = StarterMachines.all.filter { it.name !in gymNames }

    /** The picker rows of [list] (starters only), by muscle group, as the view model builds them. */
    fun pickerGroups(list: List<StarterMachine>): List<PickerGroup> =
        list.groupBy { it.muscleGroup }.toSortedMap().map { (group, starters) ->
            PickerGroup(group, starters.map { PickerRow(key = "s:" + it.name, name = it.name, group = group, starter = it) })
        }

    private fun routine(id: Long, name: String, shortName: String, position: Int) =
        Routine(id = id, planId = 1L, name = name, shortName = shortName, position = position, weekdays = emptySet())

    /** The Fichas cards: the exercises' muscle groups (distinct, in routine order) and the count. */
    val routines: List<RoutineRow> = listOf(
        RoutineRow(
            routine(1L, "Treino A", "A", 0),
            machineCount = 4,
            machineNames = listOf("Leg press", "Leg extension", "Hack squat", "Leg curl"),
            muscleGroups = listOf(MuscleGroup.LEGS),
        ),
        RoutineRow(
            routine(2L, "Treino B", "B", 1),
            machineCount = 4,
            machineNames = listOf("Lat pulldown", "Seated row", "Pull-ups", "Cable biceps curl"),
            muscleGroups = listOf(MuscleGroup.BACK, MuscleGroup.ARMS),
        ),
        RoutineRow(
            routine(3L, "Treino C", "C", 2),
            machineCount = 3,
            machineNames = listOf("Chest press", "Shoulder press", "Pull-ups"),
            muscleGroups = listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.BACK),
        ),
    )

    /** The gym populated, the picker folded, the routines below. */
    val populated = RoutinesUiState(
        routines = routines,
        scheduleMode = ScheduleMode.IN_TURN,
        machineGroups = machineGroups,
        pickerAvailable = true,
        startersExpanded = false,
        gymCount = 8,
    )

    /** Nothing registered yet: the picker is open and there are no routines. */
    val emptyGym = RoutinesUiState(
        pickerGroups = pickerGroups(StarterMachines.all),
        pickerAvailable = true,
        startersExpanded = true,
        gymCount = 0,
    )
}
