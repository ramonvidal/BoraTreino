package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EnergyRulesTest {
    private val today = LocalDate.of(2026, 11, 5)
    private val start = Instant.parse("2026-11-05T10:00:00Z")
    private val now = Instant.parse("2026-11-05T14:00:00Z")

    private val male = BodyProfile(Sex.MALE, birthYear = 1990, heightCm = 180, weightKg = 80.0)
    private val female = BodyProfile(Sex.FEMALE, birthYear = 1995, heightCm = 165, weightKg = 60.0)

    private fun session(id: Long = 1L, minutes: Long? = 60, startedAt: Instant = start) = Session(
        id = id,
        startedAt = startedAt,
        endedAt = minutes?.let { startedAt.plusSeconds(it * 60) },
        routineId = null,
        note = null,
    )

    private fun strength(type: SetType = SetType.NORMAL, id: Long = 1L) = WorkoutSet(
        id = id, sessionId = 1L, machineId = 1L, position = 0,
        weightKg = 50.0, reps = 10, seconds = null, calories = null, distanceM = null,
        loggedAt = start, type = type,
    )

    private fun cardio(calories: Int?, id: Long = 2L) = WorkoutSet(
        id = id, sessionId = 1L, machineId = 2L, position = 1,
        weightKg = null, reps = null, seconds = 600, calories = calories, distanceM = null,
        loggedAt = start,
    )

    private fun sessionOf(vararg sets: WorkoutSet, minutes: Long? = 60) = SessionSets(session(minutes = minutes), sets.toList())

    private fun burn(profile: BodyProfile, vararg sessions: SessionSets, at: Instant = now) =
        EnergyRules.trainingBurnKcal(profile, sessions.toList(), at)

    // age and bmr

    @Test
    fun `age is the year difference`() {
        assertEquals(36, EnergyRules.age(male, today))
        assertEquals(31, EnergyRules.age(female, today))
    }

    @Test
    fun `age is never below ten`() {
        assertEquals(10, EnergyRules.age(male.copy(birthYear = 2020), today))
        assertEquals(10, EnergyRules.age(male.copy(birthYear = 2026), today))
    }

    @Test
    fun `bmr for a man follows Mifflin-St Jeor`() {
        assertEquals(1750.0, EnergyRules.bmr(male, today), 1e-9)
    }

    @Test
    fun `bmr for a woman follows Mifflin-St Jeor`() {
        assertEquals(1315.25, EnergyRules.bmr(female, today), 1e-9)
    }

    @Test
    fun `bmr uses the minimum age for a very young birth year`() {
        // 800 + 1125 - 50 + 5
        assertEquals(1880.0, EnergyRules.bmr(male.copy(birthYear = 2024), today), 1e-9)
    }

    @Test
    fun `a manual basal rate replaces the formula`() {
        assertEquals(1500.0, EnergyRules.bmr(male.copy(manualBmrKcal = 1500), today), 1e-9)
    }

    // rest day

    @Test
    fun `rest day is bmr times 1_2 rounded to ten`() {
        assertEquals(2100, EnergyRules.restDayKcal(male, today))
        assertEquals(1580, EnergyRules.restDayKcal(female, today))
    }

    @Test
    fun `rest day with a manual basal rate`() {
        assertEquals(1800, EnergyRules.restDayKcal(male.copy(manualBmrKcal = 1500), today))
    }

    @Test
    fun `rest day follows the goal`() {
        assertEquals(2310, EnergyRules.restDayKcal(male.copy(goalAdjustmentPercent = 10), today))
        assertEquals(1680, EnergyRules.restDayKcal(male.copy(goalAdjustmentPercent = -20), today))
    }

    @Test
    fun `a goal out of range is clamped`() {
        assertEquals(2730, EnergyRules.restDayKcal(male.copy(goalAdjustmentPercent = 50), today))
        assertEquals(1470, EnergyRules.restDayKcal(male.copy(goalAdjustmentPercent = -50), today))
    }

    // training burn

    @Test
    fun `no sessions burn nothing`() {
        assertEquals(0.0, burn(male), 1e-9)
    }

    @Test
    fun `an hour of strength burns four times the weight`() {
        assertEquals(320.0, burn(male, sessionOf(strength())), 1e-9)
    }

    @Test
    fun `strength burn scales with the duration`() {
        assertEquals(640.0, burn(male, sessionOf(strength(), minutes = 120)), 1e-9)
        assertEquals(160.0, burn(male, sessionOf(strength(), minutes = 30)), 1e-9)
    }

    @Test
    fun `strength duration is capped at three hours`() {
        assertEquals(960.0, burn(male, sessionOf(strength(), minutes = 300)), 1e-9)
    }

    @Test
    fun `a closed session with zero duration counts twenty minutes`() {
        assertEquals(320.0 / 3.0, burn(male, sessionOf(strength(), minutes = 0)), 1e-6)
    }

    @Test
    fun `an open session counts up to now`() {
        val open = SessionSets(session(minutes = null, startedAt = now.minusSeconds(30 * 60)), listOf(strength()))
        assertEquals(160.0, burn(male, open), 1e-9)
    }

    @Test
    fun `an open session is capped at three hours`() {
        val open = SessionSets(session(minutes = null, startedAt = now.minusSeconds(10 * 3600)), listOf(strength()))
        assertEquals(960.0, burn(male, open), 1e-9)
    }

    @Test
    fun `an open session with now before the start burns nothing`() {
        val open = SessionSets(session(minutes = null, startedAt = now.plusSeconds(3600)), listOf(strength()))
        assertEquals(0.0, burn(male, open), 1e-9)
    }

    @Test
    fun `warm-up sets alone burn nothing`() {
        assertEquals(0.0, burn(male, sessionOf(strength(SetType.WARMUP))), 1e-9)
    }

    @Test
    fun `drop and failure sets count as working`() {
        assertEquals(320.0, burn(male, sessionOf(strength(SetType.DROP))), 1e-9)
        assertEquals(320.0, burn(male, sessionOf(strength(SetType.FAILURE))), 1e-9)
    }

    @Test
    fun `cardio calories are summed`() {
        assertEquals(250.0, burn(male, sessionOf(cardio(100, id = 2L), cardio(150, id = 3L))), 1e-9)
    }

    @Test
    fun `cardio without calories burns nothing`() {
        assertEquals(0.0, burn(male, sessionOf(cardio(null))), 1e-9)
    }

    @Test
    fun `cardio and strength in one session add up`() {
        assertEquals(570.0, burn(male, sessionOf(strength(), cardio(250))), 1e-9)
    }

    @Test
    fun `sessions add up`() {
        val one = sessionOf(strength())
        val two = SessionSets(session(id = 2L, minutes = 30), listOf(strength(id = 5L)))
        assertEquals(480.0, burn(male, one, two), 1e-9)
    }

    // day

    @Test
    fun `a session with sets is a trained day`() {
        val day = EnergyRules.day(male, today, now, listOf(sessionOf(strength())), trainingExpected = false)

        assertEquals(DayKind.TRAINED, day.kind)
        assertEquals(2420, day.kcal)
        assertEquals(2100, day.restDayKcal)
        assertEquals(320, day.trainingBurnKcal)
    }

    @Test
    fun `a trained day applies the goal to the whole total`() {
        val day = EnergyRules.day(male.copy(goalAdjustmentPercent = 10), today, now, listOf(sessionOf(strength())), false)

        // (2100 + 320) * 1.1 = 2662
        assertEquals(2660, day.kcal)
        assertEquals(2310, day.restDayKcal)
    }

    @Test
    fun `a trained day with only warm-ups is still trained with no burn`() {
        val day = EnergyRules.day(male, today, now, listOf(sessionOf(strength(SetType.WARMUP))), false)

        assertEquals(DayKind.TRAINED, day.kind)
        assertEquals(2100, day.kcal)
        assertEquals(0, day.trainingBurnKcal)
    }

    @Test
    fun `cardio only day adds its calories`() {
        val day = EnergyRules.day(male, today, now, listOf(sessionOf(cardio(300))), false)

        assertEquals(DayKind.TRAINED, day.kind)
        assertEquals(2400, day.kcal)
        assertEquals(300, day.trainingBurnKcal)
    }

    @Test
    fun `an expected training day assumes one hour`() {
        val day = EnergyRules.day(male, today, now, emptyList(), trainingExpected = true)

        assertEquals(DayKind.TRAINING_EXPECTED, day.kind)
        assertEquals(2420, day.kcal)
        assertEquals(2100, day.restDayKcal)
        assertEquals(320, day.trainingBurnKcal)
    }

    @Test
    fun `no sessions and no training expected is a rest day`() {
        val day = EnergyRules.day(male, today, now, emptyList(), trainingExpected = false)

        assertEquals(DayKind.REST, day.kind)
        assertEquals(2100, day.kcal)
        assertEquals(2100, day.restDayKcal)
        assertEquals(0, day.trainingBurnKcal)
    }

    @Test
    fun `a session without sets is not a trained day`() {
        val empty = listOf(SessionSets(session(), emptyList()))

        assertEquals(DayKind.REST, EnergyRules.day(male, today, now, empty, false).kind)
        assertEquals(DayKind.TRAINING_EXPECTED, EnergyRules.day(male, today, now, empty, true).kind)
    }

    @Test
    fun `an expected day ignores the plan when a session already has sets`() {
        val day = EnergyRules.day(male, today, now, listOf(sessionOf(strength(), minutes = 30)), trainingExpected = true)

        assertEquals(DayKind.TRAINED, day.kind)
        assertEquals(2260, day.kcal)
    }

    @Test
    fun `the day uses the given date for the age`() {
        val next = LocalDate.of(2027, 1, 10)
        // age 37: 800 + 1125 - 185 + 5 = 1745 -> 2094 -> 2090
        assertEquals(2090, EnergyRules.day(male, next, now, emptyList(), false).kcal)
    }

    @Test
    fun `a goal out of range is clamped on a trained day`() {
        val day = EnergyRules.day(male.copy(goalAdjustmentPercent = 99), today, now, listOf(sessionOf(strength())), false)

        // 2420 * 1.3 = 3146
        assertEquals(3150, day.kcal)
    }

    // rounding

    private val manual = male.copy(manualBmrKcal = 1000, weightKg = 61.25)

    @Test
    fun `half a ten rounds up`() {
        // 1200 + 245 = 1445 -> 1450, burn 245 -> 250
        val day = EnergyRules.day(manual, today, now, listOf(sessionOf(cardio(245))), false)

        assertEquals(1450, day.kcal)
        assertEquals(250, day.trainingBurnKcal)
    }

    @Test
    fun `just under half a ten rounds down`() {
        // 1200 + 244 = 1444 -> 1440, burn 244 -> 240
        val day = EnergyRules.day(manual, today, now, listOf(sessionOf(cardio(244))), false)

        assertEquals(1440, day.kcal)
        assertEquals(240, day.trainingBurnKcal)
    }

    @Test
    fun `just over half a ten rounds up`() {
        val day = EnergyRules.day(manual, today, now, listOf(sessionOf(cardio(246))), false)

        assertEquals(1450, day.kcal)
        assertEquals(250, day.trainingBurnKcal)
    }

    @Test
    fun `the expected burn is rounded too`() {
        // 4 * 61.25 = 245 -> 250; 1200 + 245 = 1445 -> 1450
        val day = EnergyRules.day(manual, today, now, emptyList(), trainingExpected = true)

        assertEquals(1450, day.kcal)
        assertEquals(250, day.trainingBurnKcal)
        assertEquals(1200, day.restDayKcal)
    }

    @Test
    fun `the rest day figure is rounded`() {
        // 1204 * 1.2 = 1444.8
        val day = EnergyRules.day(male.copy(manualBmrKcal = 1204), today, now, emptyList(), false)

        assertEquals(1440, day.kcal)
        assertEquals(1440, day.restDayKcal)
    }
}
