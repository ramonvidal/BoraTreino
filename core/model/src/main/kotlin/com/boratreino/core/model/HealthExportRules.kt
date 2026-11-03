package com.boratreino.core.model

import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** The kind of exercise session sent to the phone's health store. */
enum class HealthExerciseKind {
    STRENGTH_TRAINING,
    TREADMILL,
    STATIONARY_BIKE,
    ELLIPTICAL,
    ROWING_MACHINE,
    STAIR_CLIMBING_MACHINE,
    WALKING,
    RUNNING,
    OTHER,
}

/** One finished session, ready to be written to the health store. */
data class HealthExport(
    val sessionId: Long,
    /** "boratreino-session-<id>". */
    val clientRecordId: String,
    /** Content hash >= 0: same content gives the same version; changes when sets or times change. */
    val version: Long,
    /** [end] is after [start] by at least one minute. */
    val start: Instant,
    val end: Instant,
    val kind: HealthExerciseKind,
    /** The routine's name, or "Training". */
    val title: String,
    /** Sum of cardio calories; 0 = none. */
    val activeCalories: Int,
    /** Sum of cardio distance; 0 = none. */
    val distanceM: Int,
    val strengthSets: Int,
    val strengthReps: Int,
)

object HealthExportRules {
    const val CLIENT_ID_PREFIX = "boratreino-session-"
    private const val DEFAULT_TITLE = "Training"

    /** Null when the session is open or has no sets. Machines map machine id -> Machine. */
    fun export(
        session: Session,
        sets: List<WorkoutSet>,
        machines: Map<Long, Machine>,
        routineName: String?,
    ): HealthExport? {
        val endedAt = session.endedAt ?: return null
        if (sets.isEmpty()) return null
        val start = session.startedAt
        val end = maxOf(endedAt, start.plus(Duration.ofMinutes(1)))
        val cardio = sets.filter { CardioRules.isCardio(it) }
        val strength = sets.filterNot { CardioRules.isCardio(it) }
        val kind = kindFor(sets, machines)
        val title = routineName?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_TITLE
        val calories = cardio.sumOf { it.calories ?: 0 }
        val distance = cardio.sumOf { it.distanceM ?: 0 }
        val strengthReps = strength.sumOf { it.reps ?: 0 }
        return HealthExport(
            sessionId = session.id,
            clientRecordId = clientId(session.id),
            version = hash(session.id, start, end, kind, title, sets),
            start = start,
            end = end,
            kind = kind,
            title = title,
            activeCalories = calories,
            distanceM = distance,
            strengthSets = strength.size,
            strengthReps = strengthReps,
        )
    }

    /**
     * Any weights set -> STRENGTH_TRAINING. Cardio only: the kind of the machine with the most minutes,
     * from keywords in its name; OTHER when nothing matches or the machine is unknown.
     */
    fun kindFor(sets: List<WorkoutSet>, machines: Map<Long, Machine>): HealthExerciseKind {
        if (sets.any { !CardioRules.isCardio(it) }) return HealthExerciseKind.STRENGTH_TRAINING
        val top = sets.groupBy { it.machineId }
            .maxByOrNull { (_, group) -> group.sumOf { it.seconds ?: 0 } }
            ?.key
        val name = top?.let { machines[it]?.name } ?: return HealthExerciseKind.OTHER
        val n = normalize(name)
        return when {
            has(n, "treadmill", "esteira") -> HealthExerciseKind.TREADMILL
            has(n, "bike", "bicicleta", "spinning") -> HealthExerciseKind.STATIONARY_BIKE
            has(n, "elliptical", "eliptico") -> HealthExerciseKind.ELLIPTICAL
            has(n, "row", "remo") -> HealthExerciseKind.ROWING_MACHINE
            has(n, "stair", "escada") -> HealthExerciseKind.STAIR_CLIMBING_MACHINE
            has(n, "walk", "caminhada") -> HealthExerciseKind.WALKING
            has(n, "run", "corrida") -> HealthExerciseKind.RUNNING
            else -> HealthExerciseKind.OTHER
        }
    }

    fun clientId(sessionId: Long): String = CLIENT_ID_PREFIX + sessionId

    /** The session id in [clientRecordId], also for the "-cal" and "-dist" suffixes; null when it is not ours. */
    fun sessionIdOf(clientRecordId: String): Long? {
        if (!clientRecordId.startsWith(CLIENT_ID_PREFIX)) return null
        val rest = clientRecordId.removePrefix(CLIENT_ID_PREFIX)
        val digits = rest.takeWhile { it.isDigit() }
        if (digits.isEmpty()) return null
        val suffix = rest.removePrefix(digits)
        if (suffix.isNotEmpty() && suffix != "-cal" && suffix != "-dist") return null
        return digits.toLongOrNull()
    }

    private fun has(text: String, vararg words: String): Boolean = words.any { text.contains(it) }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)

    /** Stable 63-bit FNV-1a style hash of everything that goes into the record. */
    private fun hash(
        sessionId: Long,
        start: Instant,
        end: Instant,
        kind: HealthExerciseKind,
        title: String,
        sets: List<WorkoutSet>,
    ): Long {
        var h = -3750763034362895579L
        fun mix(value: Long) {
            h = (h xor value) * 1099511628211L
        }
        fun mix(text: String) {
            for (c in text) mix(c.code.toLong())
            mix(0x1F)
        }
        mix(sessionId)
        mix(start.toEpochMilli())
        mix(end.toEpochMilli())
        mix(kind.name)
        mix(title)
        for (set in sets.sortedWith(compareBy({ it.position }, { it.id }))) {
            mix(set.id)
            mix(set.machineId)
            mix(set.position.toLong())
            mix(set.weightKg?.toRawBits() ?: -1L)
            mix((set.reps ?: -1).toLong())
            mix((set.seconds ?: -1).toLong())
            mix((set.calories ?: -1).toLong())
            mix((set.distanceM ?: -1).toLong())
            mix(set.loggedAt.toEpochMilli())
        }
        return h and Long.MAX_VALUE
    }
}
