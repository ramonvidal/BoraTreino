package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.BackupData
import com.boratreino.core.model.Machine
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutinePlan
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * In-memory [WorkoutStore] that follows the store's documented rules, including the foreign keys
 * (deleting a routine removes its items and clears its sessions' routine). Observed flows
 * re-read after every write, like Room does.
 */
internal class FakeWorkoutStore : WorkoutStore {
    private val machineRows = mutableListOf<Machine>()
    private val sessions = mutableListOf<Session>()
    private val sets = mutableListOf<WorkoutSet>()
    private val plans = mutableListOf<RoutinePlan>()
    private val routines = mutableListOf<Routine>()
    private val items = mutableListOf<RoutineItem>()
    private var nextMachineId = 1L
    private var nextSessionId = 1L
    private var nextSetId = 1L
    private var nextPlanId = 1L
    private var nextRoutineId = 1L
    private val changes = MutableStateFlow(0)

    /** Every session, open or closed, in insertion order. For assertions only. */
    fun allSessions(): List<Session> = sessions.toList()

    /** Every set, in insertion order. For assertions only. */
    fun allSets(): List<WorkoutSet> = sets.toList()

    /** Every routine, by position. */
    private fun routinesByPosition(): List<Routine> = routines.sortedWith(compareBy<Routine>({ it.position }, { it.id }))

    /** Every routine item, by routine then position. For assertions only. */
    fun allRoutineItems(): List<RoutineItem> = items.sortedWith(compareBy({ it.routineId }, { it.position }))

    private fun <T> observe(read: () -> T): Flow<T> = changes.map { read() }

    private fun changed() {
        changes.value = changes.value + 1
    }

    private fun openSessionOrNull(): Session? =
        sessions.filter { it.endedAt == null }.maxWithOrNull(compareBy<Session>({ it.startedAt }, { it.id }))

    private fun setsOf(sessionId: Long, machineId: Long? = null): List<WorkoutSet> =
        sets.filter { it.sessionId == sessionId && (machineId == null || it.machineId == machineId) }

    private fun routinesOf(planId: Long): List<Routine> =
        routines.filter { it.planId == planId }.sortedWith(compareBy<Routine>({ it.position }, { it.id }))

    // Machines

    override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = observe {
        machineRows
            .filter { includeArchived || !it.archived }
            .sortedWith(compareBy<Machine>({ it.name.lowercase() }, { it.id }))
    }

    override fun observeMachine(id: Long): Flow<Machine?> = observe {
        machineRows.firstOrNull { it.id == id }
    }

    override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = observe {
        machineRows
            .filter { !it.archived }
            .mapNotNull { machine ->
                sets.filter { it.machineId == machine.id }
                    .maxWithOrNull(compareBy<WorkoutSet>({ it.loggedAt }, { it.id }))
                    ?.let { last -> machine to last }
            }
            .sortedWith(compareByDescending<Pair<Machine, WorkoutSet>> { it.second.loggedAt }.thenByDescending { it.second.id })
            .take(limit)
            .map { it.first }
    }

    override suspend fun saveMachine(machine: Machine): Long {
        val id = if (machine.id == 0L) nextMachineId++ else machine.id
        machineRows.removeAll { it.id == id }
        machineRows += machine.copy(id = id)
        changed()
        return id
    }

    /** Matches the stored name trimmed and ignoring case, like the store's TRIM(name) = :name COLLATE NOCASE. */
    override suspend fun addMachineOrUnarchive(machine: Machine): Long {
        val wanted = machine.name.trim()
        val existing = machineRows.firstOrNull { it.name.trim().equals(wanted, ignoreCase = true) }
        if (existing != null) {
            if (existing.archived) {
                machineRows.removeAll { it.id == existing.id }
                machineRows += existing.copy(archived = false)
                changed()
            }
            return existing.id
        }
        return saveMachine(machine.copy(id = 0))
    }

    override suspend fun setMachineArchived(id: Long, archived: Boolean) {
        val existing = machineRows.firstOrNull { it.id == id } ?: return
        machineRows.removeAll { it.id == id }
        machineRows += existing.copy(archived = archived)
        changed()
    }

    // Sessions and sets

    override fun observeOpenSession(): Flow<Session?> = observe { openSessionOrNull() }

    override suspend fun openSession(): Session? = openSessionOrNull()

    override fun observeSessionSets(sessionId: Long): Flow<List<WorkoutSet>> = observe {
        setsOf(sessionId).sortedWith(compareBy<WorkoutSet>({ it.machineId }, { it.position }, { it.id }))
    }

    override fun observeMachineSets(sessionId: Long, machineId: Long): Flow<List<WorkoutSet>> = observe {
        setsOf(sessionId, machineId).sortedWith(compareBy<WorkoutSet>({ it.position }, { it.id }))
    }

    override fun observeLastTimeSets(machineId: Long, excludeSessionId: Long): Flow<List<WorkoutSet>> = observe {
        val candidates = sessions.filter { session ->
            session.id != excludeSessionId && sets.any { it.sessionId == session.id && it.machineId == machineId }
        }
        val last = candidates.maxWithOrNull(compareBy<Session>({ it.startedAt }, { it.id }))
        if (last == null) {
            emptyList()
        } else {
            setsOf(last.id, machineId).sortedWith(compareBy<WorkoutSet>({ it.position }, { it.id }))
        }
    }

    override suspend fun lastSetAt(sessionId: Long): Instant? =
        setsOf(sessionId).maxOfOrNull { it.loggedAt }

    override suspend fun setCount(sessionId: Long, machineId: Long): Int =
        setsOf(sessionId, machineId).size

    override suspend fun summary(sessionId: Long): SessionSummary {
        val inSession = setsOf(sessionId)
        return SessionSummary(
            sessionId = sessionId,
            machineCount = inSession.map { it.machineId }.distinct().size,
            setCount = inSession.size,
        )
    }

    override suspend fun insertSession(session: Session): Long {
        val id = nextSessionId++
        sessions += session.copy(id = id)
        changed()
        return id
    }

    override suspend fun setSessionEnd(sessionId: Long, endedAt: Instant?) {
        val existing = sessions.firstOrNull { it.id == sessionId } ?: return
        sessions.removeAll { it.id == sessionId }
        sessions += existing.copy(endedAt = endedAt)
        changed()
    }

    override suspend fun hasOtherOpenSession(sessionId: Long): Boolean =
        sessions.any { it.endedAt == null && it.id != sessionId }

    override suspend fun insertSet(set: WorkoutSet): Long {
        val id = if (set.id == 0L) nextSetId++ else set.id
        sets += set.copy(id = id)
        changed()
        return id
    }

    override suspend fun updateSet(set: WorkoutSet) {
        sets.removeAll { it.id == set.id }
        sets += set
        changed()
    }

    override suspend fun deleteSet(setId: Long) {
        sets.removeAll { it.id == setId }
        changed()
    }

    override suspend fun setSessionRoutine(sessionId: Long, routineId: Long?) {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index >= 0) {
            sessions[index] = sessions[index].copy(routineId = routineId)
            changed()
        }
    }

    /** Only finished sessions count, most recent first. */
    private fun lastRoutineIdOrNull(): Long? =
        sessions
            .filter { it.routineId != null && it.endedAt != null }
            .maxWithOrNull(compareBy<Session>({ it.startedAt }, { it.id }))
            ?.routineId

    override fun observeLastRoutineId(): Flow<Long?> = observe { lastRoutineIdOrNull() }

    override suspend fun lastRoutineId(): Long? = lastRoutineIdOrNull()

    // History

    override fun observeLastSessionSets(
        machineId: Long,
        excludeSessionId: Long,
        limit: Int,
    ): Flow<List<List<WorkoutSet>>> = observe {
        sessions
            .filter { session ->
                session.id != excludeSessionId && sets.any { it.sessionId == session.id && it.machineId == machineId }
            }
            .sortedWith(compareByDescending<Session> { it.startedAt }.thenByDescending { it.id })
            .take(limit)
            .map { session -> setsOf(session.id, machineId).sortedWith(compareBy<WorkoutSet>({ it.position }, { it.id })) }
    }

    override suspend fun machineSets(machineId: Long): List<WorkoutSet> =
        sets.filter { it.machineId == machineId }

    override suspend fun machinesSetsBefore(machineIds: List<Long>, before: Instant): List<WorkoutSet> =
        sets.filter { it.machineId in machineIds && it.loggedAt.isBefore(before) }

    override suspend fun sessionSets(sessionId: Long): List<WorkoutSet> =
        setsOf(sessionId).sortedWith(compareBy<WorkoutSet>({ it.loggedAt }, { it.id }))

    // Routines

    override fun observePlan(): Flow<RoutinePlan?> = observe { plans.firstOrNull() }

    override suspend fun plan(): RoutinePlan? = plans.firstOrNull()

    override suspend fun ensurePlan(): RoutinePlan {
        plans.firstOrNull()?.let { return it }
        val plan = RoutinePlan(id = nextPlanId++, scheduleMode = ScheduleMode.IN_TURN)
        plans += plan
        changed()
        return plan
    }

    override suspend fun setScheduleMode(planId: Long, mode: ScheduleMode) {
        val index = plans.indexOfFirst { it.id == planId }
        if (index >= 0) {
            plans[index] = plans[index].copy(scheduleMode = mode)
            changed()
        }
    }

    override fun observeRoutines(planId: Long): Flow<List<Routine>> = observe { routinesOf(planId) }

    override suspend fun routines(planId: Long): List<Routine> = routinesOf(planId)

    override fun observeRoutine(id: Long): Flow<Routine?> = observe { routines.firstOrNull { it.id == id } }

    override fun observeAllRoutineItems(): Flow<List<RoutineItem>> = observe { allRoutineItems() }

    override fun observeRoutineItems(routineId: Long): Flow<List<RoutineItem>> = observe {
        items.filter { it.routineId == routineId }.sortedBy { it.position }
    }

    override suspend fun insertRoutine(routine: Routine): Long {
        val id = nextRoutineId++
        routines += routine.copy(id = id)
        changed()
        return id
    }

    override suspend fun updateRoutine(routine: Routine) {
        val index = routines.indexOfFirst { it.id == routine.id }
        if (index >= 0) {
            routines[index] = routine
            changed()
        }
    }

    override suspend fun setRoutinePosition(id: Long, position: Int) {
        val index = routines.indexOfFirst { it.id == id }
        if (index >= 0) {
            routines[index] = routines[index].copy(position = position)
            changed()
        }
    }

    /** Foreign keys: the routine's items go with it, and sessions keep their sets with no routine. */
    override suspend fun deleteRoutine(id: Long) {
        routines.removeAll { it.id == id }
        items.removeAll { it.routineId == id }
        for (i in sessions.indices) {
            if (sessions[i].routineId == id) sessions[i] = sessions[i].copy(routineId = null)
        }
        changed()
    }

    override suspend fun replaceRoutineItems(routineId: Long, items: List<RoutineItem>) {
        this.items.removeAll { it.routineId == routineId }
        this.items += items
        changed()
    }

    // Calendar and history

    private fun startedIn(session: Session, start: Instant, end: Instant): Boolean =
        !session.startedAt.isBefore(start) && session.startedAt.isBefore(end)

    private fun sessionsByStart(): List<Session> =
        sessions.sortedWith(compareBy<Session>({ it.startedAt }, { it.id }))

    override fun observeSessionsStartedBetween(start: Instant, end: Instant): Flow<List<Session>> = observe {
        sessionsByStart().filter { startedIn(it, start, end) }
    }

    override fun observeSetsOfSessionsStartedBetween(start: Instant, end: Instant): Flow<List<WorkoutSet>> = observe {
        val inRange = sessions.filter { startedIn(it, start, end) }.map { it.id }.toSet()
        sets.filter { it.sessionId in inRange }
    }

    override fun observeSessionStarts(): Flow<List<Instant>> = observe {
        sessions.map { it.startedAt }.sorted()
    }

    override fun observeSession(id: Long): Flow<Session?> = observe { sessions.firstOrNull { it.id == id } }

    override suspend fun session(id: Long): Session? = sessions.firstOrNull { it.id == id }

    override fun observeAllRoutines(): Flow<List<Routine>> = observe { routinesByPosition() }

    override suspend fun allRoutines(): List<Routine> = routinesByPosition()

    /** The machine's sets in the latest session that started before [sessionId]'s (an equal start counts as earlier by id). */
    override suspend fun lastTimeBefore(sessionId: Long, machineId: Long): List<WorkoutSet> {
        val current = sessions.firstOrNull { it.id == sessionId } ?: return emptyList()
        val earlier = sessions.filter { s ->
            s.id != sessionId &&
                (s.startedAt.isBefore(current.startedAt) || (s.startedAt == current.startedAt && s.id < current.id)) &&
                sets.any { it.sessionId == s.id && it.machineId == machineId }
        }
        val latest = earlier.maxWithOrNull(compareBy<Session>({ it.startedAt }, { it.id })) ?: return emptyList()
        return setsOf(latest.id, machineId).sortedWith(compareBy<WorkoutSet>({ it.position }, { it.id }))
    }

    /** Foreign key cascade: the session's sets go with it. */
    override suspend fun deleteSession(sessionId: Long) {
        sessions.removeAll { it.id == sessionId }
        sets.removeAll { it.sessionId == sessionId }
        changed()
    }

    /** Re-inserts with the given ids, as Room does with explicit ids. */
    override suspend fun restoreSession(session: Session, sets: List<WorkoutSet>) {
        sessions += session
        this.sets += sets
        nextSessionId = maxOf(nextSessionId, session.id + 1)
        nextSetId = maxOf(nextSetId, (sets.maxOfOrNull { it.id } ?: 0L) + 1)
        changed()
    }

    override suspend fun machines(): List<Machine> = machineRows.toList()

    // Backup

    /** Everything, each table by id (items by routine then position). */
    override suspend fun exportAll(): BackupData = BackupData(
        machines = machineRows.sortedBy { it.id },
        plans = plans.sortedBy { it.id },
        routines = routines.sortedBy { it.id },
        items = items.sortedWith(compareBy({ it.routineId }, { it.position })),
        sessions = sessions.sortedBy { it.id },
        sets = sets.sortedBy { it.id },
    )

    /** Replaces every table; ids are kept and the counters continue after the highest id seen. */
    override suspend fun replaceAll(data: BackupData) {
        sets.clear()
        sessions.clear()
        items.clear()
        routines.clear()
        plans.clear()
        machineRows.clear()
        machineRows += data.machines
        plans += data.plans
        routines += data.routines
        items += data.items
        sessions += data.sessions
        sets += data.sets
        nextMachineId = maxOf(nextMachineId, (data.machines.maxOfOrNull { it.id } ?: 0L) + 1)
        nextPlanId = maxOf(nextPlanId, (data.plans.maxOfOrNull { it.id } ?: 0L) + 1)
        nextRoutineId = maxOf(nextRoutineId, (data.routines.maxOfOrNull { it.id } ?: 0L) + 1)
        nextSessionId = maxOf(nextSessionId, (data.sessions.maxOfOrNull { it.id } ?: 0L) + 1)
        nextSetId = maxOf(nextSetId, (data.sets.maxOfOrNull { it.id } ?: 0L) + 1)
        changed()
    }

    override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
}
