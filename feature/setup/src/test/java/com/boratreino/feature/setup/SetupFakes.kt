package com.boratreino.feature.setup

import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlan
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.RoutineUpdate
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.settings.BodySettings
import com.boratreino.core.model.WeightEntry
import com.boratreino.core.testing.StubBodySettings
import com.boratreino.core.testing.StubMachineRepository
import com.boratreino.core.testing.StubRoutineRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * The gym as the setup sees it: starters are added once per name (a second add of the same name returns the
 * same id). [addCalls] records every batch of names in call order. Set [gate] to hold the next add until it completes.
 */
internal class FakeSetupMachines(private val all: List<Machine> = emptyList()) : StubMachineRepository(all) {
    val addCalls = mutableListOf<List<String>>()
    var gate: CompletableDeferred<Unit>? = null
    private val ids = LinkedHashMap<String, Long>()

    /** The id a starter name got, or null when it was never added. */
    fun idOf(name: String): Long? = ids[name]

    override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long =
        addStarters(listOf(starter), portuguese).single()

    override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> {
        addCalls += starters.map { it.name }
        gate?.await()
        return starters.map { starter -> ids.getOrPut(starter.name) { 100L + ids.size } }
    }
}

/**
 * The routine store as the setup uses it: one plan (in turn, no days), routines kept by id. Every write is
 * recorded: [createCount], [saved] (routine and items, in call order), [trainingDayWrites], [scheduleModeWrites].
 */
internal class FakeSetupRoutines : StubRoutineRepository() {
    private val plan = MutableStateFlow(RoutinePlan(id = 1L, scheduleMode = ScheduleMode.IN_TURN))
    private val routines = MutableStateFlow<List<RoutineWithItems>>(emptyList())
    private var nextId = 1L

    var createCount = 0
        private set
    val saved = mutableListOf<Pair<Routine, List<RoutineItem>>>()
    val trainingDayWrites = mutableListOf<Set<DayOfWeek>>()
    val scheduleModeWrites = mutableListOf<ScheduleMode>()

    /** Set to hold the next routine save until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    /** The routines in the store right now, by position. */
    fun current(): List<RoutineWithItems> = routines.value

    override fun observePlan(): Flow<RoutinePlanWithRoutines> =
        combine(plan, routines) { current, all -> RoutinePlanWithRoutines(current, all) }

    override fun observeRoutine(id: Long): Flow<RoutineWithItems?> = routines.map { list -> list.firstOrNull { it.routine.id == id } }

    override suspend fun setScheduleMode(mode: ScheduleMode) {
        scheduleModeWrites += mode
        plan.value = plan.value.copy(scheduleMode = mode)
    }

    override suspend fun setTrainingDays(days: Set<DayOfWeek>) {
        trainingDayWrites += days
        plan.value = plan.value.copy(trainingDays = days)
    }

    override suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>): Long {
        saved += routine to items
        gate?.await()
        val id = if (routine.id == 0L) nextId++ else routine.id
        val stored = routine.copy(id = id, planId = 1L)
        val entry = RoutineWithItems(stored, items.map { it.copy(routineId = id) })
        routines.value = routines.value.map { if (it.routine.id == id) entry else it }
            .let { list -> if (list.any { it.routine.id == id }) list else list + entry }
        return id
    }

    override suspend fun deleteRoutine(id: Long) {
        routines.value = routines.value.filter { it.routine.id != id }
    }

    override suspend fun createRoutine(): Long {
        createCount++
        val id = nextId++
        val position = routines.value.size
        val routine = Routine(id = id, planId = 1L, name = "Routine A", shortName = "A", position = position, weekdays = emptySet())
        routines.value = routines.value + RoutineWithItems(routine, emptyList())
        return id
    }
}

/** The owner's body data: only the profile matters to the setup (for the mascot). Weights and the energy switch are unused. */
internal class FakeBodySettings(initial: BodyProfile? = null) : StubBodySettings() {
    private val state = MutableStateFlow(initial)

    override val profile: Flow<BodyProfile?> = state

    override suspend fun setProfile(profile: BodyProfile) {
        state.value = profile
    }
}
