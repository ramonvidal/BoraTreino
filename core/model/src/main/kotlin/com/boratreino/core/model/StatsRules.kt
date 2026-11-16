package com.boratreino.core.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * How far back the statistics look: a rolling window that ends today, made of 7-day blocks anchored on today
 * (not on calendar weeks). [ONE_WEEK] is the last 7 days, today included, a single block; [FOUR_WEEKS] is 28 days,
 * [THREE_MONTHS] 91, [ONE_YEAR] 364, [ALL] reaches back to the first session (at least 4 blocks).
 * Anything that needs two points to compare (strength trends, strength index) is empty for [ONE_WEEK].
 */
enum class StatsPeriod { ONE_WEEK, FOUR_WEEKS, THREE_MONTHS, ONE_YEAR, ALL }

/** Rep buckets for the rep-range split. */
enum class RepRange { R1_5, R6_8, R9_12, R13_PLUS }

data class WeekStats(
    /** First day of the 7-day block (blocks end on the day before the next one; the last ends today). */
    val weekStart: LocalDate,
    val sessions: Int,
    val trainingDays: Int,
    val workingSets: Int,
    val volumeKg: Double,
)

/** [weekStart] is the first day of the 7-day block. */
data class MuscleWeek(val weekStart: LocalDate, val muscleGroup: MuscleGroup, val workingSets: Int, val volumeKg: Double)

/** Whole history; [lastTrained] and [daysSince] are null when the group was never trained. */
data class MuscleRecency(val muscleGroup: MuscleGroup, val lastTrained: LocalDate?, val daysSince: Int?)

data class Consistency(
    val weeksCounted: Int,
    val weeksMetGoal: Int,
    val goal: Int,
    val sessionsPerWeek: Double,
    val bestWeekStart: LocalDate?,
    val bestWeekTrainingDays: Int,
)

data class StrengthTrend(
    val machine: Machine,
    val before: Double,
    val after: Double,
    val changeKg: Double,
    val changePercent: Double,
)

data class RecordEvent(val machine: Machine, val date: LocalDate, val record: Record)

data class Plateau(val machine: Machine, val sessionsWithoutImprovement: Int, val bestEstimate: Double)

data class RepRangeShare(val range: RepRange, val sets: Int, val percent: Double)

data class SessionTimes(val sessionsCounted: Int, val averageMinutes: Int?, val averageMinutesBetweenSets: Double?)

/** A machine's best estimated one-rep max over the last window of the period. */
data class MachineEstimate(val machine: Machine, val estimate1rm: Double)

/** A value indexed to 100.0 at the first point of a series; [weekStart] is the first day of a 7-day block. */
data class IndexPoint(val weekStart: LocalDate, val percent: Double)

/** Number of working sets of a 7-day block ([weekStart] = its first day) that worked [muscleGroup] indirectly (as a secondary muscle). */
data class IndirectWeek(val weekStart: LocalDate, val muscleGroup: MuscleGroup, val sets: Int)

/** Working non-cardio sets of the whole period, split by opposites. */
data class BalanceStats(
    val pushSets: Int,
    val pullSets: Int,
    val upperSets: Int,
    val lowerSets: Int,
    val quadSets: Int,
    val hamstringSets: Int,
    val unclassifiedSets: Int,
)

