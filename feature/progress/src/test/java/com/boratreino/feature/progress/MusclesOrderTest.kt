package com.boratreino.feature.progress

import com.boratreino.core.model.Muscle
import org.junit.Assert.assertEquals
import org.junit.Test

/** The order of the Muscles card: highest load first, ties and untrained muscles in display order, on the plain JVM. */
class MusclesOrderTest {
    /** A load for every muscle: the given ones, the rest 0.0. */
    private fun load(vararg given: Pair<Muscle, Double>): Map<Muscle, Double> =
        Muscle.entries.associateWith { muscle -> given.firstOrNull { it.first == muscle }?.second ?: 0.0 }

    @Test
    fun `the muscles are listed highest load first`() {
        val order = musclesByLoad(load(Muscle.TRAPS to 3.0, Muscle.CHEST to 8.0, Muscle.ABS to 1.5))

        assertEquals(listOf(Muscle.CHEST, Muscle.TRAPS, Muscle.ABS), order.take(3))
    }

    @Test
    fun `equal loads keep the display order`() {
        val order = musclesByLoad(load(Muscle.QUADRICEPS to 2.0, Muscle.BICEPS to 2.0, Muscle.CHEST to 2.0))

        assertEquals(listOf(Muscle.CHEST, Muscle.BICEPS, Muscle.QUADRICEPS), order.take(3))
    }

    @Test
    fun `untrained muscles come last in display order`() {
        val order = musclesByLoad(load(Muscle.LOWER_BACK to 1.0))

        assertEquals(Muscle.LOWER_BACK, order.first())
        assertEquals(Muscle.entries.filter { it != Muscle.LOWER_BACK }, order.drop(1))
    }

    @Test
    fun `a small load is still ahead of an untrained muscle`() {
        val order = musclesByLoad(load(Muscle.ABS to 0.01))

        assertEquals(Muscle.ABS, order.first())
    }

    @Test
    fun `with no load at all the display order stays`() {
        assertEquals(Muscle.entries, musclesByLoad(load()))
    }

    @Test
    fun `every muscle is listed exactly once`() {
        val order = musclesByLoad(load(Muscle.HIPS to 4.0, Muscle.CALVES to 4.0))

        assertEquals(Muscle.entries.size, order.size)
        assertEquals(Muscle.entries.toSet(), order.toSet())
    }
}
