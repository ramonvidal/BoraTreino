package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Statistics rules. "Today" is Thursday 2026-11-05 and weeks start on Monday unless a test says otherwise, so the
 * current week starts on Monday 2026-11-02. Every test builds a tiny data set on explicit dates.
 */
class StatsRulesTest {
    private val today = LocalDate.of(2026, 11, 5)
    private val utc: ZoneId = ZoneOffset.UTC

    private fun d(month: Int, day: Int, year: Int = 2026): LocalDate = LocalDate.of(year, month, day)

    private fun at(date: LocalDate, hour: Int = 10, minute: Int = 0): Instant =
        date.atTime(hour, minute).toInstant(ZoneOffset.UTC)

    /** Builds machines, sessions and sets with automatic ids. */
    private class Log {
        val machines = mutableListOf<Machine>()
        val sessions = mutableListOf<Session>()
        val sets = mutableListOf<WorkoutSet>()

        fun machine(group: MuscleGroup = MuscleGroup.CHEST, kind: MachineKind = MachineKind.WEIGHT_REPS): Long {
            val id = machines.size + 1L
            machines += Machine(id, "Machine $id", group, kind, 2.5, null, null, false)
            return id
        }

        fun session(date: LocalDate, hour: Int = 10, minute: Int = 0, endedAfterSeconds: Long? = null): Long {
            val id = sessions.size + 1L
            val start = date.atTime(hour, minute).toInstant(ZoneOffset.UTC)
            sessions += Session(id, start, endedAfterSeconds?.let { start.plusSeconds(it) }, null, null)
            return id
        }

        fun set(
            sessionId: Long,
            machineId: Long,
            weightKg: Double?,
            reps: Int?,
            type: SetType = SetType.NORMAL,
            position: Int = 0,
            seconds: Int? = null,
            loggedAt: Instant? = null,
        ): WorkoutSet {
            val start = sessions.first { it.id == sessionId }.startedAt
            val set = WorkoutSet(
                id = sets.size + 1L,
                sessionId = sessionId,
                machineId = machineId,
                position = position,
                weightKg = weightKg,
                reps = reps,
                seconds = seconds,
                calories = null,
                distanceM = null,
                loggedAt = loggedAt ?: start.plusSeconds(60L * sets.size),
                type = type,
            )
            sets += set
            return set
        }

        /** A new session on [date] with one set; returns the session id. */
        fun lift(date: LocalDate, machineId: Long, weightKg: Double?, reps: Int?, type: SetType = SetType.NORMAL): Long {
            val s = session(date)
            set(s, machineId, weightKg, reps, type)
            return s
        }

        /** A new session on [date] with one cardio set. */
        fun cardio(date: LocalDate, machineId: Long, seconds: Int = 600): Long {
            val s = session(date)
            set(s, machineId, null, null, seconds = seconds)
            return s
        }
    }

    private fun report(
        log: Log,
        period: StatsPeriod = StatsPeriod.FOUR_WEEKS,
        goal: Int = 3,
        first: DayOfWeek = DayOfWeek.MONDAY,
        zone: ZoneId = utc,
        now: LocalDate = today,
    ): StatsReport = StatsRules.report(period, now, first, goal, log.machines, log.sessions, log.sets, zone)

    // Period windows

    @Test
    fun `four weeks is the current week and the three before`() {
        val r = report(Log(), StatsPeriod.FOUR_WEEKS)

        assertEquals(d(10, 12), r.from)
        assertEquals(today, r.to)
        assertEquals(StatsPeriod.FOUR_WEEKS, r.period)
        assertEquals(listOf(d(10, 12), d(10, 19), d(10, 26), d(11, 2)), r.weeks.map { it.weekStart })
    }

    @Test
    fun `three months is 13 weeks`() {
        val r = report(Log(), StatsPeriod.THREE_MONTHS)

        assertEquals(13, r.weeks.size)
        assertEquals(d(8, 10), r.from)
        assertEquals(d(8, 10), r.weeks.first().weekStart)
        assertEquals(d(11, 2), r.weeks.last().weekStart)
    }

    @Test
    fun `one year is 52 weeks`() {
        val r = report(Log(), StatsPeriod.ONE_YEAR)

        assertEquals(52, r.weeks.size)
        assertEquals(d(11, 10, 2025), r.from)
        assertEquals(d(11, 2), r.weeks.last().weekStart)
    }

    @Test
    fun `all starts at the week of the first session`() {
        val log = Log()
        val m = log.machine()
        log.lift(d(9, 16), m, 50.0, 10) // Wednesday, week of Monday 14 September
        log.lift(d(11, 3), m, 50.0, 10)

        val r = report(log, StatsPeriod.ALL)

        assertEquals(d(9, 14), r.from)
        assertEquals(8, r.weeks.size)
        assertEquals(d(9, 14), r.weeks.first().weekStart)
        assertEquals(d(11, 2), r.weeks.last().weekStart)
    }

    @Test
    fun `all is at least four weeks when the first session is this week`() {
        val log = Log()
        log.lift(d(11, 3), log.machine(), 50.0, 10)

        val r = report(log, StatsPeriod.ALL)

        assertEquals(4, r.weeks.size)
        assertEquals(d(10, 12), r.from)
    }

    @Test
    fun `all is four weeks when there are no sessions`() {
        val r = report(Log(), StatsPeriod.ALL)

        assertEquals(4, r.weeks.size)
        assertEquals(d(10, 12), r.from)
        assertTrue(r.weeks.all { it.sessions == 0 && it.workingSets == 0 && it.volumeKg == 0.0 })
    }

