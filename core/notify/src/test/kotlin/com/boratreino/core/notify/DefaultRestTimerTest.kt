package com.boratreino.core.notify

import androidx.test.ext.junit.runners.AndroidJUnit4
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
