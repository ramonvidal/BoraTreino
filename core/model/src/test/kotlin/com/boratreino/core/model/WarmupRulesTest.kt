package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The warm-up suggestion: 50 x 8, 70 x 5, 85 x 3 of the working weight, rounded down to the machine step. */
class WarmupRulesTest {
    private fun weights(plan: List<SetDraft>) = plan.map { it.weightKg }

    private fun reps(plan: List<SetDraft>) = plan.map { it.reps }

    @Test
    fun `the scheme is 50 x 8, 70 x 5 and 85 x 3`() {
        assertEquals(listOf(50 to 8, 70 to 5, 85 to 3), WarmupRules.SCHEME)
    }

    @Test
    fun `100 kg in steps of 2 and a half gives 50 x 8, 70 x 5 and 85 x 3`() {
        val plan = WarmupRules.plan(100.0, 2.5)

        assertEquals(listOf(50.0, 70.0, 85.0), weights(plan))
        assertEquals(listOf(8, 5, 3), reps(plan))
    }

    @Test
    fun `62 and a half kg rounds each weight down to the step`() {
        val plan = WarmupRules.plan(62.5, 2.5)

        assertEquals(listOf(30.0, 42.5, 52.5), weights(plan))
        assertEquals(listOf(8, 5, 3), reps(plan))
    }

    @Test
    fun `steps of 5 round down to multiples of 5`() {
        // 80 -> 40, 56, 68
        assertEquals(listOf(40.0, 55.0, 65.0), weights(WarmupRules.plan(80.0, 5.0)))
    }

    @Test
    fun `steps of 1 round down to whole kilos`() {
        // 45 -> 22.5, 31.5, 38.25
        assertEquals(listOf(22.0, 31.0, 38.0), weights(WarmupRules.plan(45.0, 1.0)))
    }

    @Test
    fun `every draft is a warm-up set`() {
        val plan = WarmupRules.plan(100.0, 2.5)

        assertTrue(plan.isNotEmpty())
        assertTrue(plan.all { it.type == SetType.WARMUP })
    }

    @Test
    fun `the plan is ascending`() {
        for (working in listOf(10.0, 27.5, 62.5, 100.0, 142.5)) {
            val w = weights(WarmupRules.plan(working, 2.5))
            assertEquals(w.sorted(), w)
        }
    }

    @Test
    fun `weights that round to the same value appear once`() {
        // 10 kg in steps of 2.5: 5, 7, 8.5 -> 5, 5, 7.5 ... the first two are the same weight
        val plan = WarmupRules.plan(10.0, 2.5)

        assertEquals(listOf(5.0, 7.5), weights(plan))
        assertEquals(listOf(8, 3), reps(plan))
    }

    @Test
    fun `no weight is repeated`() {
        for (working in listOf(10.0, 12.5, 15.0, 20.0, 25.0)) {
            val w = weights(WarmupRules.plan(working, 2.5))
            assertEquals(w.distinct(), w)
        }
    }

    @Test
    fun `no warm-up reaches the working weight`() {
        for (working in listOf(10.0, 12.5, 15.0, 20.0, 62.5, 100.0)) {
            assertTrue(WarmupRules.plan(working, 2.5).all { it.weightKg < working })
        }
    }

    @Test
    fun `exactly 4 steps is the smallest weight that gets a plan`() {
        assertEquals(listOf(5.0, 7.5), weights(WarmupRules.plan(10.0, 2.5)))
        assertTrue(WarmupRules.plan(7.5, 2.5).isEmpty())
    }

    @Test
    fun `a weight under 4 steps gives no plan`() {
        assertTrue(WarmupRules.plan(5.0, 2.5).isEmpty())
        assertTrue(WarmupRules.plan(2.5, 2.5).isEmpty())
        assertTrue(WarmupRules.plan(15.0, 5.0).isEmpty())
        assertTrue(WarmupRules.plan(3.0, 1.0).isEmpty())
    }

    @Test
    fun `a step of zero or less gives no plan`() {
        assertTrue(WarmupRules.plan(100.0, 0.0).isEmpty())
        assertTrue(WarmupRules.plan(100.0, -2.5).isEmpty())
    }

    @Test
    fun `a weight of zero or less gives no plan`() {
        assertTrue(WarmupRules.plan(0.0, 2.5).isEmpty())
        assertTrue(WarmupRules.plan(-20.0, 2.5).isEmpty())
    }

    @Test
    fun `a weight a hair under 4 steps still counts as 4 steps`() {
        assertEquals(listOf(5.0, 7.5), weights(WarmupRules.plan(10.0 - 0.0001, 2.5)))
    }

    @Test
    fun `floating point noise around a step does not round down a whole step`() {
        // 0.1 steps of 0.1: 3 * 0.1 is 0.30000000000000004 and 0.7 * 0.1 style sums must not lose a step
        val plan = WarmupRules.plan(40.0, 0.1)

        assertEquals(listOf(20.0, 28.0, 34.0), weights(plan))
    }

    @Test
    fun `a step of 0 point 1 gives tidy weights`() {
        val plan = WarmupRules.plan(12.3, 0.1)

        assertEquals(listOf(6.1, 8.6, 10.4), weights(plan))
    }
}
