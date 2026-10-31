package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek

class RoutineTest {
    private fun routine(name: String = "Routine A", shortName: String = "A") = Routine(
        id = 1L,
        planId = 1L,
        name = name,
        shortName = shortName,
        position = 0,
        weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
    )

    @Test
    fun `a routine with a name and a short name is accepted`() {
        val routine = routine()
        assertEquals("Routine A", routine.name)
        assertEquals("A", routine.shortName)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), routine.weekdays)
    }

    @Test
    fun `a blank name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { routine(name = "") }
        assertThrows(IllegalArgumentException::class.java) { routine(name = "  ") }
    }

    @Test
    fun `a blank short name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { routine(shortName = "") }
        assertThrows(IllegalArgumentException::class.java) { routine(shortName = " ") }
    }
}
