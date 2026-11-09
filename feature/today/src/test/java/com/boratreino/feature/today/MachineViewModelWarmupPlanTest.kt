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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The optional warm-up suggestion on the machine screen: when it is offered, and the one-tap log. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MachineViewModelWarmupPlanTest {
    private val legPress = Machine(11L, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, archived = false)
    private val pullUps = Machine(12L, "Pull-ups", MuscleGroup.BACK, MachineKind.BODYWEIGHT_REPS, 2.5, null, null, archived = false)
    private val treadmill = Machine(10L, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)

    private val earlier = Instant.parse("2026-10-24T18:00:00Z")
    private val now = Instant.parse("2026-10-31T18:10:00Z")

    private fun set(machine: Machine, id: Long, position: Int, kg: Double, reps: Int, type: SetType = SetType.NORMAL, at: Instant = now) =
        WorkoutSet(id, 1L, machine.id, position, kg, reps, null, null, null, at, type)

    private val lastTime = listOf(
        set(legPress, 1, 0, 120.0, 12, at = earlier),
        set(legPress, 2, 1, 125.0, 10, at = earlier),
    )

    private val outcome = RepeatOutcome(sessionId = 7L, sessionCreated = true, setIds = listOf(31L, 32L, 33L), machineCount = 1)

    private class FakeTraining : TrainingRepository {
    override suspend fun routineUpdateOffer(sessionId: Long): com.boratreino.core.model.RoutineUpdate? = null
    override suspend fun trainingSummary(sessionId: Long): com.boratreino.core.model.TrainingSummary? = null
    override suspend fun sessionRoutineId(sessionId: Long): Long? = null

        val log = MutableStateFlow<MachineLog?>(null)
        val warmupsLogged = mutableListOf<Pair<Long, List<SetDraft>>>()
        val undone = mutableListOf<RepeatOutcome>()
        val setLogged = mutableListOf<Pair<Long, SetDraft>>()
        var failWarmups = false
        var result = RepeatOutcome(7L, true, listOf(31L, 32L, 33L), 1)

        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = log

        override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet {
            setLogged += machineId to draft
            val set = WorkoutSet(200L, 1L, machineId, 0, draft.weightKg, draft.reps, null, null, null, Instant.EPOCH, draft.type)
            return LoggedSet(set, null)
        }

        override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet =
            LoggedSet(CardioRules.toSet(300L, 1L, machineId, 0, draft, Instant.EPOCH), null)

        override suspend fun updateSet(set: WorkoutSet) = Unit
        override suspend fun deleteSet(set: WorkoutSet) = Unit
        override suspend fun restoreSet(set: WorkoutSet) = Unit
        override fun observeOpenSession(): Flow<OpenSession?> = emptyFlow()
        override fun observeTodayPlan(): Flow<TodayPlan> = emptyFlow()
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

        override suspend fun logWarmups(machineId: Long, sets: List<SetDraft>): RepeatOutcome {
            if (failWarmups) error("disk full")
            warmupsLogged += machineId to sets
            return result
        }

        override suspend fun undoRepeat(outcome: RepeatOutcome) {
            undone += outcome
        }
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
        val warmup = MutableStateFlow(true)
        override val weeklyGoal: Flow<Int> = MutableStateFlow(4)
        override suspend fun setWeeklyGoal(goal: Int) = Unit
        override val reminders: Flow<ReminderSettings> = MutableStateFlow(ReminderSettings())
        override suspend fun setReminders(settings: ReminderSettings) = Unit
        override val restTimer: Flow<RestTimerSettings> = MutableStateFlow(RestTimerSettings(enabled = true, defaultSeconds = 90))
        override suspend fun setRestTimer(settings: RestTimerSettings) = Unit
        override val warmupCalculator: Flow<Boolean> get() = warmup
        override suspend fun setWarmupCalculator(enabled: Boolean) {
            warmup.value = enabled
        }
    }

    private lateinit var training: FakeTraining
    private lateinit var rest: FakeRestTimer
    private lateinit var settings: FakeSettings
    private lateinit var viewModel: MachineViewModel
    private val events = mutableListOf<MachineEvent>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        training = FakeTraining()
        rest = FakeRestTimer()
        settings = FakeSettings()
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
            settings,
            Clock.fixed(now, ZoneOffset.UTC),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()
    }

    private val state: MachineUiState get() = viewModel.state.value

    private fun TestScope.typeWeight(text: String) {
        viewModel.openPad(PadTarget.WEIGHT)
        viewModel.confirmPad(text)
        runCurrent()
    }

    private fun warmups(vararg pairs: Pair<Double, Int>) = pairs.map { SetDraft(it.first, it.second, SetType.WARMUP) }

    // When the offer exists

    @Test
    fun `a weight machine with a known weight and nothing logged today gets the plan`() = runTest {
        open()

        assertEquals(120.0, state.weightKg, 0.0)
        assertEquals(warmups(60.0 to 8, 80.0 to 5, 100.0 to 3), state.warmupPlan)
    }

    @Test
    fun `the plan follows the stepper weight`() = runTest {
        open()

        viewModel.stepWeight(up = false)
        runCurrent()

        assertEquals(115.0, state.weightKg, 0.0)
        assertEquals(warmups(55.0 to 8, 80.0 to 5, 95.0 to 3), state.warmupPlan)
    }

    @Test
    fun `a weight typed on the number pad changes the plan`() = runTest {
        open()

        typeWeight("200")

        assertEquals(warmups(100.0 to 8, 140.0 to 5, 170.0 to 3), state.warmupPlan)
    }

    @Test
    fun `a bodyweight machine with added weight gets the plan`() = runTest {
        open(pullUps, last = listOf(set(pullUps, 1, 0, 10.0, 8, at = earlier)))

        assertEquals(10.0, state.weightKg, 0.0)
        assertEquals(warmups(5.0 to 8, 7.5 to 3), state.warmupPlan)
    }

    @Test
    fun `a bodyweight machine without added weight gets no plan`() = runTest {
        open(pullUps, last = listOf(set(pullUps, 1, 0, 0.0, 8, at = earlier)))

        assertTrue(state.warmupPlan.isEmpty())
    }

    @Test
    fun `no weight yet means no plan`() = runTest {
        open(last = emptyList())

        assertEquals(0.0, state.weightKg, 0.0)
        assertTrue(state.warmupPlan.isEmpty())
    }

    @Test
    fun `a weight under 4 steps has no plan and 4 steps does`() = runTest {
        open(last = emptyList())

        typeWeight("15")
        assertTrue(state.warmupPlan.isEmpty())

        typeWeight("20")
        assertEquals(warmups(10.0 to 8, 15.0 to 3), state.warmupPlan)
    }

    @Test
    fun `cardio gets no plan`() = runTest {
        open(treadmill, last = emptyList())

        assertTrue(state.warmupPlan.isEmpty())
        assertFalse(state.warmupDialog)
    }

    @Test
    fun `switching the setting off removes the plan and on brings it back`() = runTest {
        open()
        assertTrue(state.warmupPlan.isNotEmpty())

        settings.setWarmupCalculator(false)
        runCurrent()
        assertTrue(state.warmupPlan.isEmpty())

        settings.setWarmupCalculator(true)
        runCurrent()
        assertTrue(state.warmupPlan.isNotEmpty())
    }

    @Test
    fun `no plan when the setting is off from the start`() = runTest {
        settings.warmup.value = false
        open()

        assertTrue(state.warmupPlan.isEmpty())
    }

    @Test
    fun `a machine with a working set today gets no plan`() = runTest {
        open(thisSession = listOf(set(legPress, 5, 0, 120.0, 12)))

        assertTrue(state.warmupPlan.isEmpty())
    }

    @Test
    fun `a machine with a warm-up set today gets no plan`() = runTest {
        open(thisSession = listOf(set(legPress, 5, 0, 60.0, 8, SetType.WARMUP)))

        assertTrue(state.warmupPlan.isEmpty())
    }

    @Test
    fun `the plan goes away after the first set is logged`() = runTest {
        open()
        assertTrue(state.warmupPlan.isNotEmpty())

        training.log.value = MachineLog(legPress, lastTime, earlier, listOf(set(legPress, 5, 0, 120.0, 12)))
        runCurrent()

        assertTrue(state.warmupPlan.isEmpty())
    }

    // The dialog

    @Test
    fun `the dialog is closed by default`() = runTest {
        open()

        assertFalse(state.warmupDialog)
    }

    @Test
    fun `openWarmups opens the dialog and closeWarmups closes it`() = runTest {
        open()

        viewModel.openWarmups()
        runCurrent()
        assertTrue(state.warmupDialog)

        viewModel.closeWarmups()
        runCurrent()
        assertFalse(state.warmupDialog)
    }

    @Test
    fun `openWarmups does nothing without a plan`() = runTest {
        settings.warmup.value = false
        open()

        viewModel.openWarmups()
        runCurrent()

        assertFalse(state.warmupDialog)
    }

    @Test
    fun `the dialog closes itself when the plan disappears`() = runTest {
        open()
        viewModel.openWarmups()
        runCurrent()
        assertTrue(state.warmupDialog)

        settings.setWarmupCalculator(false)
        runCurrent()

        assertFalse(state.warmupDialog)
    }

    @Test
    fun `the dialog closes itself when a set is logged on the machine`() = runTest {
        open()
        viewModel.openWarmups()
        runCurrent()

        training.log.value = MachineLog(legPress, lastTime, earlier, listOf(set(legPress, 5, 0, 120.0, 12)))
        runCurrent()

        assertFalse(state.warmupDialog)
    }

    // Logging

    @Test
    fun `logWarmups hands the plan to the repository and closes the dialog`() = runTest {
        open()
        val plan = state.warmupPlan
        viewModel.openWarmups()
        runCurrent()

        viewModel.logWarmups()
        runCurrent()

        assertEquals(listOf(legPress.id to plan), training.warmupsLogged)
        assertFalse(state.warmupDialog)
    }

    @Test
    fun `logWarmups sends WarmupsLogged with the repository's outcome`() = runTest {
        open()
        viewModel.openWarmups()
        runCurrent()

        viewModel.logWarmups()
        runCurrent()

        assertEquals(listOf<MachineEvent>(MachineEvent.WarmupsLogged(outcome)), events)
    }

    @Test
    fun `logWarmups logs no normal set and starts no rest timer`() = runTest {
        open()

        viewModel.logWarmups()
        runCurrent()

        assertTrue(training.setLogged.isEmpty())
        assertTrue(rest.started.isEmpty())
    }

    @Test
    fun `logWarmups with no plan does nothing`() = runTest {
        settings.warmup.value = false
        open()

        viewModel.logWarmups()
        runCurrent()

        assertTrue(training.warmupsLogged.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a failed write sends Failed and no WarmupsLogged`() = runTest {
        training.failWarmups = true
        open()
        viewModel.openWarmups()
        runCurrent()

        viewModel.logWarmups()
        runCurrent()

        assertEquals(listOf<MachineEvent>(MachineEvent.Failed), events)
    }

    @Test
    fun `undoWarmups hands the outcome to undoRepeat`() = runTest {
        open()

        viewModel.undoWarmups(outcome)
        runCurrent()

        assertEquals(listOf(outcome), training.undone)
        assertTrue(events.isEmpty())
    }
}
