package com.boratreino.feature.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.DayKind
import com.boratreino.core.model.EnergyRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.SessionSets
import com.boratreino.core.model.Sex
import com.boratreino.core.model.WorkoutSet
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Calendar's quiet energy line for the selected day. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CalendarViewModelEnergyTest {
    private val clock = MutableClock(Instant.parse("2026-11-05T12:00:00Z"))
    private val profile = BodyProfile(Sex.MALE, birthYear = 1990, heightCm = 180, weightKg = 80.0)
    private val trainedDate = LocalDate.of(2026, 11, 3)
    private val restDate = LocalDate.of(2026, 11, 4)

    private val legPress = Machine(1L, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, archived = false)
    private val session = Session(
        id = 10L,
        startedAt = Instant.parse("2026-11-03T18:00:00Z"),
        endedAt = Instant.parse("2026-11-03T19:00:00Z"),
        routineId = null,
        note = null,
    )
    private val sets = listOf(
        WorkoutSet(1L, 10L, 1L, 0, 100.0, 10, null, null, null, Instant.parse("2026-11-03T18:10:00Z")),
        WorkoutSet(2L, 10L, 1L, 1, 100.0, 10, null, null, null, Instant.parse("2026-11-03T18:15:00Z")),
    )
    private val detail = SessionDetail(session, null, listOf(SessionMachine(legPress, sets)))

    private lateinit var history: EnergyHistory
    private lateinit var body: FakeBodySettings
    private lateinit var viewModel: CalendarViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        history = EnergyHistory()
        history.days.value = mapOf(trainedDate to listOf(detail))
        body = FakeBodySettings()
        body.profileFlow.value = profile
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.start() {
        viewModel = CalendarViewModel(history, PlanRoutines(), FakeTrainingSettings(), body, clock)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    private val energy get() = viewModel.state.value.energy

    @Test
    fun `a past day with sessions shows the trained estimate`() = runTest {
        start()
        viewModel.selectDay(trainedDate)
        runCurrent()

        // 2100 + 4 * 80 * 1 h = 2420
        assertEquals(DayEnergyUi(2420, DayKind.TRAINED), energy)
        val expected = EnergyRules.day(profile, trainedDate, clock.instant(), listOf(SessionSets(session, sets)), false)
        assertEquals(DayEnergyUi(expected.kcal, expected.kind), energy)
    }

    @Test
    fun `a past day without sessions is a rest day`() = runTest {
        start()
        viewModel.selectDay(restDate)
        runCurrent()

        assertEquals(DayEnergyUi(2100, DayKind.REST), energy)
    }

    @Test
    fun `today without sessions is a rest day because nothing is expected`() = runTest {
        start()

        assertEquals(DayEnergyUi(2100, DayKind.REST), energy)
    }

    @Test
    fun `the goal is applied`() = runTest {
        body.profileFlow.value = profile.copy(goalAdjustmentPercent = 10)
        start()
        viewModel.selectDay(restDate)
        runCurrent()

        assertEquals(DayEnergyUi(2310, DayKind.REST), energy)
    }

    @Test
    fun `a day after today has no line`() = runTest {
        start()
        assertNotNull(energy)
        // The clock moves back past midnight: the selected day (today) is now in the future.
        clock.now = Instant.parse("2026-11-04T12:00:00Z")
        body.showFlow.value = false
        runCurrent()
        body.showFlow.value = true
        runCurrent()

        assertEquals(LocalDate.of(2026, 11, 5), viewModel.state.value.selected)
        assertNull(energy)
    }

    @Test
    fun `without a profile there is no line`() = runTest {
        body.profileFlow.value = null
        start()
        viewModel.selectDay(trainedDate)
        runCurrent()

        assertNull(energy)
    }

    @Test
    fun `with the switch off there is no line`() = runTest {
        body.showFlow.value = false
        start()
        viewModel.selectDay(trainedDate)
        runCurrent()

        assertNull(energy)
    }

    @Test
    fun `filling in the profile shows the line`() = runTest {
        body.profileFlow.value = null
        start()
        assertNull(energy)

        body.profileFlow.value = profile
        runCurrent()

        assertNotNull(energy)
    }
}
