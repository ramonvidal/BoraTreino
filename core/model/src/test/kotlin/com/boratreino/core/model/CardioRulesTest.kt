package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class CardioRulesTest {
    private val at = Instant.parse("2026-11-02T18:00:00Z")

    private fun cardioSet(
        minutes: Int,
        calories: Int? = null,
        distanceM: Int? = null,
        position: Int = 0,
        id: Long = 1L,
    ) = WorkoutSet(
        id = id,
        sessionId = 1L,
        machineId = 1L,
        position = position,
        weightKg = null,
        reps = null,
        seconds = if (minutes > 0) minutes * 60 else null,
        calories = calories,
        distanceM = distanceM,
        loggedAt = at,
    )

    private fun weightSet(weightKg: Double = 40.0, reps: Int = 10) = WorkoutSet(
        id = 9L,
        sessionId = 1L,
        machineId = 2L,
        position = 0,
        weightKg = weightKg,
        reps = reps,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = at,
    )

    private fun draft(minutes: Int, calories: Int = 0, distanceM: Int = 0) = CardioDraft(minutes, calories, distanceM)

    // constants

    @Test
    fun `the constants match the contract`() {
        assertEquals(20, CardioRules.DEFAULT_MINUTES)
        assertEquals(600, CardioRules.MAX_MINUTES)
        assertEquals(5000, CardioRules.MAX_CALORIES)
        assertEquals(200_000, CardioRules.MAX_DISTANCE_M)
        assertEquals(10, CardioRules.CALORIE_STEP)
        assertEquals(100, CardioRules.DISTANCE_STEP_M)
    }

    // prefill

    @Test
    fun `prefill with no history is 20 minutes and nothing else`() {
        assertEquals(draft(20, 0, 0), CardioRules.prefill(emptyList(), emptyList()))
    }

    @Test
    fun `prefill takes last time's first set when this session has none`() {
        val lastTime = listOf(cardioSet(30, 250, 5000, position = 0), cardioSet(10, 80, position = 1))

        assertEquals(draft(30, 250, 5000), CardioRules.prefill(lastTime, emptyList()))
    }

    @Test
    fun `prefill takes this session's last set before last time`() {
        val lastTime = listOf(cardioSet(30, 250, 5000))
        val thisSession = listOf(cardioSet(15, 100, position = 0), cardioSet(12, 90, 2000, position = 1))

        assertEquals(draft(12, 90, 2000), CardioRules.prefill(lastTime, thisSession))
    }

    @Test
    fun `prefill uses this session even when there is no last time`() {
        assertEquals(draft(15, 0, 0), CardioRules.prefill(emptyList(), listOf(cardioSet(15))))
    }

    // sameAgain

    @Test
    fun `same again is last time's set at the index of how many sets are done`() {
        val lastTime = listOf(cardioSet(30, 250, position = 0), cardioSet(10, 80, 1500, position = 1))

        assertEquals(draft(30, 250, 0), CardioRules.sameAgain(lastTime, emptyList()))
        assertEquals(draft(10, 80, 1500), CardioRules.sameAgain(lastTime, listOf(cardioSet(30, 250))))
    }

    @Test
    fun `same again is null when last time has no set at that index`() {
        val lastTime = listOf(cardioSet(30, 250))

        assertNull(CardioRules.sameAgain(lastTime, listOf(cardioSet(30, 250))))
        assertNull(CardioRules.sameAgain(emptyList(), emptyList()))
    }

    // steppers

    @Test
    fun `minutes step by one and stop at 0 and 600`() {
        assertEquals(21, CardioRules.stepMinutes(20, up = true))
        assertEquals(19, CardioRules.stepMinutes(20, up = false))
        assertEquals(0, CardioRules.stepMinutes(0, up = false))
        assertEquals(1, CardioRules.stepMinutes(0, up = true))
        assertEquals(600, CardioRules.stepMinutes(600, up = true))
        assertEquals(599, CardioRules.stepMinutes(600, up = false))
        assertEquals(600, CardioRules.stepMinutes(599, up = true))
    }

    @Test
    fun `calories step by ten on the grid`() {
        assertEquals(110, CardioRules.stepCalories(100, up = true))
        assertEquals(90, CardioRules.stepCalories(100, up = false))
        assertEquals(10, CardioRules.stepCalories(0, up = true))
    }

    @Test
    fun `calories off the grid snap to the next multiple of ten in that direction`() {
        assertEquals(20, CardioRules.stepCalories(15, up = true))
        assertEquals(10, CardioRules.stepCalories(15, up = false))
        assertEquals(10, CardioRules.stepCalories(1, up = true))
        assertEquals(0, CardioRules.stepCalories(3, up = false))
        assertEquals(130, CardioRules.stepCalories(121, up = true))
        assertEquals(120, CardioRules.stepCalories(129, up = false))
    }

    @Test
    fun `calories stop at 0 and 5000`() {
        assertEquals(0, CardioRules.stepCalories(0, up = false))
        assertEquals(0, CardioRules.stepCalories(10, up = false))
        assertEquals(5000, CardioRules.stepCalories(5000, up = true))
        assertEquals(5000, CardioRules.stepCalories(4995, up = true))
        assertEquals(4990, CardioRules.stepCalories(5000, up = false))
    }

    @Test
    fun `distance steps by 100 m on the grid`() {
        assertEquals(3200, CardioRules.stepDistance(3100, up = true))
        assertEquals(3000, CardioRules.stepDistance(3100, up = false))
        assertEquals(100, CardioRules.stepDistance(0, up = true))
    }

    @Test
    fun `distance off the grid snaps to the next multiple of 100 in that direction`() {
        assertEquals(200, CardioRules.stepDistance(150, up = true))
        assertEquals(100, CardioRules.stepDistance(150, up = false))
        assertEquals(0, CardioRules.stepDistance(50, up = false))
        assertEquals(100, CardioRules.stepDistance(1, up = true))
    }

    @Test
    fun `distance stops at 0 and 200 km`() {
        assertEquals(0, CardioRules.stepDistance(0, up = false))
        assertEquals(200_000, CardioRules.stepDistance(200_000, up = true))
        assertEquals(200_000, CardioRules.stepDistance(199_950, up = true))
        assertEquals(199_900, CardioRules.stepDistance(200_000, up = false))
    }

    // canLog

    @Test
    fun `a set can be logged with minutes or calories`() {
        assertTrue(CardioRules.canLog(draft(1)))
        assertTrue(CardioRules.canLog(draft(0, calories = 10)))
        assertTrue(CardioRules.canLog(draft(20, 100, 3000)))
    }

    @Test
    fun `a set cannot be logged with nothing or distance alone`() {
        assertFalse(CardioRules.canLog(draft(0, 0, 0)))
        assertFalse(CardioRules.canLog(draft(0, 0, 5000)))
    }

    // toSet and toDraft

    @Test
    fun `toSet writes seconds, calories and distance and no weight or reps`() {
        val set = CardioRules.toSet(7L, 3L, 4L, 2, draft(25, 200, 4500), at)

        assertEquals(WorkoutSet(7L, 3L, 4L, 2, null, null, 1500, 200, 4500, at), set)
    }

    @Test
    fun `toSet leaves a zero value null`() {
        val minutesOnly = CardioRules.toSet(1L, 1L, 1L, 0, draft(20, 0, 0), at)
        assertEquals(1200, minutesOnly.seconds)
        assertNull(minutesOnly.calories)
        assertNull(minutesOnly.distanceM)

        val caloriesOnly = CardioRules.toSet(1L, 1L, 1L, 0, draft(0, 150, 0), at)
        assertNull(caloriesOnly.seconds)
        assertEquals(150, caloriesOnly.calories)
        assertNull(caloriesOnly.weightKg)
        assertNull(caloriesOnly.reps)
    }

    @Test
    fun `toDraft is the inverse of toSet`() {
        for (d in listOf(draft(20), draft(0, 120), draft(45, 300, 8200), draft(600, 5000, 200_000), draft(1, 0, 100))) {
            assertEquals(d, CardioRules.toDraft(CardioRules.toSet(1L, 1L, 1L, 0, d, at)))
        }
    }

    @Test
    fun `toDraft rounds seconds to the nearest minute`() {
        fun minutesOf(seconds: Int) = CardioRules.toDraft(cardioSet(0).copy(seconds = seconds)).minutes

        assertEquals(0, minutesOf(29))
        assertEquals(1, minutesOf(31))
        assertEquals(1, minutesOf(89))
        assertEquals(2, minutesOf(91))
    }

    @Test
    fun `toDraft of a set with nothing is all zeros`() {
        assertEquals(draft(0, 0, 0), CardioRules.toDraft(weightSet()))
    }

    // isCardio

    @Test
    fun `a set is cardio when it has seconds, calories or distance`() {
        assertTrue(CardioRules.isCardio(cardioSet(20)))
        assertTrue(CardioRules.isCardio(cardioSet(0, calories = 50)))
        assertTrue(CardioRules.isCardio(cardioSet(0, distanceM = 800)))
        assertFalse(CardioRules.isCardio(weightSet()))
    }

    // weeks

    private val wednesday = LocalDate.of(2026, 11, 4)

    private fun day(month: Int, dayOfMonth: Int) = LocalDate.of(2026, month, dayOfMonth)

    @Test
    fun `weeks gives exactly count weeks, oldest first, ending with this week, zero-filled`() {
        val weeks = CardioRules.weeks(emptyList(), wednesday, DayOfWeek.MONDAY, 4)

        assertEquals(listOf(day(10, 12), day(10, 19), day(10, 26), day(11, 2)), weeks.map { it.weekStart })
        assertTrue(weeks.all { it.minutes == 0 && it.calories == 0 && it.distanceM == 0 && it.sessions == 0 })
    }

    @Test
    fun `weeks starting on Sunday end with the Sunday week`() {
        val weeks = CardioRules.weeks(emptyList(), wednesday, DayOfWeek.SUNDAY, 2)

        assertEquals(listOf(day(10, 25), day(11, 1)), weeks.map { it.weekStart })
    }

    @Test
    fun `weeks sums minutes, calories and distance per week`() {
        val sets = listOf(
            day(11, 3) to cardioSet(20, 150, 3000, id = 1),
            day(11, 4) to cardioSet(30, 250, 5000, id = 2),
            day(10, 27) to cardioSet(45, 400, id = 3),
        )

        val weeks = CardioRules.weeks(sets, wednesday, DayOfWeek.MONDAY, 3)

        assertEquals(CardioWeek(day(10, 19), 0, 0, 0, 0), weeks[0])
        assertEquals(CardioWeek(day(10, 26), 45, 400, 0, 1), weeks[1])
        assertEquals(CardioWeek(day(11, 2), 50, 400, 8000, 2), weeks[2])
    }

    @Test
    fun `sessions counts distinct dates, not sets`() {
        val sets = listOf(
            day(11, 3) to cardioSet(10, id = 1),
            day(11, 3) to cardioSet(10, id = 2),
            day(11, 3) to cardioSet(10, id = 3),
        )

        val week = CardioRules.weeks(sets, wednesday, DayOfWeek.MONDAY, 1).single()

        assertEquals(1, week.sessions)
        assertEquals(30, week.minutes)
    }

    @Test
    fun `sets before the first week or after today's week are left out`() {
        val sets = listOf(
            day(9, 1) to cardioSet(60, id = 1),
            day(11, 10) to cardioSet(60, id = 2),
            day(11, 2) to cardioSet(15, id = 3),
        )

        val weeks = CardioRules.weeks(sets, wednesday, DayOfWeek.MONDAY, 2)

        assertEquals(listOf(0, 15), weeks.map { it.minutes })
    }

    @Test
    fun `weight sets are ignored`() {
        val sets = listOf(day(11, 3) to weightSet(), day(11, 3) to cardioSet(10))

        val week = CardioRules.weeks(sets, wednesday, DayOfWeek.MONDAY, 1).single()

        assertEquals(10, week.minutes)
        assertEquals(1, week.sessions)
    }

    @Test
    fun `a Sunday belongs to the week before on a Monday-first week and to its own on a Sunday-first week`() {
        val sets = listOf(day(11, 1) to cardioSet(20))

        val mondayFirst = CardioRules.weeks(sets, wednesday, DayOfWeek.MONDAY, 2)
        val sundayFirst = CardioRules.weeks(sets, wednesday, DayOfWeek.SUNDAY, 2)

        assertEquals(listOf(20, 0), mondayFirst.map { it.minutes })
        assertEquals(listOf(0, 20), sundayFirst.map { it.minutes })
    }

    @Test
    fun `thisWeek is the current week's totals`() {
        val sets = listOf(
            day(11, 2) to cardioSet(20, 100, id = 1),
            day(11, 4) to cardioSet(25, 120, 4000, id = 2),
            day(10, 30) to cardioSet(60, id = 3),
        )

        assertEquals(CardioWeek(day(11, 2), 45, 220, 4000, 2), CardioRules.thisWeek(sets, wednesday, DayOfWeek.MONDAY))
    }

    @Test
    fun `thisWeek with no cardio is zeros for the current week`() {
        assertEquals(CardioWeek(day(11, 2), 0, 0, 0, 0), CardioRules.thisWeek(emptyList(), wednesday, DayOfWeek.MONDAY))
        assertEquals(CardioWeek(day(11, 1), 0, 0, 0, 0), CardioRules.thisWeek(emptyList(), wednesday, DayOfWeek.SUNDAY))
    }

    // Records, progression and summaries ignore or understand cardio

    @Test
    fun `cardio sets are never records`() {
        val earlier = listOf(cardioSet(10, id = 1), cardioSet(15, id = 2))

        assertNull(RecordRules.recordFor(cardioSet(60, 500, 10_000, id = 3), earlier))
    }

    @Test
    fun `countRecords skips cardio sets`() {
        val earlier = listOf(cardioSet(10, id = 1))
        val session = listOf(cardioSet(60, 500, id = 2), cardioSet(90, 700, id = 3, position = 1))

        assertEquals(0, RecordRules.countRecords(session, earlier))
    }

    @Test
    fun `a weight record next to cardio sets is still counted`() {
        val earlier = listOf(weightSet(40.0, 10).copy(id = 1, loggedAt = at.minusSeconds(3600)))
        val session = listOf(
            cardioSet(30, id = 2),
            weightSet(45.0, 10).copy(id = 3, loggedAt = at.plusSeconds(60)),
        )

        assertEquals(1, RecordRules.countRecords(session, earlier))
    }

    @Test
    fun `progression suggests nothing for cardio sets`() {
        val target = RoutineItem(routineId = 1L, machineId = 1L, position = 0, targetSets = 1, repsMin = 8, repsMax = 12)
        val history = listOf(listOf(cardioSet(30, 250)), listOf(cardioSet(25, 200)))

        assertNull(ProgressionRules.suggest(target, stepKg = 1.0, history = history))
    }

    @Test
    fun `summary of cardio sets gives the totals`() {
        val sets = listOf(cardioSet(20, 150, 3000, position = 0, id = 1), cardioSet(10, 80, 1500, position = 1, id = 2))

        assertEquals(SetSummary.Cardio(count = 2, minutes = 30, calories = 230, distanceM = 4500), SetSummary.of(sets))
    }

    @Test
    fun `summary of a calories-only set has zero minutes and distance`() {
        assertEquals(SetSummary.Cardio(1, 0, 120, 0), SetSummary.of(listOf(cardioSet(0, calories = 120))))
    }

    @Test
    fun `summary of weight sets is unchanged`() {
        assertEquals(SetSummary.Uniform(2, 40.0, 10), SetSummary.of(listOf(weightSet(), weightSet().copy(id = 10, position = 1))))
    }

    // Progress

    private val bike = Machine(1L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, false)

    @Test
    fun `a cardio machine measures minutes`() {
        assertEquals(ProgressMetric.MINUTES, ProgressRules.metricFor(bike, emptyList()))
        assertEquals(ProgressMetric.MINUTES, ProgressRules.metricFor(bike, listOf(cardioSet(20))))
    }

    @Test
    fun `minutes points hold the machine's total minutes of each session`() {
        val sets = listOf(
            cardioSet(20, 150, id = 1, position = 0).copy(sessionId = 1L),
            cardioSet(10, 70, id = 2, position = 1).copy(sessionId = 1L),
            cardioSet(45, 300, id = 3, position = 0).copy(sessionId = 2L),
        )
        val dates = mapOf(1L to day(10, 20), 2L to day(10, 27))

        val points = ProgressRules.points(sets, dates, ProgressMetric.MINUTES)

        assertEquals(listOf(1L, 2L), points.map { it.sessionId })
        assertEquals(listOf(30.0, 45.0), points.map { it.value })
        assertEquals(listOf(2, 1), points.map { it.setCount })
        assertEquals(1L, points[0].topSet.id)
    }

    @Test
    fun `minutes points are ascending by date and ignore sessions without a date`() {
        val sets = listOf(
            cardioSet(20, id = 1).copy(sessionId = 2L),
            cardioSet(30, id = 2).copy(sessionId = 1L),
            cardioSet(40, id = 3).copy(sessionId = 3L),
        )
        val dates = mapOf(1L to day(10, 20), 2L to day(10, 10))

        val points = ProgressRules.points(sets, dates, ProgressMetric.MINUTES)

        assertEquals(listOf(2L, 1L), points.map { it.sessionId })
    }
}
