package com.boratreino.core.database

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlan
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.Session
import com.boratreino.core.model.WorkoutSet
import java.time.DayOfWeek
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

internal fun RoutinePlanEntity.toModel() = RoutinePlan(id = id, scheduleMode = ScheduleMode.valueOf(scheduleMode))

internal fun RoutineEntity.toModel() = Routine(
    id = id,
    planId = planId,
    name = name,
    shortName = shortName,
    position = position,
    weekdays = if (weekdays.isBlank()) emptySet() else weekdays.split(',').map { DayOfWeek.valueOf(it) }.toSet(),
)

internal fun Routine.toEntity() = RoutineEntity(
    id = id,
    planId = planId,
    name = name,
    shortName = shortName,
    position = position,
    // Monday..Sunday order, so the stored string is stable.
    weekdays = weekdays.sorted().joinToString(",") { it.name },
)

internal fun RoutineItemEntity.toModel() = RoutineItem(
    routineId = routineId,
    machineId = machineId,
    position = position,
    targetSets = targetSets,
    repsMin = repsMin,
    repsMax = repsMax,
)

internal fun RoutineItem.toEntity() = RoutineItemEntity(
    routineId = routineId,
    machineId = machineId,
    position = position,
    targetSets = targetSets,
    repsMin = repsMin,
    repsMax = repsMax,
)

internal fun RoutinePlan.toEntity() = RoutinePlanEntity(id = id, scheduleMode = scheduleMode.name)
