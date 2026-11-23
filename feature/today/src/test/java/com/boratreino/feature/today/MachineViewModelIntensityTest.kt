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
import com.boratreino.core.model.SetSummary
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The cardio intensity on the machine screen's view model: the stepper, prefill from last time, Log, and the editor
 * of a logged set. Works from the phase 41 contract; the fakes are the cardio test's own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MachineViewModelIntensityTest {

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

    private val earlier = Instant.parse("2026-10-24T18:00:00Z")
    private val now = Instant.parse("2026-10-31T18:10:00Z")

    private fun cardioSet(id: Long, machine: Machine, position: Int, draft: CardioDraft, at: Instant = now) =
        CardioRules.toSet(id, sessionId = 1L, machineId = machine.id, position = position, draft = draft, loggedAt = at)

    private class FakeTraining : TrainingRepository {
    override suspend fun routineUpdateOffer(sessionId: Long): com.boratreino.core.model.RoutineUpdate? = null
    override suspend fun trainingSummary(sessionId: Long): com.boratreino.core.model.TrainingSummary? = null
    override suspend fun sessionRoutineId(sessionId: Long): Long? = null

        val log = MutableStateFlow<MachineLog?>(null)
        val cardioLogged = mutableListOf<Pair<Long, CardioDraft>>()
        val setLogged = mutableListOf<Pair<Long, SetDraft>>()
        val updated = mutableListOf<WorkoutSet>()
        val deleted = mutableListOf<WorkoutSet>()
        val restored = mutableListOf<WorkoutSet>()
        var failNextWrite = false

        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = log

        override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet {
            if (failNextWrite) {
                failNextWrite = false
                error("disk full")
            }
            cardioLogged += machineId to draft
            val position = log.value?.thisSession?.size ?: 0
            val set = CardioRules.toSet(100L + position, 1L, machineId, position, draft, Instant.parse("2026-10-31T18:11:00Z"))
            return LoggedSet(set, null)
        }

        override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet {
            setLogged += machineId to draft
            val set = WorkoutSet(200L, 1L, machineId, 0, draft.weightKg, draft.reps, null, null, null, Instant.parse("2026-10-31T18:11:00Z"))
            return LoggedSet(set, null)
        }

        override suspend fun updateSet(set: WorkoutSet) {
            updated += set
        }

        override suspend fun deleteSet(set: WorkoutSet) {
            deleted += set
        }

        override suspend fun restoreSet(set: WorkoutSet) {
            restored += set
        }

        override fun observeOpenSession(): Flow<OpenSession?> = flowOf(null)
        override fun observeTodayPlan(): Flow<TodayPlan> = flowOf(noRoutineTodayPlan())
        override suspend fun chooseRoutine(choice: RoutineChoice) = Unit
        override suspend fun closeStaleSession() = Unit
        override suspend fun finishSession(): SessionSummary? = null
        override suspend fun finishIfRoutineComplete(): SessionSummary? = null
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

    /** Opens the view model on [machine] with the given history and starts observing it. */
    private fun TestScope.open(
        machine: Machine,
        lastTime: List<WorkoutSet> = emptyList(),
        thisSession: List<WorkoutSet> = emptyList(),
    ): MachineUiState {
        training.log.value = MachineLog(machine, lastTime, if (lastTime.isEmpty()) null else earlier, thisSession)
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
        return viewModel.state.value
    }

    private val state: MachineUiState get() = viewModel.state.value

    // Stepper

    @Test
    fun `a first time has no intensity`() = runTest {
        assertNull(open(treadmill).cardio.intensity)
    }

    @Test
    fun `stepping intensity up from unset gives the start value, and down from unset stays unset`() = runTest {
        open(treadmill)

        viewModel.stepIntensity(up = false)
        runCurrent()
        assertNull(state.cardio.intensity)

        viewModel.stepIntensity(up = true)
        runCurrent()
        assertEquals(0.70, state.cardio.intensity!!, 0.0)
    }

    @Test
    fun `stepping intensity changes only intensity`() = runTest {
        open(treadmill, lastTime = listOf(cardioSet(1, treadmill, 0, CardioDraft(30, 250, 3200, 0.79), earlier)))

        viewModel.stepIntensity(up = true)
        runCurrent()

        assertEquals(CardioDraft(30, 250, 3200, 0.80), state.cardio)
    }

    // Prefill and Log

    @Test
    fun `prefill carries last time's intensity`() = runTest {
        val last = listOf(cardioSet(1, treadmill, 0, CardioDraft(30, 250, 3200, 0.79), earlier))

        assertEquals(CardioDraft(30, 250, 3200, 0.79), open(treadmill, lastTime = last).cardio)
    }

    @Test
    fun `Log writes the intensity with the other steppers`() = runTest {
        open(treadmill, lastTime = listOf(cardioSet(1, treadmill, 0, CardioDraft(30, 250, 3200, 0.79), earlier)))
        viewModel.stepIntensity(up = true)
        runCurrent()

        viewModel.logCardio()
        runCurrent()

        assertEquals(listOf(treadmill.id to CardioDraft(30, 250, 3200, 0.80)), training.cardioLogged)
    }

    @Test
    fun `Log with only an intensity does nothing`() = runTest {
        open(treadmill, lastTime = listOf(cardioSet(1, treadmill, 0, CardioDraft(0, 0, 0, 0.79), earlier)))

        viewModel.logCardio()
        runCurrent()

        assertEquals(CardioDraft(0, 0, 0, 0.79), state.cardio)
        assertTrue(training.cardioLogged.isEmpty())
    }

    @Test
    fun `Same again logs last time's intensity`() = runTest {
        open(treadmill, lastTime = listOf(cardioSet(1, treadmill, 0, CardioDraft(30, 250, 3200, 0.79), earlier)))
        assertEquals(CardioDraft(30, 250, 3200, 0.79), state.cardioSameAgain)

        viewModel.logCardioSameAgain()
        runCurrent()

        assertEquals(listOf(treadmill.id to CardioDraft(30, 250, 3200, 0.79)), training.cardioLogged)
    }

    // Editing a logged set

    @Test
    fun `opening a logged set shows its intensity in the editor`() = runTest {
        val set = cardioSet(1, treadmill, 0, CardioDraft(30, 250, 0, 0.79), earlier)
        open(treadmill, thisSession = listOf(set))

        viewModel.openCardioSet(set)
        runCurrent()

        assertEquals(0.79, state.cardioEditor?.draft?.intensity)
    }

    @Test
    fun `the editor intensity steps and saving writes it to the same set`() = runTest {
        val set = cardioSet(1, treadmill, 0, CardioDraft(30, 250, 0, 0.79), earlier)
        open(treadmill, thisSession = listOf(set))
        viewModel.openCardioSet(set)
        runCurrent()

        viewModel.stepEditorIntensity(up = true)
        viewModel.saveCardioEditor()
        runCurrent()

        val saved = training.updated.single()
        assertEquals(set.id, saved.id)
        assertEquals(0.80, saved.intensity!!, 0.0)
        assertEquals(30 * 60, saved.seconds)
    }

    @Test
    fun `the editor intensity stays unset when stepped down from unset`() = runTest {
        val set = cardioSet(1, treadmill, 0, CardioDraft(30, 250, 0, null), earlier)
        open(treadmill, thisSession = listOf(set))
        viewModel.openCardioSet(set)
        runCurrent()

        viewModel.stepEditorIntensity(up = false)
        runCurrent()

        assertNull(state.cardioEditor?.draft?.intensity)
    }
}
