package com.boratreino.feature.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** "Add training" on the Calendar writes nothing; it only opens the editor for a new session on the selected day. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CalendarAddTrainingTest {
    private val legPress = Machine(1L, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, archived = false)
    private val clock = Clock.fixed(Instant.parse("2026-11-02T18:05:00Z"), ZoneOffset.UTC)

    private lateinit var store: NewSessionStore
    private val events = mutableListOf<CalendarEvent>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = NewSessionStore(listOf(legPress))
        events.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `add training writes nothing and asks for a new session on the selected day`() = runTest {
        val viewModel = CalendarViewModel(NewSessionHistory(store), PlanRoutines(), FakeTrainingSettings(), FakeBodySettings(), clock)
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()

        viewModel.selectDay(LocalDate.of(2026, 11, 10))
        viewModel.addTraining()
        runCurrent()

        assertEquals(listOf<CalendarEvent>(CalendarEvent.NewSession(LocalDate.of(2026, 11, 10))), events)
        assertEquals(emptyList<String>(), store.writes)
        assertNull(store.detail.value)
    }
}
