package com.boratreino.feature.today

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.RepeatOffer
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.LoggedSet
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineUpdate
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetType
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.TrainingSummary
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.notify.RestTimer
import com.boratreino.core.notify.RestTimerState
import com.boratreino.core.settings.TrainingSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The machine screen when the logged set completes the routine: the training finishes by itself and the screen
 * goes back to Today; warm-ups and edits never finish it; a failed finish keeps the set. Also the set row's Delete.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MachineRoutineFinishTest {
    private val legPress = Machine(
        id = 11L,
        name = "Leg press",
        muscleGroup = MuscleGroup.LEGS,
        kind = MachineKind.WEIGHT_REPS,
        stepKg = 5.0,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )
    private val treadmill = Machine(
        id = 10L,
        name = "Treadmill",
        muscleGroup = MuscleGroup.CARDIO,
        kind = MachineKind.CARDIO,
        stepKg = 1.0,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val now = Instant.parse("2026-11-09T18:10:00Z")
    private val finishedSummary = SessionSummary(sessionId = 5L, machineCount = 1, setCount = 3, recordCount = 0)

    private fun savedSet(id: Long, machineId: Long, type: SetType = SetType.NORMAL) =
        WorkoutSet(id, 5L, machineId, id.toInt(), 60.0, 10, null, null, null, now.minusSeconds(120), type)

    private class FakeTraining : TrainingRepository {
        val log = MutableStateFlow<MachineLog?>(null)
        val setLogged = mutableListOf<Pair<Long, SetDraft>>()
        val cardioLogged = mutableListOf<Pair<Long, CardioDraft>>()
        val updated = mutableListOf<WorkoutSet>()
        val deleted = mutableListOf<WorkoutSet>()
        var finishCalls = 0
        /** How many sets were already written when the finish was asked: the set is saved first. */
        val setsWhenFinishAsked = mutableListOf<Int>()
        var finishResult: SessionSummary? = null
        var finishFails = false
        var failNextWrite = false
        var failNextDelete = false

        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = log

        override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet {
            if (failNextWrite) {
                failNextWrite = false
                error("disk full")
            }
            setLogged += machineId to draft
            val set = WorkoutSet(200L + setLogged.size, 5L, machineId, setLogged.size, draft.weightKg, draft.reps, null, null, null, Instant.parse("2026-11-09T18:11:00Z"), draft.type)
            return LoggedSet(set, null)
        }

        override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet {
            cardioLogged += machineId to draft
            return LoggedSet(CardioRules.toSet(300L, 5L, machineId, 0, draft, Instant.parse("2026-11-09T18:11:00Z")), null)
        }

        override suspend fun updateSet(set: WorkoutSet) {
            updated += set
        }

        override suspend fun deleteSet(set: WorkoutSet) {
            if (failNextDelete) {
                failNextDelete = false
                error("disk full")
            }
            deleted += set
        }

        override suspend fun finishIfRoutineComplete(): SessionSummary? {
            finishCalls++
            setsWhenFinishAsked += setLogged.size + cardioLogged.size
            if (finishFails) error("disk full")
            return finishResult
        }

        override suspend fun restoreSet(set: WorkoutSet) = Unit
        override fun observeOpenSession(): Flow<OpenSession?> = flowOf(null)
        override fun observeTodayPlan(): Flow<TodayPlan> = flowOf(noRoutineTodayPlan())
        override suspend fun chooseRoutine(choice: RoutineChoice) = Unit
        override suspend fun closeStaleSession() = Unit
        override suspend fun finishSession(): SessionSummary? = null
        override val pendingSummarySessionId: Flow<Long?> = MutableStateFlow(null)
        override suspend fun consumePendingSummary() = Unit
        override suspend fun discardMachineToday(machineId: Long): List<WorkoutSet> = emptyList()
        override suspend fun undoDiscardMachineToday(machineId: Long, sets: List<WorkoutSet>) = Unit
        override suspend fun reopenSession(sessionId: Long) = Unit
        override suspend fun skipMachineToday(machineId: Long) = Unit
        override suspend fun unskipMachineToday(machineId: Long) = Unit
        override fun observeRepeatOffer(): Flow<RepeatOffer?> = MutableStateFlow(null)
        override suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome? = null
        override suspend fun logWarmups(machineId: Long, sets: List<SetDraft>): RepeatOutcome = RepeatOutcome(0, false, emptyList(), 1)
        override suspend fun undoRepeat(outcome: RepeatOutcome) = Unit
        override suspend fun routineUpdateOffer(sessionId: Long): RoutineUpdate? = null
        override suspend fun sessionRoutineId(sessionId: Long): Long? = null
        override suspend fun trainingSummary(sessionId: Long): TrainingSummary? = null
    }

    private class FakeRestTimer : RestTimer {
        val started = mutableListOf<Int>()
        var skips = 0
        override val state: StateFlow<RestTimerState> = MutableStateFlow(RestTimerState())
        override val finished: SharedFlow<Unit> = MutableSharedFlow()
        override fun start(seconds: Int) {
            started += seconds
        }
        override fun extend(seconds: Int) = Unit
        override fun skip() {
            skips++
        }
    }

    private class FakeSettings : TrainingSettings {
        override val weeklyGoal: Flow<Int> = MutableStateFlow(4)
        override suspend fun setWeeklyGoal(goal: Int) = Unit
        override val reminders: Flow<ReminderSettings> = MutableStateFlow(ReminderSettings())
        override suspend fun setReminders(settings: ReminderSettings) = Unit
        override val warmupCalculator: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setWarmupCalculator(enabled: Boolean) = Unit
        override val restTimer: Flow<RestTimerSettings> = MutableStateFlow(RestTimerSettings(enabled = true, defaultSeconds = 90))
        override suspend fun setRestTimer(settings: RestTimerSettings) = Unit
    }

    private lateinit var training: FakeTraining
    private lateinit var rest: FakeRestTimer
    private lateinit var viewModel: MachineViewModel
    private val events = mutableListOf<MachineEvent>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        training = FakeTraining()
        rest = FakeRestTimer()
        events.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Opens the view model on [machine] with [thisSession] already logged today, and starts observing it. */
    private fun TestScope.open(
        machine: Machine,
        thisSession: List<WorkoutSet> = emptyList(),
        lastTime: List<WorkoutSet> = emptyList(),
    ) {
        training.log.value = MachineLog(machine, lastTime, if (lastTime.isEmpty()) null else now.minusSeconds(86_400), thisSession)
        viewModel = MachineViewModel(
            SavedStateHandle(mapOf(MACHINE_ID_ARG to machine.id)),
            training,
            FakeMachineLibrary(),
            rest,
            FakeSettings(),
            Clock.fixed(now, ZoneOffset.UTC),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()
    }

    // Working sets that finish the routine

    @Test
    fun `the set that completes the routine finishes the training and goes back to Today`() = runTest {
        open(legPress)
        training.finishResult = finishedSummary

        viewModel.logSet()
        runCurrent()

        assertEquals(1, training.finishCalls)
        assertEquals(listOf<MachineEvent>(MachineEvent.Logged(), MachineEvent.RoutineFinished), events)
    }

    @Test
    fun `the set is saved before the training is asked to finish`() = runTest {
        open(legPress)
        training.finishResult = finishedSummary

        viewModel.logSet()
        runCurrent()

        assertEquals(listOf(1), training.setsWhenFinishAsked)
    }

    @Test
    fun `the rest timer stops when the training finishes`() = runTest {
        open(legPress)
        training.finishResult = finishedSummary

        viewModel.logSet()
        runCurrent()

        assertEquals(1, rest.started.size)
        assertEquals(1, rest.skips)
    }

    @Test
    fun `a set that leaves the routine unfinished emits no RoutineFinished`() = runTest {
        open(legPress)
        training.finishResult = null

        viewModel.logSet()
        runCurrent()

        assertEquals(1, training.finishCalls)
        assertEquals(listOf<MachineEvent>(MachineEvent.Logged()), events)
        assertEquals(0, rest.skips)
    }

    @Test
    fun `a cardio set that completes the routine finishes it too`() = runTest {
        open(treadmill)
        training.finishResult = finishedSummary
        viewModel.logCardio()
        runCurrent()

        assertEquals(1, training.finishCalls)
        assertEquals(listOf<MachineEvent>(MachineEvent.Logged(), MachineEvent.RoutineFinished), events)
    }

    @Test
    fun `a same-again set counts as a working set`() = runTest {
        open(legPress, lastTime = listOf(savedSet(1L, legPress.id)))
        training.finishResult = finishedSummary

        viewModel.logSameAgain()
        runCurrent()

        assertEquals(1, training.finishCalls)
        assertTrue(events.contains(MachineEvent.RoutineFinished))
    }

    // Sets that never finish the routine

    @Test
    fun `a warm-up set never finishes the training`() = runTest {
        open(legPress)
        training.finishResult = finishedSummary
        viewModel.toggleWarmup()
        runCurrent()

        viewModel.logSet()
        runCurrent()

        assertEquals(1, training.setLogged.size)
        assertEquals(SetType.WARMUP, training.setLogged.single().second.type)
        assertEquals(0, training.finishCalls)
        assertFalse(events.contains(MachineEvent.RoutineFinished))
    }

    @Test
    fun `an edited set never finishes the training`() = runTest {
        val set = savedSet(7L, legPress.id)
        open(legPress, thisSession = listOf(set))
        training.finishResult = finishedSummary
        viewModel.openSet(set)
        runCurrent()

        viewModel.saveEditor()
        runCurrent()

        assertEquals(listOf(set), training.updated)
        assertEquals(0, training.finishCalls)
        assertFalse(events.contains(MachineEvent.RoutineFinished))
    }

    // Failure

    @Test
    fun `a failed finish keeps the set and reports the failure`() = runTest {
        open(legPress)
        training.finishFails = true

        viewModel.logSet()
        runCurrent()

        assertEquals(1, training.setLogged.size)
        assertEquals(listOf<MachineEvent>(MachineEvent.Logged(), MachineEvent.Failed), events)
    }

    // Delete from the set row's menu

    @Test
    fun `deleteSet removes the set and offers Undo with it`() = runTest {
        val set = savedSet(7L, legPress.id)
        open(legPress, thisSession = listOf(set))

        viewModel.deleteSet(set)
        runCurrent()

        assertEquals(listOf(set), training.deleted)
        assertEquals(listOf<MachineEvent>(MachineEvent.SetRemoved(set)), events)
        assertEquals(0, training.finishCalls)
    }

    @Test
    fun `a failed delete reports the failure and emits no Undo`() = runTest {
        val set = savedSet(7L, legPress.id)
        open(legPress, thisSession = listOf(set))
        training.failNextDelete = true

        viewModel.deleteSet(set)
        runCurrent()

        assertEquals(emptyList<WorkoutSet>(), training.deleted)
        assertEquals(listOf<MachineEvent>(MachineEvent.Failed), events)
    }
}
