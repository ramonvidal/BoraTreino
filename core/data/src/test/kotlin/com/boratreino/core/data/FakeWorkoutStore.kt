package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.Machine
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * In-memory [WorkoutStore] that follows the store's documented rules. Observed flows
 * re-read after every write, like Room does.
 */
internal class FakeWorkoutStore : WorkoutStore {
    private val machines = mutableListOf<Machine>()
    private val sessions = mutableListOf<Session>()
    private val sets = mutableListOf<WorkoutSet>()
    private var nextMachineId = 1L
    private var nextSessionId = 1L
    private var nextSetId = 1L
    private val changes = MutableStateFlow(0)

    /** Every session, open or closed, in insertion order. For assertions only. */
    fun allSessions(): List<Session> = sessions.toList()

    /** Every set, in insertion order. For assertions only. */
    fun allSets(): List<WorkoutSet> = sets.toList()

    private fun <T> observe(read: () -> T): Flow<T> = changes.map { read() }

    private fun changed() {
        changes.value = changes.value + 1
    }

    private fun openSessionOrNull(): Session? =
        sessions.filter { it.endedAt == null }.maxWithOrNull(compareBy<Session>({ it.startedAt }, { it.id }))

    private fun setsOf(sessionId: Long, machineId: Long? = null): List<WorkoutSet> =
        sets.filter { it.sessionId == sessionId && (machineId == null || it.machineId == machineId) }

    // Machines

    override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> = observe {
        machines
            .filter { includeArchived || !it.archived }
            .sortedWith(compareBy<Machine>({ it.name.lowercase() }, { it.id }))
    }

    override fun observeMachine(id: Long): Flow<Machine?> = observe {
        machines.firstOrNull { it.id == id }
    }

    override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = observe {
        machines
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
        machines.removeAll { it.id == id }
        machines += machine.copy(id = id)
        changed()
        return id
    }

    override suspend fun addMachineOrUnarchive(machine: Machine): Long {
        val existing = machines.firstOrNull { it.name.equals(machine.name, ignoreCase = true) }
        if (existing != null) {
            if (existing.archived) {
                machines.removeAll { it.id == existing.id }
                machines += existing.copy(archived = false)
                changed()
            }
            return existing.id
        }
        return saveMachine(machine.copy(id = 0))
    }

    override suspend fun setMachineArchived(id: Long, archived: Boolean) {
        val existing = machines.firstOrNull { it.id == id } ?: return
        machines.removeAll { it.id == id }
        machines += existing.copy(archived = archived)
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

    override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
}
