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

/** The machine screen's view model on a cardio machine: prefill, steppers, logging, editing. Works from the Phase 7 contract. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MachineViewModelCardioTest {
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

    // Prefill

    @Test
    fun `first time on a cardio machine starts at 20 minutes and nothing else`() = runTest {
        val s = open(treadmill)

        assertTrue(s.loaded)
        assertEquals(treadmill, s.machine)
        assertEquals(CardioDraft(20, 0, 0), s.cardio)
        assertNull(s.cardioSameAgain)
        assertFalse(s.cardioDistanceShown)
        assertNull(s.lastTime)
    }

    @Test
    fun `prefill is last time's first set`() = runTest {
        val last = listOf(
            cardioSet(1, treadmill, 0, CardioDraft(30, 250, 0), earlier),
            cardioSet(2, treadmill, 1, CardioDraft(10, 90, 0), earlier),
        )

        val s = open(treadmill, lastTime = last)

        assertEquals(CardioDraft(30, 250, 0), s.cardio)
        assertEquals(SetSummary.Cardio(count = 2, minutes = 40, calories = 340, distanceM = 0), s.lastTime)
        assertEquals(earlier, s.lastTimeAt)
    }

    @Test
    fun `prefill is this session's last set when there is one`() = runTest {
        val last = listOf(cardioSet(1, treadmill, 0, CardioDraft(30, 250, 0), earlier))
        val today = listOf(
            cardioSet(5, treadmill, 0, CardioDraft(15, 100, 0)),
            cardioSet(6, treadmill, 1, CardioDraft(12, 80, 0)),
        )

        val s = open(treadmill, lastTime = last, thisSession = today)

        assertEquals(CardioDraft(12, 80, 0), s.cardio)
        assertEquals(today, s.sets)
    }

    @Test
    fun `same again is last time's set at this session's position`() = runTest {
        val last = listOf(
            cardioSet(1, treadmill, 0, CardioDraft(30, 250, 3200), earlier),
            cardioSet(2, treadmill, 1, CardioDraft(10, 90, 800), earlier),
        )

        assertEquals(CardioDraft(30, 250, 3200), open(treadmill, lastTime = last).cardioSameAgain)
    }

    @Test
    fun `same again moves to the next set of last time and disappears after the last one`() = runTest {
        val last = listOf(
            cardioSet(1, treadmill, 0, CardioDraft(30, 250, 0), earlier),
            cardioSet(2, treadmill, 1, CardioDraft(10, 90, 0), earlier),
        )
        val one = listOf(cardioSet(5, treadmill, 0, CardioDraft(30, 250, 0)))

        assertEquals(CardioDraft(10, 90, 0), open(treadmill, lastTime = last, thisSession = one).cardioSameAgain)

        training.log.value = MachineLog(treadmill, last, earlier, one + cardioSet(6, treadmill, 1, CardioDraft(10, 90, 0)))
        runCurrent()
        assertNull(state.cardioSameAgain)
    }

    // Distance

    @Test
    fun `the distance stepper is hidden when last time had no distance`() = runTest {
        val last = listOf(cardioSet(1, treadmill, 0, CardioDraft(30, 250, 0), earlier))

        assertFalse(open(treadmill, lastTime = last).cardioDistanceShown)
    }

    @Test
    fun `the distance stepper shows when last time had a distance`() = runTest {
        val last = listOf(cardioSet(1, treadmill, 0, CardioDraft(30, 250, 3200), earlier))

        val s = open(treadmill, lastTime = last)

        assertTrue(s.cardioDistanceShown)
        assertEquals(3200, s.cardio.distanceM)
    }

    @Test
    fun `the distance stepper shows when this session's last set had a distance`() = runTest {
        val today = listOf(cardioSet(5, treadmill, 0, CardioDraft(15, 0, 2000)))

        assertTrue(open(treadmill, thisSession = today).cardioDistanceShown)
    }

    @Test
    fun `tapping Add distance reveals the stepper without changing the values`() = runTest {
        open(treadmill)
        assertFalse(state.cardioDistanceShown)

        viewModel.showDistance()
        runCurrent()

        assertTrue(state.cardioDistanceShown)
        assertEquals(CardioDraft(20, 0, 0), state.cardio)
    }

    // Steppers

    @Test
    fun `minutes step by one and stop at 0 and 600`() = runTest {
        open(treadmill)

        viewModel.stepMinutes(up = true)
        assertEquals(21, state.cardio.minutes)

        viewModel.stepMinutes(up = false)
        viewModel.stepMinutes(up = false)
        assertEquals(19, state.cardio.minutes)

        repeat(30) { viewModel.stepMinutes(up = false) }
        assertEquals(0, state.cardio.minutes)

        repeat(700) { viewModel.stepMinutes(up = true) }
        assertEquals(CardioRules.MAX_MINUTES, state.cardio.minutes)
    }

    @Test
    fun `calories step by ten from zero and stop at 0`() = runTest {
        open(treadmill)

        viewModel.stepCalories(up = true)
        viewModel.stepCalories(up = true)
        assertEquals(20, state.cardio.calories)

        viewModel.stepCalories(up = false)
        viewModel.stepCalories(up = false)
        viewModel.stepCalories(up = false)
        assertEquals(0, state.cardio.calories)
    }

    @Test
    fun `an off-grid calorie value snaps to the grid when stepped`() = runTest {
        val last = listOf(cardioSet(1, treadmill, 0, CardioDraft(30, 257, 0), earlier))
        open(treadmill, lastTime = last)

        viewModel.stepCalories(up = true)
        assertEquals(260, state.cardio.calories)
    }

    @Test
    fun `distance steps by 100 metres`() = runTest {
        open(treadmill)
        viewModel.showDistance()

        viewModel.stepDistance(up = true)
        viewModel.stepDistance(up = true)
        assertEquals(200, state.cardio.distanceM)

        viewModel.stepDistance(up = false)
        assertEquals(100, state.cardio.distanceM)
    }

    @Test
    fun `stepping one value keeps the others`() = runTest {
        open(treadmill)

        viewModel.stepCalories(up = true)
        viewModel.stepMinutes(up = true)

        assertEquals(CardioDraft(21, 10, 0), state.cardio)
    }

    // Logging

    @Test
    fun `Log writes the steppers through logCardio and sends a Logged event with no record`() = runTest {
        open(treadmill)
        viewModel.stepMinutes(up = true)
        viewModel.stepCalories(up = true)

        viewModel.logCardio()
        runCurrent()

        assertEquals(listOf(treadmill.id to CardioDraft(21, 10, 0)), training.cardioLogged)
        assertEquals(listOf<MachineEvent>(MachineEvent.Logged()), events)
        assertNull((events.single() as MachineEvent.Logged).record)
        assertNull(state.record)
        assertTrue(training.setLogged.isEmpty())
    }

    @Test
    fun `after Log the steppers follow the prefill again`() = runTest {
        open(treadmill)
        viewModel.stepMinutes(up = true)
        assertEquals(21, state.cardio.minutes)

        viewModel.logCardio()
        runCurrent()
        // The repository would now report the set in this session; the fake does not, so only the override is checked.
        assertEquals(20, state.cardio.minutes)
    }

    @Test
    fun `Log does nothing when minutes and calories are both 0`() = runTest {
        open(treadmill)
        repeat(20) { viewModel.stepMinutes(up = false) }
        assertEquals(CardioDraft(0, 0, 0), state.cardio)

        viewModel.logCardio()
        runCurrent()

        assertTrue(training.cardioLogged.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `Log does nothing when only a distance is set`() = runTest {
        open(treadmill)
        repeat(20) { viewModel.stepMinutes(up = false) }
        viewModel.showDistance()
        viewModel.stepDistance(up = true)

        viewModel.logCardio()
        runCurrent()

        assertTrue(training.cardioLogged.isEmpty())
    }

    @Test
    fun `Log works with calories only`() = runTest {
        open(treadmill)
        repeat(20) { viewModel.stepMinutes(up = false) }
        viewModel.stepCalories(up = true)

        viewModel.logCardio()
        runCurrent()

        assertEquals(listOf(treadmill.id to CardioDraft(0, 10, 0)), training.cardioLogged)
    }

    @Test
    fun `Same again logs last time's draft`() = runTest {
        val last = listOf(cardioSet(1, treadmill, 0, CardioDraft(30, 250, 3200), earlier))
        open(treadmill, lastTime = last)

        viewModel.logCardioSameAgain()
        runCurrent()

        assertEquals(listOf(treadmill.id to CardioDraft(30, 250, 3200)), training.cardioLogged)
        assertEquals(listOf<MachineEvent>(MachineEvent.Logged()), events)
    }

    @Test
    fun `Same again does nothing without last time`() = runTest {
        open(treadmill)

        viewModel.logCardioSameAgain()
        runCurrent()

        assertTrue(training.cardioLogged.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `no rest timer starts after a cardio set`() = runTest {
        val last = listOf(cardioSet(1, treadmill, 0, CardioDraft(30, 250, 0), earlier))
        open(treadmill, lastTime = last)

        viewModel.logCardio()
        runCurrent()
        viewModel.logCardioSameAgain()
        runCurrent()

        assertEquals(2, training.cardioLogged.size)
        assertTrue(rest.started.isEmpty())
        assertNull(state.restSeconds)
    }

    @Test
    fun `a weight machine still starts the rest timer after a set`() = runTest {
        val sets = listOf(WorkoutSet(1L, 1L, legPress.id, 0, 120.0, 12, null, null, null, earlier))
        open(legPress, lastTime = sets)

        viewModel.logSet()
        runCurrent()

        assertEquals(listOf(90), rest.started)
    }

    @Test
    fun `a failed write sends Failed and keeps the screen usable`() = runTest {
        open(treadmill)
        training.failNextWrite = true

        viewModel.logCardio()
        runCurrent()

        assertEquals(listOf<MachineEvent>(MachineEvent.Failed), events)
        assertTrue(training.cardioLogged.isEmpty())
    }

    // Editing a logged cardio set

    private val logged = cardioSet(5, treadmill, 0, CardioDraft(15, 100, 0), Instant.parse("2026-10-31T18:05:00Z"))
    private val loggedToo = cardioSet(6, treadmill, 1, CardioDraft(12, 80, 1500), Instant.parse("2026-10-31T18:09:00Z"))

    @Test
    fun `opening a cardio set shows its steppers and its row number`() = runTest {
        open(treadmill, thisSession = listOf(logged, loggedToo))

        viewModel.openCardioSet(loggedToo)
        runCurrent()

        val editor = state.cardioEditor
        assertEquals(loggedToo, editor?.set)
        assertEquals(2, editor?.number)
        assertEquals(CardioDraft(12, 80, 1500), editor?.draft)
    }

    @Test
    fun `the editor steppers change only the editor draft`() = runTest {
        open(treadmill, thisSession = listOf(logged))
        viewModel.openCardioSet(logged)

        viewModel.stepEditorMinutes(up = true)
        viewModel.stepEditorCalories(up = false)
        viewModel.stepEditorDistance(up = true)
        runCurrent()

        assertEquals(CardioDraft(16, 90, 100), state.cardioEditor?.draft)
        // The logging steppers show the prefill (this session's last set) and are untouched.
        assertEquals(CardioDraft(15, 100, 0), state.cardio)
    }

    @Test
    fun `closing the editor drops the changes`() = runTest {
        open(treadmill, thisSession = listOf(logged))
        viewModel.openCardioSet(logged)
        viewModel.stepEditorMinutes(up = true)

        viewModel.closeCardioEditor()
        runCurrent()

        assertNull(state.cardioEditor)
        assertTrue(training.updated.isEmpty())
    }

    @Test
    fun `saving writes the edited set in the same place with the same time`() = runTest {
        open(treadmill, thisSession = listOf(logged, loggedToo))
        viewModel.openCardioSet(logged)
        viewModel.stepEditorMinutes(up = true)
        viewModel.stepEditorCalories(up = true)

        viewModel.saveCardioEditor()
        runCurrent()

        val expected = CardioRules.toSet(
            logged.id, logged.sessionId, logged.machineId, logged.position, CardioDraft(16, 110, 0), logged.loggedAt,
        )
        assertEquals(listOf(expected), training.updated)
        assertNull(state.cardioEditor)
        assertTrue(training.cardioLogged.isEmpty())
    }

    @Test
    fun `deleting removes the set and offers undo`() = runTest {
        open(treadmill, thisSession = listOf(logged))
        viewModel.openCardioSet(logged)

        viewModel.deleteCardioEditor()
        runCurrent()

        assertEquals(listOf(logged), training.deleted)
        assertEquals(listOf<MachineEvent>(MachineEvent.SetRemoved(logged)), events)
        assertNull(state.cardioEditor)

        viewModel.restoreSet(logged)
        runCurrent()
        assertEquals(listOf(logged), training.restored)
    }

    @Test
    fun `saving or deleting with no editor open does nothing`() = runTest {
        open(treadmill, thisSession = listOf(logged))

        viewModel.saveCardioEditor()
        viewModel.deleteCardioEditor()
        runCurrent()

        assertTrue(training.updated.isEmpty())
        assertTrue(training.deleted.isEmpty())
        assertTrue(events.isEmpty())
    }
}
