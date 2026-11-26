package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The statistics added to [StatsReport]: machine trends, workload, muscle volumes, patterns, cardio insight and
 * waist / hip. "Today" is Wednesday 11 November 2026; the trend dates are 14 Oct, 21 Oct, 28 Oct, 4 Nov, 11 Nov.
 */
class StatsRulesStatisticsTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val utc = ZoneOffset.UTC

    private fun d(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    private class Log {
        val machines = mutableListOf<Machine>()
        val sessions = mutableListOf<Session>()
        val sets = mutableListOf<WorkoutSet>()

        fun machine(
            name: String,
            group: MuscleGroup = MuscleGroup.CHEST,
            kind: MachineKind = MachineKind.WEIGHT_REPS,
        ): Long {
            val id = machines.size + 1L
            machines += Machine(id, name, group, kind, 2.5, null, null, false)
            return id
        }

        fun session(date: LocalDate): Long {
            val id = sessions.size + 1L
            sessions += Session(id, date.atTime(10, 0).toInstant(ZoneOffset.UTC), null, null, null)
            return id
        }

        /** A single set on a new session on [date]. */
        fun set(
            date: LocalDate,
            machineId: Long,
            weightKg: Double? = 50.0,
            reps: Int? = 1,
            type: SetType = SetType.NORMAL,
            seconds: Int? = null,
            distanceM: Int? = null,
            intensity: Double? = null,
        ) {
            val sessionId = session(date)
            addSet(sessionId, date, machineId, weightKg, reps, type, seconds, distanceM, intensity)
        }

        /** Several sets of one session on [date]. */
        fun sessionWith(date: LocalDate, vararg sets: Triple<Long, Double?, SetType>) {
            val sessionId = session(date)
            sets.forEach { (machineId, weight, type) ->
                addSet(sessionId, date, machineId, weight, 1, type, null, null, null)
            }
        }

        private fun addSet(
            sessionId: Long,
            date: LocalDate,
            machineId: Long,
            weightKg: Double?,
            reps: Int?,
            type: SetType,
            seconds: Int?,
            distanceM: Int?,
            intensity: Double?,
        ) {
            sets += WorkoutSet(
                id = sets.size + 1L,
                sessionId = sessionId,
                machineId = machineId,
                position = sets.size,
                weightKg = weightKg,
                reps = reps,
                seconds = seconds,
                calories = null,
                distanceM = distanceM,
                loggedAt = date.atTime(10, sets.size % 60).toInstant(ZoneOffset.UTC),
                type = type,
                intensity = intensity,
            )
        }
    }

    private fun report(
        log: Log,
        period: StatsPeriod = StatsPeriod.FOUR_WEEKS,
        measurements: List<BodyMeasurement> = emptyList(),
    ): StatsReport = StatsRules.report(
        period = period,
        today = today,
        firstDayOfWeek = DayOfWeek.MONDAY,
        weeklyGoal = 3,
        machines = log.machines,
        sessions = log.sessions,
        sets = log.sets,
        zone = utc,
        measurements = measurements,
    )

    /** Five strength sessions on the trend dates, one set each, with a 1RM equal to the weight (1 rep). */
    private fun fiveSessions(log: Log, machineId: Long, weights: List<Double>) {
        val dates = listOf(d(10, 14), d(10, 21), d(10, 28), d(11, 4), d(11, 11))
        dates.zip(weights).forEach { (date, w) -> log.set(date, machineId, weightKg = w, reps = 1) }
    }

    // Machine trends

    @Test
    fun `machine trends are sorted rising then plateau then falling then not enough data then by name`() {
        val log = Log()
        val bench = log.machine("Bench")
        val crunch = log.machine("Ab crunch", MuscleGroup.CORE)
        val curl = log.machine("Curl", MuscleGroup.ARMS)
        val row = log.machine("Row", MuscleGroup.BACK)
        val calf = log.machine("Calf", MuscleGroup.LEGS)
        fiveSessions(log, bench, listOf(80.0, 82.0, 84.0, 86.0, 88.0)) // slope +2 per week, no spread: RISING
        fiveSessions(log, crunch, listOf(20.0, 21.0, 22.0, 23.0, 24.0)) // slope +1 per week: RISING
        fiveSessions(log, curl, listOf(40.0, 38.0, 36.0, 34.0, 32.0)) // slope -2 per week: FALLING
        fiveSessions(log, row, listOf(60.0, 70.0, 50.0, 70.0, 60.0)) // slope 0 with a wide interval: PLATEAU
        log.set(d(10, 14), calf, weightKg = 50.0, reps = 1)
        log.set(d(11, 4), calf, weightKg = 55.0, reps = 1) // two sessions: NOT_ENOUGH_DATA

        val trends = report(log, StatsPeriod.ONE_WEEK).machineTrends

        assertEquals(listOf("Ab crunch", "Bench", "Row", "Curl", "Calf"), trends.map { it.name })
        assertEquals(
            listOf(TrendVerdict.RISING, TrendVerdict.RISING, TrendVerdict.PLATEAU, TrendVerdict.FALLING, TrendVerdict.NOT_ENOUGH_DATA),
            trends.map { it.result.verdict },
        )
        assertEquals(listOf(crunch, bench, row, curl, calf), trends.map { it.machineId })
        assertEquals(MuscleGroup.CORE, trends.first().group)
    }

    @Test
    fun `the trend slope comes from the whole history even for a one week period`() {
        val log = Log()
        val bench = log.machine("Bench")
        fiveSessions(log, bench, listOf(80.0, 82.0, 84.0, 86.0, 88.0))

        val bestTrend = report(log, StatsPeriod.ONE_WEEK).machineTrends.single()

        assertEquals(5, bestTrend.result.points)
        assertEquals(2.0, bestTrend.result.fit!!.slopePerWeek, 1e-9)
        assertEquals(TrendVerdict.RISING, bestTrend.result.verdict)
    }

    @Test
    fun `cardio machines and machines without sets are not in the trends`() {
        val log = Log()
        val bench = log.machine("Bench")
        val treadmill = log.machine("Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO)
        log.machine("Unused")
        fiveSessions(log, bench, listOf(80.0, 82.0, 84.0, 86.0, 88.0))
        for (date in listOf(d(10, 14), d(10, 21), d(10, 28), d(11, 4), d(11, 11))) {
            log.set(date, treadmill, weightKg = null, reps = null, seconds = 1200, distanceM = 3000)
        }

        val trends = report(log).machineTrends

        assertEquals(listOf("Bench"), trends.map { it.name })
    }

    // Workload

    @Test
    fun `workload counts only strength working sets and uses the whole history`() {
        val log = Log()
        val bench = log.machine("Bench")
        val treadmill = log.machine("Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO)
        // Outside the one week period, but in the history: 2 Oct.
        log.set(d(10, 2), bench, weightKg = 50.0, reps = 10)
        // Today: two normal, one failure, plus a warm-up, a drop set and a cardio set that do not count.
        log.sessionWith(
            today,
            Triple(bench, 80.0, SetType.NORMAL),
            Triple(bench, 80.0, SetType.NORMAL),
            Triple(bench, 80.0, SetType.FAILURE),
            Triple(bench, 40.0, SetType.WARMUP),
            Triple(bench, 60.0, SetType.DROP),
        )
        log.set(today, treadmill, weightKg = null, reps = null, seconds = 600, distanceM = 1500)

        val workload = report(log, StatsPeriod.ONE_WEEK).workload!!

        assertEquals(3, workload.acuteSets)
        assertEquals(0.75, workload.chronicWeeklyAverage, 1e-12)
        assertEquals(4.0, workload.acwr!!, 1e-12)
        assertEquals(41, workload.historyDays)
        assertEquals(AcwrStatus.VERY_HIGH, workload.status)
        // Seven daily loads with only today non-zero: monotony 1 / sqrt(6), strain 3 x that.
        assertEquals(0.4082483, workload.monotony!!, 1e-6)
        assertEquals(1.2247449, workload.strain!!, 1e-6)
    }

    // Muscle volumes

    @Test
    fun `muscle volumes average over the weeks of the period`() {
        val log = Log()
        val press = log.machine("Press")
        // Three sets in the last block (5-11 Nov) and three in the block of 15-21 Oct.
        repeat(3) { log.set(d(11, 10), press, weightKg = 50.0, reps = 10) }
        repeat(3) { log.set(d(10, 20), press, weightKg = 50.0, reps = 10) }

        val fourWeeks = report(log, StatsPeriod.FOUR_WEEKS).muscleVolumes.single { it.muscle == Muscle.CHEST }
        val oneWeek = report(log, StatsPeriod.ONE_WEEK).muscleVolumes.single { it.muscle == Muscle.CHEST }

        assertEquals(1.5, fourWeeks.setsPerWeek, 1e-9)
        assertEquals(VolumeBand.BELOW, fourWeeks.band)
        assertEquals(3.0, oneWeek.setsPerWeek, 1e-9)
    }

    @Test
    fun `muscle volumes list all fourteen muscles`() {
        val empty = report(Log())

        assertEquals(Muscle.entries.toList(), empty.muscleVolumes.map { it.muscle })
    }

    // Patterns

    @Test
    fun `patterns count the sessions of the strength machines over the whole history`() {
        val log = Log()
        val bench = log.machine("Bench")
        fiveSessions(log, bench, listOf(80.0, 82.0, 84.0, 86.0, 88.0))

        val patterns = report(log).patterns!!

        assertEquals(5, patterns.sessionsUsed)
        assertFalse(patterns.enough)
        assertEquals(4, patterns.rest.sumOf { it.n })
    }

    // Cardio insight

    @Test
    fun `the cardio insight uses speed and efficiency of the cardio sessions`() {
        val log = Log()
        val treadmill = log.machine("Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO)
        // 3000 m in 1200 s is 9.0 km/h at intensity 0.6, efficiency 15. 4000 m in 1200 s is 12.0 km/h at 0.8, efficiency 15.
        log.set(d(10, 14), treadmill, weightKg = null, reps = null, seconds = 1200, distanceM = 3000, intensity = 0.6)
        log.set(d(11, 11), treadmill, weightKg = null, reps = null, seconds = 1200, distanceM = 4000, intensity = 0.8)

        val insight = report(log).cardioInsight

        assertNotNull(insight)
        assertEquals(2, insight!!.sessions)
        assertEquals(15.0, insight.efficiencyLatest!!, 1e-9)
        assertEquals(15.0, insight.efficiencyBest!!, 1e-9)
    }

    @Test
    fun `no cardio sessions give no cardio insight`() {
        val log = Log()
        val bench = log.machine("Bench")
        fiveSessions(log, bench, listOf(80.0, 82.0, 84.0, 86.0, 88.0))

        assertNull(report(log).cardioInsight)
    }

    // Waist / hip

    @Test
    fun `waist to hip comes from the measurements with both sides`() {
        val measurements = listOf(
            BodyMeasurement(date = d(11, 1), type = MeasurementType.WAIST, valueCm = 90.0),
            BodyMeasurement(date = d(11, 1), type = MeasurementType.HIPS, valueCm = 100.0),
            BodyMeasurement(date = d(11, 5), type = MeasurementType.WAIST, valueCm = 88.0),
        )

        val waistHip = report(Log(), measurements = measurements).waistHip

        assertEquals(1, waistHip.size)
        assertEquals(d(11, 1), waistHip[0].first)
        assertEquals(0.9, waistHip[0].second, 1e-12)
    }

    // Defaults

    @Test
    fun `with no data the new fields have their empty values`() {
        val empty = report(Log())

        val workload = empty.workload!!
        val patterns = empty.patterns!!
        assertEquals(AcwrStatus.NO_DATA, workload.status)
        assertNull(workload.acwr)
        assertTrue(empty.machineTrends.isEmpty())
        assertNull(empty.cardioInsight)
        assertTrue(empty.waistHip.isEmpty())
        assertEquals(0, patterns.sessionsUsed)
        assertFalse(patterns.enough)
        assertTrue(empty.muscleVolumes.all { it.setsPerWeek == 0.0 && it.band == VolumeBand.NONE })
    }
}