data class StatsReport(
    val period: StatsPeriod,
    /** First day of the first 7-day block of the period: today minus (7 x blocks - 1) days. */
    val from: LocalDate,
    /** Today. */
    val to: LocalDate,
    /** Zero-filled, oldest first, one per 7-day block of the period; the last block ends today. */
    val weeks: List<WeekStats>,
    /** Only groups with working sets in a week; zero-volume weeks are omitted. */
    val muscleWeeks: List<MuscleWeek>,
    /** Every [MuscleGroup], in enum order. */
    val recency: List<MuscleRecency>,
    val consistency: Consistency,
    /** Best change first. */
    val strength: List<StrengthTrend>,
    /** Average of the machines' changePercent; null when there are none. */
    val overallStrengthPercent: Double?,
    /** Newest first. */
    val records: List<RecordEvent>,
    val plateaus: List<Plateau>,
    /** All four ranges, enum order, percent 0..100, summing to 100 (all 0 when there are no sets). */
    val repRanges: List<RepRangeShare>,
    val times: SessionTimes,
    /** Best estimate per non-cardio machine over the last window of the period, best first. */
    val strengthNow: List<MachineEstimate> = emptyList(),
    /** Average strength of all machines per week, 100.0 at the first point; oldest first. */
    val strengthIndex: List<IndexPoint> = emptyList(),
    val balance: BalanceStats = BalanceStats(0, 0, 0, 0, 0, 0, 0),
    /** Only (week, group) pairs with sets > 0, by week then group; indirect work of the period's working sets. */
    val indirectWeeks: List<IndirectWeek> = emptyList(),
)

/** Statistics over the sets, sessions and machines already logged. Working sets only; pure functions. */
object StatsRules {
    private const val MIN_ALL_WEEKS = 4
    private const val MAX_STRENGTH_WINDOW_WEEKS = 4
    private const val DAYS_IN_BLOCK = 7L
    private const val PLATEAU_MIN_SESSIONS = 6
    private const val PLATEAU_AFTER_SESSIONS = 4
    private const val MAX_GAP_MINUTES = 15L

    /** Indirect sets count as half a set: an approximation, not a measurement. */
    const val INDIRECT_WEIGHT = 0.5

    /** Secondary groups of a machine without library data, by its own group. */
    private val DEFAULT_INDIRECT: Map<MuscleGroup, List<MuscleGroup>> = mapOf(
        MuscleGroup.CHEST to listOf(MuscleGroup.SHOULDERS, MuscleGroup.ARMS),
        MuscleGroup.BACK to listOf(MuscleGroup.SHOULDERS, MuscleGroup.ARMS),
        MuscleGroup.SHOULDERS to listOf(MuscleGroup.ARMS),
        MuscleGroup.LEGS to listOf(MuscleGroup.CORE),
    )

    /** A set with the date of its session and its machine. */
    private class Entry(val set: WorkoutSet, val machine: Machine, val date: LocalDate, val session: Session)

    /**
     * [firstDayOfWeek] is kept for source compatibility; it no longer shapes the period, which is a rolling window
     * of 7-day blocks anchored on [today].
     */
    fun report(
        period: StatsPeriod,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
        weeklyGoal: Int,
        machines: List<Machine>,
        sessions: List<Session>,
        sets: List<WorkoutSet>,
        zone: ZoneId = ZoneId.systemDefault(),
        library: Map<String, LibraryExercise> = emptyMap(),
    ): StatsReport {
        val machineById = machines.associateBy { it.id }
        val sessionById = sessions.associateBy { it.id }
        val dateOf = sessions.associate { it.id to it.startedAt.atZone(zone).toLocalDate() }

        // Every set whose session and machine are known, whole history.
        val all = sets.mapNotNull { set ->
            val machine = machineById[set.machineId] ?: return@mapNotNull null
            val session = sessionById[set.sessionId] ?: return@mapNotNull null
            Entry(set, machine, dateOf.getValue(session.id), session)
        }

        val weekCount = weekCount(period, sessions.map { dateOf.getValue(it.id) }, today)
        val from = today.minusDays(DAYS_IN_BLOCK * weekCount - 1)
        val inPeriod = all.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }

