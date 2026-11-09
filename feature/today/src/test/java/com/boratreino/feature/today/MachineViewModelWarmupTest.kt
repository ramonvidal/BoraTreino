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
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetType
import com.boratreino.core.model.TodayPlan
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The Warm-up chip on the machine screen and the type chosen in the edit sheet. Works from the Phase 9 contract. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MachineViewModelWarmupTest {
    private val legPress = Machine(11L, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, archived = false)
    private val treadmill = Machine(10L, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)

    private val earlier = Instant.parse("2026-10-24T18:00:00Z")
    private val now = Instant.parse("2026-10-31T18:10:00Z")

    private fun set(id: Long, position: Int, kg: Double, reps: Int, type: SetType = SetType.NORMAL, at: Instant = now) =
        WorkoutSet(id, 1L, legPress.id, position, kg, reps, null, null, null, at, type)

    private val lastTime = listOf(
        set(1, 0, 120.0, 12, at = earlier),
        set(2, 1, 125.0, 10, at = earlier),
    )

    private class FakeTraining : TrainingRepository {
    override suspend fun routineUpdateOffer(sessionId: Long): com.boratreino.core.model.RoutineUpdate? = null
    override suspend fun sessionRoutineId(sessionId: Long): Long? = null

        val log = MutableStateFlow<MachineLog?>(null)
        val setLogged = mutableListOf<Pair<Long, SetDraft>>()
        val cardioLogged = mutableListOf<Pair<Long, CardioDraft>>()
        val updated = mutableListOf<WorkoutSet>()

        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = log

        override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet {
            setLogged += machineId to draft
            val set = WorkoutSet(
                200L, 1L, machineId, 0, draft.weightKg, draft.reps, null, null, null,
                Instant.parse("2026-10-31T18:11:00Z"), draft.type,
            )
            return LoggedSet(set, null)
        }

        override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet {
            cardioLogged += machineId to draft
            return LoggedSet(CardioRules.toSet(300L, 1L, machineId, 0, draft, Instant.EPOCH), null)
        }

        override suspend fun updateSet(set: WorkoutSet) {
            updated += set
        }

        override suspend fun deleteSet(set: WorkoutSet) = Unit
        override suspend fun restoreSet(set: WorkoutSet) = Unit
        override fun observeOpenSession(): Flow<OpenSession?> = emptyFlow()
        override fun observeTodayPlan(): Flow<TodayPlan> = emptyFlow()
        override suspend fun chooseRoutine(choice: RoutineChoice) = Unit
        override suspend fun closeStaleSession() = Unit
        override suspend fun finishSession(): SessionSummary? = null
        override suspend fun reopenSession(sessionId: Long) = Unit
        override suspend fun skipMachineToday(machineId: Long) = Unit
        override suspend fun unskipMachineToday(machineId: Long) = Unit
        override fun observeRepeatOffer(): Flow<RepeatOffer?> = MutableStateFlow(null)
        override suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome? = null
        override suspend fun logWarmups(machineId: Long, sets: List<SetDraft>): RepeatOutcome = RepeatOutcome(0, false, emptyList(), 1)
        override suspend fun undoRepeat(outcome: RepeatOutcome) = Unit
    }

    private class FakeRestTimer : RestTimer {
        val started = mutableListOf<Int>()
        override val state: StateFlow<RestTimerState> = MutableStateFlow(RestTimerState())
        override val finished: SharedFlow<Unit> = MutableSharedFlow()
        override fun start(seconds: Int) {
            started += seconds
        }
        override fun extend(seconds: Int) = Unit
        override fun skip() = Unit
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

    private fun TestScope.open(
        machine: Machine = legPress,
        last: List<WorkoutSet> = lastTime,
        thisSession: List<WorkoutSet> = emptyList(),
    ) {
        training.log.value = MachineLog(machine, last, if (last.isEmpty()) null else earlier, thisSession)
        viewModel = MachineViewModel(
            SavedStateHandle(mapOf(MACHINE_ID_ARG to machine.id)),
            training,
            rest,
            FakeSettings(),
            Clock.fixed(now, ZoneOffset.UTC),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()
    }

    private val state: MachineUiState get() = viewModel.state.value

    // The chip

    @Test
    fun `the Warm-up chip is off by default`() = runTest {
        open()

        assertFalse(state.warmupOn)
    }

    @Test
    fun `toggleWarmup flips the chip on and off`() = runTest {
        open()

        viewModel.toggleWarmup()
        runCurrent()
        assertTrue(state.warmupOn)

        viewModel.toggleWarmup()
        runCurrent()
        assertFalse(state.warmupOn)
    }

    @Test
    fun `toggling the chip does not move the steppers`() = runTest {
        open()
        val before = state.weightKg to state.reps

        viewModel.toggleWarmup()
        runCurrent()

        assertEquals(before, state.weightKg to state.reps)
    }

    // Logging

    @Test
    fun `Log with the chip off writes a normal set`() = runTest {
        open()

        viewModel.logSet()
        runCurrent()

        assertEquals(listOf(legPress.id to SetDraft(120.0, 12, SetType.NORMAL)), training.setLogged)
    }

    @Test
    fun `Log with the chip on writes a warm-up set with the stepper values`() = runTest {
        open()
        viewModel.stepWeight(up = false)
        viewModel.toggleWarmup()
        runCurrent()

        viewModel.logSet()
        runCurrent()

        assertEquals(listOf(legPress.id to SetDraft(115.0, 12, SetType.WARMUP)), training.setLogged)
        assertEquals(1, events.size)
        assertTrue(events.single() is MachineEvent.Logged)
    }

    @Test
    fun `Same again with the chip on writes last time's set as a warm-up`() = runTest {
        open()
        viewModel.toggleWarmup()
        runCurrent()

        viewModel.logSameAgain()
        runCurrent()

        assertEquals(listOf(legPress.id to SetDraft(120.0, 12, SetType.WARMUP)), training.setLogged)
    }

    @Test
    fun `the chip turns itself off after Log`() = runTest {
        open()
        viewModel.toggleWarmup()
        runCurrent()

        viewModel.logSet()
        runCurrent()
        assertFalse(state.warmupOn)

        viewModel.logSet()
        runCurrent()
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL), training.setLogged.map { it.second.type })
    }

    @Test
    fun `the chip turns itself off after Same again`() = runTest {
        open()
        viewModel.toggleWarmup()
        runCurrent()

        viewModel.logSameAgain()
        runCurrent()

        assertFalse(state.warmupOn)
    }

    @Test
    fun `a warm-up starts the rest timer as usual`() = runTest {
        open()
        viewModel.toggleWarmup()
        runCurrent()

        viewModel.logSet()
        runCurrent()

        assertEquals(listOf(90), rest.started)
    }

    @Test
    fun `a warm-up does not start a record celebration`() = runTest {
        open()
        viewModel.toggleWarmup()
        runCurrent()

        viewModel.logSet()
        runCurrent()

        assertNull(state.record)
        assertNull((events.single() as MachineEvent.Logged).record)
    }

    // Prefill

    @Test
    fun `after a warm-up the steppers and Same again go back to the working prefill`() = runTest {
        open()
        viewModel.stepWeight(up = false)
        viewModel.stepWeight(up = false)
        viewModel.toggleWarmup()
        runCurrent()
        viewModel.logSet()
        runCurrent()

        // The repository now reports the warm-up in this session.
        training.log.value = MachineLog(legPress, lastTime, earlier, listOf(set(5, 0, 110.0, 12, SetType.WARMUP)))
        runCurrent()

        assertEquals(120.0, state.weightKg, 0.0001)
        assertEquals(12, state.reps)
        assertEquals(SetDraft(120.0, 12), state.sameAgain)
        assertEquals(1, state.sets.size)
        assertEquals(SetType.WARMUP, state.sets.single().type)
    }

    @Test
    fun `a working set after a warm-up is the new prefill`() = runTest {
        open(thisSession = listOf(set(5, 0, 100.0, 15, SetType.WARMUP), set(6, 1, 122.5, 8)))

        assertEquals(122.5, state.weightKg, 0.0001)
        assertEquals(8, state.reps)
        // One working set is done, so Same again is last time's second working set.
        assertEquals(SetDraft(125.0, 10), state.sameAgain)
    }

    // Cardio

    @Test
    fun `the chip is not offered on a cardio machine`() = runTest {
        open(treadmill, last = emptyList())

        viewModel.toggleWarmup()
        runCurrent()

        assertFalse(state.warmupOn)
    }

    @Test
    fun `logging cardio is not affected by the chip`() = runTest {
        open(treadmill, last = emptyList())
        viewModel.toggleWarmup()
        runCurrent()

        viewModel.logCardio()
        runCurrent()

        assertEquals(listOf(treadmill.id to CardioDraft(20, 0, 0)), training.cardioLogged)
        assertTrue(training.setLogged.isEmpty())
    }

    // The edit sheet

    @Test
    fun `opening a set puts its type in the editor`() = runTest {
        val drop = set(5, 0, 100.0, 8, SetType.DROP)
        open(thisSession = listOf(drop))

        viewModel.openSet(drop)
        runCurrent()

        assertEquals(SetType.DROP, state.editor?.type)
    }

    @Test
    fun `setEditorType changes only the editor`() = runTest {
        val normal = set(5, 0, 120.0, 12)
        open(thisSession = listOf(normal))
        viewModel.openSet(normal)

        viewModel.setEditorType(SetType.FAILURE)
        runCurrent()

        assertEquals(SetType.FAILURE, state.editor?.type)
        assertEquals(SetType.NORMAL, state.editor?.set?.type)
        assertTrue(training.updated.isEmpty())
    }

    @Test
    fun `saving writes the chosen type`() = runTest {
        val normal = set(5, 0, 120.0, 12)
        open(thisSession = listOf(normal))
        viewModel.openSet(normal)
        viewModel.setEditorType(SetType.WARMUP)

        viewModel.saveEditor()
        runCurrent()

        assertEquals(listOf(normal.copy(type = SetType.WARMUP)), training.updated)
        assertNull(state.editor)
    }

    @Test
    fun `saving with the type unchanged keeps it`() = runTest {
        val failure = set(5, 0, 120.0, 12, SetType.FAILURE)
        open(thisSession = listOf(failure))
        viewModel.openSet(failure)
        viewModel.stepEditorReps(up = false)

        viewModel.saveEditor()
        runCurrent()

        assertEquals(listOf(failure.copy(reps = 11)), training.updated)
    }

    @Test
    fun `closing the editor drops a type change`() = runTest {
        val normal = set(5, 0, 120.0, 12)
        open(thisSession = listOf(normal))
        viewModel.openSet(normal)
        viewModel.setEditorType(SetType.DROP)

        viewModel.closeEditor()
        runCurrent()

        assertNull(state.editor)
        assertTrue(training.updated.isEmpty())
    }
}
