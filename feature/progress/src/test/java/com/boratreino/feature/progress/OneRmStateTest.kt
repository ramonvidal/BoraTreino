package com.boratreino.feature.progress

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.ProgressRepository
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.OneRepMaxRules
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressOverview
import com.boratreino.core.model.ProgressRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.time.ZoneOffset
import java.util.Locale

/** Estimated 1RM in the machine detail: the state builder, the view model and the formatting helpers. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OneRmStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = ProgressSamples.today
    private val withOneRm = OneRmSamples.withOneRm

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun build(
        progress: MachineProgress? = withOneRm,
        showOneRm: Boolean,
        range: ProgressRange = ProgressRange.THREE_MONTHS,
        selected: Int? = null,
    ) = buildMachineState(progress, range, today, selected = selected, showAll = false, showOneRm = showOneRm)

    // buildMachineState

    @Test
    fun `top weight view keeps the weight values and still offers the switch`() {
        val state = build(showOneRm = false)
        assertTrue(state.oneRmAvailable)
        assertFalse(state.showOneRm)
        assertEquals(ProgressMetric.WEIGHT, state.metric)
        assertEquals(listOf(80.0, 85.0, 90.0, 100.0, 95.0, 100.0), state.points.map { it.value })
    }

    @Test
    fun `one rm view charts the estimates with the one rm metric`() {
        val state = build(showOneRm = true)
        assertTrue(state.showOneRm)
        assertEquals(ProgressMetric.ONE_RM, state.metric)
        assertEquals(withOneRm.oneRmPoints.map { it.value }, state.points.map { it.value })
        assertEquals(80.0 * (1 + 8 / 30.0), state.points.first().value, 1e-9)
    }

    @Test
    fun `show one rm is not effective without one rm points`() {
        val state = build(OneRmSamples.withoutEligibleSets, showOneRm = true)
        assertFalse(state.oneRmAvailable)
        assertFalse(state.showOneRm)
        assertEquals(ProgressMetric.WEIGHT, state.metric)
        assertNull(state.oneRmBest)
    }

    @Test
    fun `a machine without eligible sets shows the weight values`() {
        val state = build(OneRmSamples.withoutEligibleSets, showOneRm = true, range = ProgressRange.ALL)
        assertEquals(OneRmSamples.withoutEligibleSets.points.map { it.value }, state.points.map { it.value })
    }

    @Test
    fun `one rm best is the highest estimate`() {
        val state = build(showOneRm = false)
        val expected = withOneRm.oneRmPoints.maxByOrNull { it.value }!!
        assertEquals(expected.value, state.oneRmBest!!.value, 1e-9)
        assertEquals(expected.date, state.oneRmBest!!.date)
        // 95 x 8 = 120.33 beats 100 x 5 = 116.67, 100 x 3 = 110 and 80 x 8 = 101.33.
        assertEquals(95.0 * (1 + 8 / 30.0), state.oneRmBest!!.value, 1e-9)
    }

    @Test
    fun `best follows the shown view`() {
        val top = build(showOneRm = false)
        assertEquals(100.0, top.best!!.value, 1e-9)
        val oneRm = build(showOneRm = true)
        assertEquals(oneRm.oneRmBest!!.value, oneRm.best!!.value, 1e-9)
    }

    @Test
    fun `change in the one rm view is the last estimate minus the first`() {
        val state = build(showOneRm = true)
        val points = withOneRm.oneRmPoints
        assertEquals(points.last().value - points.first().value, state.change!!, 1e-9)
    }

    @Test
    fun `scale covers the estimates in the one rm view`() {
        val state = build(showOneRm = true)
        assertTrue(state.scale.min <= state.points.minOf { it.value })
        assertTrue(state.scale.max >= state.points.maxOf { it.value })
        assertTrue(state.scale.max > 116.0)
    }

    @Test
    fun `selected index is kept in the one rm view`() {
        assertEquals(2, build(showOneRm = true, selected = 2).selectedIndex)
    }

    @Test
    fun `a cardio machine has no one rm`() {
        val state = build(ProgressCardioSamples.treadmillHistory, showOneRm = true)
        assertFalse(state.oneRmAvailable)
        assertFalse(state.showOneRm)
        assertEquals(ProgressMetric.MINUTES, state.metric)
        assertNull(state.oneRmBest)
    }

    @Test
    fun `a missing machine has no one rm`() {
        val state = build(null, showOneRm = true)
        assertFalse(state.found)
        assertFalse(state.oneRmAvailable)
        assertFalse(state.showOneRm)
    }

    @Test
    fun `the one rm values match the model rule`() {
        val state = build(showOneRm = true)
        val top = state.points[3]
        assertEquals(OneRepMaxRules.estimate(100.0, 3)!!, top.value, 1e-9)
        assertEquals(100.0, top.topSet.weightKg!!, 1e-9)
        assertEquals(3, top.topSet.reps)
    }

    // View model

    private class FakeProgressRepository(progress: MachineProgress?) : ProgressRepository {
        val flow = MutableStateFlow(progress)
        override fun observeOverview(): Flow<ProgressOverview> = emptyFlow()
        override fun observeMachineProgress(machineId: Long): Flow<MachineProgress?> = flow
    }

    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)

    private fun kotlinx.coroutines.test.TestScope.viewModel(
        handle: SavedStateHandle = SavedStateHandle(mapOf(MACHINE_ID_ARG to 1L)),
        progress: MachineProgress? = withOneRm,
    ): MachineProgressViewModel {
        val vm = MachineProgressViewModel(handle, FakeProgressRepository(progress), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        runCurrent()
        return vm
    }

    @Test
    fun `the view model starts on top weight`() = runTest(dispatcher) {
        val vm = viewModel()
        assertTrue(vm.state.value.loaded)
        assertTrue(vm.state.value.oneRmAvailable)
        assertFalse(vm.state.value.showOneRm)
        assertEquals(ProgressMetric.WEIGHT, vm.state.value.metric)
    }

    @Test
    fun `setShowOneRm switches the chart to the estimates and back`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setShowOneRm(true)
        runCurrent()
        assertTrue(vm.state.value.showOneRm)
        assertEquals(ProgressMetric.ONE_RM, vm.state.value.metric)
        assertNotNull(vm.state.value.oneRmBest)

        vm.setShowOneRm(false)
        runCurrent()
        assertFalse(vm.state.value.showOneRm)
        assertEquals(ProgressMetric.WEIGHT, vm.state.value.metric)
    }

    @Test
    fun `the choice is kept in the saved state handle`() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf(MACHINE_ID_ARG to 1L))
        val vm = viewModel(handle)

        vm.setShowOneRm(true)
        runCurrent()

        // A new view model over the same handle (rotation, process death) starts on the estimate.
        val restored = viewModel(handle)
        assertTrue(restored.state.value.showOneRm)
        assertEquals(ProgressMetric.ONE_RM, restored.state.value.metric)
    }

    @Test
    fun `a different machine starts on top weight`() = runTest(dispatcher) {
        val first = viewModel()
        first.setShowOneRm(true)
        runCurrent()

        val other = viewModel(SavedStateHandle(mapOf(MACHINE_ID_ARG to 2L)))
        assertFalse(other.state.value.showOneRm)
    }

    @Test
    fun `selecting a view clears the selected point`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.selectPoint(2)
        runCurrent()
        assertEquals(2, vm.state.value.selectedIndex)

        vm.setShowOneRm(true)
        runCurrent()
        assertNull(vm.state.value.selectedIndex)

        vm.selectPoint(1)
        runCurrent()
        assertEquals(1, vm.state.value.selectedIndex)
        vm.setShowOneRm(false)
        runCurrent()
        assertNull(vm.state.value.selectedIndex)
    }

    @Test
    fun `the switch has no effect on a machine without eligible sets`() = runTest(dispatcher) {
        val vm = viewModel(progress = OneRmSamples.withoutEligibleSets)
        vm.setShowOneRm(true)
        runCurrent()
        assertFalse(vm.state.value.showOneRm)
        assertEquals(ProgressMetric.WEIGHT, vm.state.value.metric)
    }

    // Formatting

    @Test
    fun `an estimate shows up to one decimal`() {
        assertEquals("101.3", formatEstimate(101.3333333, Locale.US))
        assertEquals("100", formatEstimate(100.0, Locale.US))
        assertEquals("105.5", formatEstimate(105.5, Locale.US))
    }

    @Test
    fun `an estimate is not rounded to half kilos`() {
        assertEquals("101.3", formatEstimate(80.0 * (1 + 8 / 30.0), Locale.US))
    }

    @Test
    fun `an estimate uses the locale decimal separator`() {
        assertEquals("101,3", formatEstimate(101.3333333, Locale.forLanguageTag("pt-BR")))
        assertEquals("101,3", formatEstimate(101.3333333, Locale.GERMANY))
    }

    @Test
    fun `a signed estimate has a plus for a rise`() {
        Locale.setDefault(Locale.US)
        assertEquals("+1.5", signedEstimate(1.5))
        assertEquals("+16.7", signedEstimate(16.6666))
    }

    @Test
    fun `a signed estimate has a minus for a fall and plain zero for none`() {
        Locale.setDefault(Locale.US)
        assertEquals("-5", signedEstimate(-5.0))
        assertEquals("-2.5", signedEstimate(-2.5))
        assertEquals("0", signedEstimate(0.0))
    }
}
