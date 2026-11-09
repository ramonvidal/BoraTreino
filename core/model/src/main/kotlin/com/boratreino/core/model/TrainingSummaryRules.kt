package com.boratreino.core.model

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** What the "Training complete" sheet shows for one session. */
data class TrainingSummary(
    val sessionId: Long,
    val routineName: String?,
    val date: LocalDate,
    /** Null when the session has no real duration (added by hand: endedAt == startedAt, or still open). */
    val durationMinutes: Int?,
    val workingSets: Int,
    val volumeKg: Double,
    val machineCount: Int,
    /** The records set during this session, newest first. */
    val records: List<RecordEvent>,
    /** The last earlier closed session of the same routine; null for a free session or when there is none. */
    val previous: PreviousComparison?,
)

/** This session against the previous one of the same routine. */
data class PreviousComparison(
    val volumeKg: Double,
    val workingSets: Int,
    /** Null when the previous volume is 0. */
    val volumeChangePercent: Double?,
    val setsChange: Int,
)

object TrainingSummaryRules {
    private const val MAX_DURATION_MINUTES = 360

    /**
     * [sets] are the session's sets; [earlierSetsByMachine] the sets of the machines used that were logged
     * before the session started (for records). [previous] is the last earlier closed session of the same
     * routine with its sets. Warm-ups count for nothing; cardio sets add nothing to volume.
     */
    fun build(
        session: Session,
        routineName: String?,
        sets: List<WorkoutSet>,
        machines: List<Machine>,
        earlierSetsByMachine: Map<Long, List<WorkoutSet>>,
        previous: Pair<Session, List<WorkoutSet>>?,
        zone: ZoneId,
    ): TrainingSummary {
        val working = sets.working()
        val volume = volumeOf(working)
        val byId = machines.associateBy { it.id }
        return TrainingSummary(
            sessionId = session.id,
            routineName = routineName,
            date = session.startedAt.atZone(zone).toLocalDate(),
            durationMinutes = durationMinutes(session),
            workingSets = working.size,
            volumeKg = volume,
            machineCount = working.map { it.machineId }.distinct().size,
            records = records(session, sets, byId, earlierSetsByMachine, zone),
            previous = previous?.let { (_, prevSets) ->
                val prevWorking = prevSets.working()
                val prevVolume = volumeOf(prevWorking)
                PreviousComparison(
                    volumeKg = prevVolume,
                    workingSets = prevWorking.size,
                    volumeChangePercent = if (prevVolume > 0.0) (volume - prevVolume) / prevVolume * 100.0 else null,
                    setsChange = working.size - prevWorking.size,
                )
            },
        )
    }

    private fun volumeOf(working: List<WorkoutSet>): Double =
        working.filterNot { CardioRules.isCardio(it) }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }

    private fun durationMinutes(session: Session): Int? {
        val end = session.endedAt ?: return null
        if (end <= session.startedAt) return null
        val seconds = Duration.between(session.startedAt, end).seconds
        return ((seconds + 30L) / 60L).toInt().coerceIn(1, MAX_DURATION_MINUTES)
    }

    /** Replays the session's sets in order over the earlier history; newest record first. */
    private fun records(
        session: Session,
        sets: List<WorkoutSet>,
        machines: Map<Long, Machine>,
        earlierSetsByMachine: Map<Long, List<WorkoutSet>>,
        zone: ZoneId,
    ): List<RecordEvent> {
        val date = session.startedAt.atZone(zone).toLocalDate()
        val history = HashMap<Long, MutableList<WorkoutSet>>()
        val events = ArrayList<RecordEvent>()
        val ordered = sets.sortedWith(compareBy({ it.loggedAt }, { it.position }, { it.id }))
        for (set in ordered) {
            val earlier = history.getOrPut(set.machineId) { earlierSetsByMachine[set.machineId].orEmpty().toMutableList() }
            val machine = machines[set.machineId]
            if (machine != null) {
                RecordRules.recordFor(set, earlier)?.let { events.add(RecordEvent(machine, date, it)) }
            }
            earlier.add(set)
        }
        return events.reversed()
    }
}
