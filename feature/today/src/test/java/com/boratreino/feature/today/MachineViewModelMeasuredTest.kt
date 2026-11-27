package com.boratreino.feature.today

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.RemovedSets
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The machine screen for the measured kinds. A set is logged from the steppers as before, and "same
 * again" logs last time's set in one tap. The seconds and metres steppers move by five, the number pad
 * clamps to the range, and a time or distance kind is never offered a warm-up plan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MachineViewModelMeasuredTest {
    private val farmersWalk = Machine(
        id = 21L,
        name = "Farmer's walk",
        muscleGroup = MuscleGroup.ARMS,
        kind = MachineKind.WEIGHT_TIME,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val sledPush = Machine(
        id = 22L,
        name = "Sled push",
        muscleGroup = MuscleGroup.LEGS,
        kind = MachineKind.WEIGHT_DISTANCE,
        stepKg = 5.0,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val plank = Machine(
        id = 23L,
        name = "Plank",
        muscleGroup = MuscleGroup.CORE,
        kind = MachineKind.TIME,
        stepKg = 2.5,
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

    /** A set on [machine] from last time: [kg] with [seconds] or [metres] (reps are null). */
    private fun timed(id: Long, machine: Machine, kg: Double, seconds: Int? = null, metres: Int? = null) = WorkoutSet(
        id, 1L, machine.id, 0, kg, null, seconds, null, metres, earlier, SetType.NORMAL,
    )

    private class FakeTraining : TrainingRepository {
        val log = MutableStateFlow<MachineLog?>(null)
        val setLogged = mutableListOf<Pair<Long, SetDraft>>()

        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = log

        override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet {
            setLogged += machineId to draft
            val set = WorkoutSet(
                200L, 1L, machineId, 0, draft.weightKg, draft.reps, null, null, null,
                Instant.parse("2026-10-31T18:11:00Z"), draft.type,
            )
            return LoggedSet(set, null)
        }

        override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet =
            LoggedSet(CardioRules.toSet(300L, 1L, machineId, 0, draft, Instant.EPOCH), null)

        override suspend fun updateSet(set: WorkoutSet) = Unit
        override suspend fun deleteSet(set: WorkoutSet): RemovedSets = RemovedSets(listOf(set), null)
        override suspend fun restoreSets(removed: RemovedSets) = Unit
        override fun observeOpenSession(): Flow<OpenSession?> = flowOf(null)
        override fun observeTodayPlan(): Flow<TodayPlan> = flowOf(noRoutineTodayPlan())
        override suspend fun chooseRoutine(choice: RoutineChoice) = Unit
        override suspend fun closeStaleSession() = Unit
        override suspend fun finishSession(): SessionSummary? = null
        override suspend fun finishIfRoutineComplete(): SessionSummary? = null
        override val pendingSummarySessionId: Flow<Long?> = MutableStateFlow(null)
        override suspend fun consumePendingSummary() = Unit
        override suspend fun discardMachineToday(machineId: Long): RemovedSets = RemovedSets(emptyList(), null)
        override suspend fun undoDiscardMachineToday(machineId: Long, removed: RemovedSets) = Unit
        override suspend fun routineUpdateOffer(sessionId: Long): RoutineUpdate? = null
        override suspend fun sessionRoutineId(sessionId: Long): Long? = null
        override suspend fun trainingSummary(sessionId: Long): TrainingSummary? = null
        override suspend fun reopenSession(sessionId: Long) = Unit
        override suspend fun skipMachineToday(machineId: Long) = Unit
        override suspend fun unskipMachineToday(machineId: Long) = Unit
        override fun observeRepeatOffer(): Flow<RepeatOffer?> = MutableStateFlow(null)
        override suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome? = null
        override suspend fun logWarmups(machineId: Long, sets: List<SetDraft>): RepeatOutcome = RepeatOutcome(0, false, emptyList(), 1)
        override suspend fun undoRepeat(outcome: RepeatOutcome) = Unit
    }

    private class FakeRestTimer : RestTimer {
        override val state: StateFlow<RestTimerState> = MutableStateFlow(RestTimerState())
        override val finished: SharedFlow<Unit> = MutableSharedFlow()
        override fun start(seconds: Int) = Unit
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
    private lateinit var viewModel: MachineViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        training = FakeTraining()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Opens the screen for [machine] with [lastTime] as its history and nothing logged today. */
    private fun TestScope.open(machine: Machine, lastTime: List<WorkoutSet> = emptyList()) {
        training.log.value = MachineLog(machine, lastTime, if (lastTime.isEmpty()) null else earlier, emptyList())
        viewModel = MachineViewModel(
            SavedStateHandle(mapOf(MACHINE_ID_ARG to machine.id)),
            training,
            FakeMachineLibrary(listOf(machine)),
            FakeRestTimer(),
            FakeSettings(),
            Clock.fixed(now, ZoneOffset.UTC),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    private val state: MachineUiState get() = viewModel.state.value

    @Test
    fun `weight and time starts from the last set, and same again logs it in one tap`() = runTest {
        open(farmersWalk, listOf(timed(1L, farmersWalk, kg = 47.5, seconds = 30)))

        assertEquals(47.5, state.weightKg, 0.0)
        assertEquals(30, state.seconds)
        assertEquals(SetDraft(47.5, 0, SetType.NORMAL, 30, 0), state.sameAgain)

        viewModel.logSameAgain()
        runCurrent()

        assertEquals(listOf(farmersWalk.id to SetDraft(47.5, 0, SetType.NORMAL, 30, 0)), training.setLogged)
    }

    @Test
    fun `the seconds stepper moves by five and the logged set carries the new value`() = runTest {
        open(farmersWalk, listOf(timed(1L, farmersWalk, kg = 47.5, seconds = 30)))

        viewModel.stepSeconds(true)
        runCurrent()
        assertEquals(35, state.seconds)

        viewModel.stepSeconds(false)
        viewModel.stepSeconds(false)
        runCurrent()
        assertEquals(25, state.seconds)

        viewModel.logSet()
        runCurrent()

        assertEquals(listOf(farmersWalk.id to SetDraft(47.5, 0, SetType.NORMAL, 25, 0)), training.setLogged)
    }

    @Test
    fun `an off-grid number of seconds snaps in the direction of the step`() = runTest {
        open(farmersWalk)

        viewModel.openPad(PadTarget.SECONDS)
        viewModel.confirmPad("32")
        runCurrent()
        assertEquals(32, state.seconds)

        viewModel.stepSeconds(true)
        runCurrent()
        assertEquals(35, state.seconds)

        viewModel.openPad(PadTarget.SECONDS)
        viewModel.confirmPad("32")
        viewModel.stepSeconds(false)
        runCurrent()
        assertEquals(30, state.seconds)
    }

    @Test
    fun `the seconds pad clamps to one second and to an hour`() = runTest {
        open(farmersWalk)

        viewModel.openPad(PadTarget.SECONDS)
        viewModel.confirmPad("99999")
        runCurrent()
        assertEquals(3600, state.seconds)

        viewModel.openPad(PadTarget.SECONDS)
        viewModel.confirmPad("0")
        runCurrent()
        assertEquals(1, state.seconds)
    }

    @Test
    fun `the metres stepper moves by five, and the logged set carries the metres`() = runTest {
        open(sledPush)
        assertEquals(20, state.distanceM)

        viewModel.stepMetres(true)
        runCurrent()
        assertEquals(25, state.distanceM)

        viewModel.stepMetres(false)
        viewModel.stepMetres(false)
        runCurrent()
        assertEquals(15, state.distanceM)

        viewModel.logSet()
        runCurrent()

        assertEquals(listOf(sledPush.id to SetDraft(0.0, 0, SetType.NORMAL, 0, 15)), training.setLogged)
    }

    @Test
    fun `the metres pad clamps to one metre and five kilometres`() = runTest {
        open(sledPush)

        viewModel.openPad(PadTarget.DISTANCE)
        viewModel.confirmPad("9999")
        runCurrent()
        assertEquals(5000, state.distanceM)

        viewModel.openPad(PadTarget.DISTANCE)
        viewModel.confirmPad("0")
        runCurrent()
        assertEquals(1, state.distanceM)
    }

    @Test
    fun `weight and distance same again carries the load and the metres`() = runTest {
        open(sledPush, listOf(timed(2L, sledPush, kg = 40.0, metres = 25)))

        assertEquals(SetDraft(40.0, 0, SetType.NORMAL, 0, 25), state.sameAgain)

        viewModel.logSameAgain()
        runCurrent()

        assertEquals(listOf(sledPush.id to SetDraft(40.0, 0, SetType.NORMAL, 0, 25)), training.setLogged)
    }

    @Test
    fun `time same again logs the seconds with no weight in one tap`() = runTest {
        open(plank, listOf(timed(3L, plank, kg = 0.0, seconds = 45)))

        assertEquals(0.0, state.weightKg, 0.0)
        assertEquals(SetDraft(0.0, 0, SetType.NORMAL, 45, 0), state.sameAgain)

        viewModel.logSameAgain()
        runCurrent()

        assertEquals(listOf(plank.id to SetDraft(0.0, 0, SetType.NORMAL, 45, 0)), training.setLogged)
    }

    @Test
    fun `a weight and time kind gets no warm-up plan even with the calculator on`() = runTest {
        open(farmersWalk, listOf(timed(1L, farmersWalk, kg = 47.5, seconds = 30)))

        assertTrue(state.warmupPlan.isEmpty())
    }

    @Test
    fun `a reps kind with the same load gets a warm-up plan`() = runTest {
        open(legPress, listOf(WorkoutSet(4L, 1L, legPress.id, 0, 120.0, 12, null, null, null, earlier, SetType.NORMAL)))

        assertTrue(state.warmupPlan.isNotEmpty())
    }
}
