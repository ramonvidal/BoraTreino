package com.boratreino.feature.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.Routine
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.testing.TestClock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The days the rotation puts on the Agenda: today and the 7 days after it, one routine per day, on the training
 * days, with a manual plan winning its day. Today is Thursday 5 November 2026; the view model reads the routines
 * in the order the repository gives them, and the last rotation routine from the repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CalendarProjectionViewModelTest {
    private val clock = TestClock(Instant.parse("2026-11-05T12:00:00Z"))
    private val today = LocalDate.of(2026, 11, 5)

    private val routineA = Routine(1L, 1L, "Treino A", "A", 0, emptySet())
    private val routineB = Routine(2L, 1L, "Treino B", "B", 1, emptySet())
    private val routineC = Routine(3L, 1L, "Treino C", "C", 2, emptySet())

    private lateinit var routines: PlanRoutines
    private lateinit var history: EnergyHistory
    private lateinit var viewModel: CalendarViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        routines = PlanRoutines(listOf(routineA, routineB, routineC))
        history = EnergyHistory()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.start() {
        viewModel = CalendarViewModel(history, routines, FakeTrainingSettings(), FakeBodySettings(), clock)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    private fun day(date: Int) = LocalDate.of(2026, 11, date)

    /** The projected days from today on, as (day of the month, routine letter), in date order. */
    private fun letters(): List<Pair<Int, String>> = (0L..7L).mapNotNull { offset ->
        val date = today.plusDays(offset)
        viewModel.state.value.projected[date]?.let { date.dayOfMonth to it.shortName }
    }

    private fun session(routine: Routine?, finished: Boolean, extra: Boolean = false) = SessionDetail(
        session = Session(
            id = 1L,
            startedAt = Instant.parse("2026-11-05T09:00:00Z"),
            endedAt = if (finished) Instant.parse("2026-11-05T10:00:00Z") else null,
            routineId = routine?.id,
            note = null,
            isExtra = extra,
        ),
        routine = routine,
        machines = emptyList(),
    )

    @Test
    fun `the projection covers today and the 7 days after it, one routine each`() = runTest {
        routines.lastRoutineFlow.value = routineA.id
        start()

        assertEquals(
            listOf(5 to "B", 6 to "C", 7 to "A", 8 to "B", 9 to "C", 10 to "A", 11 to "B", 12 to "C"),
            letters(),
        )
    }

    @Test
    fun `each projected day carries the routine's id, short name and name`() = runTest {
        routines.lastRoutineFlow.value = routineA.id
        start()

        assertEquals(
            ProjectedUi(routineB.id, "B", "Treino B"),
            viewModel.state.value.projected[today],
        )
    }

    @Test
    fun `without a last routine the rotation starts at the first routine`() = runTest {
        start()

        assertEquals(
            listOf(5 to "A", 6 to "B", 7 to "C", 8 to "A", 9 to "B", 10 to "C", 11 to "A", 12 to "B"),
            letters(),
        )
    }

    @Test
    fun `only the training days get a routine, and the rotation moves on only on those`() = runTest {
        routines.lastRoutineFlow.value = routineA.id
        routines.setTrainingDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        start()

        // Thursday is not a training day; Friday is due B, then Monday C, then Wednesday A.
        assertEquals(listOf(6 to "B", 9 to "C", 11 to "A"), letters())
    }

    @Test
    fun `a fixed-day routine comes after that day's rotation routine and does not move the rotation`() = runTest {
        val fixedC = Routine(3L, 1L, "Treino C", "C", 2, setOf(DayOfWeek.FRIDAY))
        routines = PlanRoutines(listOf(routineA, routineB, fixedC))
        routines.setTrainingDays(setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        start()

        // Friday: the rotation's B first, then the fixed C, which is only in projectedMore.
        assertEquals(listOf(5 to "A", 6 to "B", 7 to "A", 8 to "B", 12 to "A"), letters())
        assertEquals(listOf("C"), viewModel.state.value.projectedMore[day(6)]?.map { it.shortName })
        assertEquals(null, viewModel.state.value.projectedMore[today])
    }

    @Test
    fun `a fixed-day routine trained today leaves the projection and the rotation routine of the day stays`() = runTest {
        val fixedThursday = Routine(3L, 1L, "Treino C", "C", 2, setOf(DayOfWeek.THURSDAY))
        routines = PlanRoutines(listOf(routineA, routineB, fixedThursday))
        routines.lastRoutineFlow.value = routineA.id
        routines.setTrainingDays(setOf(DayOfWeek.THURSDAY))
        history.days.value = mapOf(today to listOf(session(fixedThursday, finished = true)))
        start()

        assertEquals(ProjectedUi(routineB.id, "B", "Treino B"), viewModel.state.value.projected[today])
        assertEquals(null, viewModel.state.value.projectedMore[today])
    }

    @Test
    fun `a manual plan takes its day, gets no suggestion, and the rotation goes on from it`() = runTest {
        routines.plannedFlow.value = listOf(PlannedWorkout(day(6), routineC.id))
        start()

        assertFalse(viewModel.state.value.projected.containsKey(day(6)))
        assertEquals(
            listOf(5 to "A", 7 to "A", 8 to "B", 9 to "C", 10 to "A", 11 to "B", 12 to "C"),
            letters(),
        )
    }

    @Test
    fun `a manual plan of a fixed-day routine does not hide the rotation and does not move it`() = runTest {
        // C has fixed days, so it is not in the rotation: planning it on Friday gets no entry, and the rotation
        // still gives Friday its A. The pointer moves as if C were not planned.
        val fixedC = Routine(3L, 1L, "Treino C", "C", 2, setOf(DayOfWeek.MONDAY))
        routines = PlanRoutines(listOf(routineA, routineB, fixedC))
        routines.lastRoutineFlow.value = routineA.id
        routines.setTrainingDays(DayOfWeek.entries.toSet())
        routines.plannedFlow.value = listOf(PlannedWorkout(day(6), fixedC.id))
        start()

        assertEquals("A", viewModel.state.value.projected[day(6)]?.shortName)
        assertEquals(null, viewModel.state.value.projectedMore[day(6)])
        // Monday's own fixed C comes after the rotation's B.
        assertEquals(listOf("C"), viewModel.state.value.projectedMore[day(9)]?.map { it.shortName })
        assertEquals(listOf(5 to "B", 6 to "A", 7 to "B", 8 to "A", 9 to "B", 10 to "A", 11 to "B", 12 to "A"), letters())
    }

    @Test
    fun `once today is trained its routine leaves the projection and the rotation goes on from the last one`() = runTest {
        routines.lastRoutineFlow.value = routineA.id
        history.days.value = mapOf(today to listOf(session(routineA, finished = true)))
        start()

        assertFalse(viewModel.state.value.projected.containsKey(today))
        assertEquals(
            listOf(6 to "B", 7 to "C", 8 to "A", 9 to "B", 10 to "C", 11 to "A", 12 to "B"),
            letters(),
        )
    }

    @Test
    fun `an open session today is not a training yet, so today keeps its routine`() = runTest {
        routines.lastRoutineFlow.value = routineA.id
        history.days.value = mapOf(today to listOf(session(routineA, finished = false)))
        start()

        assertTrue(viewModel.state.value.projected.containsKey(today))
        assertEquals(
            listOf(5 to "B", 6 to "C", 7 to "A", 8 to "B", 9 to "C", 10 to "A", 11 to "B", 12 to "C"),
            letters(),
        )
    }

    @Test
    fun `an extra workout today counts as trained, but the rotation follows the last rotation routine`() = runTest {
        // The repository ignores extra workouts when it gives the last rotation routine: here A, not the extra B.
        routines.lastRoutineFlow.value = routineA.id
        history.days.value = mapOf(today to listOf(session(routineB, finished = true, extra = true)))
        start()

        assertFalse(viewModel.state.value.projected.containsKey(today))
        assertEquals(
            listOf(6 to "B", 7 to "C", 8 to "A", 9 to "B", 10 to "C", 11 to "A", 12 to "B"),
            letters(),
        )
    }

    @Test
    fun `the projection is empty without routines`() = runTest {
        routines = PlanRoutines(emptyList())
        start()

        assertTrue(viewModel.state.value.projected.isEmpty())
    }
}
