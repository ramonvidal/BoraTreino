package com.boratreino.feature.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RepeatOffer
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.HealthExport
import com.boratreino.core.model.LoggedSet
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutinePlan
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionInfo
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * The session editor's cardio intensity: the editor stepper, Save, the saved set, canSave and Add set. Works from
 * the phase 41 contract; the fakes are the cardio test's own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SessionEditorCardioIntensityTest {
    private val treadmill = Machine(30L, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)
    private val legPress = Machine(1L, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, archived = false)

    private val started = Instant.parse("2026-11-02T18:05:00Z")
    private val session = Session(id = 20L, startedAt = started, endedAt = Instant.parse("2026-11-02T19:10:00Z"), routineId = null, note = null)

    private fun cardio(id: Long, position: Int, draft: CardioDraft) =
        CardioRules.toSet(id, session.id, treadmill.id, position, draft, started.plusSeconds(600L * (position + 1)))

    private val first = cardio(1, 0, CardioDraft(30, 250, 3200))
    private val second = cardio(2, 1, CardioDraft(10, 0, 0))
    private val weightSet = WorkoutSet(9L, session.id, legPress.id, 0, 120.0, 12, null, null, null, started.plusSeconds(60))

    private class FakeHistory : HistoryRepository {
        val detail = MutableStateFlow<SessionDetail?>(null)
        var lastTime: List<WorkoutSet> = emptyList()
        val cardioAdded = mutableListOf<Triple<Long, Long, CardioDraft>>()
        val weightAdded = mutableListOf<Triple<Long, Long, SetDraft>>()

        override fun observeSession(sessionId: Long): Flow<SessionDetail?> = detail
        override suspend fun lastTimeBefore(sessionId: Long, machineId: Long): List<WorkoutSet> = lastTime

        override suspend fun addCardioSet(sessionId: Long, machineId: Long, draft: CardioDraft): WorkoutSet {
            cardioAdded += Triple(sessionId, machineId, draft)
            return CardioRules.toSet(500L, sessionId, machineId, 0, draft, Instant.EPOCH)
        }

        override suspend fun addSet(sessionId: Long, machineId: Long, draft: SetDraft): WorkoutSet {
            weightAdded += Triple(sessionId, machineId, draft)
            return WorkoutSet(501L, sessionId, machineId, 0, draft.weightKg, draft.reps, null, null, null, Instant.EPOCH)
        }

        override fun observeMonth(month: YearMonth): Flow<Map<LocalDate, List<SessionInfo>>> = emptyFlow()
        override fun observeDay(date: LocalDate): Flow<List<SessionDetail>> = emptyFlow()
        override fun observeTrainingDates(): Flow<Set<LocalDate>> = emptyFlow()
        override suspend fun addSession(date: LocalDate, routineId: Long?, isExtra: Boolean): Long = 0L
        override suspend fun setSessionRoutine(sessionId: Long, routineId: Long?) = Unit
        override suspend fun setSessionExtra(sessionId: Long, extra: Boolean) = Unit
        override fun observeClosedSessionExports(sinceDays: Int): Flow<List<HealthExport>> = emptyFlow()
        override fun observeSessionIds(): Flow<Set<Long>> = emptyFlow()
        override suspend fun deleteSession(sessionId: Long): SessionDetail? = null
        override suspend fun restoreSession(detail: SessionDetail) = Unit
        override suspend fun repeatLastSession(sessionId: Long, mode: RepeatMode): List<WorkoutSet> = emptyList()
    }

    private class FakeTraining : TrainingRepository {
    override suspend fun routineUpdateOffer(sessionId: Long): com.boratreino.core.model.RoutineUpdate? = null
    override suspend fun trainingSummary(sessionId: Long): com.boratreino.core.model.TrainingSummary? = null
    override suspend fun sessionRoutineId(sessionId: Long): Long? = null

        val updated = mutableListOf<WorkoutSet>()
        val deleted = mutableListOf<WorkoutSet>()
        val restored = mutableListOf<WorkoutSet>()

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
        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = emptyFlow()
        override suspend fun closeStaleSession() = Unit
        override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet = error("not used")
        override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet = error("not used")
        override suspend fun finishSession(): SessionSummary? = null
        override suspend fun finishIfRoutineComplete(): SessionSummary? = null
        override val pendingSummarySessionId: Flow<Long?> = MutableStateFlow(null)
        override suspend fun consumePendingSummary() = Unit
        override suspend fun discardMachineToday(machineId: Long): List<WorkoutSet> = emptyList()
        override suspend fun undoDiscardMachineToday(machineId: Long, sets: List<WorkoutSet>) = Unit
        override suspend fun reopenSession(sessionId: Long) = Unit
        override suspend fun skipMachineToday(machineId: Long) = Unit
        override suspend fun unskipMachineToday(machineId: Long) = Unit
        override fun observeRepeatOffer(): Flow<RepeatOffer?> = emptyFlow()
        override suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome? = null
        override suspend fun logWarmups(machineId: Long, sets: List<SetDraft>): RepeatOutcome = RepeatOutcome(0, false, emptyList(), 1)
        override suspend fun undoRepeat(outcome: RepeatOutcome) = Unit
    }

    private class FakeRoutines : RoutineRepository {
    override suspend fun updateRoutineFromSession(routineId: Long, sessionId: Long): com.boratreino.core.model.RoutineUpdate =
        com.boratreino.core.model.RoutineUpdate(emptyList(), emptyList())
    override suspend fun undoRoutineUpdate(routineId: Long, previousItems: List<com.boratreino.core.model.RoutineItem>) {}

        override fun observePlan(): Flow<RoutinePlanWithRoutines> =
            flowOf(RoutinePlanWithRoutines(RoutinePlan(1L, ScheduleMode.IN_TURN), emptyList()))

        override fun observeRoutine(id: Long): Flow<RoutineWithItems?> = flowOf(null)
        override fun observeLastRoutineId(): Flow<Long?> = flowOf(null)
        override fun observeLastTrained(): Flow<Map<Long, java.time.Instant>> = flowOf(emptyMap())
        override suspend fun setScheduleMode(mode: ScheduleMode) = Unit
        override suspend fun setTrainingDays(days: Set<java.time.DayOfWeek>) = Unit
        override suspend fun saveRoutine(routine: com.boratreino.core.model.Routine, items: List<com.boratreino.core.model.RoutineItem>): Long = 0L
        override suspend fun deleteRoutine(id: Long) = Unit
        override suspend fun moveRoutine(id: Long, up: Boolean) = Unit
        override suspend fun moveItem(routineId: Long, machineId: Long, up: Boolean) = Unit
        override suspend fun duplicateRoutine(id: Long): Long = 0L
        override suspend fun createRoutine(): Long = 0L
        override suspend fun addMachine(routineId: Long, machineId: Long) = Unit
        override suspend fun removeMachine(routineId: Long, machineId: Long): com.boratreino.core.model.RoutineItem? = null
        override fun observePlanned(): kotlinx.coroutines.flow.Flow<List<com.boratreino.core.model.PlannedWorkout>> =
            kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun planWorkout(date: java.time.LocalDate, routineId: Long) = Unit
        override suspend fun unplanWorkout(date: java.time.LocalDate, routineId: Long) = Unit
        override suspend fun reorderPlanned(date: java.time.LocalDate, routineIds: List<Long>) = Unit
        override suspend fun restoreItem(item: com.boratreino.core.model.RoutineItem) = Unit
    }

    private class FakeMachines(private val all: List<Machine>) : MachineRepository {
    override suspend fun addFromLibrary(exercise: com.boratreino.core.model.LibraryExercise, portuguese: Boolean): Long = error("not used by this test")
    override fun observeLibraryIdsInUse(): kotlinx.coroutines.flow.Flow<Set<String>> = kotlinx.coroutines.flow.flowOf(emptySet())

        override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = flowOf(all)
        override fun observeMachine(id: Long): Flow<Machine?> = flowOf(all.firstOrNull { it.id == id })
        override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = flowOf(all)
        override fun observeStartersNotAdded(): Flow<List<StarterMachine>> = flowOf(emptyList())
        override suspend fun save(machine: Machine): Long = machine.id
        override suspend fun usage(machineId: Long): MachineUsage = error("not used")
        override suspend fun delete(machineId: Long): Boolean = false
        override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long = 0L
        override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> = emptyList()
        override suspend fun setArchived(id: Long, archived: Boolean) = Unit
    }

    private lateinit var history: FakeHistory
    private lateinit var training: FakeTraining
    private lateinit var viewModel: SessionEditorViewModel
    private val events = mutableListOf<SessionEditorEvent>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        history = FakeHistory()
        training = FakeTraining()
        events.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state: SessionEditorUiState get() = viewModel.state.value

    private fun TestScope.open(vararg entries: SessionMachine) {
        history.detail.value = SessionDetail(session, null, entries.toList())
        viewModel = SessionEditorViewModel(
            SavedStateHandle(mapOf(SESSION_ID_ARG to session.id)),
            history,
            training,
            FakeRoutines(),
            FakeMachines(listOf(legPress, treadmill)),
            Clock.fixed(started, ZoneOffset.UTC),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()
    }

    private val cardioEntry get() = SessionMachine(treadmill, listOf(first, second))
    private val weightEntry get() = SessionMachine(legPress, listOf(weightSet))

    /** A treadmill set with an intensity of 0.79. */
    private val withIntensity get() = cardio(3, 0, CardioDraft(30, 250, 3200, 0.79))

    /** A set with an intensity and nothing else: it cannot be saved. */
    private val intensityOnly get() = cardio(4, 2, CardioDraft(0, 0, 0, 0.79))

    // Opening and stepping

    @Test
    fun `opening a set shows its intensity`() = runTest {
        open(SessionMachine(treadmill, listOf(withIntensity)))

        viewModel.openSet(withIntensity)
        runCurrent()

        assertEquals(0.79, state.editor?.intensity)
        assertEquals(CardioDraft(30, 250, 3200, 0.79), state.editor?.cardioDraft)
    }

    @Test
    fun `opening a set without an intensity leaves it unset`() = runTest {
        open(cardioEntry)

        viewModel.openSet(first)
        runCurrent()

        assertNull(state.editor?.intensity)
    }

    @Test
    fun `the editor intensity steps up from unset, and down from unset stays unset`() = runTest {
        open(cardioEntry)
        viewModel.openSet(first)
        runCurrent()

        viewModel.stepEditorIntensity(up = false)
        runCurrent()
        assertNull(state.editor?.intensity)

        viewModel.stepEditorIntensity(up = true)
        runCurrent()
        assertEquals(0.70, state.editor?.intensity)
    }

    @Test
    fun `the editor intensity steps without changing the other values`() = runTest {
        open(SessionMachine(treadmill, listOf(withIntensity)))
        viewModel.openSet(withIntensity)
        runCurrent()

        viewModel.stepEditorIntensity(up = true)
        runCurrent()

        assertEquals(CardioDraft(30, 250, 3200, 0.80), state.editor?.cardioDraft)
    }

    // Saving

    @Test
    fun `saving writes the intensity through updateSet with the same id, place and time`() = runTest {
        open(SessionMachine(treadmill, listOf(withIntensity)))
        viewModel.openSet(withIntensity)
        viewModel.stepEditorIntensity(up = true)

        viewModel.saveEditor()
        runCurrent()

        val expected = CardioRules.toSet(
            withIntensity.id,
            withIntensity.sessionId,
            withIntensity.machineId,
            withIntensity.position,
            CardioDraft(30, 250, 3200, 0.80),
            withIntensity.loggedAt,
        )
        assertEquals(listOf(expected), training.updated)
    }

    @Test
    fun `a set with only an intensity cannot be saved`() = runTest {
        open(SessionMachine(treadmill, listOf(intensityOnly)))
        viewModel.openSet(intensityOnly)
        viewModel.stepEditorIntensity(up = true)
        runCurrent()

        assertFalse(state.editor!!.canSave)
        viewModel.saveEditor()
        runCurrent()
        assertTrue(training.updated.isEmpty())
    }

    // Adding

    @Test
    fun `Add set carries this session's last intensity`() = runTest {
        open(SessionMachine(treadmill, listOf(withIntensity)))

        viewModel.addSet(treadmill.id)
        runCurrent()

        assertEquals(listOf(Triple(session.id, treadmill.id, CardioDraft(30, 250, 3200, 0.79))), history.cardioAdded)
    }
}