    @Test
    fun `weeks can start on a Sunday`() {
        val log = Log()
        val m = log.machine()
        log.lift(d(11, 1), m, 50.0, 10) // Sunday, first day of the current week
        log.lift(d(10, 11), m, 50.0, 10) // Sunday, first day of the first week

        val r = report(log, first = DayOfWeek.SUNDAY)

        assertEquals(d(10, 11), r.from)
        assertEquals(d(11, 1), r.weeks.last().weekStart)
        assertEquals(1, r.weeks.last().sessions)
        assertEquals(1, r.weeks.first().sessions)
    }

    @Test
    fun `sets before the first day of the period are left out of the weeks but sets on it count`() {
        val log = Log()
        val m = log.machine()
        log.lift(d(10, 11), m, 50.0, 10) // Sunday before the period
        log.lift(d(10, 12), m, 50.0, 10) // first day of the period

        val r = report(log)

        assertEquals(listOf(1, 0, 0, 0), r.weeks.map { it.sessions })
    }

    @Test
    fun `sets after today are left out`() {
        val log = Log()
        log.lift(d(11, 6), log.machine(), 50.0, 10)

        val r = report(log)

        assertTrue(r.weeks.all { it.sessions == 0 })
    }

    // Weeks

    @Test
    fun `weeks are zero filled and oldest first`() {
        val log = Log()
        log.lift(d(10, 20), log.machine(), 40.0, 10)

        val r = report(log)

        assertEquals(listOf(0, 1, 0, 0), r.weeks.map { it.sessions })
        assertEquals(listOf(0.0, 400.0, 0.0, 0.0), r.weeks.map { it.volumeKg })
        assertEquals(listOf(0, 1, 0, 0), r.weeks.map { it.workingSets })
    }

    @Test
    fun `volume is weight times reps of working sets and warm ups are left out`() {
        val log = Log()
        val m = log.machine()
        val s = log.session(d(11, 3))
        log.set(s, m, 50.0, 10, position = 0)                              // 500
        log.set(s, m, 20.0, 10, type = SetType.WARMUP, position = 1)       // excluded
        log.set(s, m, null, 8, position = 2)                               // counts as a set, 0 volume
        log.set(s, m, 60.0, 5, type = SetType.DROP, position = 3)          // 300

        val week = report(log).weeks.last()

        assertEquals(3, week.workingSets)
        assertEquals(800.0, week.volumeKg, 1e-9)
    }

    @Test
    fun `a set with null reps adds no volume`() {
        val log = Log()
        val m = log.machine()
        val s = log.session(d(11, 3))
        log.set(s, m, 50.0, null)

        val week = report(log).weeks.last()

        assertEquals(1, week.workingSets)
        assertEquals(0.0, week.volumeKg, 0.0)
    }

    @Test
    fun `training days are distinct dates and sessions are distinct ids`() {
        val log = Log()
        val m = log.machine()
        log.lift(d(11, 3), m, 50.0, 10)
        log.lift(d(11, 3), m, 50.0, 10) // second session the same day
        log.lift(d(11, 4), m, 50.0, 10)

        val week = report(log).weeks.last()

        assertEquals(3, week.sessions)
        assertEquals(2, week.trainingDays)
    }

    @Test
    fun `a warm up only session does not count as a session or a training day`() {
        val log = Log()
        val m = log.machine()
        log.lift(d(11, 3), m, 50.0, 10)
        log.lift(d(11, 4), m, 30.0, 10, type = SetType.WARMUP)

        val week = report(log).weeks.last()

        assertEquals(1, week.sessions)
        assertEquals(1, week.trainingDays)
        assertEquals(1, week.workingSets)
    }

    @Test
    fun `a cardio only session is a session and a training day without working sets or volume`() {
        val log = Log()
        val bike = log.machine(MuscleGroup.CARDIO, MachineKind.CARDIO)
        log.cardio(d(11, 3), bike)

        val week = report(log).weeks.last()

        assertEquals(1, week.sessions)
        assertEquals(1, week.trainingDays)
        assertEquals(0, week.workingSets)
        assertEquals(0.0, week.volumeKg, 0.0)
    }

    @Test
    fun `a set that carries seconds is cardio and is left out of volume even on a weight machine`() {
        val log = Log()
        val m = log.machine()
        val s = log.session(d(11, 3))
        log.set(s, m, 50.0, 10, position = 0)
        log.set(s, m, 40.0, 10, position = 1, seconds = 600)

        val week = report(log).weeks.last()

        assertEquals(1, week.workingSets)
        assertEquals(500.0, week.volumeKg, 1e-9)
    }

    // Muscle weeks

    @Test
    fun `muscle weeks list only groups with working sets sorted by week then group order`() {
        val log = Log()
        val chest = log.machine(MuscleGroup.CHEST)
        val legs = log.machine(MuscleGroup.LEGS)
        val back = log.machine(MuscleGroup.BACK)
        val arms = log.machine(MuscleGroup.ARMS)
        val bike = log.machine(MuscleGroup.CARDIO, MachineKind.CARDIO)
        val current = log.session(d(11, 3))
        log.set(current, chest, 50.0, 10, position = 0)        // 500
        log.set(current, chest, 50.0, 8, position = 1)         // 400
        log.set(current, legs, 100.0, 5, position = 2)         // 500
        log.set(current, arms, 20.0, 10, type = SetType.WARMUP, position = 3)
        log.set(current, bike, null, null, seconds = 600, position = 4)
        log.lift(d(10, 28), back, 40.0, 10)                    // 400, earlier week

        val weeks = report(log).muscleWeeks

        assertEquals(
            listOf(
                MuscleWeek(d(10, 26), MuscleGroup.BACK, 1, 400.0),
                MuscleWeek(d(11, 2), MuscleGroup.LEGS, 1, 500.0),
                MuscleWeek(d(11, 2), MuscleGroup.CHEST, 2, 900.0),
            ),
            weeks,
        )
    }

    // Recency

