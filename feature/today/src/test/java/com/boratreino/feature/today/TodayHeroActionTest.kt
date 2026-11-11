package com.boratreino.feature.today

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which action the hero's one main button takes, for each state of the day. Plain JVM: no Android needed. */
class TodayHeroActionTest {
    private val notStarted = requireNotNull(TodaySamples.routineBNotStarted.routines)
    private val inProgress = requireNotNull(TodaySamples.routineBInProgress.routines)

    @Test
    fun `no routines at all means Add exercise`() {
        assertEquals(HeroAction.ADD, heroActionFor(null))
    }

    @Test
    fun `a free session without a routine means Add exercise`() {
        val free = RoutineUi(chips = TodaySamples.chips(null), freeSelected = true)

        assertEquals(HeroAction.ADD, heroActionFor(free))
    }

    @Test
    fun `an empty routine means Add exercise`() {
        val empty = notStarted.copy(machines = emptyList())

        assertEquals(HeroAction.ADD, heroActionFor(empty))
    }

    @Test
    fun `when every exercise is done the hero finishes the training`() {
        val allDone = inProgress.copy(
            machines = inProgress.machines.map { it.copy(done = true, highlighted = false, setCount = it.target.targetSets) },
        )

        assertEquals(HeroAction.FINISH, heroActionFor(allDone))
    }

    @Test
    fun `once some set is logged the hero continues`() {
        assertEquals(HeroAction.CONTINUE, heroActionFor(inProgress))
    }

    @Test
    fun `with nothing logged yet the hero starts`() {
        assertEquals(HeroAction.START, heroActionFor(notStarted))
    }
}
