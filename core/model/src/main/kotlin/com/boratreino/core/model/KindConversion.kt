package com.boratreino.core.model

/** What one rep becomes when a machine changes between a reps kind and a time or distance kind. */
enum class ConversionFactor { SECONDS_PER_REP, METRES_PER_REP }

/** Why a kind change with history is not offered. */
enum class NotOfferedReason { CARDIO, LOAD_WOULD_BE_LOST, NO_COMMON_MEASURE }

/** What changing a machine's kind means for the sets already logged on it. */
sealed interface KindConversion {
    /** Same kind, or no logged sets: save as usual, nothing to ask. */
    data object Free : KindConversion

    /** The sets keep their numbers (WR <-> BR; TIME -> WEIGHT_TIME; WEIGHT_TIME -> TIME when no load > 0). */
    data class Unchanged(val setCount: Int) : KindConversion

    /**
     * Each set's amount is converted with a factor the user confirms. [default] is 30 for SECONDS_PER_REP and null
     * for METRES_PER_REP (ask). [sample] = the most recent working set, for the preview.
     */
    data class NeedsFactor(
        val setCount: Int,
        val factor: ConversionFactor,
        val default: Int?,
        val sample: WorkoutSet,
    ) : KindConversion

    data class NotOffered(val reason: NotOfferedReason) : KindConversion
}

/** Pure rules for changing a machine's kind when it already has sets. */
object KindConversionRules {
    const val DEFAULT_SECONDS_PER_REP = 30
    const val MAX_SECONDS_PER_REP = 600
    const val MAX_METRES_PER_REP = 1000

    private const val EPSILON = 0.001

    /**
     * From/to with sets (any measure pair not listed is NotOffered(NO_COMMON_MEASURE)):
     *  - same kind or no sets -> Free
     *  - either side CARDIO -> NotOffered(CARDIO)
     *  - target TIME and any set's weightKg > 0.001 -> NotOffered(LOAD_WOULD_BE_LOST) (checked before the rows below)
     *  - REPS <-> REPS (WR <-> BR) -> Unchanged
     *  - REPS -> SECONDS -> NeedsFactor(SECONDS_PER_REP, 30); REPS -> METRES -> NeedsFactor(METRES_PER_REP, null)
     *  - SECONDS -> REPS -> NeedsFactor(SECONDS_PER_REP, 30); METRES -> REPS -> NeedsFactor(METRES_PER_REP, null)
     *  - TIME <-> WEIGHT_TIME -> Unchanged
     *  - SECONDS <-> METRES -> NotOffered(NO_COMMON_MEASURE)
     * Warm-ups count like any other set.
     */
    fun conversion(from: MachineKind, to: MachineKind, sets: List<WorkoutSet>): KindConversion {
        if (from == to || sets.isEmpty()) return KindConversion.Free
        if (from == MachineKind.CARDIO || to == MachineKind.CARDIO) {
            return KindConversion.NotOffered(NotOfferedReason.CARDIO)
        }
        if (to == MachineKind.TIME && sets.any { (it.weightKg ?: 0.0) > EPSILON }) {
            return KindConversion.NotOffered(NotOfferedReason.LOAD_WOULD_BE_LOST)
        }
        val fromMeasure = from.measure
        val toMeasure = to.measure
        val count = sets.size
        return when {
            fromMeasure == toMeasure -> KindConversion.Unchanged(count)
            fromMeasure == SetMeasure.REPS -> needsFactor(toMeasure, count, sets)
            toMeasure == SetMeasure.REPS -> needsFactor(fromMeasure, count, sets)
            else -> KindConversion.NotOffered(NotOfferedReason.NO_COMMON_MEASURE)
        }
    }

    private fun needsFactor(measure: SetMeasure?, count: Int, sets: List<WorkoutSet>): KindConversion {
        val sample = sample(sets)
        return when (measure) {
            SetMeasure.SECONDS ->
                KindConversion.NeedsFactor(count, ConversionFactor.SECONDS_PER_REP, DEFAULT_SECONDS_PER_REP, sample)
            SetMeasure.METRES ->
                KindConversion.NeedsFactor(count, ConversionFactor.METRES_PER_REP, null, sample)
            else -> KindConversion.NotOffered(NotOfferedReason.NO_COMMON_MEASURE)
        }
    }