    @Test
    fun `recency covers every group in order and looks at the whole history`() {
        val log = Log()
        val chest = log.machine(MuscleGroup.CHEST)
        val legs = log.machine(MuscleGroup.LEGS)
        val arms = log.machine(MuscleGroup.ARMS)
        val bike = log.machine(MuscleGroup.CARDIO, MachineKind.CARDIO)
        log.lift(d(11, 3), chest, 50.0, 10)
        log.lift(d(10, 1), legs, 100.0, 5)        // far outside four weeks
        log.lift(d(11, 4), arms, 20.0, 10, type = SetType.WARMUP)
        log.cardio(d(10, 30), bike)

        val recency = report(log).recency

        assertEquals(MuscleGroup.entries.toList(), recency.map { it.muscleGroup })
        val byGroup = recency.associateBy { it.muscleGroup }
        assertEquals(d(11, 3), byGroup.getValue(MuscleGroup.CHEST).lastTrained)
        assertEquals(2, byGroup.getValue(MuscleGroup.CHEST).daysSince)
        assertEquals(d(10, 1), byGroup.getValue(MuscleGroup.LEGS).lastTrained)
        assertEquals(35, byGroup.getValue(MuscleGroup.LEGS).daysSince)
        assertEquals(d(10, 30), byGroup.getValue(MuscleGroup.CARDIO).lastTrained)
        assertEquals(6, byGroup.getValue(MuscleGroup.CARDIO).daysSince)
        // Never trained, and a warm up does not count.
        for (group in listOf(MuscleGroup.BACK, MuscleGroup.SHOULDERS, MuscleGroup.CORE, MuscleGroup.ARMS)) {
            assertNull(byGroup.getValue(group).lastTrained)
            assertNull(byGroup.getValue(group).daysSince)
        }
    }

    @Test
    fun `recency uses the latest date of the group`() {
        val log = Log()
        val chest = log.machine(MuscleGroup.CHEST)
        log.lift(d(11, 5), chest, 50.0, 10)
        log.lift(d(11, 1), chest, 50.0, 10)

        val chestRecency = report(log).recency.first { it.muscleGroup == MuscleGroup.CHEST }

        assertEquals(d(11, 5), chestRecency.lastTrained)
        assertEquals(0, chestRecency.daysSince)
    }

    // Consistency

    private fun consistencyLog(): Log {
        val log = Log()
        val m = log.machine()
        // Week of 12 Oct: 3 days, 19 Oct: 1 day, 26 Oct: 2 days, current week: 1 day. 7 sessions in all.
        for (date in listOf(d(10, 12), d(10, 13), d(10, 14), d(10, 20), d(10, 27), d(10, 28), d(11, 3))) {
            log.lift(date, m, 50.0, 10)
        }
        return log
    }

    @Test
    fun `the current week is not counted until it met the goal`() {
        val c = report(consistencyLog(), goal = 2).consistency

        assertEquals(3, c.weeksCounted)
        assertEquals(2, c.weeksMetGoal)
        assertEquals(2, c.goal)
        assertEquals(7 / 4.0, c.sessionsPerWeek, 1e-9)
        assertEquals(d(10, 12), c.bestWeekStart)
        assertEquals(3, c.bestWeekTrainingDays)
    }

    @Test
    fun `the current week is counted once it met the goal`() {
        val c = report(consistencyLog(), goal = 1).consistency

        assertEquals(4, c.weeksCounted)
        assertEquals(4, c.weeksMetGoal)
        assertEquals(1, c.goal)
    }

    @Test
    fun `the goal is clamped to 1 and 7`() {
        assertEquals(1, report(consistencyLog(), goal = 0).consistency.goal)
        assertEquals(1, report(consistencyLog(), goal = -3).consistency.goal)
        val high = report(consistencyLog(), goal = 10).consistency
        assertEquals(7, high.goal)
        assertEquals(0, high.weeksMetGoal)
        assertEquals(3, high.weeksCounted)
    }

    @Test
    fun `the best week ties go to the latest week`() {
        val log = Log()
        val m = log.machine()
        log.lift(d(10, 13), m, 50.0, 10)
        log.lift(d(10, 14), m, 50.0, 10)
        log.lift(d(10, 27), m, 50.0, 10)
        log.lift(d(10, 28), m, 50.0, 10)

        val c = report(log).consistency

        assertEquals(d(10, 26), c.bestWeekStart)
        assertEquals(2, c.bestWeekTrainingDays)
    }

    @Test
    fun `with no sessions consistency is empty`() {
        val c = report(Log(), goal = 3).consistency

        assertEquals(3, c.weeksCounted)
        assertEquals(0, c.weeksMetGoal)
        assertEquals(0.0, c.sessionsPerWeek, 0.0)
        assertNull(c.bestWeekStart)
        assertEquals(0, c.bestWeekTrainingDays)
    }

    @Test
    fun `sessions per week divides by all weeks of the period`() {
        val log = Log()
        val m = log.machine()
        log.lift(d(11, 3), m, 50.0, 10)
        log.lift(d(11, 4), m, 50.0, 10)

        val c = report(log, StatsPeriod.THREE_MONTHS).consistency

        assertEquals(2 / 13.0, c.sessionsPerWeek, 1e-9)
    }

    // Strength