        val weeks = weekStats(inPeriod, from, weekCount)
        val strength = strengthTrends(inPeriod, from, weekCount)
        return StatsReport(
            period = period,
            from = from,
            to = today,
            weeks = weeks,
            muscleWeeks = muscleWeeks(inPeriod, from),
            recency = recency(all, today),
            consistency = consistency(weeks, weeklyGoal),
            strength = strength,
            overallStrengthPercent = strength.takeIf { it.isNotEmpty() }?.map { it.changePercent }?.average(),
            records = records(all, from, today),
            plateaus = plateaus(all),
            repRanges = repRanges(inPeriod),
            times = times(sessions, dateOf, from, today, sets),
            strengthNow = strengthNow(inPeriod, from, weekCount),
            strengthIndex = strengthIndex(inPeriod, from, weekCount),
            balance = balance(inPeriod, library),
            indirectWeeks = indirectWeeks(inPeriod, library, from),
        )
    }

    private fun weekCount(
        period: StatsPeriod,
        sessionDates: List<LocalDate>,
        today: LocalDate,
    ): Int = when (period) {
        StatsPeriod.ONE_WEEK -> 1
        StatsPeriod.FOUR_WEEKS -> 4
        StatsPeriod.THREE_MONTHS -> 13
        StatsPeriod.ONE_YEAR -> 52
        StatsPeriod.ALL -> {
            val first = sessionDates.minOrNull() ?: today
            val days = ChronoUnit.DAYS.between(first, today) + 1
            Math.floorDiv(days + DAYS_IN_BLOCK - 1, DAYS_IN_BLOCK).toInt().coerceAtLeast(MIN_ALL_WEEKS)
        }
    }

    /** First day of the 7-day block of the period (which starts on [from]) that contains [date]. */
    private fun blockStart(date: LocalDate, from: LocalDate): LocalDate =
        from.plusDays(DAYS_IN_BLOCK * Math.floorDiv(ChronoUnit.DAYS.between(from, date), DAYS_IN_BLOCK))

    private fun isStrengthSet(e: Entry): Boolean = e.set.isWorking && !CardioRules.isCardio(e.set)

    private fun volumeOf(set: WorkoutSet): Double = (set.weightKg ?: 0.0) * (set.reps ?: 0)

    private fun weekStats(inPeriod: List<Entry>, from: LocalDate, count: Int): List<WeekStats> {
        val byWeek = inPeriod.groupBy { blockStart(it.date, from) }
        return (0 until count).map { i ->
            val start = from.plusDays(DAYS_IN_BLOCK * i)
            val entries = byWeek[start].orEmpty()
            val trained = entries.filter { it.set.isWorking || CardioRules.isCardio(it.set) }
            val strengthSets = entries.filter(::isStrengthSet)
            WeekStats(
                weekStart = start,
                sessions = trained.map { it.session.id }.toSet().size,
                trainingDays = trained.map { it.date }.toSet().size,
                workingSets = strengthSets.size,
                volumeKg = strengthSets.sumOf { volumeOf(it.set) },
            )
        }
    }

    private fun muscleWeeks(inPeriod: List<Entry>, from: LocalDate): List<MuscleWeek> =
        inPeriod.filter(::isStrengthSet)
            .groupBy { blockStart(it.date, from) to it.machine.muscleGroup }
            .map { (key, entries) ->
                MuscleWeek(key.first, key.second, entries.size, entries.sumOf { volumeOf(it.set) })
            }
            .sortedWith(compareBy({ it.weekStart }, { it.muscleGroup }))

    private fun indirectGroups(machine: Machine, library: Map<String, LibraryExercise>): Set<MuscleGroup> {
        val exercise = machine.libraryId?.let { library[it] }
        val groups = if (exercise != null) {
            exercise.secondaryMuscles.mapNotNull(::libraryMuscleGroup)
        } else {
            DEFAULT_INDIRECT[machine.muscleGroup].orEmpty()
        }
        return groups.filter { it != machine.muscleGroup && it != MuscleGroup.CARDIO }.toSet()
    }

    private fun indirectWeeks(
        inPeriod: List<Entry>,
        library: Map<String, LibraryExercise>,
        from: LocalDate,
    ): List<IndirectWeek> {
        val counts = HashMap<Pair<LocalDate, MuscleGroup>, Int>()
        for (e in inPeriod) {
            if (!isStrengthSet(e) || e.machine.kind == MachineKind.CARDIO) continue
            val week = blockStart(e.date, from)
            for (group in indirectGroups(e.machine, library)) counts.merge(week to group, 1, Int::plus)
        }
        return counts.map { (key, n) -> IndirectWeek(key.first, key.second, n) }
            .sortedWith(compareBy({ it.weekStart }, { it.muscleGroup }))
    }

    private fun recency(all: List<Entry>, today: LocalDate): List<MuscleRecency> {
        val lastByGroup = all.filter { it.set.isWorking }
            .groupBy { it.machine.muscleGroup }
            .mapValues { (_, entries) -> entries.maxOf { it.date } }
        return MuscleGroup.entries.map { group ->
            val last = lastByGroup[group]
            MuscleRecency(group, last, last?.let { ChronoUnit.DAYS.between(it, today).toInt() })
        }
    }

    /** Every block is complete (the last one ends today), so all blocks count toward the goal. */
    private fun consistency(weeks: List<WeekStats>, weeklyGoal: Int): Consistency {
        val goal = weeklyGoal.coerceIn(WeekGoalRules.MIN_GOAL, WeekGoalRules.MAX_GOAL)
        val best = weeks.filter { it.trainingDays > 0 }.maxWithOrNull(compareBy({ it.trainingDays }, { it.weekStart }))
        return Consistency(
            weeksCounted = weeks.size,
            weeksMetGoal = weeks.count { it.trainingDays >= goal },
            goal = goal,
            sessionsPerWeek = if (weeks.isEmpty()) 0.0 else weeks.sumOf { it.sessions }.toDouble() / weeks.size,
            bestWeekStart = best?.weekStart,
            bestWeekTrainingDays = best?.trainingDays ?: 0,
        )
    }

    private fun strengthTrends(
        inPeriod: List<Entry>,
        from: LocalDate,
        weekCount: Int,
    ): List<StrengthTrend> {
        // A trend compares an early window with a late one: one week has nothing to compare.
        if (weekCount < 2) return emptyList()
        val window = minOf(MAX_STRENGTH_WINDOW_WEEKS, weekCount / 2).coerceAtLeast(1)
        val firstEnd = from.plusDays(DAYS_IN_BLOCK * window)              // exclusive
        val lastStart = from.plusDays(DAYS_IN_BLOCK * (weekCount - window))
        return inPeriod
            .filter { it.machine.kind != MachineKind.CARDIO && !CardioRules.isCardio(it.set) }
            .groupBy { it.machine }
            .mapNotNull { (machine, entries) ->
                val before = entries.filter { it.date < firstEnd }.bestEstimate() ?: return@mapNotNull null
                val after = entries.filter { it.date >= lastStart }.bestEstimate() ?: return@mapNotNull null
                val change = after - before
                StrengthTrend(machine, before, after, change, change / before * 100.0)
            }
            .sortedByDescending { it.changePercent }
    }

    private fun isNonCardio(e: Entry): Boolean = e.machine.kind != MachineKind.CARDIO && !CardioRules.isCardio(e.set)

    /** Best estimate of each non-cardio machine over the same last window as the strength trend. */
    private fun strengthNow(inPeriod: List<Entry>, from: LocalDate, weekCount: Int): List<MachineEstimate> {
        val window = minOf(MAX_STRENGTH_WINDOW_WEEKS, weekCount / 2).coerceAtLeast(1)
        val lastStart = from.plusDays(DAYS_IN_BLOCK * (weekCount - window))
        return inPeriod.filter { isNonCardio(it) && it.date >= lastStart }
            .groupBy { it.machine }
            .mapNotNull { (machine, entries) -> entries.bestEstimate()?.let { MachineEstimate(machine, it) } }
            .sortedByDescending { it.estimate1rm }
    }

    /**
     * Chain-linked strength index, 100.0 at the first block with a value. A machine's value in a block is its best
     * estimate over the trailing 4 blocks ending that block. Each next point is the previous index times the mean of
     * value(this week) / value(previous week) over the machines that have a value in both weeks, so a machine joining
     * or leaving does not move the index. With no such machine the previous index carries forward; weeks without any
     * machine value get no point.
     */
    private fun strengthIndex(inPeriod: List<Entry>, from: LocalDate, weekCount: Int): List<IndexPoint> {
        if (weekCount < 2) return emptyList()   // a single point is not a trend
        val values = inPeriod.filter(::isNonCardio).groupBy { it.machine }.values.map { entries ->
            (0 until weekCount).map { i ->
                val start = from.plusDays(DAYS_IN_BLOCK * maxOf(0, i - MAX_STRENGTH_WINDOW_WEEKS + 1))
                val endExclusive = from.plusDays(DAYS_IN_BLOCK * (i + 1))
                entries.filter { it.date >= start && it.date < endExclusive }.bestEstimate()
            }
        }
        val points = ArrayList<IndexPoint>()
        var index = 100.0
        // The week of the previous point: a gap with no data compares with the last week that had some.
        var previousWeek = -1
        for (i in 0 until weekCount) {
            if (values.none { it[i] != null }) continue
            if (previousWeek >= 0) {
                val ratios = values.mapNotNull { v ->
                    val now = v[i]
                    val before = v[previousWeek]
                    if (now != null && before != null) now / before else null
                }
                if (ratios.isNotEmpty()) index *= ratios.average()
            }
            points.add(IndexPoint(from.plusDays(DAYS_IN_BLOCK * i), index))
            previousWeek = i
        }
        return points
    }

    private fun balance(inPeriod: List<Entry>, library: Map<String, LibraryExercise>): BalanceStats {
        var push = 0; var pull = 0; var upper = 0; var lower = 0; var quad = 0; var ham = 0; var unclassified = 0
        for (e in inPeriod) {
            if (!isStrengthSet(e) || e.machine.kind == MachineKind.CARDIO) continue
            val exercise = e.machine.libraryId?.let { library[it] }
            val isPush: Boolean
            val isPull: Boolean
            val isUpper: Boolean
            val isLower: Boolean
            if (exercise != null) {
                val muscles = exercise.primaryMuscles.map { it.trim().lowercase(Locale.ROOT) }
                val force = exercise.force?.trim()?.lowercase(Locale.ROOT)
                isPush = force == "push"
                isPull = force == "pull"
                isUpper = muscles.any { it in UPPER_MUSCLES }
                isLower = muscles.any { it in LOWER_MUSCLES }
                if ("quadriceps" in muscles) quad++
                if ("hamstrings" in muscles) ham++
            } else {
                when (e.machine.muscleGroup) {
                    MuscleGroup.CHEST, MuscleGroup.SHOULDERS -> { isPush = true; isPull = false; isUpper = true; isLower = false }
                    MuscleGroup.BACK -> { isPush = false; isPull = true; isUpper = true; isLower = false }
                    MuscleGroup.LEGS -> { isPush = false; isPull = false; isUpper = false; isLower = true }
                    MuscleGroup.ARMS -> { isPush = false; isPull = false; isUpper = true; isLower = false }
                    MuscleGroup.CORE, MuscleGroup.CARDIO -> { isPush = false; isPull = false; isUpper = false; isLower = false }
                }
            }
            if (isPush) push++
            if (isPull) pull++
            if (isUpper) upper++
            if (isLower) lower++
            if (!isPush && !isPull && !isUpper && !isLower) unclassified++
        }
        return BalanceStats(push, pull, upper, lower, quad, ham, unclassified)
    }

    private val UPPER_MUSCLES = setOf(
        "chest", "shoulders", "lats", "middle back", "lower back", "traps", "biceps", "triceps", "forearms",
    )
    private val LOWER_MUSCLES = setOf("quadriceps", "hamstrings", "glutes", "calves", "adductors", "abductors")

    private fun List<Entry>.bestEstimate(): Double? = mapNotNull { OneRepMaxRules.estimate(it.set) }.maxOrNull()

    /** Replays each machine's working sets over the whole history; keeps the records dated inside the period. */
    private fun records(all: List<Entry>, from: LocalDate, today: LocalDate): List<RecordEvent> {
        val events = ArrayList<RecordEvent>()
        all.filter(::isStrengthSet).groupBy { it.machine }.forEach { (machine, entries) ->
            val earlier = ArrayList<WorkoutSet>()
            for (e in entries.sortedWith(chronological)) {
                val record = RecordRules.recordFor(e.set, earlier)
                if (record != null && !e.date.isBefore(from) && !e.date.isAfter(today)) {
                    events.add(RecordEvent(machine, e.date, record))
                }
                earlier.add(e.set)
            }
        }
        return events.sortedByDescending { it.date }
    }

    private val chronological: Comparator<Entry> =
        compareBy<Entry>({ it.session.startedAt }, { it.session.id }, { it.set.position }, { it.set.id })

    /** Machines with at least 6 sessions that have an estimate and no new best for 4 or more sessions. */
    private fun plateaus(all: List<Entry>): List<Plateau> =
        all.filter { it.machine.kind != MachineKind.CARDIO && !CardioRules.isCardio(it.set) }
            .groupBy { it.machine }
            .mapNotNull { (machine, entries) ->
                val bestPerSession = entries
                    .sortedWith(chronological)
                    .mapNotNull { e -> OneRepMaxRules.estimate(e.set)?.let { e.session to it } }
                    .groupBy({ it.first.id }, { it.second })
                    .values.map { it.max() }   // groupBy keeps first-seen order, so oldest session first
                if (bestPerSession.size < PLATEAU_MIN_SESSIONS) return@mapNotNull null
                val best = bestPerSession.max()
                val sinceBest = bestPerSession.size - 1 - bestPerSession.indexOf(best)
                if (sinceBest >= PLATEAU_AFTER_SESSIONS) Plateau(machine, sinceBest, best) else null
            }
            .sortedByDescending { it.sessionsWithoutImprovement }

    private fun repRanges(inPeriod: List<Entry>): List<RepRangeShare> {
        val counts = inPeriod.filter(::isStrengthSet)
            .mapNotNull { e -> e.set.reps?.takeIf { it >= 1 }?.let(::rangeOf) }
            .groupingBy { it }.eachCount()
        val total = counts.values.sum()
        return RepRange.entries.map { range ->
            val n = counts[range] ?: 0
            RepRangeShare(range, n, if (total == 0) 0.0 else n * 100.0 / total)
        }
    }

    private fun rangeOf(reps: Int): RepRange = when {
        reps <= 5 -> RepRange.R1_5
        reps <= 8 -> RepRange.R6_8
        reps <= 12 -> RepRange.R9_12
        else -> RepRange.R13_PLUS
    }

    private fun times(
        sessions: List<Session>,
        dateOf: Map<Long, LocalDate>,
        from: LocalDate,
        today: LocalDate,
        sets: List<WorkoutSet>,
    ): SessionTimes {
        val setsBySession = sets.groupBy { it.sessionId }
        val counted = sessions.filter {
            val date = dateOf.getValue(it.id)
            val end = it.endedAt
            end != null && end > SessionTimeRules.effectiveStart(it, setsBySession[it.id].orEmpty()) && !date.isBefore(from) && !date.isAfter(today)
        }
        if (counted.isEmpty()) return SessionTimes(0, null, null)
        val averageMinutes = counted
            .map {
                val start = SessionTimeRules.effectiveStart(it, setsBySession[it.id].orEmpty())
                Duration.between(start, it.endedAt!!).toMillis() / 60_000.0
            }
            .average().roundToInt()
        val gaps = counted.flatMap { session ->
            setsBySession[session.id].orEmpty().map { it.loggedAt }.sorted().zipWithNext { a, b ->
                Duration.between(a, b).toMillis() / 60_000.0
            }
        }.filter { it <= MAX_GAP_MINUTES }
        return SessionTimes(counted.size, averageMinutes, gaps.takeIf { it.isNotEmpty() }?.average())
    }
}
