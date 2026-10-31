package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RoutineItemTest {
    private fun item(
        targetSets: Int = 3,
        repsMin: Int = 8,
        repsMax: Int = 12,
    ) = RoutineItem(
        routineId = 1L,
        machineId = 2L,
        position = 0,
        targetSets = targetSets,
        repsMin = repsMin,
        repsMax = repsMax,
    )

    @Test
    fun `a target of 3 sets of 8 to 12 reps is accepted`() {
        val item = item()
        assertEquals(3, item.targetSets)
        assertEquals(8, item.repsMin)
        assertEquals(12, item.repsMax)
    }

    @Test
    fun `one set is the smallest target accepted`() {
        assertEquals(1, item(targetSets = 1).targetSets)
    }

    @Test
    fun `zero target sets is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { item(targetSets = 0) }
    }

    @Test
    fun `a negative number of target sets is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { item(targetSets = -1) }
    }

    @Test
    fun `zero minimum reps is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { item(repsMin = 0, repsMax = 5) }
    }

    @Test
    fun `a minimum above the maximum is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { item(repsMin = 12, repsMax = 8) }
    }

    @Test
    fun `a single rep target where minimum equals maximum is accepted`() {
        val item = item(repsMin = 10, repsMax = 10)
        assertEquals(10, item.repsMin)
        assertEquals(10, item.repsMax)
    }
}