    @Test
    fun `strength compares the first and last windows and sorts by change percent`() {
        val log = Log()
        val a = log.machine()   // 1
        val b = log.machine()   // 2
        val e = log.machine()   // 3
        val onlyLast = log.machine()    // 4
        val onlyFirst = log.machine()   // 5
        val tooManyReps = log.machine() // 6
        val bike = log.machine(MuscleGroup.CARDIO, MachineKind.CARDIO) // 7
        // Windows of a four week period: first = 12 and 19 Oct, last = 26 Oct and the current week.
        val warmupDay = log.session(d(10, 13))
        log.set(warmupDay, a, 60.0, 10, position = 0)                         // 80
        log.set(warmupDay, a, 200.0, 1, type = SetType.WARMUP, position = 1)  // no estimate
        log.lift(d(10, 20), a, 50.0, 1)                                       // 50, lower
        log.lift(d(10, 27), a, 75.0, 4)                                       // 85
        log.lift(d(11, 3), a, 70.0, 1)                                        // 70
        log.lift(d(10, 14), b, 100.0, 1)
        log.lift(d(10, 28), b, 90.0, 1)
        log.lift(d(10, 13), e, 40.0, 1)
        log.lift(d(10, 27), e, 60.0, 1)
        log.lift(d(10, 27), onlyLast, 80.0, 1)
        log.lift(d(10, 13), onlyFirst, 80.0, 1)
        log.lift(d(10, 13), tooManyReps, 50.0, 15)
        log.lift(d(10, 27), tooManyReps, 50.0, 1)
        log.cardio(d(10, 13), bike)
        log.cardio(d(10, 27), bike)

        val r = report(log)

        assertEquals(listOf(e, a, b), r.strength.map { it.machine.id })
        val trendE = r.strength[0]
        assertEquals(40.0, trendE.before, 1e-9)
        assertEquals(60.0, trendE.after, 1e-9)
        assertEquals(20.0, trendE.changeKg, 1e-9)
        assertEquals(50.0, trendE.changePercent, 1e-9)
        val trendA = r.strength[1]
        assertEquals(80.0, trendA.before, 1e-9)
        assertEquals(85.0, trendA.after, 1e-9)
        assertEquals(5.0, trendA.changeKg, 1e-9)
        assertEquals(6.25, trendA.changePercent, 1e-9)
        val trendB = r.strength[2]
        assertEquals(-10.0, trendB.changeKg, 1e-9)
        assertEquals(-10.0, trendB.changePercent, 1e-9)
        assertEquals((50.0 + 6.25 - 10.0) / 3, r.overallStrengthPercent!!, 1e-6)
    }

    @Test
    fun `strength is empty and the overall change is null when no machine has both windows`() {
        val log = Log()
        val m = log.machine()
        log.lift(d(10, 13), m, 80.0, 5) // first window only
        log.lift(d(11, 3), log.machine(), 80.0, 5) // last window only

        val r = report(log)

        assertTrue(r.strength.isEmpty())
        assertNull(r.overallStrengthPercent)
    }

    @Test
    fun `strength is empty with no data`() {
        val r = report(Log())

        assertTrue(r.strength.isEmpty())
        assertNull(r.overallStrengthPercent)
    }

    @Test
    fun `strength windows are four weeks when the period has eight weeks`() {
        val log = Log()
        val m = log.machine()
        // ALL from 14 Sep: eight weeks, windows of four. The first window ends before Monday 12 Oct.
        log.lift(d(9, 16), m, 100.0, 1)
        log.lift(d(10, 8), m, 110.0, 1)
        log.lift(d(10, 11), m, 120.0, 1) // still the first window
        log.lift(d(10, 12), m, 130.0, 1) // the last window

        val r = report(log, StatsPeriod.ALL)

        assertEquals(1, r.strength.size)
        assertEquals(120.0, r.strength.single().before, 1e-9)
        assertEquals(130.0, r.strength.single().after, 1e-9)
        assertEquals(10.0 / 120.0 * 100.0, r.strength.single().changePercent, 1e-9)
    }

    @Test
    fun `strength windows are four weeks in a 13 week period and the weeks between are in neither`() {
        val log = Log()
        val m = log.machine()
        // From 10 Aug: the first window is the weeks of 10, 17, 24 and 31 Aug; the last starts on Monday 12 Oct.
        log.lift(d(8, 12), m, 100.0, 1)  // first window
        log.lift(d(9, 9), m, 300.0, 1)   // week of 7 Sep: in neither
        log.lift(d(10, 11), m, 400.0, 1) // Sunday before the last window: in neither
        log.lift(d(10, 13), m, 110.0, 1) // last window

        val r = report(log, StatsPeriod.THREE_MONTHS)

        assertEquals(100.0, r.strength.single().before, 1e-9)
        assertEquals(110.0, r.strength.single().after, 1e-9)
    }

    // Records

    @Test
    fun `records are replayed over the whole history but only events in the period are kept newest first`() {
        val log = Log()
        val a = log.machine() // 1
        val b = log.machine() // 2
        log.lift(d(9, 1), a, 100.0, 5)   // first time: no record
        log.lift(d(9, 15), a, 105.0, 5)  // record, but outside the period
        log.lift(d(10, 14), a, 110.0, 5) // heaviest weight
        log.lift(d(10, 28), a, 110.0, 6) // most reps at 110
        val last = log.session(d(11, 3))
        log.set(last, a, 100.0, 5, position = 0)                        // matches, no record
        log.set(last, a, 200.0, 5, type = SetType.WARMUP, position = 1) // warm ups are never a record
        log.lift(d(10, 6), b, 50.0, 10)
        log.lift(d(10, 20), b, 60.0, 10)

        val events = report(log).records

        assertEquals(listOf(d(10, 28), d(10, 20), d(10, 14)), events.map { it.date })
        assertEquals(listOf(1L, 2L, 1L), events.map { it.machine.id })
        assertEquals(Record.MostReps(110.0, 6), events[0].record)
        assertEquals(Record.HeaviestWeight(60.0), events[1].record)
        assertEquals(Record.HeaviestWeight(110.0), events[2].record)
    }

    @Test
    fun `sets of one session are replayed in position order`() {
        val log = Log()
        val m = log.machine()
        log.lift(d(10, 20), m, 110.0, 5)
        val s = log.session(d(11, 4))
        // Added in reverse order; position decides.
        log.set(s, m, 125.0, 5, position = 1)
        log.set(s, m, 120.0, 5, position = 0)

        val events = report(log).records

        assertEquals(2, events.size)
        assertEquals(setOf<Record>(Record.HeaviestWeight(120.0), Record.HeaviestWeight(125.0)), events.map { it.record }.toSet())
        assertTrue(events.all { it.date == d(11, 4) })
    }

