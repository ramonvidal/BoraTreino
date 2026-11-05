package com.boratreino.core.notify

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.settings.TrainingSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The rest countdown: start, extend, skip and the finish, with a clock and a scheduler the test moves. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DefaultRestTimerTest {
    private val start: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private lateinit var clock: MovableClock
    private lateinit var scope: TestScope
    private lateinit var timer: DefaultRestTimer

    @Before
    fun setUp() {
        clock = MovableClock(start)
        scope = TestScope()
        timer = DefaultRestTimer(RuntimeEnvironment.getApplication(), clock, scope)
    }

    private fun secondsFromStart(seconds: Long): Instant = start.plusSeconds(seconds)

    // start

    @Test
    fun `starting counts down from now for the given seconds`() {
        timer.start(90)

        assertEquals(secondsFromStart(90), timer.state.value.endsAt)
        assertTrue(timer.state.value.isRunning)
    }

    @Test
    fun `a start below 15 seconds is raised to 15`() {
        timer.start(5)

        assertEquals(secondsFromStart(15), timer.state.value.endsAt)
    }

    @Test
    fun `a start above 600 seconds is lowered to 600`() {
        timer.start(9_999)

        assertEquals(secondsFromStart(600), timer.state.value.endsAt)
    }

    @Test
    fun `a new start replaces a running timer`() {
        timer.start(90)
        clock.advance(Duration.ofSeconds(30))

        timer.start(120)

        assertEquals(clock.instant().plusSeconds(120), timer.state.value.endsAt)
    }

    // extend

    @Test
    fun `extend adds 30 seconds by default`() {
        timer.start(90)

        timer.extend()

        assertEquals(secondsFromStart(120), timer.state.value.endsAt)
    }

    @Test
    fun `extend adds the seconds it is given`() {
        timer.start(90)

        timer.extend(45)

        assertEquals(secondsFromStart(135), timer.state.value.endsAt)
    }

    @Test
    fun `extend does nothing when no timer runs`() {
        timer.extend(30)

        assertNull(timer.state.value.endsAt)
    }

    @Test
    fun `extend never goes past 600 seconds from now`() {
        timer.start(600)

        timer.extend(30)

        assertEquals(secondsFromStart(600), timer.state.value.endsAt)
    }

    @Test
    fun `the 600 second cap is measured from the moment of the extend`() {
        timer.start(15)
        clock.advance(Duration.ofSeconds(100))

        timer.extend(1_000)

        assertEquals(clock.instant().plusSeconds(600), timer.state.value.endsAt)
    }

    // durationSeconds

    @Test
    fun `start fills the duration with the clamped seconds`() {
        timer.start(90)
        assertEquals(90, timer.state.value.durationSeconds)

        timer.start(5)
        assertEquals(15, timer.state.value.durationSeconds)

        timer.start(9_999)
        assertEquals(600, timer.state.value.durationSeconds)
    }

    @Test
    fun `extend adds the seconds to the duration`() {
        timer.start(90)

        timer.extend(30)

        assertEquals(120, timer.state.value.durationSeconds)
        assertEquals(secondsFromStart(120), timer.state.value.endsAt)
    }

    @Test
    fun `extend adds only the seconds actually added when capped at 600`() {
        timer.start(500)

        timer.extend(200)

        assertEquals(secondsFromStart(600), timer.state.value.endsAt)
        assertEquals(600, timer.state.value.durationSeconds)
    }

    @Test
    fun `extend at the cap adds nothing to the duration`() {
        timer.start(600)

        timer.extend(30)

        assertEquals(600, timer.state.value.durationSeconds)
    }

    @Test
    fun `skip and finish reset the duration`() {
        timer.start(90)
        timer.skip()
        assertEquals(0, timer.state.value.durationSeconds)

        timer.start(15)
        clock.advance(Duration.ofSeconds(15))
        scope.advanceUntilIdle()
        scope.runCurrent()
        assertEquals(RestTimerState(), timer.state.value)
    }

    // beep

    private class BeepSettings(beep: Boolean) : TrainingSettings {
        override val weeklyGoal: Flow<Int> = MutableStateFlow(3)
        override suspend fun setWeeklyGoal(goal: Int) = Unit
        override val reminders: Flow<ReminderSettings> = MutableStateFlow(ReminderSettings())
        override suspend fun setReminders(settings: ReminderSettings) = Unit
        override val warmupCalculator: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setWarmupCalculator(enabled: Boolean) = Unit
        override val restTimer: Flow<RestTimerSettings> = MutableStateFlow(RestTimerSettings(beep = beep))
        override suspend fun setRestTimer(settings: RestTimerSettings) = Unit
    }

    private fun finishRest(t: DefaultRestTimer) {
        t.start(15)
        clock.advance(Duration.ofSeconds(15))
        scope.advanceUntilIdle()
        scope.runCurrent()
    }

    @Test
    fun `a rest that ends with no settings does not throw and finishes`() {
        var finished = 0
        scope.backgroundScope.launch { timer.finished.collect { finished++ } }
        scope.runCurrent()

        finishRest(timer)

        assertNull(timer.state.value.endsAt)
        assertEquals(1, finished)
    }

    @Test
    fun `a rest that ends with the beep on does not throw and finishes once`() {
        val withBeep = DefaultRestTimer(RuntimeEnvironment.getApplication(), clock, scope, BeepSettings(beep = true))
        var finished = 0
        scope.backgroundScope.launch { withBeep.finished.collect { finished++ } }
        scope.runCurrent()

        finishRest(withBeep)

        assertNull(withBeep.state.value.endsAt)
        assertEquals(1, finished)
    }

    @Test
    fun `a rest that ends with the beep off does not throw and finishes once`() {
        val quiet = DefaultRestTimer(RuntimeEnvironment.getApplication(), clock, scope, BeepSettings(beep = false))
        var finished = 0
        scope.backgroundScope.launch { quiet.finished.collect { finished++ } }
        scope.runCurrent()

        finishRest(quiet)

        assertEquals(1, finished)
    }

    // skip

    @Test
    fun `skip stops the timer`() {
        timer.start(90)

        timer.skip()

        assertNull(timer.state.value.endsAt)
        assertFalse(timer.state.value.isRunning)
    }

    @Test
    fun `skip with no timer running does nothing`() {
        timer.skip()

        assertNull(timer.state.value.endsAt)
    }

    // finish

    @Test
    fun `a timer that runs out clears its state and emits finished once`() {
        var finished = 0
        scope.backgroundScope.launch { timer.finished.collect { finished++ } }
        scope.runCurrent()
        timer.start(15)

        clock.advance(Duration.ofSeconds(14))
        scope.advanceTimeBy(14_000)
        scope.runCurrent()
        assertTrue(timer.state.value.isRunning)
        assertEquals(0, finished)

        clock.advance(Duration.ofSeconds(1))
        scope.advanceUntilIdle()
        scope.runCurrent()

        assertNull(timer.state.value.endsAt)
        assertEquals(1, finished)
    }

    @Test
    fun `a timer replaced by a new start does not finish at its old end`() {
        var finished = 0
        scope.backgroundScope.launch { timer.finished.collect { finished++ } }
        scope.runCurrent()
        timer.start(15)
        clock.advance(Duration.ofSeconds(10))
        timer.start(60)

        clock.advance(Duration.ofSeconds(15))
        scope.advanceTimeBy(15_000)
        scope.runCurrent()

        assertTrue(timer.state.value.isRunning)
        assertEquals(0, finished)
    }

    @Test
    fun `a skipped timer does not finish later`() {
        var finished = 0
        scope.backgroundScope.launch { timer.finished.collect { finished++ } }
        scope.runCurrent()
        timer.start(15)
        timer.skip()

        clock.advance(Duration.ofSeconds(20))
        scope.advanceUntilIdle()
        scope.runCurrent()

        assertEquals(0, finished)
    }
}

/** A clock the test moves by hand. */
private class MovableClock(private var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now

    fun advance(by: Duration) {
        now = now.plus(by)
    }
}
