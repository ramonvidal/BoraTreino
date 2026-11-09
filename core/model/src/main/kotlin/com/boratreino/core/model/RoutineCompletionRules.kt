package com.boratreino.core.model

/** When a training has done everything its routine programmed. */
object RoutineCompletionRules {
    /**
     * True when [items] is not empty and every item whose machine is not in [skipped] has at least
     * `targetSets` working sets (warm-ups excluded) of that machine in [sessionSets]; the same rule
     * Today uses for a row being done.
     */
    fun isComplete(items: List<RoutineItem>, sessionSets: List<WorkoutSet>, skipped: Set<Long>): Boolean {
        if (items.isEmpty()) return false
        val counts = sessionSets.working().groupingBy { it.machineId }.eachCount()
        return items.all { it.machineId in skipped || (counts[it.machineId] ?: 0) >= it.targetSets }
    }
}
