package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RestTimerRulesTest {
    private val now: Instant = Instant.parse("2026-11-02T18:00:00Z")

    private fun machine(restSeconds: Int?) = Machine(
        id = 1L,
        name = "Leg press",
        muscleGroup = MuscleGroup.LEGS,
        kind = MachineKind.WEIGHT_REPS,
        stepKg = 5.0,
        setupNote = null,
        restSeconds = restSeconds,
        archived = false,
    )

    // Constants

    @Test
    fun `the timer ranges and steps are the ones the owner chose`() {
        assertEquals(15, RestTimerRules.MIN_SECONDS)
        assertEquals(600, RestTimerRules.MAX_SECONDS)
        assertEquals(90, RestTimerRules.DEFAULT_SECONDS)
        assertEquals(30, RestTimerRules.EXTEND_SECONDS)
        assertEquals(listOf(60, 90, 120, 180), RestTimerRules.OPTIONS)
    }

    // durationFor

    @Test
    fun `a machine's own rest wins over the default`() {
        assertEquals(120, RestTimerRules.durationFor(machine(120), defaultSeconds = 90))
    }

    @Test
    fun `a machine without its own rest uses the default`() {
        assertEquals(180, RestTimerRules.durationFor(machine(null), defaultSeconds = 180))
    }

    @Test
    fun `a machine rest below 15 seconds is raised to 15`() {
        assertEquals(15, RestTimerRules.durationFor(machine(5), defaultSeconds = 90))
    }

    @Test
    fun `a machine rest above 600 seconds is lowered to 600`() {
        assertEquals(600, RestTimerRules.durationFor(machine(900), defaultSeconds = 90))
    }

    @Test
    fun `a default below 15 seconds is raised to 15 when the machine has none`() {
        assertEquals(15, RestTimerRules.durationFor(machine(null), defaultSeconds = 1))
    }

    @Test
    fun `a default above 600 seconds is lowered to 600 when the machine has none`() {
        assertEquals(600, RestTimerRules.durationFor(machine(null), defaultSeconds = 4_000))
    }

    // remainingSeconds

    @Test
    fun `a whole number of seconds left is shown as it is`() {
        assertEquals(90, RestTimerRules.remainingSeconds(now.plusSeconds(90), now))
    }

    @Test
    fun `a part of a second left rounds up`() {
        assertEquals(2, RestTimerRules.remainingSeconds(now.plusMillis(1_500), now))
        assertEquals(1, RestTimerRules.remainingSeconds(now.plusMillis(1_000), now))
        assertEquals(2, RestTimerRules.remainingSeconds(now.plusMillis(1_001), now))
        assertEquals(1, RestTimerRules.remainingSeconds(now.plusMillis(1), now))
    }

    @Test
    fun `nothing left, or an end already passed, is zero and never negative`() {
        assertEquals(0, RestTimerRules.remainingSeconds(now, now))
        assertEquals(0, RestTimerRules.remainingSeconds(now.minusSeconds(5), now))
        assertEquals(0, RestTimerRules.remainingSeconds(now.minus(Duration.ofDays(3)), now))
    }

    // format

    @Test
    fun `formats minutes and seconds with two digits for the seconds`() {
        assertEquals("0:05", RestTimerRules.format(5))
        assertEquals("1:30", RestTimerRules.format(90))
        assertEquals("10:00", RestTimerRules.format(600))
    }

    @Test
    fun `formats the edges and a minute boundary`() {
        assertEquals("0:00", RestTimerRules.format(0))
        assertEquals("0:59", RestTimerRules.format(59))
        assertEquals("1:00", RestTimerRules.format(60))
        assertEquals("1:12", RestTimerRules.format(72))
    }
}