    /** The most recent working set (by loggedAt, then position); the most recent set when all are warm-ups. */
    private fun sample(sets: List<WorkoutSet>): WorkoutSet {
        val order = compareBy<WorkoutSet>({ it.loggedAt }, { it.position }, { it.id })
        return sets.working().maxWithOrNull(order) ?: sets.maxWith(order)
    }

    /** [conversion] for every kind other than [from], for the editor's chips. */
    fun conversions(from: MachineKind, sets: List<WorkoutSet>): Map<MachineKind, KindConversion> =
        MachineKind.entries.filter { it != from }.associateWith { conversion(from, it, sets) }

    /**
     * One set in [to]'s shape. reps -> seconds = (reps ?: 0) x factor; reps -> metres likewise;
     * seconds -> reps = round-half-up(seconds / factor), at least 1 when seconds > 0, else 0; metres -> reps likewise.
     * Clamped to LoggingRules' maxima. null weight -> 0.0; TIME -> 0.0. id, session, position, loggedAt and type kept.
     * require(factor in 1..max) when a factor is needed.
     */
    fun convertSet(set: WorkoutSet, from: MachineKind, to: MachineKind, factor: Int?): WorkoutSet {
        val fromMeasure = requireNotNull(from.measure) { "Cannot convert from $from" }
        val toMeasure = requireNotNull(to.measure) { "Cannot convert to $to" }
        val amount = when (fromMeasure) {
            SetMeasure.REPS -> set.reps ?: 0
            SetMeasure.SECONDS -> set.seconds ?: 0
            SetMeasure.METRES -> set.distanceM ?: 0
        }
        val weight = set.weightKg ?: 0.0
        val draft = when {
            fromMeasure == toMeasure -> draftWith(set, weight, toMeasure, amount)
            fromMeasure == SetMeasure.REPS -> {
                val f = checkedFactor(toMeasure, factor)
                draftWith(set, weight, toMeasure, (amount.toLong() * f).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }
            toMeasure == SetMeasure.REPS -> {
                val f = checkedFactor(fromMeasure, factor)
                val reps = ((amount.toLong() * 2 + f) / (2L * f)).toInt()
                draftWith(set, weight, toMeasure, if (amount > 0) maxOf(reps, 1) else 0)
            }
            else -> throw IllegalArgumentException("No common measure between $from and $to")
        }
        return LoggingRules.applyDraft(set, draft, to)
    }

    private fun draftWith(set: WorkoutSet, weight: Double, measure: SetMeasure, amount: Int): SetDraft = when (measure) {
        SetMeasure.REPS -> SetDraft(weight, amount, set.type)
        SetMeasure.SECONDS -> SetDraft(weight, 0, set.type, seconds = amount)
        SetMeasure.METRES -> SetDraft(weight, 0, set.type, distanceM = amount)
    }

    /** The factor for a pair involving [timedMeasure] (SECONDS or METRES), checked against its range. */
    private fun checkedFactor(timedMeasure: SetMeasure, factor: Int?): Int {
        val max = if (timedMeasure == SetMeasure.SECONDS) MAX_SECONDS_PER_REP else MAX_METRES_PER_REP
        require(factor != null && factor in 1..max) { "factor must be in 1..$max, was $factor" }
        return factor
    }

    /** All sets; throws IllegalArgumentException when [conversion] is NotOffered or a needed factor is missing or out of range. */
    fun convert(sets: List<WorkoutSet>, from: MachineKind, to: MachineKind, factor: Int?): List<WorkoutSet> =
        when (val c = conversion(from, to, sets)) {
            is KindConversion.Free -> sets
            is KindConversion.NotOffered -> throw IllegalArgumentException("Conversion $from -> $to not offered: ${c.reason}")
            is KindConversion.Unchanged -> sets.map { convertSet(it, from, to, null) }
            is KindConversion.NeedsFactor -> {
                val max = if (c.factor == ConversionFactor.SECONDS_PER_REP) MAX_SECONDS_PER_REP else MAX_METRES_PER_REP
                require(factor != null && factor in 1..max) { "factor must be in 1..$max, was $factor" }
                sets.map { convertSet(it, from, to, factor) }
            }
        }
}
