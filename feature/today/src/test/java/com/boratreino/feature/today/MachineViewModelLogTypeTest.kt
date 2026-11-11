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
import com.boratreino.core.model.Suggestion
import com.boratreino.core.model.TodayPlan
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
 * The set type picked under "Set type" on the machine screen: it applies to the next logged set and goes back
 * to Normal after it. "Try 125 kg" (the suggestion) sets the steppers and logs nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MachineViewModelLogTypeTest {
    private val legPress = Machine(11L, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, archived = false)

    private val earlier = Instant.parse("2026-10-24T18:00:00Z")
    private val now = Instant.parse("2026-10-31T18:10:00Z")

    private fun set(id: Long, position: Int, kg: Double, reps: Int, at: Instant = now) =
        WorkoutSet(id, 1L, legPress.id, position, kg, reps, null, null, null, at, SetType.NORMAL)

    /** Last time: 120 kg x 12 first. With no set yet today the steppers start there. */
    private val lastTime = listOf(
        set(1, 0, 120.0, 12, at = earlier),
        set(2, 1, 125.0, 10, at = earlier),
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
        override suspend fun deleteSet(set: WorkoutSet) = Unit
        override suspend fun restoreSet(set: WorkoutSet) = Unit
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

    private fun TestScope.open(suggestion: Suggestion? = null) {
        training.log.value = MachineLog(legPress, lastTime, earlier, emptyList(), suggestion = suggestion)
        viewModel = MachineViewModel(
            SavedStateHandle(mapOf(MACHINE_ID_ARG to legPress.id)),
            training,
            FakeMachineLibrary(listOf(legPress)),
            FakeRestTimer(),
            FakeSettings(),
            Clock.fixed(now, ZoneOffset.UTC),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    private val state: MachineUiState get() = viewModel.state.value

    @Test
    fun `the set type picked is the type of the next logged set`() = runTest {
        open()

        viewModel.setLogType(SetType.DROP)
        runCurrent()
        assertEquals(SetType.DROP, state.logType)

        viewModel.logSet()
        runCurrent()

        assertEquals(listOf(legPress.id to SetDraft(120.0, 12, SetType.DROP)), training.setLogged)
    }

    @Test
    fun `the set type goes back to Normal after the set is logged`() = runTest {
        open()
        viewModel.setLogType(SetType.FAILURE)
        runCurrent()

        viewModel.logSet()
        runCurrent()

        assertEquals(SetType.NORMAL, state.logType)
        viewModel.logSet()
        runCurrent()
        assertEquals(SetDraft(120.0, 12, SetType.NORMAL), training.setLogged.last().second)
    }

    @Test
    fun `picking Warm-up turns the warm-up toggle off so only one type applies`() = runTest {
        open()
        viewModel.toggleWarmup()
        runCurrent()

        viewModel.setLogType(SetType.WARMUP)
        runCurrent()

        assertEquals(false, state.warmupOn)
        assertEquals(SetType.WARMUP, state.logType)
    }

    @Test
    fun `the suggestion chip sets the steppers to the suggestion and logs nothing`() = runTest {
        open(suggestion = Suggestion.Up(weightKg = 125.0, reps = 8))
        runCurrent()

        assertEquals(Suggestion.Up(weightKg = 125.0, reps = 8), state.suggestion)

        viewModel.applySuggestion()
        runCurrent()

        assertEquals(125.0, state.weightKg, 0.0)
        assertEquals(8, state.reps)
        assertTrue(training.setLogged.isEmpty())
    }
}
