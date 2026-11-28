package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun cardio(calories: Int?, id: Long = 2L, seconds: Int? = 600) = WorkoutSet(
        id = id, sessionId = 1L, machineId = 2L, position = 1,
        weightKg = null, reps = null, seconds = seconds, calories = calories, distanceM = null,
        loggedAt = start.plusSeconds(3600), // logged when it ended, at the end of the hour
    )

    private fun sessionOf(vararg sets: WorkoutSet, minutes: Long? = 60) = SessionSets(session(minutes = minutes), sets.toList())

    private fun burn(profile: BodyProfile, vararg sessions: SessionSets, on: LocalDate = today, at: Instant = now) =
        EnergyRules.trainingBurnKcal(profile, on, sessions.toList(), at)

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

    // training burn (net: only the extra over the resting burn)

    @Test
    fun `no sessions burn nothing`() {
        assertEquals(0.0, burn(male), 1e-9)
    }

    @Test
    fun `an hour of strength burns three times the weight net`() {
        // (4 - 1) * 80 * 1 h
        assertEquals(240.0, burn(male, sessionOf(strength())), 1e-9)
    }

    @Test
    fun `strength burn scales with the duration`() {
        assertEquals(480.0, burn(male, sessionOf(strength(), minutes = 120)), 1e-9)
        assertEquals(120.0, burn(male, sessionOf(strength(), minutes = 30)), 1e-9)
    }

    @Test
    fun `strength duration is capped at three hours`() {
        // 3 * 80 * 3
        assertEquals(720.0, burn(male, sessionOf(strength(), minutes = 300)), 1e-9)
    }

    @Test
    fun `a closed session with zero duration counts twenty minutes`() {
        // 3 * 80 * 20 / 60
        assertEquals(80.0, burn(male, sessionOf(strength(), minutes = 0)), 1e-6)
    }

    @Test
    fun `an open session counts up to now`() {
        val open = SessionSets(session(minutes = null, startedAt = now.minusSeconds(30 * 60)), listOf(strength()))
        // 3 * 80 * 0.5
        assertEquals(120.0, burn(male, open), 1e-9)
    }

    @Test
    fun `strength burn is timed from the start of a cardio set logged at the end`() {
        // Cardio of 60 min (150 kcal) logged at 11:00, when it ended; the weights set at 11:10; the session ends at 11:20.
        // It began at 10:00, so it lasts 80 min: 3 * 80 * 80 / 60 = 320 for the weights.
        // Net cardio: 150 - 1750 / 1440 * 60 = 77.0833...
        val at11 = start.plusSeconds(3600)
        val sets = listOf(
            WorkoutSet(1L, 1L, 2L, 0, null, null, 3600, 150, null, at11),
            strength(id = 2L).copy(loggedAt = at11.plusSeconds(600), position = 1),
        )
        val open = SessionSets(Session(1L, at11, at11.plusSeconds(1200), null, null), sets)

        assertEquals(397.0833333, burn(male, open), 1e-6)
    }

    @Test
    fun `a cardio set that began the session gives it its full minutes, not zero`() {
        // Session added with the same start and end, then a 60 min cardio logged at 11:00: 60 min, not 20.
        // Net cardio 77.0833... plus 3 * 80 * 1 = 240.
        val at11 = start.plusSeconds(3600)
        val sets = listOf(
            WorkoutSet(1L, 1L, 2L, 0, null, null, 3600, 150, null, at11),
            strength(id = 2L).copy(loggedAt = at11, position = 1),
        )
        val hand = SessionSets(Session(1L, at11, at11, null, null), sets)

        assertEquals(317.0833333, burn(male, hand), 1e-6)
    }

    @Test
    fun `the three hour cap applies to the time from the start of the cardio`() {
        // Five hours of cardio (500 kcal, 300 min) logged at 15:00 and the weights a minute later.
        // Began at 10:00, so 5 h, capped at 3 h: 3 * 80 * 3 = 720. Net cardio 500 - 1750 / 1440 * 300 = 135.4166...
        val at15 = start.plusSeconds(5 * 3600L)
        val sets = listOf(
            WorkoutSet(1L, 1L, 2L, 0, null, null, 5 * 3600, 500, null, at15),
            strength(id = 2L).copy(loggedAt = at15.plusSeconds(60), position = 1),
        )
        val open = SessionSets(Session(1L, at15, at15.plusSeconds(300), null, null), sets)

        assertEquals(855.4166667, burn(male, open), 1e-6)
    }

    @Test
    fun `a cardio-only session burns its calories net of the resting burn`() {
        // 300 - 1750 / 1440 * 60 = 227.0833...
        val at11 = start.plusSeconds(3600)
        val only = SessionSets(
            Session(1L, at11, at11, null, null),
            listOf(WorkoutSet(1L, 1L, 2L, 0, null, null, 3600, 300, null, at11)),
        )

        assertEquals(227.0833333, burn(male, only), 1e-6)
    }

    @Test
    fun `cardio burn is net of the resting burn of its minutes`() {
        // 100 - 1750 / 1440 * 10 = 87.8472...
        assertEquals(87.8472222, burn(male, sessionOf(cardio(100))), 1e-6)
    }

    @Test
    fun `cardio below the resting burn of its minutes adds nothing`() {
        // 50 - 1750 / 1440 * 60 < 0
        val at11 = start.plusSeconds(3600)
        val low = SessionSets(
            Session(1L, at11, at11, null, null),
            listOf(WorkoutSet(1L, 1L, 2L, 0, null, null, 3600, 50, null, at11)),
        )

        assertEquals(0.0, burn(male, low), 1e-9)
    }

    @Test
    fun `cardio without seconds has no resting burn to take off`() {
        val noTime = SessionSets(
            Session(1L, start, start, null, null),
            listOf(WorkoutSet(1L, 1L, 2L, 0, null, null, null, 200, null, start)),
        )

        assertEquals(200.0, burn(male, noTime), 1e-9)
    }

    @Test
    fun `the resting burn of cardio uses the BMR of that date`() {
        val at11 = start.plusSeconds(3600)
        val hour = SessionSets(
            Session(1L, at11, at11, null, null),
            listOf(WorkoutSet(1L, 1L, 2L, 0, null, null, 3600, 300, null, at11)),
        )
        // 2026: age 36, BMR 1750 -> 300 - 72.9166... = 227.0833...
        assertEquals(227.0833333, burn(male, hour, on = today), 1e-6)
        // 2027-01-10: age 37, BMR 1745 -> 300 - 72.7083... = 227.2916...
        assertEquals(227.2916667, burn(male, hour, on = LocalDate.of(2027, 1, 10)), 1e-6)
    }

    @Test
    fun `an open session is capped at three hours`() {
        val open = SessionSets(session(minutes = null, startedAt = now.minusSeconds(10 * 3600)), listOf(strength()))
        assertEquals(720.0, burn(male, open), 1e-9)
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
        assertEquals(240.0, burn(male, sessionOf(strength(SetType.DROP))), 1e-9)
        assertEquals(240.0, burn(male, sessionOf(strength(SetType.FAILURE))), 1e-9)
    }

    @Test
    fun `cardio calories are summed net`() {
        // (100 - 12.1528) + (150 - 12.1528) = 225.6944...
        assertEquals(225.6944444, burn(male, sessionOf(cardio(100, id = 2L), cardio(150, id = 3L))), 1e-6)
    }

    @Test
    fun `cardio without calories burns nothing`() {
        assertEquals(0.0, burn(male, sessionOf(cardio(null))), 1e-9)
    }

    @Test
    fun `cardio and strength in one session add up`() {
        // 240 + (250 - 12.1528) = 477.8472...
        assertEquals(477.8472222, burn(male, sessionOf(strength(), cardio(250))), 1e-6)
    }

    @Test
    fun `sessions add up`() {
        val one = sessionOf(strength())
        val two = SessionSets(session(id = 2L, minutes = 30), listOf(strength(id = 5L)))
        // 240 + 120
        assertEquals(360.0, burn(male, one, two), 1e-9)
    }

    // day

    @Test
    fun `a session with sets is a trained day`() {
        val day = EnergyRules.day(male, today, now, listOf(sessionOf(strength())), trainingExpected = false)

        assertEquals(DayKind.TRAINED, day.kind)
        assertEquals(2340, day.kcal)
        assertEquals(2100, day.restDayKcal)
        assertEquals(240, day.trainingBurnKcal)
    }

    @Test
    fun `a trained day applies the goal to the whole total`() {
        val day = EnergyRules.day(male.copy(goalAdjustmentPercent = 10), today, now, listOf(sessionOf(strength())), false)

        // (2100 + 240) * 1.1 = 2574 -> 2570
        assertEquals(2570, day.kcal)
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
    fun `cardio only day adds its net calories`() {
        // 2100 + (300 - 12.1528) = 2387.8472 -> 2390; burn 287.8472 -> 290
        val day = EnergyRules.day(male, today, now, listOf(sessionOf(cardio(300))), false)

        assertEquals(DayKind.TRAINED, day.kind)
        assertEquals(2390, day.kcal)
        assertEquals(290, day.trainingBurnKcal)
    }

    @Test
    fun `an expected training day assumes one hour of net strength`() {
        val day = EnergyRules.day(male, today, now, emptyList(), trainingExpected = true)

        // 3 * 80 * 1 = 240
        assertEquals(DayKind.TRAINING_EXPECTED, day.kind)
        assertEquals(2340, day.kcal)
        assertEquals(2100, day.restDayKcal)
        assertEquals(240, day.trainingBurnKcal)
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

        // 2100 + 120
        assertEquals(DayKind.TRAINED, day.kind)
        assertEquals(2220, day.kcal)
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

        // (2100 + 240) * 1.3 = 3042 -> 3040
        assertEquals(3040, day.kcal)
    }

    // weight on a day

    @Test
    fun `without entries the weight is the profile's`() {
        assertEquals(80.0, EnergyRules.weightOn(male, emptyList(), today), 1e-9)
    }

    @Test
    fun `the weight on a day is the newest entry on or before it`() {
        val weights = listOf(
            WeightEntry(LocalDate.of(2026, 11, 10), 77.0),
            WeightEntry(LocalDate.of(2026, 11, 1), 79.0),
            WeightEntry(LocalDate.of(2026, 11, 5), 78.5),
        )

        assertEquals(79.0, EnergyRules.weightOn(male, weights, LocalDate.of(2026, 11, 4)), 1e-9)
        assertEquals(78.5, EnergyRules.weightOn(male, weights, LocalDate.of(2026, 11, 5)), 1e-9)
        assertEquals(77.0, EnergyRules.weightOn(male, weights, LocalDate.of(2026, 11, 30)), 1e-9)
    }

    @Test
    fun `before the first entry the weight is the profile's`() {
        val weights = listOf(WeightEntry(LocalDate.of(2026, 11, 1), 79.0))

        assertEquals(80.0, EnergyRules.weightOn(male, weights, LocalDate.of(2026, 10, 31)), 1e-9)
    }

    // history

    private fun history(
        from: LocalDate,
        to: LocalDate,
        sessions: Map<LocalDate, List<SessionSets>> = emptyMap(),
        weights: List<WeightEntry> = emptyList(),
        profile: BodyProfile = male,
    ) = EnergyRules.history(profile, weights, sessions, from, to, now)

    private val nov1 = LocalDate.of(2026, 11, 1)

    @Test
    fun `history has one point per calendar day, rest days included`() {
        val h = history(nov1, LocalDate.of(2026, 11, 5))

        assertEquals(5, h.points.size)
        assertEquals((1..5).map { LocalDate.of(2026, 11, it) }, h.points.map { it.date })
        assertEquals(setOf(2100), h.points.map { it.kcal }.toSet())
        assertEquals(setOf(0), h.points.map { it.burnKcal }.toSet())
        assertEquals(setOf(false), h.points.map { it.trained }.toSet())
    }

    @Test
    fun `history includes the days before the first session or weight entry`() {
        val h = history(
            LocalDate.of(2026, 10, 29),
            LocalDate.of(2026, 11, 2),
            sessions = mapOf(LocalDate.of(2026, 11, 2) to listOf(sessionOf(strength()))),
        )

        assertEquals(5, h.points.size)
        assertEquals(LocalDate.of(2026, 10, 29), h.points.first().date)
        assertEquals(2100, h.points.first().kcal)
        assertEquals(false, h.points.first().trained)
    }

    @Test
    fun `history never includes days after the end`() {
        val h = history(nov1, LocalDate.of(2026, 11, 3), sessions = mapOf(LocalDate.of(2026, 11, 10) to listOf(sessionOf(strength()))))

        assertEquals(3, h.points.size)
        assertEquals(LocalDate.of(2026, 11, 3), h.points.last().date)
        assertEquals(0, h.trainedDays)
    }

    @Test
    fun `a day with sets is a trained point with its net burn`() {
        val h = history(nov1, LocalDate.of(2026, 11, 2), sessions = mapOf(nov1 to listOf(sessionOf(strength()))))

        val trained = h.points.first()
        assertEquals(true, trained.trained)
        assertEquals(240, trained.burnKcal)
        assertEquals(2340, trained.kcal)
        assertEquals(false, h.points[1].trained)
        assertEquals(2100, h.points[1].kcal)
    }

    @Test
    fun `sessions on one day add up`() {
        val two = SessionSets(session(id = 2L, minutes = 30), listOf(strength(id = 5L)))
        val h = history(nov1, nov1, sessions = mapOf(nov1 to listOf(sessionOf(strength()), two)))

        // burn 240 + 120 = 360; kcal 2100 + 360 = 2460
        assertEquals(360, h.points.single().burnKcal)
        assertEquals(2460, h.points.single().kcal)
        assertEquals(1, h.trainedDays)
    }

    @Test
    fun `a session without sets is a rest point and not a trained day`() {
        val h = history(nov1, nov1, sessions = mapOf(nov1 to listOf(SessionSets(session(), emptyList()))))

        assertEquals(false, h.points.single().trained)
        assertEquals(2100, h.points.single().kcal)
        assertEquals(0, h.trainedDays)
        assertNull(h.averageBurnKcal)
    }

    @Test
    fun `each day uses the weight of that day`() {
        val weights = listOf(WeightEntry(LocalDate.of(2026, 11, 2), 70.0))
        val h = history(
            nov1,
            LocalDate.of(2026, 11, 3),
            sessions = mapOf(LocalDate.of(2026, 11, 3) to listOf(sessionOf(strength()))),
            weights = weights,
        )

        // 11-01: no entry yet, 80 kg -> 2100. 11-02: 70 kg -> 1650 * 1.2 = 1980. 11-03: 70 kg, burn 3 * 70 = 210 -> 2190.
        assertEquals(2100, h.points[0].kcal)
        assertEquals(1980, h.points[1].kcal)
        assertEquals(2190, h.points[2].kcal)
        assertEquals(210, h.points[2].burnKcal)
    }

    @Test
    fun `no days in the range gives no points and no averages`() {
        val h = history(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 4))

        assertEquals(emptyList<EnergyPoint>(), h.points)
        assertNull(h.last7AverageKcal)
        assertNull(h.last28AverageKcal)
        assertNull(h.averageBurnKcal)
        assertEquals(0, h.trainedDays)
    }

    @Test
    fun `the 7-day average is the mean of the last seven points`() {
        // 11-01..11-07, trained on 11-07 only: (6 * 2100 + 2340) / 7 = 2134.3 -> 2130
        val h = history(nov1, LocalDate.of(2026, 11, 7), sessions = mapOf(LocalDate.of(2026, 11, 7) to listOf(sessionOf(strength()))))

        assertEquals(2130, h.last7AverageKcal)
    }

    @Test
    fun `the 28-day average needs seven points and uses what there is`() {
        // exactly seven points: the 28-day window holds all of them
        val h = history(nov1, LocalDate.of(2026, 11, 7), sessions = mapOf(LocalDate.of(2026, 11, 7) to listOf(sessionOf(strength()))))

        assertEquals(2130, h.last28AverageKcal)
    }

    @Test
    fun `the 28-day average is null with fewer than seven points`() {
        val h = history(nov1, LocalDate.of(2026, 11, 6))

        assertEquals(2100, h.last7AverageKcal)
        assertNull(h.last28AverageKcal)
    }

    @Test
    fun `the 7-day average covers only the last seven days`() {
        val sessions = mapOf(
            nov1 to listOf(sessionOf(strength())),
            LocalDate.of(2026, 11, 2) to listOf(sessionOf(strength())),
            LocalDate.of(2026, 11, 3) to listOf(sessionOf(strength())),
        )
        val h = history(nov1, LocalDate.of(2026, 11, 10), sessions = sessions)

        // last 7 (11-04..11-10) are all rest days
        assertEquals(2100, h.last7AverageKcal)
        // all ten: (3 * 2340 + 7 * 2100) / 10 = 2172 -> 2170
        assertEquals(2170, h.last28AverageKcal)
        assertEquals(3, h.trainedDays)
    }

    @Test
    fun `the average burn is the mean over trained days only`() {
        val sessions = mapOf(
            nov1 to listOf(sessionOf(strength())),
            LocalDate.of(2026, 11, 2) to listOf(sessionOf(strength(), minutes = 30)),
        )
        val h = history(nov1, LocalDate.of(2026, 11, 3), sessions = sessions)

        // (240 + 120) / 2 = 180, the rest day on 11-03 not counted
        assertEquals(180, h.averageBurnKcal)
        assertEquals(2, h.trainedDays)
    }

    @Test
    fun `the average burn is null without trained days`() {
        val h = history(nov1, LocalDate.of(2026, 11, 3))

        assertNull(h.averageBurnKcal)
        assertEquals(0, h.trainedDays)
    }

    // rounding

    private val manual = male.copy(manualBmrKcal = 1000, weightKg = 61.25)

    @Test
    fun `half a ten rounds up`() {
        // 1200 + 245 = 1445 -> 1450, burn 245 -> 250 (seconds 0: the calories are the net)
        val day = EnergyRules.day(manual, today, now, listOf(sessionOf(cardio(245, seconds = 0))), false)

        assertEquals(1450, day.kcal)
        assertEquals(250, day.trainingBurnKcal)
    }

    @Test
    fun `just under half a ten rounds down`() {
        // 1200 + 244 = 1444 -> 1440, burn 244 -> 240
        val day = EnergyRules.day(manual, today, now, listOf(sessionOf(cardio(244, seconds = 0))), false)

        assertEquals(1440, day.kcal)
        assertEquals(240, day.trainingBurnKcal)
    }

    @Test
    fun `just over half a ten rounds up`() {
        val day = EnergyRules.day(manual, today, now, listOf(sessionOf(cardio(246, seconds = 0))), false)

        assertEquals(1450, day.kcal)
        assertEquals(250, day.trainingBurnKcal)
    }

    @Test
    fun `the expected burn is rounded too`() {
        // 3 * 61.25 = 183.75 -> 180; 1200 + 183.75 = 1383.75 -> 1380
        val day = EnergyRules.day(manual, today, now, emptyList(), trainingExpected = true)

        assertEquals(1380, day.kcal)
        assertEquals(180, day.trainingBurnKcal)
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
