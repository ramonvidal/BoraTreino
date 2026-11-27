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
import com.boratreino.core.testing.StubHistoryRepository
import com.boratreino.core.testing.StubMachineRepository
import com.boratreino.core.testing.StubRoutineRepository
import com.boratreino.core.testing.StubTrainingRepository
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
import com.boratreino.core.data.RemovedSets

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

    private class FakeHistory : StubHistoryRepository() {
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
        override fun observeClosedSessionExports(sinceDays: Int): Flow<List<HealthExport>> = emptyFlow()
        override fun observeSessionIds(): Flow<Set<Long>> = emptyFlow()
    }

    private class FakeTraining : StubTrainingRepository() {
        val updated = mutableListOf<WorkoutSet>()
        val deleted = mutableListOf<WorkoutSet>()
        val restored = mutableListOf<WorkoutSet>()

        override suspend fun updateSet(set: WorkoutSet) {
            updated += set
        }

        override suspend fun deleteSet(set: WorkoutSet): RemovedSets {
            deleted += set
            return RemovedSets(listOf(set), null)
        }

        override suspend fun restoreSets(removed: RemovedSets) {
            restored += removed.sets
        }

        override fun observeOpenSession(): Flow<OpenSession?> = emptyFlow()
        override fun observeTodayPlan(): Flow<TodayPlan> = emptyFlow()
        override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = emptyFlow()
        override fun observeRepeatOffer(): Flow<RepeatOffer?> = emptyFlow()
    }

    private class FakeRoutines : StubRoutineRepository() {
        override suspend fun saveRoutine(routine: com.boratreino.core.model.Routine, items: List<com.boratreino.core.model.RoutineItem>): Long = 0L
    }

    private class FakeMachines(private val all: List<Machine>) : StubMachineRepository(all) {
        override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = flowOf(all)
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
