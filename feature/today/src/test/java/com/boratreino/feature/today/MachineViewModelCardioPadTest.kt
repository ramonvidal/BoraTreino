package com.boratreino.feature.today

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.RepeatOffer
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioField
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
import com.boratreino.core.data.RemovedSets

/**
 * The number pad on the cardio machine screen: opening it on a cardio value, what confirming types into the
 * log draft or the logged set's editor, and that logging still needs minutes or calories (phase 44).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MachineViewModelCardioPadTest {
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

    private val earlier = Instant.parse("2026-10-24T18:00:00Z")
    private val now = Instant.parse("2026-10-31T18:10:00Z")

    private fun cardioSet(id: Long, position: Int, draft: CardioDraft, at: Instant = now) =
        CardioRules.toSet(id, sessionId = 1L, machineId = treadmill.id, position = position, draft = draft, loggedAt = at)

    private class FakeTraining : TrainingRepository {
        override suspend fun routineUpdateOffer(sessionId: Long): com.boratreino.core.model.RoutineUpdate? = null
        override suspend fun trainingSummary(sessionId: Long): com.boratreino.core.model.TrainingSummary? = null
        override suspend fun sessionRoutineId(sessionId: Long): Long? = null

        val log = MutableStateFlow<MachineLog?>(null)
        val cardioLogged = mutableListOf<CardioDraft>()
        val updated = mutableListOf<WorkoutSet>()

        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = log

        override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet {
            cardioLogged += draft
            val set = CardioRules.toSet(300L, 1L, machineId, 0, draft, Instant.parse("2026-10-31T18:11:00Z"))
            return LoggedSet(set, null)
        }

        override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet = error("not used by cardio")

        override suspend fun updateSet(set: WorkoutSet) {
            updated += set
        }

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

    /** Opens the view model on the treadmill with the given history and starts observing it. */
    private fun TestScope.open(
        lastTime: List<WorkoutSet> = emptyList(),
        thisSession: List<WorkoutSet> = emptyList(),
    ): MachineUiState {
        training.log.value = MachineLog(treadmill, lastTime, if (lastTime.isEmpty()) null else earlier, thisSession)
        viewModel = MachineViewModel(
            SavedStateHandle(mapOf(MACHINE_ID_ARG to treadmill.id)),
            training,
            FakeMachineLibrary(),
            FakeRestTimer(),
            FakeSettings(),
            Clock.fixed(now, ZoneOffset.UTC),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        return viewModel.state.value
    }

    private val state: MachineUiState get() = viewModel.state.value

    // Opening the pad

    @Test
    fun `opening the pad on minutes starts it with the minutes stepper's value`() = runTest {
        open()

        viewModel.openCardioPad(CardioField.MINUTES)

        assertEquals(NumberPad(PadTarget.CARDIO, "20", CardioField.MINUTES), state.pad)
    }

    @Test
    fun `opening the pad on distance starts it in kilometres`() = runTest {
        open(lastTime = listOf(cardioSet(1, 0, CardioDraft(30, 0, 17_300), earlier)))

        viewModel.openCardioPad(CardioField.DISTANCE_KM)

        assertEquals(NumberPad(PadTarget.CARDIO, "17.3", CardioField.DISTANCE_KM), state.pad)
    }

    @Test
    fun `opening the pad on a value that is not set starts it empty`() = runTest {
        open()

        viewModel.openCardioPad(CardioField.INTENSITY)

        assertEquals(NumberPad(PadTarget.CARDIO, "", CardioField.INTENSITY), state.pad)
    }

    @Test
    fun `opening the pad on a logged set's value starts it with that set's value`() = runTest {
        val logged = cardioSet(2, 0, CardioDraft(35, 300, 0, 0.79), earlier)
        open(thisSession = listOf(logged))
        viewModel.openCardioSet(logged)

        viewModel.openCardioPad(CardioField.INTENSITY, inEditor = true)

        assertEquals(NumberPad(PadTarget.EDIT_CARDIO, "0.79", CardioField.INTENSITY), state.pad)
    }

    @Test
    fun `opening the pad does not change the steppers`() = runTest {
        val before = open()

        viewModel.openCardioPad(CardioField.CALORIES)

        assertEquals(before.cardio, state.cardio)
    }

    // Confirming

    @Test
    fun `confirming the minutes pad sets the minutes and closes the pad`() = runTest {
        open()
        viewModel.openCardioPad(CardioField.MINUTES)

        viewModel.confirmPad("45")

        assertEquals(45, state.cardio.minutes)
        assertNull(state.pad)
    }

    @Test
    fun `a typed distance with a comma is kilometres in the log draft`() = runTest {
        open()
        viewModel.openCardioPad(CardioField.DISTANCE_KM)

        viewModel.confirmPad("12,5")

        assertEquals(12_500, state.cardio.distanceM)
    }

    @Test
    fun `a typed value keeps the other values of the log draft`() = runTest {
        open()
        viewModel.stepCalories(up = true)
        viewModel.openCardioPad(CardioField.INTENSITY)

        viewModel.confirmPad("0.15")

        assertEquals(CardioDraft(20, 10, 0, 0.15), state.cardio)
    }

    @Test
    fun `a typed intensity is rounded to 2 decimals`() = runTest {
        open()
        viewModel.openCardioPad(CardioField.INTENSITY)

        viewModel.confirmPad("0.785")

        assertEquals(0.79, state.cardio.intensity!!, 0.0)
    }

    @Test
    fun `a text that is not a number is ignored`() = runTest {
        val before = open()
        viewModel.openCardioPad(CardioField.MINUTES)

        viewModel.confirmPad("abc")

        assertEquals(before.cardio, state.cardio)
        assertTrue(training.cardioLogged.isEmpty())
    }

    @Test
    fun `the typed value is what Log writes`() = runTest {
        open()
        viewModel.openCardioPad(CardioField.MINUTES)
        viewModel.confirmPad("45")

        viewModel.logCardio()
        runCurrent()

        assertEquals(listOf(CardioDraft(45, 0, 0)), training.cardioLogged)
    }

    @Test
    fun `confirming the edit pad changes only the logged set's draft`() = runTest {
        val logged = cardioSet(2, 0, CardioDraft(35, 300, 0), earlier)
        val before = open(thisSession = listOf(logged))
        viewModel.openCardioSet(logged)
        viewModel.openCardioPad(CardioField.MINUTES, inEditor = true)

        viewModel.confirmPad("16")
        runCurrent()

        assertEquals(16, state.cardioEditor?.draft?.minutes)
        assertEquals(300, state.cardioEditor?.draft?.calories)
        assertEquals(before.cardio, state.cardio)
        assertTrue(training.updated.isEmpty())
    }

    @Test
    fun `a text that is not a number leaves the logged set's draft as it was`() = runTest {
        val logged = cardioSet(2, 0, CardioDraft(35, 300, 0), earlier)
        open(thisSession = listOf(logged))
        viewModel.openCardioSet(logged)
        viewModel.openCardioPad(CardioField.CALORIES, inEditor = true)

        viewModel.confirmPad("1e3")

        assertEquals(CardioDraft(35, 300, 0), state.cardioEditor?.draft)
    }

    // Distance and dismissing

    @Test
    fun `a typed distance above zero keeps the distance stepper visible`() = runTest {
        val before = open()
        assertFalse(before.cardioDistanceShown)
        viewModel.openCardioPad(CardioField.DISTANCE_KM)

        viewModel.confirmPad("3")

        assertTrue(state.cardioDistanceShown)
        assertEquals(3_000, state.cardio.distanceM)
    }

    @Test
    fun `dismissing the pad closes it and changes nothing`() = runTest {
        val before = open()
        viewModel.openCardioPad(CardioField.MINUTES)

        viewModel.dismissPad()

        assertNull(state.pad)
        assertEquals(before.cardio, state.cardio)
    }

    // Logging still needs minutes or calories

    @Test
    fun `Log does nothing when the typed minutes are cleared and only a distance is left`() = runTest {
        open()
        viewModel.openCardioPad(CardioField.MINUTES)
        viewModel.confirmPad("")
        viewModel.openCardioPad(CardioField.DISTANCE_KM)
        viewModel.confirmPad("5")

        viewModel.logCardio()
        runCurrent()

        assertTrue(training.cardioLogged.isEmpty())
    }

    @Test
    fun `Log works once a typed calorie value is there`() = runTest {
        open()
        viewModel.openCardioPad(CardioField.MINUTES)
        viewModel.confirmPad("")
        viewModel.openCardioPad(CardioField.CALORIES)
        viewModel.confirmPad("100")

        viewModel.logCardio()
        runCurrent()

        assertEquals(listOf(CardioDraft(0, 100, 0)), training.cardioLogged)
    }
}