    @Test
    fun `cardio sets are never records`() {
        val log = Log()
        val bike = log.machine(MuscleGroup.CARDIO, MachineKind.CARDIO)
        log.cardio(d(10, 14), bike, 600)
        log.cardio(d(10, 28), bike, 1200)

        assertTrue(report(log).records.isEmpty())
    }

    @Test
    fun `a record set before the period starts is not listed`() {
        val log = Log()
        val m = log.machine()
        log.lift(d(9, 1), m, 100.0, 5)
        log.lift(d(9, 8), m, 110.0, 5)

        assertTrue(report(log).records.isEmpty())
    }

    // Plateaus

    private fun plateauLog(bests: List<Double>): Pair<Log, Long> {
        val log = Log()
        val m = log.machine()
        val dates = listOf(d(9, 21), d(9, 28), d(10, 5), d(10, 12), d(10, 19), d(10, 26))
        bests.forEachIndexed { i, kg -> log.lift(dates[i], m, kg, 1) }
        return log to m
    }

    @Test
    fun `six sessions with no new best for five sessions is a plateau`() {
        val (log, m) = plateauLog(listOf(90.0, 90.0, 90.0, 90.0, 90.0, 90.0))
        // Session one also had a lower set; a heavy warm up in session three is not an estimate.
        log.set(1L, m, 80.0, 1, position = 1)
        log.set(3L, m, 300.0, 1, type = SetType.WARMUP, position = 1)
        log.set(1L, m, 100.0, 1, position = 2)

        val plateaus = report(log).plateaus

        assertEquals(1, plateaus.size)
        assertEquals(m, plateaus.single().machine.id)
        assertEquals(5, plateaus.single().sessionsWithoutImprovement)
        assertEquals(100.0, plateaus.single().bestEstimate, 1e-9)
    }

    @Test
    fun `four sessions since the best is a plateau`() {
        val (log, _) = plateauLog(listOf(90.0, 100.0, 90.0, 90.0, 90.0, 90.0))

        val plateau = report(log).plateaus.single()

        assertEquals(4, plateau.sessionsWithoutImprovement)
        assertEquals(100.0, plateau.bestEstimate, 1e-9)
    }

    @Test
    fun `three sessions since the best is not a plateau`() {
        val (log, _) = plateauLog(listOf(90.0, 90.0, 100.0, 90.0, 90.0, 90.0))

        assertTrue(report(log).plateaus.isEmpty())
    }

    @Test
    fun `a tie for the best counts the earliest session as the one that set it`() {
        val (log, _) = plateauLog(listOf(100.0, 100.0, 90.0, 90.0, 90.0, 90.0))

        assertEquals(5, report(log).plateaus.single().sessionsWithoutImprovement)
    }

    @Test
    fun `a machine with fewer than six sessions is never a plateau`() {
        val log = Log()
        val m = log.machine()
        for (date in listOf(d(9, 21), d(9, 28), d(10, 5), d(10, 12), d(10, 19))) log.lift(date, m, 90.0, 1)
        log.sets.clear()
        log.set(1L, m, 100.0, 1)
        for (id in 2L..5L) log.set(id, m, 90.0, 1)

        assertTrue(report(log).plateaus.isEmpty())
    }

    @Test
    fun `sessions without an estimate do not count towards the six`() {
        val log = Log()
        val m = log.machine()
        log.lift(d(9, 21), m, 100.0, 1)
        for (date in listOf(d(9, 28), d(10, 5), d(10, 12), d(10, 19))) log.lift(date, m, 90.0, 1)
        log.lift(d(10, 26), m, 90.0, 15) // 15 reps: no estimate

        assertTrue(report(log).plateaus.isEmpty())
    }

    @Test
    fun `the plateau looks at the whole history, not only the period`() {
        val (log, _) = plateauLog(listOf(100.0, 90.0, 90.0, 90.0, 90.0, 90.0))

        // Only the last four of the six sessions fall inside four weeks.
        val r = report(log, StatsPeriod.FOUR_WEEKS)

        assertEquals(1, r.plateaus.size)
    }

    // Rep ranges

    @Test
    fun `rep ranges split working sets into four buckets`() {
        val log = Log()
        val m = log.machine()
        val bike = log.machine(MuscleGroup.CARDIO, MachineKind.CARDIO)
        val s = log.session(d(11, 3))
        var pos = 0
        for (reps in listOf(1, 5, 6, 8, 9, 12, 13, 20)) log.set(s, m, 50.0, reps, position = pos++)
        log.set(s, m, 50.0, 0, position = pos++)                              // no reps: ignored
        log.set(s, m, 50.0, null, position = pos++)                           // ignored
        log.set(s, m, 20.0, 10, type = SetType.WARMUP, position = pos++)     // warm up: ignored
        log.set(s, bike, null, null, seconds = 600, position = pos++)         // cardio: ignored
        log.lift(d(10, 11), m, 50.0, 5)                                       // outside the period

        val shares = report(log).repRanges

        assertEquals(listOf(RepRange.R1_5, RepRange.R6_8, RepRange.R9_12, RepRange.R13_PLUS), shares.map { it.range })
        assertEquals(listOf(2, 2, 2, 2), shares.map { it.sets })
        shares.forEach { assertEquals(25.0, it.percent, 1e-9) }
    }

