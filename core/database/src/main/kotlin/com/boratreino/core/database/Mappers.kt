package com.boratreino.core.database

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Session
import com.boratreino.core.model.WorkoutSet
import java.time.Instant

internal fun MachineEntity.toModel() = Machine(
    id = id,
    name = name,
    muscleGroup = MuscleGroup.valueOf(muscleGroup),
    kind = MachineKind.valueOf(kind),
    stepKg = stepKg,
    setupNote = setupNote,
    restSeconds = restSeconds,
    archived = archived,
)

internal fun Machine.toEntity() = MachineEntity(
    id = id,
    name = name,
    muscleGroup = muscleGroup.name,
    kind = kind.name,
    stepKg = stepKg,
    setupNote = setupNote,
    restSeconds = restSeconds,
    archived = archived,
)

internal fun SessionEntity.toModel() = Session(
    id = id,
    startedAt = Instant.ofEpochMilli(startedAt),
    endedAt = endedAt?.let(Instant::ofEpochMilli),
    routineId = routineId,
    note = note,
)

internal fun Session.toEntity() = SessionEntity(
    id = id,
    startedAt = startedAt.toEpochMilli(),
    endedAt = endedAt?.toEpochMilli(),
    routineId = routineId,
    note = note,
)

internal fun WorkoutSetEntity.toModel() = WorkoutSet(
    id = id,
    sessionId = sessionId,
    machineId = machineId,
    position = position,
    weightKg = weightKg,
    reps = reps,
    seconds = seconds,
    calories = calories,
    distanceM = distanceM,
    loggedAt = Instant.ofEpochMilli(loggedAt),
)

internal fun WorkoutSet.toEntity() = WorkoutSetEntity(
    id = id,
    sessionId = sessionId,
    machineId = machineId,
    position = position,
    weightKg = weightKg,
    reps = reps,
    seconds = seconds,
    calories = calories,
    distanceM = distanceM,
    loggedAt = loggedAt.toEpochMilli(),
)
