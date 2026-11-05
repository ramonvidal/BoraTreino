package com.boratreino.feature.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RepeatOffer
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.LoggedSet
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetType
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.notify.RestTimer
import com.boratreino.core.notify.RestTimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** Warm-ups do not count for "sets done" on a ficha row, its tick, or the session's set count. Works from the Phase 9 contract. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TodayViewModelWarmupCountTest {
    private val sample = TodaySamples
    private val at = Instant.parse("2026-10-31T18:05:00Z")

    private fun set(id: Long, machineId: Long, position: Int, type: SetType = SetType.NORMAL) =
        WorkoutSet(id, 1L, machineId, position, 50.0, 10, null, null, null, at, type)

    private class Training : TrainingRepository {
        val plan = MutableStateFlow(
            TodayPlan(ScheduleMode.IN_TURN, emptyList(), null, RoutineChoice.Suggested, null, restDay = false),
        )
        val open = MutableStateFlow<OpenSession?>(null)

        override fun observeOpenSession(): Flow<OpenSession?> = open
        override fun observeTodayPlan(): Flow<TodayPlan> = plan
        override fun observeRepeatOffer(): Flow<RepeatOffer?> = MutableStateFlow(null)
        override suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome? = null
        override suspend fun undoRepeat(outcome: RepeatOutcome) = Unit
        override suspend fun chooseRoutine(choice: RoutineChoice) = Unit
        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = emptyFlow()
        override suspend fun closeStaleSession() = Unit
        override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet = error("not used")
        override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet = error("not used")
        override suspend fun updateSet(set: WorkoutSet) = Unit
        override suspend fun deleteSet(set: WorkoutSet) = Unit
        override suspend fun restoreSet(set: WorkoutSet) = Unit
        override suspend fun finishSession(): SessionSummary? = null
        override suspend fun reopenSession(sessionId: Long) = Unit
        override suspend fun skipMachineToday(machineId: Long) = Unit
        override suspend fun unskipMachineToday(machineId: Long) = Unit
    }

    private class Machines(private val all: List<Machine>) : MachineRepository {
        override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = MutableStateFlow(all)
        override fun observeMachine(id: Long): Flow<Machine?> = MutableStateFlow(all.firstOrNull { it.id == id })
        override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = MutableStateFlow(emptyList())
        override fun observeStartersNotAdded(): Flow<List<StarterMachine>> = MutableStateFlow(emptyList())
        override suspend fun save(machine: Machine): Long = machine.id
        override suspend fun usage(machineId: Long): MachineUsage = MachineUsage(0, emptyList())
        override suspend fun delete(machineId: Long): Boolean = true
        override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long = 0L
        override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> = emptyList()
        override suspend fun setArchived(id: Long, archived: Boolean) = Unit
    }

    private class Routines : RoutineRepository {
        override fun observePlan(): Flow<RoutinePlanWithRoutines> = emptyFlow()
        override fun observeRoutine(id: Long): Flow<RoutineWithItems?> = emptyFlow()
        override suspend fun setScheduleMode(mode: ScheduleMode) = Unit
        override suspend fun saveRoutine(routine: Routine, items: List<RoutineItem>): Long = routine.id
        override suspend fun deleteRoutine(id: Long) = Unit
        override suspend fun moveRoutine(id: Long, up: Boolean) = Unit
        override suspend fun moveItem(routineId: Long, machineId: Long, up: Boolean) = Unit
        override suspend fun duplicateRoutine(id: Long): Long = 0L
        override suspend fun createRoutine(): Long = 0L
        override suspend fun addMachine(routineId: Long, machineId: Long) = Unit
        override suspend fun removeMachine(routineId: Long, machineId: Long): RoutineItem? = null
        override suspend fun restoreItem(item: RoutineItem) = Unit
    }

    private class Rest : RestTimer {
        override val state: StateFlow<RestTimerState> = MutableStateFlow(RestTimerState())
        override val finished: SharedFlow<Unit> = MutableSharedFlow()
        override fun start(seconds: Int) = Unit
        override fun extend(seconds: Int) = Unit
        override fun skip() = Unit
    }

    private lateinit var training: Training
    private lateinit var viewModel: TodayViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        training = Training()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.open(vararg entries: SessionMachine) {
        training.plan.value = TodayPlan(
            mode = ScheduleMode.IN_TURN,
            routines = listOf(sample.routineA, sample.routineB, sample.routineC),
            suggested = sample.routineB,
            choice = RoutineChoice.Suggested,
            active = RoutineWithItems(sample.routineB, sample.routineBItems),
            restDay = false,
        )
        training.open.value = OpenSession(Session(1L, at, null, sample.routineB.id, null), entries.toList())
        viewModel = TodayViewModel(
            training,
            Machines(listOf(sample.legPress, sample.legExtension, sample.latPulldown, sample.chestPress)),
            Routines(),
            Rest(),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    private fun row(machineId: Long) = viewModel.state.value.routines!!.machines.first { it.machine.id == machineId }

    @Test
    fun `a warm-up does not count as a set done on a ficha row`() = runTest {
        val legs = sample.legPress.id
        open(SessionMachine(sample.legPress, listOf(set(1, legs, 0, SetType.WARMUP), set(2, legs, 1), set(3, legs, 2))))

        assertEquals(2, row(legs).setCount)
        assertFalse(row(legs).done)
    }

    @Test
    fun `the row is done after the target number of working sets, warm-ups aside`() = runTest {
        val legs = sample.legPress.id
        open(
            SessionMachine(
                sample.legPress,
                listOf(set(1, legs, 0, SetType.WARMUP), set(2, legs, 1), set(3, legs, 2), set(4, legs, 3)),
            ),
        )

        assertEquals(3, row(legs).setCount)
        assertTrue(row(legs).done)
    }

    @Test
    fun `drop and failure sets count as working sets`() = runTest {
        val legs = sample.legPress.id
        open(
            SessionMachine(
                sample.legPress,
                listOf(set(1, legs, 0), set(2, legs, 1, SetType.DROP), set(3, legs, 2, SetType.FAILURE)),
            ),
        )

        assertEquals(3, row(legs).setCount)
        assertTrue(row(legs).done)
    }

    @Test
    fun `a machine with only a warm-up has no sets done and can still be skipped`() = runTest {
        val ext = sample.legExtension.id
        open(SessionMachine(sample.legExtension, listOf(set(1, ext, 0, SetType.WARMUP))))

        assertEquals(0, row(ext).setCount)
        assertFalse(row(ext).done)
        assertTrue(row(ext).canSkip)
    }

    @Test
    fun `the session set count leaves warm-ups out`() = runTest {
        val legs = sample.legPress.id
        val ext = sample.legExtension.id
        open(
            SessionMachine(sample.legExtension, listOf(set(10, ext, 0, SetType.WARMUP), set(11, ext, 1))),
            SessionMachine(
                sample.legPress,
                listOf(set(1, legs, 0, SetType.WARMUP), set(2, legs, 1, SetType.WARMUP), set(3, legs, 2), set(4, legs, 3)),
            ),
        )

        assertEquals(3, viewModel.state.value.session?.setCount)
    }
}