    @Test
    fun `rep range percentages sum to 100`() {
        val log = Log()
        val m = log.machine()
        val s = log.session(d(11, 3))
        var pos = 0
        for (reps in listOf(3, 4, 5, 6, 7, 8, 10)) log.set(s, m, 50.0, reps, position = pos++)

        val shares = report(log).repRanges

        assertEquals(listOf(3, 3, 1, 0), shares.map { it.sets })
        assertEquals(300.0 / 7, shares[0].percent, 1e-9)
        assertEquals(300.0 / 7, shares[1].percent, 1e-9)
        assertEquals(100.0 / 7, shares[2].percent, 1e-9)
        assertEquals(0.0, shares[3].percent, 0.0)
        assertEquals(100.0, shares.sumOf { it.percent }, 1e-9)
    }

    @Test
    fun `rep ranges are all zero when there are no sets`() {
        val shares = report(Log()).repRanges

        assertEquals(RepRange.entries.toList(), shares.map { it.range })
        assertTrue(shares.all { it.sets == 0 && it.percent == 0.0 })
    }

    // Times

    @Test
    fun `times average closed sessions with an end after the start`() {
        val log = Log()
        val m = log.machine()
        val one = log.session(d(11, 3), endedAfterSeconds = 60 * 60)
        val two = log.session(d(11, 4), endedAfterSeconds = 45 * 60)
        val three = log.session(d(11, 4), hour = 15, endedAfterSeconds = 46 * 60)
        log.session(d(11, 4), hour = 18)                         // open: skipped
        log.session(d(11, 4), hour = 20, endedAfterSeconds = 0)  // added by hand: skipped
        log.session(d(10, 1), endedAfterSeconds = 120 * 60)      // before the period: skipped
        log.set(one, m, 50.0, 10, loggedAt = at(d(11, 3), 10, 0), position = 0)
        log.set(one, m, 50.0, 10, loggedAt = at(d(11, 3), 10, 10), position = 1)   // gap 10
        log.set(one, m, 50.0, 10, loggedAt = at(d(11, 3), 10, 40), position = 2)   // gap 30: too long
        log.set(two, m, 50.0, 10, loggedAt = at(d(11, 4), 10, 0), position = 0)
        log.set(two, m, 50.0, 10, loggedAt = at(d(11, 4), 10, 5), position = 1)    // gap 5
        log.set(three, m, 50.0, 10, loggedAt = at(d(11, 4), 15, 0), position = 0)
        log.set(three, m, 50.0, 10, loggedAt = at(d(11, 4), 15, 15), position = 1) // gap 15: counts

        val times = report(log).times

        assertEquals(3, times.sessionsCounted)
        assertEquals(50, times.averageMinutes) // (60 + 45 + 46) / 3 = 50.33
        assertEquals(10.0, times.averageMinutesBetweenSets!!, 1e-9) // (10 + 5 + 15) / 3
    }

    @Test
    fun `the average is rounded to whole minutes`() {
        val log = Log()
        log.machine()
        // 40 min 20 s and 41 min 10 s: mean 40 min 45 s = 40.75, rounded to 41.
        log.session(d(11, 3), endedAfterSeconds = 40 * 60 + 20)
        log.session(d(11, 4), endedAfterSeconds = 41 * 60 + 10)

        assertEquals(41, report(log).times.averageMinutes)
    }

    @Test
    fun `times between sets are null when every gap is too long or there is only one set`() {
        val log = Log()
        val m = log.machine()
        val one = log.session(d(11, 3), endedAfterSeconds = 60 * 60)
        log.set(one, m, 50.0, 10, loggedAt = at(d(11, 3), 10, 0), position = 0)
        log.set(one, m, 50.0, 10, loggedAt = at(d(11, 3), 10, 16), position = 1)
        val two = log.session(d(11, 4), endedAfterSeconds = 30 * 60)
        log.set(two, m, 50.0, 10, loggedAt = at(d(11, 4), 10, 0), position = 0)

        val times = report(log).times

        assertEquals(2, times.sessionsCounted)
        assertEquals(45, times.averageMinutes)
        assertNull(times.averageMinutesBetweenSets)
    }

    @Test
    fun `times are empty when no session qualifies`() {
        val log = Log()
        log.machine()
        log.session(d(11, 3)) // open
        log.session(d(11, 4), endedAfterSeconds = 0)

        val times = report(log).times

        assertEquals(0, times.sessionsCounted)
        assertNull(times.averageMinutes)
        assertNull(times.averageMinutesBetweenSets)
    }

    // Unknown ids, cardio, zone

    @Test
    fun `sets whose session or machine is unknown are ignored`() {
        val log = Log()
        val m = log.machine()
        val s = log.session(d(11, 3))
        log.sets += WorkoutSet(1, 99L, m, 0, 50.0, 10, null, null, null, at(d(11, 3)))  // unknown session
        log.sets += WorkoutSet(2, s, 99L, 0, 50.0, 10, null, null, null, at(d(11, 3)))  // unknown machine

        val r = report(log)

        assertTrue(r.weeks.all { it.sessions == 0 && it.workingSets == 0 && it.volumeKg == 0.0 })
        assertTrue(r.muscleWeeks.isEmpty())
        assertTrue(r.recency.all { it.lastTrained == null })
        assertTrue(r.records.isEmpty())
        assertTrue(r.repRanges.all { it.sets == 0 })
    }

    @Test
    fun `cardio counts for cardio recency but not for volume rep ranges or strength`() {
        val log = Log()
        val bike = log.machine(MuscleGroup.CARDIO, MachineKind.CARDIO)
        log.cardio(d(10, 13), bike)
        log.cardio(d(11, 3), bike)

        val r = report(log)

        assertEquals(d(11, 3), r.recency.first { it.muscleGroup == MuscleGroup.CARDIO }.lastTrained)
        assertEquals(2, r.recency.first { it.muscleGroup == MuscleGroup.CARDIO }.daysSince)
        assertTrue(r.weeks.all { it.volumeKg == 0.0 && it.workingSets == 0 })
        assertEquals(2, r.weeks.sumOf { it.trainingDays })
        assertTrue(r.muscleWeeks.isEmpty())
        assertTrue(r.repRanges.all { it.sets == 0 })
        assertTrue(r.strength.isEmpty())
        assertTrue(r.plateaus.isEmpty())
    }

