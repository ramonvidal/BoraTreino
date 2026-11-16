package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Session times with cardio: a cardio set is logged when the cardio ends, so the session can start later than the cardio did. */
class SessionTimeRulesTest {
    private val t: Instant = Instant.parse("2026-11-16T19:00:00Z")

    private fun session(startedAt: Instant = t, endedAt: Instant? = t) =
        Session(id = 1L, startedAt = startedAt, endedAt = endedAt, routineId = null, note = null)

    private fun weights(id: Long, loggedAt: Instant) =
        WorkoutSet(id, 1L, 1L, 0, 50.0, 10, null, null, null, loggedAt)

    private fun cardio(
        id: Long,
        loggedAt: Instant,
        seconds: Int? = 3600,
        calories: Int? = null,
        distanceM: Int? = null,
    ) = WorkoutSet(id, 1L, 2L, 0, null, null, seconds, calories, distanceM, loggedAt)

    private fun minutes(n: Long): Duration = Duration.ofMinutes(n)

    // Effective start

    @Test
    fun `a cardio set logged at the end moves the start back by its seconds`() {
        val sets = listOf(cardio(1, loggedAt = t))

        assertEquals(t.minusSeconds(3600), SessionTimeRules.effectiveStart(session(endedAt = t.plusSeconds(60)), sets))
    }

    @Test
    fun `with no sets the effective start is the session start`() {
        assertEquals(t, SessionTimeRules.effectiveStart(session(), emptyList()))
    }

    @Test
    fun `a weights session keeps its start when the cardio came later`() {
        val sets = listOf(weights(1, t), cardio(2, loggedAt = t.plus(minutes(50)), seconds = 1200))

        assertEquals(t, SessionTimeRules.effectiveStart(session(endedAt = t.plus(minutes(55))), sets))
    }

    @Test
    fun `cardio first and weights after start at the start of the cardio`() {
        val sets = listOf(
            cardio(1, loggedAt = t.plus(minutes(20)), seconds = 1200),
            weights(2, t.plus(minutes(30))),
        )

        assertEquals(t, SessionTimeRules.effectiveStart(session(startedAt = t.plus(minutes(20)), endedAt = t.plus(minutes(40))), sets))
    }

    @Test
    fun `a strength set logged before the session start does not move the start`() {
        val sets = listOf(weights(1, t.minus(minutes(30))))

        assertEquals(t, SessionTimeRules.effectiveStart(session(), sets))
    }

    @Test
    fun `with several cardio sets the earliest cardio start wins`() {
        val sets = listOf(
            cardio(1, loggedAt = t.plus(minutes(30)), seconds = 1200), // began at t + 10
            cardio(2, loggedAt = t.plus(minutes(60)), seconds = 1800), // began at t + 30
            cardio(3, loggedAt = t.plus(minutes(100)), seconds = 600), // began at t + 90
        )

        assertEquals(t.plus(minutes(10)), SessionTimeRules.effectiveStart(session(startedAt = t.plus(minutes(30)), endedAt = t.plus(minutes(120))), sets))
    }

    @Test
    fun `a cardio set with null seconds counts as zero seconds`() {
        val sets = listOf(cardio(1, loggedAt = t.plus(minutes(5)), seconds = null, distanceM = 2000))

        assertEquals(t, SessionTimeRules.effectiveStart(session(endedAt = t.plus(minutes(10))), sets))
    }

    // Duration

    @Test
    fun `a 60 minute cardio logged at the end is 60 minutes, not 1`() {
        val sets = listOf(cardio(1, loggedAt = t, seconds = 3600))

        assertEquals(60, SessionTimeRules.durationMinutes(session(startedAt = t, endedAt = t), sets))
    }

    @Test
    fun `a minute between the cardio and the end adds that minute to the duration`() {
        val sets = listOf(cardio(1, loggedAt = t, seconds = 3600))

        assertEquals(61, SessionTimeRules.durationMinutes(session(startedAt = t, endedAt = t.plus(minutes(1))), sets))
    }

