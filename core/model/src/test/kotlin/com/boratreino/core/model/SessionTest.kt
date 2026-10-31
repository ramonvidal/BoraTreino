package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SessionTest {
    private val start: Instant = Instant.parse("2026-10-29T18:00:00Z")

    private fun session(endedAt: Instant? = null) = Session(
        id = 1L,
        startedAt = start,
        endedAt = endedAt,
        routineId = null,
        note = null,
    )

    @Test
    fun `a session with no end time is accepted and open`() {
        val session = session(endedAt = null)
        assertNull(session.endedAt)
        assertTrue(session.isOpen)
    }

    @Test
    fun `a session that ended after it started is accepted and closed`() {
        val session = session(endedAt = start.plusSeconds(3600))
        assertFalse(session.isOpen)
    }

    @Test
    fun `a session that ends at the moment it started is accepted`() {
        val session = session(endedAt = start)
        assertEquals(start, session.endedAt)
        assertFalse(session.isOpen)
    }

    @Test
    fun `a session that ends before it started is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            session(endedAt = start.minusSeconds(1))
        }
    }

    @Test
    fun `optional routine and note may be null`() {
        val session = Session(
            id = 2L,
            startedAt = start,
            endedAt = null,
            routineId = 7L,
            note = "felt strong",
        )
        assertEquals(7L, session.routineId)
        assertEquals("felt strong", session.note)
    }
}
