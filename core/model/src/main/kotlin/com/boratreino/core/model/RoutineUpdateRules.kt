package com.boratreino.core.model

/** A routine item whose target sets go from [from] to [to]. */
data class SetsChange(val machineId: Long, val from: Int, val to: Int)

/** A machine trained but not in the routine, added at the end with [sets] target sets. */
data class Addition(val machineId: Long, val sets: Int)

/**
 * What "update the routine with this training" changes. [previousItems] is filled by the repository
 * once the update is applied (the routine's items before it), so the update can be undone.
 */
data class RoutineUpdate(
    val setChanges: List<SetsChange>,
    val additions: List<Addition>,
    val previousItems: List<RoutineItem> = emptyList(),
) {
    val isEmpty: Boolean get() = setChanges.isEmpty() && additions.isEmpty()
}

/** Brings a routine in line with a finished training. Plain Kotlin. */
object RoutineUpdateRules {
    const val MIN_SETS = 1
    const val MAX_SETS = 10

    /**
     * For each routine item whose machine has working, non-cardio sets in the session, targetSets becomes
     * the number of working sets done (clamped 1..10) when different. Machines trained but not in the
     * routine are added at the end (in order of first set) with the sets done; cardio machines are only
     * added, their target is left alone. Warm-ups never count; items without sets are left alone; rep
     * ranges never change. Null when nothing changes.
     */
    fun plan(routineItems: List<RoutineItem>, sessionSets: List<WorkoutSet>): RoutineUpdate? {
        val byMachine = sessionSets.working().groupBy { it.machineId }
        if (byMachine.isEmpty()) return null
        val inRoutine = routineItems.map { it.machineId }.toSet()

        val changes = routineItems.mapNotNull { item ->
            val sets = byMachine[item.machineId] ?: return@mapNotNull null
            if (sets.any { CardioRules.isCardio(it) }) return@mapNotNull null
            val to = sets.size.coerceIn(MIN_SETS, MAX_SETS)
            if (to == item.targetSets) null else SetsChange(item.machineId, item.targetSets, to)
        }
        val additions = byMachine.entries
            .filter { it.key !in inRoutine }
            .sortedBy { (_, sets) -> sets.minOf { it.loggedAt } }
            .map { (machineId, sets) -> Addition(machineId, sets.size.coerceIn(MIN_SETS, MAX_SETS)) }

        return if (changes.isEmpty() && additions.isEmpty()) null else RoutineUpdate(changes, additions)
    }

    /** The items after [update]: new targets, additions appended with the default rep range, positions 0..n-1. */
    fun apply(routineId: Long, items: List<RoutineItem>, update: RoutineUpdate): List<RoutineItem> {
        val newTargets = update.setChanges.associate { it.machineId to it.to }
        val kept = items.map { item -> newTargets[item.machineId]?.let { item.copy(targetSets = it) } ?: item }
        val known = items.map { it.machineId }.toSet()
        val added = update.additions.filter { it.machineId !in known }.map {
            RoutineItem(
                routineId, it.machineId, 0, it.sets,
                RoutineEditRules.DEFAULT_REPS_MIN, RoutineEditRules.DEFAULT_REPS_MAX,
            )
        }
        return (kept + added).mapIndexed { i, item -> item.copy(position = i) }
    }
}
