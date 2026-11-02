package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SessionRulesTest {
    private val start = Instant.parse("2026-10-30T18:00:00Z")
    private val session = Session(id = 1L, startedAt = start, endedAt = null, routineId = null, note = null)

    @Test
    fun `a session closes by itself after three hours`() {
        assertEquals(Duration.ofHours(3), SessionRules.AUTO_CLOSE_AFTER)
    }

    @Test
    fun `not stale at 2 h 59 min after the last set`() {
        val lastSet = start.plus(Duration.ofHours(1))
        val now = lastSet.plus(Duration.ofMinutes(179))

        assertFalse(SessionRules.isStale(session, lastSet, now))
    }

    @Test
    fun `stale at exactly 3 h after the last set`() {
        val lastSet = start.plus(Duration.ofHours(1))
        val now = lastSet.plus(Duration.ofHours(3))

        assertTrue(SessionRules.isStale(session, lastSet, now))
    }

    @Test
    fun `stale well after the last set`() {
        val lastSet = start.plus(Duration.ofHours(1))

        assertTrue(SessionRules.isStale(session, lastSet, lastSet.plus(Duration.ofHours(5))))
    }

    @Test
    fun `a recent last set keeps a session open even when it started long ago`() {
        val lastSet = start.plus(Duration.ofHours(5)).minus(Duration.ofMinutes(1))
        val now = start.plus(Duration.ofHours(5))

        assertFalse(SessionRules.isStale(session, lastSet, now))
    }

    @Test
    fun `with no sets, not stale at 2 h 59 min after the start`() {
        val now = start.plus(Duration.ofMinutes(179))

        assertFalse(SessionRules.isStale(session, lastSetAt = null, now = now))
    }

    @Test
    fun `with no sets, stale at exactly 3 h after the start`() {
        val now = start.plus(Duration.ofHours(3))

        assertTrue(SessionRules.isStale(session, lastSetAt = null, now = now))
    }

    @Test
    fun `close time is the last set's time when there is one`() {
        val lastSet = start.plus(Duration.ofHours(1))

        assertEquals(lastSet, SessionRules.closeTime(session, lastSet))
    }

    @Test
    fun `close time is the start when there are no sets`() {
        assertEquals(start, SessionRules.closeTime(session, lastSetAt = null))
    }
}
