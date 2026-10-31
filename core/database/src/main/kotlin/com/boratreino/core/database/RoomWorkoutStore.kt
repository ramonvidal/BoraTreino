package com.boratreino.core.database

import androidx.room.withTransaction
import com.boratreino.core.model.Machine
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

internal class RoomWorkoutStore(private val db: BoraTreinoDatabase) : WorkoutStore {
    private val machineDao = db.machineDao()
    private val sessionDao = db.sessionDao()
    private val setDao = db.workoutSetDao()

    override fun observeMachines(includeArchived: Boolean): Flow<List<Machine>> =
        machineDao.observeAll(includeArchived).map { list -> list.map { it.toModel() } }

    override fun observeMachine(id: Long): Flow<Machine?> =
        machineDao.observe(id).map { it?.toModel() }

    override fun observeRecentMachines(limit: Int): Flow<List<Machine>> =
        machineDao.observeRecent(limit).map { list -> list.map { it.toModel() } }

    override suspend fun saveMachine(machine: Machine): Long =
        if (machine.id == 0L) {
            machineDao.insert(machine.toEntity())
        } else {
            machineDao.update(machine.toEntity())
            machine.id
        }

    override suspend fun addMachineOrUnarchive(machine: Machine): Long = db.withTransaction {
        val existing = machineDao.byName(machine.name)
        if (existing != null) {
            if (existing.archived) machineDao.setArchived(existing.id, false)
            existing.id
        } else {
            machineDao.insert(machine.copy(id = 0).toEntity())
        }
    }

    override suspend fun setMachineArchived(id: Long, archived: Boolean) {
        machineDao.setArchived(id, archived)
    }

    override fun observeOpenSession(): Flow<Session?> =
        sessionDao.observeOpen().map { it?.toModel() }

    override suspend fun openSession(): Session? = sessionDao.open()?.toModel()

    override fun observeSessionSets(sessionId: Long): Flow<List<WorkoutSet>> =
        setDao.observeForSession(sessionId).map { list -> list.map { it.toModel() } }

    override fun observeMachineSets(sessionId: Long, machineId: Long): Flow<List<WorkoutSet>> =
        setDao.observeForSessionMachine(sessionId, machineId).map { list -> list.map { it.toModel() } }

    override fun observeLastTimeSets(machineId: Long, excludeSessionId: Long): Flow<List<WorkoutSet>> =
        setDao.observeLastTime(machineId, excludeSessionId).map { list -> list.map { it.toModel() } }

    override suspend fun lastSetAt(sessionId: Long): Instant? =
        setDao.lastLoggedAt(sessionId)?.let(Instant::ofEpochMilli)

    override suspend fun setCount(sessionId: Long, machineId: Long): Int =
        setDao.countForMachine(sessionId, machineId)

    override suspend fun summary(sessionId: Long): SessionSummary =
        SessionSummary(
            sessionId = sessionId,
            machineCount = setDao.machineCount(sessionId),
            setCount = setDao.count(sessionId),
        )

    override suspend fun insertSession(session: Session): Long =
        sessionDao.insert(session.copy(id = 0).toEntity())

    override suspend fun setSessionEnd(sessionId: Long, endedAt: Instant?) {
        sessionDao.setEndedAt(sessionId, endedAt?.toEpochMilli())
    }

    override suspend fun hasOtherOpenSession(sessionId: Long): Boolean =
        sessionDao.openCountExcept(sessionId) > 0

    override suspend fun insertSet(set: WorkoutSet): Long = setDao.insert(set.toEntity())

    override suspend fun updateSet(set: WorkoutSet) {
        setDao.update(set.toEntity())
    }

    override suspend fun deleteSet(setId: Long) {
        setDao.delete(setId)
    }

    override suspend fun <T> inTransaction(block: suspend () -> T): T = db.withTransaction { block() }
}
