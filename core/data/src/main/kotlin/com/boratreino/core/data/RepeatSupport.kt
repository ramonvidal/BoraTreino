package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.PlannedSet
import com.boratreino.core.model.ProgressionRules
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.Suggestion
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.first
import java.time.Instant

/** Progression sessions looked at per machine, as in the machine screen. */
private const val HISTORY_SESSIONS = 2

internal fun PlannedSet.toWorkoutSet(sessionId: Long, position: Int, loggedAt: Instant) = WorkoutSet(
    id = 0,
    sessionId = sessionId,
    machineId = machineId,
    position = position,
    weightKg = weightKg,
    reps = reps,
    seconds = seconds,
    calories = calories,
    distanceM = distanceM,
    loggedAt = loggedAt,
)

/** Progression suggestion per weight machine of [items], from the machine's sessions other than [excludeSessionId]. */
internal suspend fun WorkoutStore.repeatSuggestions(items: List<RoutineItem>, excludeSessionId: Long): Map<Long, Suggestion> {
    val byId = machines().associateBy { it.id }
    val result = HashMap<Long, Suggestion>()
    for (item in items) {
        val machine = byId[item.machineId] ?: continue
        if (machine.kind == MachineKind.CARDIO) continue
        val history = observeLastSessionSets(item.machineId, excludeSessionId, HISTORY_SESSIONS).first()
        ProgressionRules.suggest(item, machine.stepKg, history)?.let { result[item.machineId] = it }
    }
    return result
}
