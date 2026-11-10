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
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutineUpdate
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionMachine
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
 * The machine view model for an exercise in the routine: the rest timer after its last programmed working set,
 * how many sets are done, the next exercises of the routine, and the extra set after completion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MachineExerciseCompleteViewModelTest {
    private val now = Instant.parse("2026-11-09T18:10:00Z")

    private fun gymMachine(id: Long, name: String, group: MuscleGroup, kind: MachineKind) =
        Machine(id, name, group, kind, 2.5, null, null, archived = false)

    private val legPress = gymMachine(11L, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS)
    private val seatedRow = gymMachine(12L, "Seated row", MuscleGroup.BACK, MachineKind.WEIGHT_REPS)
    private val pullUps = gymMachine(13L, "Pull-ups", MuscleGroup.BACK, MachineKind.BODYWEIGHT_REPS)
    private val chestPress = gymMachine(14L, "Chest press", MuscleGroup.CHEST, MachineKind.WEIGHT_REPS)
    private val shoulderPress = gymMachine(15L, "Shoulder press", MuscleGroup.SHOULDERS, MachineKind.WEIGHT_REPS)
    private val cableCurl = gymMachine(16L, "Cable biceps curl", MuscleGroup.ARMS, MachineKind.WEIGHT_REPS)
    private val treadmill = gymMachine(10L, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO)
    private val library = listOf(legPress, seatedRow, pullUps, chestPress, shoulderPress, cableCurl, treadmill)

    private val legsRoutine = Routine(7L, 1L, "Legs", "Legs", 0, emptySet())
    private val session = Session(1L, now.minusSeconds(3_600), null, 7L, null)

    private fun item(machine: Machine, position: Int, targetSets: Int) =
        RoutineItem(7L, machine.id, position, targetSets, 8, 12)

    private fun working(id: Long, machine: Machine, position: Int, type: SetType = SetType.NORMAL) =
        WorkoutSet(id, 1L, machine.id, position, 100.0, 10, null, null, null, now.minusSeconds(600), type)

    /** [count] working sets of leg press, positions 0 and up. */
    private fun legSets(count: Int) = (0 until count).map { working(it + 1L, legPress, it) }

    private fun cardioSet(id: Long, machine: Machine, position: Int) =
        CardioRules.toSet(id, 1L, machine.id, position, CardioDraft(20, 100, 0), now.minusSeconds(600))

    private class FakeTraining : TrainingRepository {
        val log = MutableStateFlow<MachineLog?>(null)
        val open = MutableStateFlow<OpenSession?>(null)
        val plan = MutableStateFlow(noRoutineTodayPlan())
        val setLogged = mutableListOf<Pair<Long, SetDraft>>()
        val cardioLogged = mutableListOf<Pair<Long, CardioDraft>>()

        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = log

        override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet {
            setLogged += machineId to draft
            val set = WorkoutSet(200L, 1L, machineId, 0, draft.weightKg, draft.reps, null, null, null, Instant.EPOCH, draft.type)
            return LoggedSet(set, null)
        }

        override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet {
            cardioLogged += machineId to draft
            return LoggedSet(CardioRules.toSet(300L, 1L, machineId, 0, draft, Instant.EPOCH), null)
        }

        override suspend fun updateSet(set: WorkoutSet) = Unit
        override suspend fun deleteSet(set: WorkoutSet) = Unit
        override suspend fun restoreSet(set: WorkoutSet) = Unit
        override fun observeOpenSession(): Flow<OpenSession?> = open
        override fun observeTodayPlan(): Flow<TodayPlan> = plan
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

    /**
     * Opens the view model on [machine]. [routine] is the active routine's items (the machine is in it when one of
     * them is for this machine); [thisSession] are its sets today; [others] are the other machines of the open session.
     */
    private fun TestScope.open(
        machine: Machine = legPress,
        routine: List<RoutineItem> = listOf(item(legPress, 0, 3)),
        thisSession: List<WorkoutSet> = emptyList(),
        others: List<SessionMachine> = emptyList(),
        skipped: Set<Long> = emptySet(),
    ) {
        val target = routine.firstOrNull { it.machineId == machine.id }
        val mine = if (thisSession.isEmpty()) emptyList() else listOf(SessionMachine(machine, thisSession))
        training.log.value = MachineLog(machine, emptyList(), null, thisSession, target)
        training.open.value = OpenSession(session, mine + others)
        training.plan.value = TodayPlan(
            mode = ScheduleMode.IN_TURN,
            routines = listOf(legsRoutine),
            suggested = legsRoutine,
            choice = RoutineChoice.Suggested,
            active = if (routine.isEmpty()) null else RoutineWithItems(legsRoutine, routine),
            restDay = false,
            skipped = skipped,
        )
        viewModel = MachineViewModel(
            SavedStateHandle(mapOf(MACHINE_ID_ARG to machine.id)),
            training,
            FakeMachineLibrary(library),
            rest,
            FakeSettings(),
            Clock.fixed(now, ZoneOffset.UTC),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()
    }

    private val state: MachineUiState get() = viewModel.state.value

    // The rest timer after a working set

    @Test
    fun `the last programmed working set does not start the rest timer`() = runTest {
        open(thisSession = legSets(2))

        viewModel.logSet()
        runCurrent()

        assertEquals(1, training.setLogged.size)
        assertTrue(rest.started.isEmpty())
        assertEquals(1, rest.skips)
    }

    @Test
    fun `the last programmed working set stops a rest that is running`() = runTest {
        open(thisSession = legSets(1))
        viewModel.logSet()
        runCurrent()
        assertEquals(listOf(90), rest.started)

        training.log.value = training.log.value?.copy(thisSession = legSets(2))
        runCurrent()
        viewModel.logSet()
        runCurrent()

        assertEquals(listOf(90), rest.started)
        assertEquals(1, rest.skips)
    }

    @Test
    fun `a working set before the last programmed one still starts the rest timer`() = runTest {
        open(thisSession = legSets(1))

        viewModel.logSet()
        runCurrent()

        assertEquals(listOf(90), rest.started)
        assertEquals(0, rest.skips)
    }

    @Test
    fun `a warm-up after two working sets still starts the rest timer`() = runTest {
        open(thisSession = legSets(2))
        viewModel.toggleWarmup()
        runCurrent()

        viewModel.logSet()
        runCurrent()

        assertEquals(SetType.WARMUP, training.setLogged.single().second.type)
        assertEquals(listOf(90), rest.started)
        assertEquals(0, rest.skips)
    }

    @Test
    fun `warm-ups do not count toward the programmed sets`() = runTest {
        open(thisSession = legSets(2) + working(3L, legPress, 2, SetType.WARMUP))
        assertEquals(2, state.doneSets)
        assertFalse(state.exerciseComplete)

        viewModel.logSet()
        runCurrent()

        assertTrue(rest.started.isEmpty())
        assertEquals(1, rest.skips)
    }

    @Test
    fun `an exercise not in the routine keeps its rest timer`() = runTest {
        open(routine = emptyList(), thisSession = legSets(3))

        viewModel.logSet()
        runCurrent()

        assertEquals(listOf(90), rest.started)
        assertEquals(0, rest.skips)
    }

    @Test
    fun `an extra set after the exercise is complete starts no rest timer`() = runTest {
        open(thisSession = legSets(3))
        viewModel.showExtraSet()
        runCurrent()

        viewModel.logSet()
        runCurrent()

        assertTrue(rest.started.isEmpty())
        assertEquals(1, rest.skips)
    }

    @Test
    fun `the last programmed cardio set stops a running rest and starts none`() = runTest {
        open(machine = treadmill, routine = listOf(item(treadmill, 0, 2)), thisSession = listOf(cardioSet(1, treadmill, 0)))

        viewModel.logCardio()
        runCurrent()

        assertEquals(1, training.cardioLogged.size)
        assertTrue(rest.started.isEmpty())
        assertEquals(1, rest.skips)
    }

    @Test
    fun `a cardio set before the last programmed one does not stop the rest timer`() = runTest {
        open(machine = treadmill, routine = listOf(item(treadmill, 0, 2)), thisSession = emptyList())

        viewModel.logCardio()
        runCurrent()

        assertEquals(1, training.cardioLogged.size)
        assertEquals(0, rest.skips)
    }

    // Counts

    @Test
    fun `programmed sets come from the routine and done sets count working sets only`() = runTest {
        open(thisSession = listOf(working(1L, legPress, 0), working(2L, legPress, 1, SetType.WARMUP)))

        assertEquals(3, state.programmedSets)
        assertEquals(1, state.doneSets)
        assertFalse(state.exerciseComplete)
    }

    @Test
    fun `a machine outside the routine has no programmed sets, is never complete and has no next exercises`() = runTest {
        open(routine = listOf(item(seatedRow, 0, 3)), thisSession = legSets(3))

        assertNull(state.programmedSets)
        assertFalse(state.exerciseComplete)
        assertTrue(state.nextExercises.isEmpty())
    }

    @Test
    fun `exerciseComplete once every programmed working set is done`() = runTest {
        open(thisSession = legSets(3))

        assertTrue(state.exerciseComplete)
        assertEquals(3, state.doneSets)
        assertEquals(3, state.programmedSets)
    }

    // Next exercises

    @Test
    fun `next exercises are the rest of the routine in order, without this machine, at most four`() = runTest {
        open(
            routine = listOf(
                item(cableCurl, 5, 3),
                item(seatedRow, 1, 3),
                item(legPress, 0, 3),
                item(chestPress, 3, 3),
                item(pullUps, 2, 3),
                item(shoulderPress, 4, 3),
            ),
            thisSession = legSets(3),
        )

        assertEquals(
            listOf(seatedRow.id, pullUps.id, chestPress.id, shoulderPress.id),
            state.nextExercises.map { it.machine.id },
        )
        assertEquals(item(seatedRow, 1, 3), state.nextExercises.first().target)
    }

    @Test
    fun `a machine already complete in the open session is not a next exercise`() = runTest {
        open(
            routine = listOf(item(legPress, 0, 3), item(seatedRow, 1, 2), item(pullUps, 2, 3), item(chestPress, 3, 3)),
            thisSession = legSets(3),
            others = listOf(SessionMachine(seatedRow, listOf(working(20L, seatedRow, 0), working(21L, seatedRow, 1)))),
        )

        assertEquals(listOf("Pull-ups", "Chest press"), state.nextExercises.map { it.machine.name })
    }

    @Test
    fun `a machine skipped today is not a next exercise`() = runTest {
        open(
            routine = listOf(item(legPress, 0, 3), item(seatedRow, 1, 2), item(pullUps, 2, 3), item(chestPress, 3, 3)),
            thisSession = legSets(3),
            skipped = setOf(pullUps.id),
        )

        assertEquals(listOf("Seated row", "Chest press"), state.nextExercises.map { it.machine.name })
    }

    // Extra set

    @Test
    fun `showExtraSet turns the extra set flag on`() = runTest {
        open(thisSession = legSets(3))
        assertFalse(state.extraSet)

        viewModel.showExtraSet()
        runCurrent()

        assertTrue(state.extraSet)
    }
}
