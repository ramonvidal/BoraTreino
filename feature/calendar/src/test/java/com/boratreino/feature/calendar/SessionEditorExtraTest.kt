package com.boratreino.feature.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RepeatOffer
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.HealthExport
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.LoggedSet
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineUpdate
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionInfo
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.TrainingSummary
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
 * The extra-workout flag in the session editor: the switch writes it for the session shown, and choosing No routine
 * clears it, as the switch is hidden without a routine. Written from the editor's public calls and the history writes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SessionEditorExtraTest {
    private val started = Instant.parse("2026-11-02T18:05:00Z")
    private val routineA = Routine(1L, 1L, "Treino A", "A", 0, emptySet())
    private val routineB = Routine(2L, 1L, "Treino B", "B", 1, emptySet())
    private val sessionId = 20L

    /** The history writes the editor makes, in order, and the session it shows. */
    private class ExtraHistory : HistoryRepository {
        val detail = MutableStateFlow<SessionDetail?>(null)
        val routineCalls = mutableListOf<Pair<Long, Long?>>()
        val extraCalls = mutableListOf<Pair<Long, Boolean>>()

        override fun observeSession(sessionId: Long): Flow<SessionDetail?> = detail
        override suspend fun setSessionRoutine(sessionId: Long, routineId: Long?) {
            routineCalls += sessionId to routineId
        }
        override suspend fun setSessionExtra(sessionId: Long, extra: Boolean) {
            extraCalls += sessionId to extra
        }

        override fun observeMonth(month: YearMonth): Flow<Map<LocalDate, List<SessionInfo>>> = emptyFlow()
        override fun observeDay(date: LocalDate): Flow<List<SessionDetail>> = emptyFlow()
        override fun observeTrainingDates(): Flow<Set<LocalDate>> = emptyFlow()
        override suspend fun addSession(date: LocalDate, routineId: Long?, isExtra: Boolean): Long = 0L
        override suspend fun addSet(sessionId: Long, machineId: Long, draft: SetDraft): WorkoutSet = error("not used")
        override suspend fun addCardioSet(sessionId: Long, machineId: Long, draft: CardioDraft): WorkoutSet = error("not used")
        override suspend fun lastTimeBefore(sessionId: Long, machineId: Long): List<WorkoutSet> = emptyList()
        override fun observeClosedSessionExports(sinceDays: Int): Flow<List<HealthExport>> = emptyFlow()
        override fun observeSessionIds(): Flow<Set<Long>> = emptyFlow()
        override suspend fun deleteSession(sessionId: Long): SessionDetail? = null
        override suspend fun restoreSession(detail: SessionDetail) = Unit
        override suspend fun repeatLastSession(sessionId: Long, mode: RepeatMode): List<WorkoutSet> = emptyList()
    }

    /** Training is not used by the session editor's extra switch: every call is a no-op. */
    private class QuietTraining : TrainingRepository {
        override suspend fun routineUpdateOffer(sessionId: Long): RoutineUpdate? = null
        override suspend fun trainingSummary(sessionId: Long): TrainingSummary? = null
        override suspend fun sessionRoutineId(sessionId: Long): Long? = null
        override suspend fun updateSet(set: WorkoutSet) = Unit
        override suspend fun deleteSet(set: WorkoutSet) = Unit
        override suspend fun restoreSet(set: WorkoutSet) = Unit
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

    /** No machines: the switch does not depend on them. */
    private class NoMachines : MachineRepository {
        override suspend fun addFromLibrary(exercise: LibraryExercise, portuguese: Boolean): Long = error("not used")
        override fun observeLibraryIdsInUse(): Flow<Set<String>> = flowOf(emptySet())
        override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = flowOf(emptyList())
        override fun observeMachine(id: Long): Flow<Machine?> = flowOf(null)
        override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = flowOf(emptyList())
        override fun observeStartersNotAdded(): Flow<List<StarterMachine>> = flowOf(emptyList())
        override suspend fun save(machine: Machine): Long = machine.id
        override suspend fun usage(machineId: Long): MachineUsage = error("not used")
        override suspend fun delete(machineId: Long): Boolean = false
        override suspend fun addStarter(starter: StarterMachine, portuguese: Boolean): Long = 0L
        override suspend fun addStarters(starters: List<StarterMachine>, portuguese: Boolean): List<Long> = emptyList()
        override suspend fun setArchived(id: Long, archived: Boolean) = Unit
    }

    private lateinit var history: ExtraHistory
    private lateinit var viewModel: SessionEditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        history = ExtraHistory()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state: SessionEditorUiState get() = viewModel.state.value

    /** A closed session of [routine] (none when null), flagged extra or not, shown by the editor. */
    private fun TestScope.open(routine: Routine?, extra: Boolean) {
        history.detail.value = SessionDetail(
            session = Session(
                id = sessionId,
                startedAt = started,
                endedAt = started.plusSeconds(3600),
                routineId = routine?.id,
                note = null,
                isExtra = extra,
            ),
            routine = routine,
            machines = emptyList(),
        )
        viewModel = SessionEditorViewModel(
            SavedStateHandle(mapOf(SESSION_ID_ARG to sessionId)),
            history,
            QuietTraining(),
            PlanRoutines(listOf(routineA, routineB)),
            NoMachines(),
            Clock.fixed(started, ZoneOffset.UTC),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    @Test
    fun `the editor shows the extra flag of the session`() = runTest {
        open(routineB, extra = true)

        assertTrue(state.detail!!.session.isExtra)
    }

    @Test
    fun `turning the switch on writes the extra flag of the session shown`() = runTest {
        open(routineB, extra = false)

        viewModel.setExtra(true)
        runCurrent()

        assertEquals(listOf(sessionId to true), history.extraCalls)
        assertTrue(history.routineCalls.isEmpty())
    }

    @Test
    fun `turning the switch off writes false for the session shown`() = runTest {
        open(routineB, extra = true)

        viewModel.setExtra(false)
        runCurrent()

        assertEquals(listOf(sessionId to false), history.extraCalls)
    }

    @Test
    fun `choosing No routine clears the extra flag of an extra workout`() = runTest {
        open(routineB, extra = true)

        viewModel.chooseRoutine(null)
        runCurrent()

        assertEquals(listOf(sessionId to null), history.routineCalls)
        assertEquals(listOf(sessionId to false), history.extraCalls)
    }

    @Test
    fun `choosing No routine on a workout that is not extra writes no extra change`() = runTest {
        open(routineB, extra = false)

        viewModel.chooseRoutine(null)
        runCurrent()

        assertEquals(listOf(sessionId to null), history.routineCalls)
        assertTrue(history.extraCalls.isEmpty())
    }

    @Test
    fun `choosing a routine keeps the extra flag as it is`() = runTest {
        open(routineB, extra = true)

        viewModel.chooseRoutine(routineA.id)
        runCurrent()

        assertEquals(listOf(sessionId to routineA.id), history.routineCalls)
        assertTrue(history.extraCalls.isEmpty())
    }
}
