package com.boratreino.core.model

/** How much a machine is used: logged sets and the routines it is in. */
data class MachineUsage(val setCount: Int, val routineNames: List<String>) {
    val canDelete: Boolean get() = setCount == 0
}

/** Edits to a routine's machine list; every result has positions 0..n-1 in list order. */
object RoutineEditRules {
    const val DEFAULT_SETS = 3
    const val DEFAULT_REPS_MIN = 8
    const val DEFAULT_REPS_MAX = 12

    private fun renumbered(items: List<RoutineItem>): List<RoutineItem> =
        items.mapIndexed { i, item -> if (item.position == i) item else item.copy(position = i) }

    fun contains(items: List<RoutineItem>, machineId: Long): Boolean = items.any { it.machineId == machineId }

    /** Appends the machine with the default target; no-op when already there; positions renumbered 0..n-1 in list order. */
    fun withMachine(routineId: Long, items: List<RoutineItem>, machineId: Long): List<RoutineItem> {
        if (contains(items, machineId)) return items
        val added = RoutineItem(routineId, machineId, items.size, DEFAULT_SETS, DEFAULT_REPS_MIN, DEFAULT_REPS_MAX)
        return renumbered(items + added)
    }

    /** Removes the machine; positions renumbered 0..n-1; no-op when absent. */
    fun withoutMachine(items: List<RoutineItem>, machineId: Long): List<RoutineItem> {
        if (!contains(items, machineId)) return items
        return renumbered(items.filter { it.machineId != machineId })
    }

    /** Undo of a removal: inserts [item] at its own position (clamped to 0..size); positions renumbered. No-op when its machine is already there. */
    fun withItemBack(items: List<RoutineItem>, item: RoutineItem): List<RoutineItem> {
        if (contains(items, item.machineId)) return items
        val list = items.toMutableList()
        list.add(item.position.coerceIn(0, list.size), item)
        return renumbered(list)
    }

    /** True when a ficha is active and neither it nor [skipped] contains the machine: the "Add to ficha?" question applies. */
    fun shouldOfferAdding(active: RoutineWithItems?, skipped: Set<Long>, machineId: Long): Boolean =
        active != null && !contains(active.items, machineId) && machineId !in skipped
}

/** The routine without the given machines; positions are left as they were. */
fun RoutineWithItems.withoutMachines(ids: Set<Long>): RoutineWithItems =
    copy(items = items.filter { it.machineId !in ids })
