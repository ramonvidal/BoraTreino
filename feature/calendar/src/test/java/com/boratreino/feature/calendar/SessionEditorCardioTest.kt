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

/** The session editor's view model on cardio sets: edit steppers, canSave, adding and saving. Works from the Phase 7 contract. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SessionEditorCardioTest {
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
        override suspend fun addSession(date: LocalDate, routineId: Long?): Long = 0L
        override suspend fun setSessionRoutine(sessionId: Long, routineId: Long?) = Unit
        override fun observeClosedSessionExports(sinceDays: Int): Flow<List<HealthExport>> = emptyFlow()
        override fun observeSessionIds(): Flow<Set<Long>> = emptyFlow()
        override suspend fun deleteSession(sessionId: Long): SessionDetail? = null
        override suspend fun restoreSession(detail: SessionDetail) = Unit
        override suspend fun repeatLastSession(sessionId: Long, mode: RepeatMode): List<WorkoutSet> = emptyList()
    }

    private class FakeTraining : TrainingRepository {
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
        override suspend fun reopenSession(sessionId: Long) = Unit
        override suspend fun skipMachineToday(machineId: Long) = Unit
        override suspend fun unskipMachineToday(machineId: Long) = Unit
        override fun observeRepeatOffer(): Flow<RepeatOffer?> = emptyFlow()
        override suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome? = null
        override suspend fun logWarmups(machineId: Long, sets: List<SetDraft>): RepeatOutcome = RepeatOutcome(0, false, emptyList(), 1)
        override suspend fun undoRepeat(outcome: RepeatOutcome) = Unit
    }

    private class FakeRoutines : RoutineRepository {
        override fun observePlan(): Flow<RoutinePlanWithRoutines> =
            flowOf(RoutinePlanWithRoutines(RoutinePlan(1L, ScheduleMode.IN_TURN), emptyList()))

        override fun observeRoutine(id: Long): Flow<RoutineWithItems?> = flowOf(null)
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
        override suspend fun restoreItem(item: com.boratreino.core.model.RoutineItem) = Unit
    }

    private class FakeMachines(private val all: List<Machine>) : MachineRepository {
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

    // SetEdit on its own

    @Test
    fun `a cardio SetEdit exposes its draft and saves only with minutes or calories`() {
        fun edit(minutes: Int, calories: Int, distanceM: Int) =
            SetEdit(first, treadmill, 1, 0.0, 10, minutes, calories, distanceM)

        assertTrue(edit(0, 0, 0).isCardio)
        assertEquals(CardioDraft(5, 10, 100), edit(5, 10, 100).cardioDraft)
        assertFalse(edit(0, 0, 0).canSave)
        assertFalse(edit(0, 0, 500).canSave)
        assertTrue(edit(1, 0, 0).canSave)
        assertTrue(edit(0, 10, 0).canSave)
    }

    @Test
    fun `a weight SetEdit can always be saved and is not cardio`() {
        val edit = SetEdit(weightSet, legPress, 1, 120.0, 12)

        assertFalse(edit.isCardio)
        assertTrue(edit.canSave)
    }

    // Opening and stepping

    @Test
    fun `opening a cardio set fills minutes, calories and distance and its row number`() = runTest {
        open(cardioEntry)

        viewModel.openSet(second)
        runCurrent()

        val e = state.editor!!
        assertEquals(treadmill, e.machine)
        assertEquals(2, e.number)
        assertEquals(10, e.minutes)
        assertEquals(0, e.calories)
        assertEquals(0, e.distanceM)
        assertTrue(e.isCardio)
    }

    @Test
    fun `the cardio steppers move minutes by 1, calories by 10 and distance by 100 m`() = runTest {
        open(cardioEntry)
        viewModel.openSet(first)

        viewModel.stepEditorMinutes(up = true)
        viewModel.stepEditorCalories(up = false)
        viewModel.stepEditorDistance(up = true)
        runCurrent()

        val e = state.editor!!
        assertEquals(31, e.minutes)
        assertEquals(240, e.calories)
        assertEquals(3300, e.distanceM)
    }

    @Test
    fun `the cardio steppers stop at zero and at their maximums`() = runTest {
        open(cardioEntry)
        viewModel.openSet(second)

        repeat(15) { viewModel.stepEditorMinutes(up = false) }
        viewModel.stepEditorCalories(up = false)
        viewModel.stepEditorDistance(up = false)
        assertEquals(CardioDraft(0, 0, 0), state.editor!!.cardioDraft)

        repeat(700) { viewModel.stepEditorMinutes(up = true) }
        assertEquals(CardioRules.MAX_MINUTES, state.editor!!.minutes)
    }

    @Test
    fun `canSave turns false when minutes and calories reach zero`() = runTest {
        open(cardioEntry)
        viewModel.openSet(second)
        assertTrue(state.editor!!.canSave)

        repeat(10) { viewModel.stepEditorMinutes(up = false) }

        assertFalse(state.editor!!.canSave)

        viewModel.stepEditorCalories(up = true)
        assertTrue(state.editor!!.canSave)
    }

    // Saving, deleting

    @Test
    fun `saving a cardio set writes it through updateSet with the same id, place and time`() = runTest {
        open(cardioEntry)
        viewModel.openSet(first)
        viewModel.stepEditorMinutes(up = true)
        viewModel.stepEditorCalories(up = true)
        viewModel.stepEditorDistance(up = false)

        viewModel.saveEditor()
        runCurrent()

        val expected = CardioRules.toSet(
            first.id, first.sessionId, first.machineId, first.position, CardioDraft(31, 260, 3100), first.loggedAt,
        )
        assertEquals(listOf(expected), training.updated)
        assertNull(state.editor)
        // A cardio set keeps no weight or reps.
        assertNull(training.updated.single().weightKg)
        assertNull(training.updated.single().reps)
    }

    @Test
    fun `a distance of zero is saved as no distance`() = runTest {
        open(cardioEntry)
        viewModel.openSet(first)
        repeat(40) { viewModel.stepEditorDistance(up = false) }

        viewModel.saveEditor()
        runCurrent()

        assertNull(training.updated.single().distanceM)
        assertEquals(30 * 60, training.updated.single().seconds)
    }

    @Test
    fun `Save does nothing while nothing is logged and the sheet stays open`() = runTest {
        open(cardioEntry)
        viewModel.openSet(second)
        repeat(10) { viewModel.stepEditorMinutes(up = false) }

        viewModel.saveEditor()
        runCurrent()

        assertTrue(training.updated.isEmpty())
        assertNotNull(state.editor)
    }

    @Test
    fun `saving a weight set still writes its weight and reps`() = runTest {
        open(weightEntry)
        viewModel.openSet(weightSet)
        viewModel.stepEditorReps(up = true)

        viewModel.saveEditor()
        runCurrent()

        assertEquals(listOf(weightSet.copy(reps = 13)), training.updated)
    }

    @Test
    fun `deleting a cardio set removes it and offers undo`() = runTest {
        open(cardioEntry)
        viewModel.openSet(second)

        viewModel.deleteEditor()
        runCurrent()

        assertEquals(listOf(second), training.deleted)
        assertEquals(listOf<SessionEditorEvent>(SessionEditorEvent.SetRemoved(second)), events)
        assertNull(state.editor)

        viewModel.restoreSet(second)
        runCurrent()
        assertEquals(listOf(second), training.restored)
    }

    // Adding sets

    @Test
    fun `Add set on a cardio machine uses addCardioSet with the session's last set`() = runTest {
        open(cardioEntry)

        viewModel.addSet(treadmill.id)
        runCurrent()

        assertEquals(listOf(Triple(session.id, treadmill.id, CardioDraft(10, 0, 0))), history.cardioAdded)
        assertTrue(history.weightAdded.isEmpty())
        assertEquals(listOf<SessionEditorEvent>(SessionEditorEvent.SetAdded), events)
    }

    @Test
    fun `Add set on a weight machine still uses addSet`() = runTest {
        open(weightEntry)

        viewModel.addSet(legPress.id)
        runCurrent()

        assertEquals(1, history.weightAdded.size)
        assertEquals(SetDraft(120.0, 12), history.weightAdded.single().third)
        assertTrue(history.cardioAdded.isEmpty())
    }

    @Test
    fun `adding a cardio machine prefills from its last time`() = runTest {
        open(weightEntry)
        history.lastTime = listOf(CardioRules.toSet(77, 5, treadmill.id, 0, CardioDraft(25, 200, 4000), Instant.parse("2026-10-20T18:00:00Z")))
        viewModel.openPicker()

        viewModel.addMachine(treadmill.id)
        runCurrent()

        assertEquals(listOf(Triple(session.id, treadmill.id, CardioDraft(25, 200, 4000))), history.cardioAdded)
        assertFalse(state.picking)
        assertEquals(listOf<SessionEditorEvent>(SessionEditorEvent.SetAdded), events)
    }

    @Test
    fun `adding a cardio machine with no last time starts at 20 minutes`() = runTest {
        open(weightEntry)

        viewModel.addMachine(treadmill.id)
        runCurrent()

        assertEquals(listOf(Triple(session.id, treadmill.id, CardioDraft(20, 0, 0))), history.cardioAdded)
    }
}