    @Test
    fun `a weights session duration is the time from its start to its end`() {
        val sets = listOf(weights(1, t), weights(2, t.plus(minutes(20))))

        assertEquals(55, SessionTimeRules.durationMinutes(session(endedAt = t.plus(minutes(55))), sets))
    }

    @Test
    fun `a session with a cardio set at the end and weights before it lasts from the weights`() {
        val sets = listOf(weights(1, t), cardio(2, loggedAt = t.plus(minutes(50)), seconds = 1200))

        assertEquals(55, SessionTimeRules.durationMinutes(session(endedAt = t.plus(minutes(55))), sets))
    }

    @Test
    fun `an open session has no duration`() {
        val sets = listOf(cardio(1, loggedAt = t, seconds = 3600))

        assertNull(SessionTimeRules.durationMinutes(session(endedAt = null), sets))
    }

    @Test
    fun `an end that is not after the effective start has no duration`() {
        // The session cannot end before it starts, so equal times are the only case: a hand-added session.
        assertNull(SessionTimeRules.durationMinutes(session(startedAt = t, endedAt = t), emptyList()))
        assertNull(SessionTimeRules.durationMinutes(session(startedAt = t, endedAt = t), listOf(cardio(1, loggedAt = t, seconds = 0))))
    }

    @Test
    fun `duration rounds to the nearest minute, half up`() {
        val end29 = t.plusSeconds(61 * 60L + 29)
        val end30 = t.plusSeconds(61 * 60L + 30)

        assertEquals(61, SessionTimeRules.durationMinutes(session(endedAt = end29), emptyList()))
        assertEquals(62, SessionTimeRules.durationMinutes(session(endedAt = end30), emptyList()))
    }

    @Test
    fun `a duration under half a minute is shown as one minute`() {
        assertEquals(1, SessionTimeRules.durationMinutes(session(endedAt = t.plusSeconds(10)), emptyList()))
    }

    @Test
    fun `a duration longer than the cap is capped at 600 minutes`() {
        assertEquals(600, SessionTimeRules.durationMinutes(session(endedAt = t.plusSeconds(11 * 3600L)), emptyList()))
    }

    @Test
    fun `a duration of exactly the cap is 600 minutes and one under it is 599`() {
        assertEquals(600, SessionTimeRules.durationMinutes(session(endedAt = t.plusSeconds(10 * 3600L)), emptyList()))
        assertEquals(599, SessionTimeRules.durationMinutes(session(endedAt = t.plusSeconds(10 * 3600L - 60)), emptyList()))
    }

    @Test
    fun `the cap is 600 minutes`() {
        assertEquals(600, SessionTimeRules.MAX_DURATION_MINUTES)
    }

    // Cardio minutes

    @Test
    fun `cardio minutes sum the cardio seconds`() {
        val sets = listOf(cardio(1, t, seconds = 1200), cardio(2, t, seconds = 1800))

        assertEquals(50, SessionTimeRules.cardioMinutes(sets))
    }

    @Test
    fun `cardio minutes round the total to the nearest minute`() {
        assertEquals(0, SessionTimeRules.cardioMinutes(listOf(cardio(1, t, seconds = 29))))
        assertEquals(1, SessionTimeRules.cardioMinutes(listOf(cardio(1, t, seconds = 30))))
        assertEquals(61, SessionTimeRules.cardioMinutes(listOf(cardio(1, t, seconds = 3600), cardio(2, t, seconds = 30))))
    }

    @Test
    fun `cardio minutes count null seconds as zero and ignore weights sets`() {
        val sets = listOf(cardio(1, t, seconds = null, distanceM = 1000), weights(2, t), cardio(3, t, seconds = 600))

        assertEquals(10, SessionTimeRules.cardioMinutes(sets))
    }

    @Test
    fun `cardio minutes are zero without cardio`() {
        assertEquals(0, SessionTimeRules.cardioMinutes(listOf(weights(1, t))))
        assertEquals(0, SessionTimeRules.cardioMinutes(emptyList()))
    }
}