    @Test
    fun `the zone decides which date a late evening session falls on`() {
        val log = Log()
        val m = log.machine()
        // 02:30 UTC on Monday 2 November is 23:30 on Sunday 1 November in Sao Paulo (UTC-3).
        val s = log.session(d(11, 2), hour = 2, minute = 30)
        log.set(s, m, 50.0, 10)

        val inUtc = report(log, zone = ZoneOffset.UTC)
        val inSaoPaulo = report(log, zone = ZoneId.of("America/Sao_Paulo"))

        assertEquals(listOf(0, 0, 0, 1), inUtc.weeks.map { it.sessions })
        assertEquals(listOf(0, 0, 1, 0), inSaoPaulo.weeks.map { it.sessions })
        assertEquals(d(11, 2), inUtc.recency.first { it.muscleGroup == MuscleGroup.CHEST }.lastTrained)
        assertEquals(d(11, 1), inSaoPaulo.recency.first { it.muscleGroup == MuscleGroup.CHEST }.lastTrained)
    }

    // Body insights: strength now, strength index, balance

    /** A weight machine with a library id (the Log helper has none). */
    private fun machineIn(
        log: Log,
        libraryId: String?,
        group: MuscleGroup = MuscleGroup.CHEST,
        kind: MachineKind = MachineKind.WEIGHT_REPS,
    ): Long {
        val id = log.machines.size + 1L
        log.machines += Machine(id, "Machine $id", group, kind, 2.5, null, null, false, libraryId)
        return id
    }

    private fun exercise(id: String, force: String?, vararg primary: String) = LibraryExercise(
        id = id,
        name = id,
        namePt = id,
        equipment = LibraryEquipment.MACHINE,
        level = LibraryLevel.BEGINNER,
        category = "strength",
        mechanic = null,
        force = force,
        primaryMuscles = primary.toList(),
        secondaryMuscles = emptyList(),
        steps = emptyList(),
    )

    private fun reportWithLibrary(
        log: Log,
        period: StatsPeriod,
        library: Map<String, LibraryExercise> = emptyMap(),
    ): StatsReport = StatsRules.report(period, today, DayOfWeek.MONDAY, 3, log.machines, log.sessions, log.sets, utc, library)

    /** Monday of week [i] of the 13 week period that ends this week (from 10 Aug 2026). */
    private fun weekOf(i: Int): LocalDate = d(8, 10).plusWeeks(i.toLong())

    @Test
    fun `strength now is the best estimate of each non-cardio machine in the last window, best first`() {
        val log = Log()
        val press = log.machine()
        val curl = log.machine()
        // Four weeks: the last window is the two weeks from Monday 26 Oct.
        log.lift(d(10, 27), press, 100.0, 1)   // 100 in the window: the best
        log.lift(d(11, 3), press, 80.0, 4)     // 90.7 in the window, below it
        log.lift(d(11, 2), curl, 20.0, 10)     // 26.7
        log.lift(d(10, 13), curl, 200.0, 1)    // first window only: not counted

        val now = report(log).strengthNow

        assertEquals(listOf(press, curl), now.map { it.machine.id })
        assertEquals(100.0, now[0].estimate1rm, 1e-9)
        assertEquals(20.0 * (1 + 10 / 30.0), now[1].estimate1rm, 1e-9)
    }

    @Test
    fun `strength now leaves out cardio machines and timed sets`() {
        val log = Log()
        val rower = machineIn(log, null, MuscleGroup.CARDIO, MachineKind.CARDIO)
        val press = log.machine()
        log.lift(d(11, 3), rower, 50.0, 10)
        val timed = log.session(d(11, 3))
        log.set(timed, press, 60.0, 8, seconds = 600)

        assertTrue(report(log).strengthNow.isEmpty())
    }

    @Test
    fun `strength index starts at exactly 100 and follows a machine that rises 10 percent`() {
        val log = Log()
        val press = log.machine()
        for (i in 0 until 13) {
            log.lift(weekOf(i).plusDays(1), press, if (i >= 6) 110.0 else 100.0, 1)
        }

        val index = reportWithLibrary(log, StatsPeriod.THREE_MONTHS).strengthIndex

        assertEquals(13, index.size)
        assertEquals(weekOf(0), index.first().weekStart)
        assertEquals(100.0, index.first().percent, 0.0)
        assertEquals(100.0, index[5].percent, 1e-9)
        assertEquals(110.0, index[6].percent, 1e-9)
        assertEquals(110.0, index.last().percent, 1e-9)
    }

    @Test
    fun `a machine that starts later does not move the index`() {
        val log = Log()
        val press = log.machine()
        val row = log.machine()
        for (i in 0 until 13) log.lift(weekOf(i).plusDays(1), press, 100.0, 1)
        for (i in 6 until 13) log.lift(weekOf(i).plusDays(2), row, 50.0, 10)

        val index = reportWithLibrary(log, StatsPeriod.THREE_MONTHS).strengthIndex

        assertEquals(13, index.size)
        index.forEach { assertEquals(100.0, it.percent, 1e-9) }
    }

    @Test
    fun `a machine that joins after another has risen does not move the index`() {
        val log = Log()
        val press = log.machine()
        val row = log.machine()
        for (i in 0 until 13) log.lift(weekOf(i).plusDays(1), press, if (i >= 6) 110.0 else 100.0, 1)
        for (i in 7 until 13) log.lift(weekOf(i).plusDays(2), row, 50.0, 10)

        val index = reportWithLibrary(log, StatsPeriod.THREE_MONTHS).strengthIndex

        // Week 6: the press alone is at 110. Week 7: the row joins at its own first value; the index stays at 110.
        assertEquals(110.0, index[6].percent, 1e-9)
        assertEquals(110.0, index[7].percent, 1e-9)
    }

