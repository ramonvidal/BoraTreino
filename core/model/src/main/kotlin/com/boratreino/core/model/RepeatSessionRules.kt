package com.boratreino.core.model

/** How "same as last time" fills a training. */
enum class RepeatMode { SAME, SUGGESTED }

/** A set to write when repeating a session; the repository adds ids, positions and times. */
data class PlannedSet(
    val machineId: Long,
    val weightKg: Double?,
    val reps: Int?,
    val seconds: Int?,
    val calories: Int?,
    val distanceM: Int?,
)

object RepeatSessionRules {
    /**
     * Plans the sets, ficha order (by item position) then set position. [items] are the ficha machines
     * to consider (skipped ones already removed); [lastSets] the sets of the previous session of the
     * routine; [machineIdsWithSetsToday] are left out; [suggestions] (machineId -> Suggestion) are used
     * only for [RepeatMode.SUGGESTED] and only for weight machines. A machine with no last sets is left out.
     */
    fun plan(
        items: List<RoutineItem>,
        lastSets: List<WorkoutSet>,
        machineIdsWithSetsToday: Set<Long>,
        mode: RepeatMode,
        suggestions: Map<Long, Suggestion>,
    ): List<PlannedSet> {
        val byMachine = lastSets.groupBy { it.machineId }
        return items
            .sortedBy { it.position }
            .map { it.machineId }
            .distinct()
            .filter { it !in machineIdsWithSetsToday }
            .flatMap { machineId ->
                val sets = byMachine[machineId].orEmpty().sortedWith(compareBy({ it.position }, { it.id }))
                val suggestion = if (mode == RepeatMode.SUGGESTED && sets.none { CardioRules.isCardio(it) }) {
                    suggestions[machineId]
                } else {
                    null
                }
                sets.map { set ->
                    if (suggestion != null) {
                        PlannedSet(machineId, suggestion.weightKg, suggestion.reps, null, null, null)
                    } else {
                        PlannedSet(machineId, set.weightKg, set.reps, set.seconds, set.calories, set.distanceM)
                    }
                }
            }
    }

    fun machineCount(plan: List<PlannedSet>): Int = plan.map { it.machineId }.distinct().size
}
