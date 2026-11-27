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
 * The session editor's number pad on a cardio set: each cardio value opens the pad with its text, confirming types
 * into the editor (saved only by Save), and a set needs minutes or calories to be saved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SessionEditorCardioPadViewModelTest {
    private val treadmill = Machine(30L, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)

    private val started = Instant.parse("2026-11-02T18:05:00Z")
    private val session = Session(id = 20L, startedAt = started, endedAt = Instant.parse("2026-11-02T19:10:00Z"), routineId = null, note = null)

    /** A cardio set with every value: 30 min, 250 kcal, 3.2 km and intensity 0.79. */
    private val bikeSet = CardioRules.toSet(
        id = 1L,
        sessionId = session.id,
        machineId = treadmill.id,
        position = 0,
        draft = CardioDraft(minutes = 30, calories = 250, distanceM = 3_200, intensity = 0.79),
        loggedAt = started.plusSeconds(600),
    )

    private class FakeHistory : StubHistoryRepository() {
        val detail = MutableStateFlow<SessionDetail?>(null)

        override fun observeSession(sessionId: Long): Flow<SessionDetail?> = detail
        override fun observeMonth(month: YearMonth): Flow<Map<LocalDate, List<SessionInfo>>> = emptyFlow()
        override fun observeDay(date: LocalDate): Flow<List<SessionDetail>> = emptyFlow()
        override fun observeTrainingDates(): Flow<Set<LocalDate>> = emptyFlow()
        override fun observeClosedSessionExports(sinceDays: Int): Flow<List<HealthExport>> = emptyFlow()
        override fun observeSessionIds(): Flow<Set<Long>> = emptyFlow()
    }

    private class FakeTraining : StubTrainingRepository() {
        val updated = mutableListOf<WorkoutSet>()

        override suspend fun updateSet(set: WorkoutSet) {
            updated += set
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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        history = FakeHistory()
        training = FakeTraining()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state: SessionEditorUiState get() = viewModel.state.value

    /** Opens the editor on the session with [bikeSet] and starts observing it; the set's editor is opened by the caller. */
    private fun TestScope.open() {
        history.detail.value = SessionDetail(session, null, listOf(SessionMachine(treadmill, listOf(bikeSet))))
        viewModel = SessionEditorViewModel(
            SavedStateHandle(mapOf(SESSION_ID_ARG to session.id)),
            history,
            training,
            FakeRoutines(),
            FakeMachines(listOf(treadmill)),
            Clock.fixed(started, ZoneOffset.UTC),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    private fun TestScope.openEditor() {
        open()
        viewModel.openSet(bikeSet)
        runCurrent()
    }

    // The pad's starting text

    @Test
    fun `each cardio value opens the pad with the text the editor shows`() = runTest {
        openEditor()
        val expected = mapOf(
            PadTarget.EDIT_MINUTES to "30",
            PadTarget.EDIT_CALORIES to "250",
            PadTarget.EDIT_DISTANCE_KM to "3.2",
            PadTarget.EDIT_INTENSITY to "0.79",
        )

        expected.forEach { (target, text) ->
            viewModel.openPad(target)
            assertEquals("$target", NumberPad(target, text), state.pad)
        }
    }

    @Test
    fun `a cardio value that is not set opens the pad empty`() = runTest {
        open()
        viewModel.openSet(bikeSet.copy(intensity = null))
        runCurrent()

        viewModel.openPad(PadTarget.EDIT_INTENSITY)
        assertEquals(NumberPad(PadTarget.EDIT_INTENSITY, ""), state.pad)
    }

    // Confirming

    @Test
    fun `confirming minutes sets the editor's minutes and closes the pad`() = runTest {
        openEditor()
        viewModel.openPad(PadTarget.EDIT_MINUTES)

        viewModel.confirmPad("45")

        assertEquals(45, state.editor?.minutes)
        assertNull(state.pad)
    }

    @Test
    fun `confirming distance with a comma stores metres`() = runTest {
        openEditor()
        viewModel.openPad(PadTarget.EDIT_DISTANCE_KM)

        viewModel.confirmPad("12,5")

        assertEquals(12_500, state.editor?.distanceM)
    }

    @Test
    fun `confirming a value keeps the other values of the editor`() = runTest {
        openEditor()
        viewModel.openPad(PadTarget.EDIT_INTENSITY)

        viewModel.confirmPad("0.15")

        val editor = state.editor!!
        assertEquals(0.15, editor.intensity!!, 0.0)
        assertEquals(30, editor.minutes)
        assertEquals(250, editor.calories)
        assertEquals(3_200, editor.distanceM)
    }

    @Test
    fun `a blank text clears an optional value and a blank calorie count goes to 0`() = runTest {
        openEditor()
        viewModel.openPad(PadTarget.EDIT_INTENSITY)
        viewModel.confirmPad("")
        viewModel.openPad(PadTarget.EDIT_CALORIES)
        viewModel.confirmPad("")

        assertNull(state.editor?.intensity)
        assertEquals(0, state.editor?.calories)
    }

    @Test
    fun `a text that is not a number is ignored`() = runTest {
        openEditor()
        val before = state.editor
        viewModel.openPad(PadTarget.EDIT_INTENSITY)

        viewModel.confirmPad("2,4,5")

        assertEquals(before, state.editor)
    }

    @Test
    fun `typed values are clamped to their ranges`() = runTest {
        openEditor()
        viewModel.openPad(PadTarget.EDIT_INTENSITY)
        viewModel.confirmPad("150")
        assertEquals(100.0, state.editor?.intensity!!, 0.0)

        viewModel.openPad(PadTarget.EDIT_INTENSITY)
        viewModel.confirmPad("0.785")
        assertEquals(0.79, state.editor?.intensity!!, 0.0)

        viewModel.openPad(PadTarget.EDIT_MINUTES)
        viewModel.confirmPad("700")
        assertEquals(CardioRules.MAX_MINUTES, state.editor?.minutes)
    }

    @Test
    fun `confirming a value changes nothing until Save writes the set`() = runTest {
        openEditor()
        viewModel.openPad(PadTarget.EDIT_MINUTES)
        viewModel.confirmPad("45")
        runCurrent()

        assertTrue(training.updated.isEmpty())

        viewModel.saveEditor()
        runCurrent()

        assertEquals(1, training.updated.size)
        assertEquals(45 * 60, training.updated.single().seconds)
        assertEquals(250, training.updated.single().calories)
    }

    // Dismissing and saving

    @Test
    fun `dismissing the pad closes it and keeps the editor as it was`() = runTest {
        openEditor()
        val before = state.editor
        viewModel.openPad(PadTarget.EDIT_CALORIES)

        viewModel.dismissPad()

        assertNull(state.pad)
        assertEquals(before, state.editor)
    }

    @Test
    fun `a set with neither minutes nor calories is not saved`() = runTest {
        openEditor()
        viewModel.openPad(PadTarget.EDIT_MINUTES)
        viewModel.confirmPad("")
        viewModel.openPad(PadTarget.EDIT_CALORIES)
        viewModel.confirmPad("")
        viewModel.openPad(PadTarget.EDIT_DISTANCE_KM)
        viewModel.confirmPad("5")

        viewModel.saveEditor()
        runCurrent()

        assertTrue(training.updated.isEmpty())
    }
}
