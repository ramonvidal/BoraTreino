package com.boratreino.feature.today

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetSummary
import com.boratreino.core.model.StarterMachines
import com.boratreino.core.model.WorkoutSet
import java.time.Instant

/** Fixed gym data for the Today screenshots. Dates are fixed so the pictures don't change. */
internal object TodaySamples {
    val legPress = machine(1, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, "Seat 4")
    val legExtension = machine(2, "Leg extension", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 2.5)
    val latPulldown = machine(3, "Lat pulldown", MuscleGroup.BACK, MachineKind.WEIGHT_REPS, 2.5, "Thigh pad 3")
    val seatedRow = machine(4, "Seated row", MuscleGroup.BACK, MachineKind.WEIGHT_REPS, 2.5)
    val pullUps = machine(5, "Pull-ups", MuscleGroup.BACK, MachineKind.BODYWEIGHT_REPS, 2.5)
    val chestPress = machine(6, "Chest press", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5)
    val shoulderPress = machine(7, "Shoulder press", MuscleGroup.SHOULDERS, MachineKind.WEIGHT_REPS, 2.5)
    val cableCurl = machine(8, "Cable biceps curl", MuscleGroup.ARMS, MachineKind.WEIGHT_REPS, 2.5)

    /** Names of the machines the user already has, archived ones included. */
    private val owned = listOf(legPress, legExtension, latPulldown, seatedRow, pullUps, chestPress, shoulderPress, cableCurl)
        .map { it.name }
        .toSet()

    /** Starter machines not added yet, grouped by muscle group. */
    fun startersNotAdded(): List<StarterGroup> = MuscleGroup.entries.mapNotNull { group ->
        StarterMachines.all
            .filter { it.muscleGroup == group && it.name !in owned }
            .takeIf { it.isNotEmpty() }
            ?.let { StarterGroup(group, it) }
    }

    val lastVisit: Instant = Instant.parse("2026-10-24T18:00:00Z")
    val today: Instant = Instant.parse("2026-10-31T18:05:00Z")

    /** A session with three machines and nine sets, the most recently used machine first. */
    val openSession = TodayUiState(
        loaded = true,
        session = SessionUi(
            machineCount = 3,
            setCount = 9,
            machines = listOf(
                SessionMachineUi(
                    seatedRow,
                    SetSummary.Mixed(listOf(SetDraft(40.0, 10), SetDraft(42.5, 8), SetDraft(42.5, 8))),
                ),
                SessionMachineUi(latPulldown, SetSummary.SameWeight(55.0, listOf(10, 10, 8))),
                SessionMachineUi(legPress, SetSummary.Uniform(3, 120.0, 12)),
            ),
        ),
        recent = listOf(seatedRow, latPulldown, legPress),
    )

    /** Nothing open yet: the recent machines. */
    val noSession = TodayUiState(
        loaded = true,
        session = null,
        recent = listOf(legPress, latPulldown, pullUps, chestPress),
    )

    fun set(
        id: Long,
        machine: Machine,
        position: Int,
        weightKg: Double?,
        reps: Int?,
        at: Instant,
    ) = WorkoutSet(
        id = id,
        sessionId = 1L,
        machineId = machine.id,
        position = position,
        weightKg = weightKg,
        reps = reps,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = at,
    )
}

private fun machine(
    id: Long,
    name: String,
    group: MuscleGroup,
    kind: MachineKind,
    stepKg: Double,
    setupNote: String? = null,
) = Machine(
    id = id,
    name = name,
    muscleGroup = group,
    kind = kind,
    stepKg = stepKg,
    setupNote = setupNote,
    restSeconds = null,
    archived = false,
)
