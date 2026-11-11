package com.boratreino.feature.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.Routine
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The "This week" preview of the schedule card: the trainings still to come this week, one routine after the other
 * in turn, a planned routine taking its day. Today is Thursday 5 November 2026; the week is Monday 2 to Sunday 8
 * November (the locale is set to one whose week starts on Monday).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CalendarWeekPreviewViewModelTest {
    private val clock = MutableClock(Instant.parse("2026-11-05T12:00:00Z"))
    private val today = LocalDate.of(2026, 11, 5)

    private val routineA = Routine(1L, 1L, "Treino A", "A", 0, emptySet())
    private val routineB = Routine(2L, 1L, "Treino B", "B", 1, emptySet())
    private val routineC = Routine(3L, 1L, "Treino C", "C", 2, emptySet())

    private lateinit var routines: PlanRoutines
    private lateinit var history: EnergyHistory
    private lateinit var viewModel: CalendarViewModel
    private lateinit var savedLocale: Locale

    @Before
    fun setUp() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.UK)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        routines = PlanRoutines(listOf(routineA, routineB, routineC))
        history = EnergyHistory()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(savedLocale)
    }

    private fun TestScope.start() {
        viewModel = CalendarViewModel(history, routines, FakeTrainingSettings(), FakeBodySettings(), clock)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    private val preview: List<UpcomingUi> get() = viewModel.state.value.weekPreview

    private fun day(date: Int) = LocalDate.of(2026, 11, date)

    @Test
    fun `untrained today counts, and the rotation runs on to the end of the week`() = runTest {
        start()

        assertEquals(
            listOf(
                UpcomingUi(day(5), "A"),
                UpcomingUi(day(6), "B"),
                UpcomingUi(day(7), "C"),
                UpcomingUi(day(8), "A"),
            ),
            preview,
        )
    }

    @Test
    fun `the rotation advances after each suggested training, starting after the last trained routine`() = runTest {
        routines.lastRoutineFlow.value = routineA.id
        start()

        assertEquals(
            listOf(
                UpcomingUi(day(5), "B"),
                UpcomingUi(day(6), "C"),
                UpcomingUi(day(7), "A"),
                UpcomingUi(day(8), "B"),
            ),
            preview,
        )
    }

    @Test
    fun `once today is trained its suggestion leaves the preview`() = runTest {
        routines.lastRoutineFlow.value = routineA.id
        history.trainingDates.value = setOf(today)
        start()

        assertEquals(
            listOf(
                UpcomingUi(day(6), "B"),
                UpcomingUi(day(7), "C"),
                UpcomingUi(day(8), "A"),
            ),
            preview,
        )
    }

    @Test
    fun `a planned routine takes its day, and the rotation goes on from it`() = runTest {
        routines.plannedFlow.value = listOf(PlannedWorkout(day(6), routineC.id))
        start()

        assertEquals(
            listOf(
                UpcomingUi(day(5), "A"),
                UpcomingUi(day(6), "C"),
                UpcomingUi(day(7), "A"),
                UpcomingUi(day(8), "B"),
            ),
            preview,
        )
    }

    @Test
    fun `the preview is empty when there are no routines`() = runTest {
        routines = PlanRoutines(emptyList())
        start()

        assertTrue(preview.isEmpty())
    }
}
