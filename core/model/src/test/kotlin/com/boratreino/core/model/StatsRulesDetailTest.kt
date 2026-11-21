package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The specific muscles of a period (detailWeeks). "Today" is Wednesday 2026-11-11, so the four week period has
 * blocks starting 15 Oct, 22 Oct, 29 Oct and 5 Nov. Every test builds a tiny data set on explicit dates.
 */
class StatsRulesDetailTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val utc: ZoneId = ZoneOffset.UTC

    private fun d(month: Int, day: Int, year: Int = 2026): LocalDate = LocalDate.of(year, month, day)

    /** Builds machines, sessions and sets with automatic ids. */
    private class Log {
        val machines = mutableListOf<Machine>()
        val sessions = mutableListOf<Session>()
        val sets = mutableListOf<WorkoutSet>()

        fun machine(
            group: MuscleGroup = MuscleGroup.CHEST,
            kind: MachineKind = MachineKind.WEIGHT_REPS,
            libraryId: String? = null,
        ): Long {
            val id = machines.size + 1L
            machines += Machine(id, "Machine $id", group, kind, 2.5, null, null, false, libraryId)
            return id
        }

        fun session(date: LocalDate, hour: Int = 10): Long {
            val id = sessions.size + 1L
            val start = date.atTime(hour, 0).toInstant(ZoneOffset.UTC)
            sessions += Session(id, start, null, null, null)
            return id
        }

        fun set(
            sessionId: Long,
            machineId: Long,
            weightKg: Double?,
            reps: Int?,
            type: SetType = SetType.NORMAL,
            seconds: Int? = null,
        ) {
            val start = sessions.first { it.id == sessionId }.startedAt
            sets += WorkoutSet(
                id = sets.size + 1L,
                sessionId = sessionId,
                machineId = machineId,
                position = 0,
                weightKg = weightKg,
                reps = reps,
                seconds = seconds,
                calories = null,
                distanceM = null,
                loggedAt = start.plusSeconds(60L * sets.size),
                type = type,
            )
        }

        /** One session on [date] with one working set on [machineId]. */
        fun lift(date: LocalDate, machineId: Long, weightKg: Double? = 50.0, reps: Int? = 10, type: SetType = SetType.NORMAL) {
            set(session(date), machineId, weightKg, reps, type)
        }
    }

    private fun exercise(id: String, primary: List<String>, secondary: List<String> = emptyList()) = LibraryExercise(
        id = id,
        name = id,
        namePt = id,
        equipment = LibraryEquipment.BARBELL,
        level = LibraryLevel.BEGINNER,
        category = "strength",
        mechanic = null,
        force = null,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        steps = emptyList(),
    )

    private fun report(
        log: Log,
        period: StatsPeriod = StatsPeriod.FOUR_WEEKS,
        library: Map<String, LibraryExercise> = emptyMap(),
    ): StatsReport = StatsRules.report(period, today, DayOfWeek.MONDAY, 3, log.machines, log.sessions, log.sets, utc, library)

    private fun detail(report: StatsReport, week: LocalDate, muscle: Muscle): MuscleDetailWeek? =
        report.detailWeeks.firstOrNull { it.weekStart == week && it.muscle == muscle }

    private fun direct(report: StatsReport, week: LocalDate, muscle: Muscle): Double =
        detail(report, week, muscle)?.directSets ?: 0.0

    private fun indirect(report: StatsReport, week: LocalDate, muscle: Muscle): Double =
        detail(report, week, muscle)?.indirectSets ?: 0.0

    private val lastBlock = d(11, 5)
    private val firstBlock = d(10, 15)

    // Direct sets from a library exercise

    @Test
    fun `bench press with library data credits the chest directly and the shoulders and triceps indirectly`() {
        val log = Log()
        val press = log.machine(MuscleGroup.CHEST, libraryId = "bench")
        val library = mapOf("bench" to exercise("bench", listOf("chest"), listOf("shoulders", "triceps")))
        repeat(2) { log.lift(d(11, 9), press) }

        val r = report(log, library = library)

        assertEquals(2.0, direct(r, lastBlock, Muscle.CHEST), 1e-9)
        assertEquals(0.0, indirect(r, lastBlock, Muscle.CHEST), 0.0)
        assertEquals(0.0, direct(r, lastBlock, Muscle.SHOULDERS), 0.0)
        assertEquals(2.0, indirect(r, lastBlock, Muscle.SHOULDERS), 1e-9)
        assertEquals(0.0, direct(r, lastBlock, Muscle.TRICEPS), 0.0)
        assertEquals(2.0, indirect(r, lastBlock, Muscle.TRICEPS), 1e-9)
        assertEquals(
            listOf(Muscle.CHEST, Muscle.SHOULDERS, Muscle.TRICEPS),
            r.detailWeeks.map { it.muscle },
        )
    }

    @Test
    fun `a set of an exercise with two primary muscles splits half and half between them`() {
        val log = Log()
        val squat = log.machine(MuscleGroup.LEGS, libraryId = "squat")
        val library = mapOf("squat" to exercise("squat", listOf("quadriceps", "glutes")))
        log.lift(d(11, 9), squat)

        val r = report(log, library = library)

        assertEquals(0.5, direct(r, lastBlock, Muscle.QUADRICEPS), 1e-9)
        assertEquals(0.5, direct(r, lastBlock, Muscle.GLUTES), 1e-9)
        assertEquals(0.0, indirect(r, lastBlock, Muscle.QUADRICEPS), 0.0)
    }

    @Test
    fun `two primary names that map to the same muscle count that muscle once`() {
        val log = Log()
        val hips = log.machine(MuscleGroup.LEGS, libraryId = "fly")
        val library = mapOf("fly" to exercise("fly", listOf("adductors", "abductors")))
        log.lift(d(11, 9), hips)

        val r = report(log, library = library)

        assertEquals(1.0, direct(r, lastBlock, Muscle.HIPS), 1e-9)
    }

    @Test
    fun `a secondary muscle that is also a primary one is dropped`() {
        val log = Log()
        val press = log.machine(MuscleGroup.CHEST, libraryId = "press")
        val library = mapOf("press" to exercise("press", listOf("chest"), listOf("chest", "triceps")))
        log.lift(d(11, 9), press)

        val r = report(log, library = library)

        assertEquals(1.0, direct(r, lastBlock, Muscle.CHEST), 1e-9)
        assertEquals(0.0, indirect(r, lastBlock, Muscle.CHEST), 0.0)
        assertEquals(1.0, indirect(r, lastBlock, Muscle.TRICEPS), 1e-9)
    }

    @Test
    fun `a secondary muscle listed twice counts once per set`() {
        val log = Log()
        val press = log.machine(MuscleGroup.CHEST, libraryId = "press")
        val library = mapOf("press" to exercise("press", listOf("chest"), listOf("triceps", "triceps")))
        log.lift(d(11, 9), press)

        val r = report(log, library = library)

        assertEquals(1.0, indirect(r, lastBlock, Muscle.TRICEPS), 1e-9)
    }

    // Machines without library data

    @Test
    fun `a custom arms machine without library data spreads each set over biceps triceps and forearms`() {
        val log = Log()
        val curl = log.machine(MuscleGroup.ARMS)
        log.lift(d(11, 9), curl)

        val r = report(log)

        assertEquals(1.0 / 3.0, direct(r, lastBlock, Muscle.BICEPS), 1e-9)
        assertEquals(1.0 / 3.0, direct(r, lastBlock, Muscle.TRICEPS), 1e-9)
        assertEquals(1.0 / 3.0, direct(r, lastBlock, Muscle.FOREARMS), 1e-9)
        assertEquals(0.0, indirect(r, lastBlock, Muscle.BICEPS), 0.0)
        assertEquals(0.0, indirect(r, lastBlock, Muscle.TRICEPS), 0.0)
        assertEquals(0.0, indirect(r, lastBlock, Muscle.FOREARMS), 0.0)
        assertEquals(3, r.detailWeeks.size)
    }

    @Test
    fun `a legs machine without library data spreads each set over five muscles and gives the abs indirectly`() {
        val log = Log()
        val leg = log.machine(MuscleGroup.LEGS)
        log.lift(d(11, 9), leg)

        val r = report(log)

        listOf(Muscle.QUADRICEPS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES, Muscle.HIPS).forEach {
            assertEquals(it.name, 1.0 / 5.0, direct(r, lastBlock, it), 1e-9)
            assertEquals(it.name, 0.0, indirect(r, lastBlock, it), 0.0)
        }
        assertEquals(0.0, direct(r, lastBlock, Muscle.ABS), 0.0)
        assertEquals(1.0, indirect(r, lastBlock, Muscle.ABS), 1e-9)
    }

    @Test
    fun `a chest machine without library data gives the shoulders and triceps indirectly`() {
        val log = Log()
        val chest = log.machine(MuscleGroup.CHEST)
        log.lift(d(11, 9), chest)

        val r = report(log)

        assertEquals(1.0, direct(r, lastBlock, Muscle.CHEST), 1e-9)
        assertEquals(1.0, indirect(r, lastBlock, Muscle.SHOULDERS), 1e-9)
        assertEquals(1.0, indirect(r, lastBlock, Muscle.TRICEPS), 1e-9)
        assertEquals(0.0, direct(r, lastBlock, Muscle.SHOULDERS), 0.0)
        assertEquals(0.0, direct(r, lastBlock, Muscle.TRICEPS), 0.0)
    }

    @Test
    fun `a library link with no mapped primary muscles falls back to the group spread`() {
        val log = Log()
        val curl = log.machine(MuscleGroup.ARMS, libraryId = "odd")
        val library = mapOf("odd" to exercise("odd", listOf("unknown muscle"), listOf("shoulders")))
        log.lift(d(11, 9), curl)

        val r = report(log, library = library)

        assertEquals(1.0 / 3.0, direct(r, lastBlock, Muscle.BICEPS), 1e-9)
        assertEquals(1.0 / 3.0, direct(r, lastBlock, Muscle.TRICEPS), 1e-9)
        assertEquals(1.0 / 3.0, direct(r, lastBlock, Muscle.FOREARMS), 1e-9)
        assertEquals(1.0, indirect(r, lastBlock, Muscle.SHOULDERS), 1e-9)
    }

    @Test
    fun `a machine whose library id is not in the library is treated like one without library data`() {
        val log = Log()
        val chest = log.machine(MuscleGroup.CHEST, libraryId = "gone")
        log.lift(d(11, 9), chest)

        val r = report(log, library = emptyMap())

        assertEquals(1.0, direct(r, lastBlock, Muscle.CHEST), 1e-9)
        assertEquals(1.0, indirect(r, lastBlock, Muscle.SHOULDERS), 1e-9)
        assertEquals(1.0, indirect(r, lastBlock, Muscle.TRICEPS), 1e-9)
    }

    // Cardio and warm-ups never count

    @Test
    fun `a cardio set on a strength machine does not count`() {
        val log = Log()
        val bike = log.machine(MuscleGroup.LEGS)
        log.set(log.session(d(11, 9)), bike, weightKg = null, reps = null, seconds = 600)

        assertTrue(report(log).detailWeeks.isEmpty())
    }

    @Test
    fun `a cardio machine does not count even with weight and reps`() {
        val log = Log()
        val treadmill = log.machine(MuscleGroup.CARDIO, kind = MachineKind.CARDIO)
        log.lift(d(11, 9), treadmill)

        assertTrue(report(log).detailWeeks.isEmpty())
    }

    @Test
    fun `a cardio machine of a muscle group does not count even with weight and reps`() {
        val log = Log()
        val rower = log.machine(MuscleGroup.BACK, kind = MachineKind.CARDIO)
        log.lift(d(11, 9), rower)

        assertTrue(report(log).detailWeeks.isEmpty())
    }

    @Test
    fun `warm-up sets are left out`() {
        val log = Log()
        val chest = log.machine(MuscleGroup.CHEST)
        log.lift(d(11, 9), chest, type = SetType.WARMUP)
        log.lift(d(11, 9), chest)

        val r = report(log)

        assertEquals(1.0, direct(r, lastBlock, Muscle.CHEST), 1e-9)
        assertEquals(1.0, indirect(r, lastBlock, Muscle.SHOULDERS), 1e-9)
    }

    // Blocks, ordering and totals

    @Test
    fun `blocks are the rolling seven day blocks anchored on today`() {
        val log = Log()
        val chest = log.machine(MuscleGroup.CHEST)
        log.lift(d(10, 14), chest)       // 28 days back: outside the period
        log.lift(d(10, 15), chest)       // first day of the first block
        log.lift(d(11, 4), chest)        // last day of the third block
        log.lift(d(11, 5), chest)        // first day of the last block
        log.lift(d(11, 11), chest)       // today, last block

        val r = report(log)

        assertEquals(listOf(firstBlock, d(10, 29), lastBlock), r.detailWeeks.map { it.weekStart }.distinct())
        assertEquals(1.0, direct(r, firstBlock, Muscle.CHEST), 1e-9)
        assertEquals(1.0, direct(r, d(10, 29), Muscle.CHEST), 1e-9)
        assertEquals(2.0, direct(r, lastBlock, Muscle.CHEST), 1e-9)
    }

    @Test
    fun `detail weeks are ordered by block and then by muscle order`() {
        val log = Log()
        val chest = log.machine(MuscleGroup.CHEST)
        val arms = log.machine(MuscleGroup.ARMS)
        log.lift(d(10, 20), chest)       // first block: CHEST direct, SHOULDERS and TRICEPS indirect
        log.lift(d(11, 9), arms)         // last block: BICEPS, TRICEPS, FOREARMS direct

        val r = report(log)

        assertEquals(
            listOf(
                firstBlock to Muscle.CHEST,
                firstBlock to Muscle.SHOULDERS,
                firstBlock to Muscle.TRICEPS,
                lastBlock to Muscle.BICEPS,
                lastBlock to Muscle.TRICEPS,
                lastBlock to Muscle.FOREARMS,
            ),
            r.detailWeeks.map { it.weekStart to it.muscle },
        )
    }

    @Test
    fun `the direct sets of all muscles add up to the working sets of the strength machines`() {
        val log = Log()
        val chest = log.machine(MuscleGroup.CHEST)
        val arms = log.machine(MuscleGroup.ARMS)
        val legs = log.machine(MuscleGroup.LEGS)
        val back = log.machine(MuscleGroup.BACK)
        val bench = log.machine(MuscleGroup.CHEST, libraryId = "bench")
        val library = mapOf("bench" to exercise("bench", listOf("chest"), listOf("triceps")))
        log.lift(d(11, 9), chest)
        log.lift(d(11, 9), arms)
        log.lift(d(10, 20), legs)
        log.lift(d(10, 21), back)
        log.lift(d(10, 22), bench)
        log.lift(d(10, 22), bench, type = SetType.WARMUP)

        val r = report(log, library = library)

        val directTotal = r.detailWeeks.sumOf { it.directSets }
        assertEquals(r.muscleWeeks.sumOf { it.workingSets }.toDouble(), directTotal, 1e-9)
        assertEquals(5.0, directTotal, 1e-9)
    }
}
