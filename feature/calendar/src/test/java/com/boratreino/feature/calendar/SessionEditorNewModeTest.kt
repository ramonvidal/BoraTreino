package com.boratreino.feature.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The session editor for a session that does not exist yet: nothing is written until its first set, the first set
 * creates the session, and deleting the last set goes back to that state with the same date, routine and extra flag.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SessionEditorNewModeTest {
    private val legPress = Machine(1L, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, archived = false)
    private val treadmill = Machine(30L, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)
    private val routine = Routine(5L, 1L, "Treino A", "A", 0, emptySet())
    private val day = LocalDate.of(2026, 11, 2)
    private val clock = Clock.fixed(Instant.parse("2026-11-02T18:05:00Z"), ZoneOffset.UTC)

    private lateinit var store: NewSessionStore
    private lateinit var viewModel: SessionEditorViewModel
    private val events = mutableListOf<SessionEditorEvent>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = NewSessionStore(listOf(legPress, treadmill))
        events.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state: SessionEditorUiState get() = viewModel.state.value

    /** The editor opened from the Calendar for [date]: the route carries the date and no session id. */
    private fun TestScope.openNewSession(date: String = "2026-11-02") {
        viewModel = SessionEditorViewModel(
            SavedStateHandle(mapOf(DATE_ARG to date)),
            NewSessionHistory(store),
            NewSessionTraining(store),
            PlanRoutines(listOf(routine)),
            NewSessionMachines(listOf(legPress, treadmill)),
            clock,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()
    }

    private fun lastRemoved(): com.boratreino.core.data.RemovedSets =
        (events.last() as SessionEditorEvent.SetRemoved).removed

    @Test
    fun `opening a new session writes nothing and shows an empty session on the date`() = runTest {
        openNewSession()

        assertEquals(emptyList<String>(), store.writes)
        assertNull(store.detail.value)
        assertEquals(0L, state.detail?.session?.id)
        assertEquals(day, state.date)
        assertTrue(state.detail?.machines.orEmpty().isEmpty())
    }

    @Test
    fun `the routine and the extra flag are remembered without writing anything`() = runTest {
        openNewSession()

        viewModel.chooseRoutine(routine.id)
        viewModel.setExtra(true)
        runCurrent()

        assertEquals(emptyList<String>(), store.writes)
        assertEquals(routine.id, state.detail?.session?.routineId)
        assertTrue(state.detail?.session?.isExtra == true)
    }

    @Test
    fun `an extra flag without a routine is not kept`() = runTest {
        openNewSession()

        viewModel.setExtra(true)
        runCurrent()

        assertEquals(emptyList<String>(), store.writes)
        assertFalse(state.detail?.session?.isExtra == true)
    }

    @Test
    fun `the first set creates the session on the day and the editor moves to its id`() = runTest {
        openNewSession()
        viewModel.chooseRoutine(routine.id)
        viewModel.setExtra(true)

        viewModel.addMachine(legPress.id)
        runCurrent()

        assertEquals(listOf("addSessionWithSet"), store.writes)
        val first = store.firstWrites.single()
        assertEquals(day, first.date)
        assertEquals(routine.id, first.routineId)
        assertTrue(first.isExtra)
        assertEquals(legPress.id, first.machineId)
        assertFalse(first.cardio)
        assertEquals(NEW_SESSION_ID, state.detail?.session?.id)
        assertEquals(listOf<SessionEditorEvent>(SessionEditorEvent.SetAdded), events)
    }

    @Test
    fun `a set added after the first one goes into the same session`() = runTest {
        openNewSession()
        viewModel.chooseRoutine(routine.id)
        viewModel.addMachine(legPress.id)
        runCurrent()

        viewModel.addSet(legPress.id)
        runCurrent()

        assertEquals(listOf("addSessionWithSet", "addSet"), store.writes)
        assertEquals(2, state.detail?.machines?.single()?.sets?.size)
    }

    @Test
    fun `the first cardio set creates a session with that cardio set`() = runTest {
        openNewSession()

        viewModel.addMachine(treadmill.id)
        runCurrent()

        assertEquals(listOf("addSessionWithCardio"), store.writes)
        assertTrue(store.firstWrites.single().cardio)
    }

    @Test
    fun `the first set is prefilled from the machine's last session before the day`() = runTest {
        store.lastTimeBeforeDate = listOf(WorkoutSet(9L, 40L, legPress.id, 0, 100.0, 12, null, null, null, Instant.EPOCH))
        openNewSession()

        viewModel.addMachine(legPress.id)
        runCurrent()

        assertEquals(listOf(day to legPress.id), store.lastTimeCalls)
        val first = store.firstWrites.single()
        assertEquals(100.0, first.weightKg)
        assertEquals(12, first.reps)
    }

    @Test
    fun `deleting a set that is not the last keeps the session`() = runTest {
        openNewSession()
        viewModel.chooseRoutine(routine.id)
        viewModel.addMachine(legPress.id)
        viewModel.addSet(legPress.id)
        runCurrent()
        val first = store.detail.value!!.machines.single().sets.first()

        viewModel.openSet(first)
        viewModel.deleteEditor()
        runCurrent()

        assertEquals(NEW_SESSION_ID, state.detail?.session?.id)
        assertEquals(1, state.detail?.machines?.single()?.sets?.size)
    }

    @Test
    fun `deleting the last set goes back to a new session with the same date, routine and extra flag`() = runTest {
        openNewSession()
        viewModel.chooseRoutine(routine.id)
        viewModel.setExtra(true)
        viewModel.addMachine(legPress.id)
        runCurrent()
        val set = store.detail.value!!.machines.single().sets.single()

        viewModel.openSet(set)
        viewModel.deleteEditor()
        runCurrent()

        val removed = lastRemoved()
        assertEquals(listOf(set), removed.sets)
        assertEquals(NEW_SESSION_ID, removed.session?.id)
        assertNull(store.detail.value)
        assertEquals(0L, state.detail?.session?.id)
        assertEquals(day, state.date)
        assertEquals(routine.id, state.detail?.session?.routineId)
        assertTrue(state.detail?.session?.isExtra == true)
    }

    @Test
    fun `undo of that delete brings the same session back with its id`() = runTest {
        openNewSession()
        viewModel.chooseRoutine(routine.id)
        viewModel.addMachine(legPress.id)
        runCurrent()
        val set = store.detail.value!!.machines.single().sets.single()
        viewModel.openSet(set)
        viewModel.deleteEditor()
        runCurrent()

        viewModel.restoreSets(lastRemoved())
        runCurrent()

        assertEquals("restoreSets", store.writes.last())
        assertEquals(NEW_SESSION_ID, state.detail?.session?.id)
        assertEquals(listOf<WorkoutSet>(set), state.detail?.machines?.single()?.sets)
    }
}