    @Test
    fun `weeks without a value for a machine are skipped in the index`() {
        val log = Log()
        val press = log.machine()
        log.lift(weekOf(0).plusDays(1), press, 100.0, 1)
        log.lift(weekOf(9).plusDays(1), press, 120.0, 1)

        val index = reportWithLibrary(log, StatsPeriod.THREE_MONTHS).strengthIndex

        // The trailing four weeks of the first set run out after week 3; the next value is week 9.
        assertEquals(listOf(0, 1, 2, 3, 9, 10, 11, 12).map { weekOf(it) }, index.map { it.weekStart })
        assertEquals(120.0, index[4].percent, 1e-9)
    }

    @Test
    fun `strength index and strength now are empty without any estimate`() {
        val warmUpOnly = Log()
        val press = warmUpOnly.machine()
        warmUpOnly.lift(d(11, 3), press, 20.0, 10, SetType.WARMUP)
        val cardioOnly = Log()
        cardioOnly.cardio(d(11, 3), cardioOnly.machine(MuscleGroup.CARDIO, MachineKind.CARDIO))

        for (log in listOf(Log(), warmUpOnly, cardioOnly)) {
            val r = reportWithLibrary(log, StatsPeriod.THREE_MONTHS)
            assertTrue(r.strengthIndex.isEmpty())
            assertTrue(r.strengthNow.isEmpty())
        }
    }

    @Test
    fun `balance by machine group when a machine has no library data`() {
        val log = Log()
        val chest = log.machine(MuscleGroup.CHEST)
        val shoulders = log.machine(MuscleGroup.SHOULDERS)
        val back = log.machine(MuscleGroup.BACK)
        val outsideLibrary = machineIn(log, "not-in-library", MuscleGroup.BACK)
        val legs = log.machine(MuscleGroup.LEGS)
        val arms = log.machine(MuscleGroup.ARMS)
        val core = log.machine(MuscleGroup.CORE)
        repeat(2) { log.lift(d(11, 3), chest, 60.0, 8) }
        log.lift(d(11, 3), shoulders, 20.0, 10)
        repeat(2) { log.lift(d(11, 3), back, 50.0, 10) }
        log.lift(d(11, 3), outsideLibrary, 50.0, 10)
        repeat(4) { log.lift(d(11, 3), legs, 80.0, 10) }
        repeat(2) { log.lift(d(11, 3), arms, 12.0, 10) }
        repeat(5) { log.lift(d(11, 3), core, 10.0, 15) }

        val balance = reportWithLibrary(log, StatsPeriod.FOUR_WEEKS).balance

        assertEquals(3, balance.pushSets)      // chest, shoulders
        assertEquals(3, balance.pullSets)      // back
        assertEquals(8, balance.upperSets)     // chest, shoulders, back, arms
        assertEquals(4, balance.lowerSets)     // legs
        assertEquals(0, balance.quadSets)
        assertEquals(0, balance.hamstringSets)
        assertEquals(5, balance.unclassifiedSets) // core
    }

    @Test
    fun `balance by library exercise - force decides push and pull, primary muscles decide the rest`() {
        val library = listOf(
            exercise("bench", "push", "chest", "triceps"),
            exercise("row", "pull", "lats", "biceps"),
            exercise("leg-extension", null, "quadriceps"),
            exercise("leg-curl", null, "hamstrings"),
            exercise("crunch", null, "abdominals"),
        ).associateBy { it.id }
        val log = Log()
        val bench = machineIn(log, "bench", MuscleGroup.CHEST)
        val row = machineIn(log, "row", MuscleGroup.CHEST)          // the group alone would say push
        val legExtension = machineIn(log, "leg-extension", MuscleGroup.LEGS)
        val legCurl = machineIn(log, "leg-curl", MuscleGroup.LEGS)
        val crunch = machineIn(log, "crunch", MuscleGroup.CORE)
        repeat(2) { log.lift(d(11, 3), bench, 60.0, 8) }
        repeat(3) { log.lift(d(11, 3), row, 50.0, 10) }
        repeat(4) { log.lift(d(11, 3), legExtension, 40.0, 12) }
        log.lift(d(11, 3), legCurl, 30.0, 10)
        repeat(2) { log.lift(d(11, 3), crunch, 20.0, 15) }

        val balance = reportWithLibrary(log, StatsPeriod.FOUR_WEEKS, library).balance

        assertEquals(2, balance.pushSets)
        assertEquals(3, balance.pullSets)
        assertEquals(5, balance.upperSets)
        assertEquals(5, balance.lowerSets)
        assertEquals(4, balance.quadSets)
        assertEquals(1, balance.hamstringSets)
        assertEquals(2, balance.unclassifiedSets)
    }

    @Test
    fun `balance covers the last four weeks only, and every week of a shorter period`() {
        val log = Log()
        val chest = log.machine(MuscleGroup.CHEST)
        log.lift(d(10, 11), chest, 60.0, 8)                     // week 8 of the 13: outside
        log.lift(d(10, 13), chest, 60.0, 8)                     // week 9: the first of the last four
        log.lift(d(11, 3), chest, 20.0, 10, SetType.WARMUP)    // warm ups never count

        val longPeriod = reportWithLibrary(log, StatsPeriod.THREE_MONTHS).balance
        val shortPeriod = reportWithLibrary(log, StatsPeriod.FOUR_WEEKS).balance

        assertEquals(1, longPeriod.pushSets)
        assertEquals(1, longPeriod.upperSets)
        assertEquals(1, shortPeriod.pushSets)
    }

    @Test
    fun `balance is all zero with no sets`() {
        assertEquals(BalanceStats(0, 0, 0, 0, 0, 0, 0), reportWithLibrary(Log(), StatsPeriod.FOUR_WEEKS).balance)
    }
}
