package com.boratreino.core.database

import androidx.room.withTransaction
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
import kotlinx.coroutines.flow.map
import java.time.Instant

internal class RoomWorkoutStore(private val db: BoraTreinoDatabase) : WorkoutStore {
    private val machineDao = db.machineDao()
    private val sessionDao = db.sessionDao()
    private val setDao = db.workoutSetDao()
    private val routineDao = db.routineDao()
    private val backupDao = db.backupDao()

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
        val existing = machineDao.byName(machine.name.trim())
        if (existing != null) {
            if (existing.archived) machineDao.setArchived(existing.id, false)
            existing.id
        } else {
            machineDao.insert(machine.copy(id = 0, name = machine.name.trim()).toEntity())
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

    override suspend fun setSessionRoutine(sessionId: Long, routineId: Long?) {
        sessionDao.setRoutineId(sessionId, routineId)
    }

    override fun observeLastRoutineId(): Flow<Long?> = sessionDao.observeLastRoutineId()

    override suspend fun lastRoutineId(): Long? = sessionDao.lastRoutineId()

    override fun observeLastSessionSets(
        machineId: Long,
        excludeSessionId: Long,
        limit: Int,
    ): Flow<List<List<WorkoutSet>>> =
        setDao.observeLastSessions(machineId, excludeSessionId, limit).map { list ->
            // Rows come ordered by session (most recent first); groupBy keeps that order.
            list.map { it.toModel() }.groupBy { it.sessionId }.values.toList()
        }

    override suspend fun machineSets(machineId: Long): List<WorkoutSet> =
        setDao.forMachine(machineId).map { it.toModel() }

    override suspend fun machinesSetsBefore(machineIds: List<Long>, before: Instant): List<WorkoutSet> =
        if (machineIds.isEmpty()) emptyList()
        else setDao.forMachinesBefore(machineIds, before.toEpochMilli()).map { it.toModel() }

    override suspend fun sessionSets(sessionId: Long): List<WorkoutSet> =
        setDao.forSession(sessionId).map { it.toModel() }

    override fun observePlan(): Flow<RoutinePlan?> = routineDao.observePlan().map { it?.toModel() }

    override suspend fun plan(): RoutinePlan? = routineDao.plan()?.toModel()

    override suspend fun ensurePlan(): RoutinePlan = db.withTransaction {
        routineDao.plan()?.toModel() ?: run {
            val entity = RoutinePlanEntity(scheduleMode = ScheduleMode.IN_TURN.name)
            RoutinePlan(id = routineDao.insertPlan(entity), scheduleMode = ScheduleMode.IN_TURN)
        }
    }

    override suspend fun setScheduleMode(planId: Long, mode: ScheduleMode) {
        routineDao.setScheduleMode(planId, mode.name)
    }

    override suspend fun setTrainingDays(planId: Long, days: Set<DayOfWeek>) {
        routineDao.setTrainingDays(planId, days.sorted().joinToString(",") { it.name })
    }

    override fun observeRoutines(planId: Long): Flow<List<Routine>> =
        routineDao.observeRoutines(planId).map { list -> list.map { it.toModel() } }

    override suspend fun routines(planId: Long): List<Routine> =
        routineDao.routines(planId).map { it.toModel() }

    override fun observeRoutine(id: Long): Flow<Routine?> =
        routineDao.observeRoutine(id).map { it?.toModel() }

    override fun observeAllRoutineItems(): Flow<List<RoutineItem>> =
        routineDao.observeAllItems().map { list -> list.map { it.toModel() } }

    override fun observeRoutineItems(routineId: Long): Flow<List<RoutineItem>> =
        routineDao.observeItems(routineId).map { list -> list.map { it.toModel() } }

    override suspend fun insertRoutine(routine: Routine): Long =
        routineDao.insertRoutine(routine.copy(id = 0).toEntity())

    override suspend fun updateRoutine(routine: Routine) {
        routineDao.updateRoutine(routine.toEntity())
    }

    override suspend fun setRoutinePosition(id: Long, position: Int) {
        routineDao.setPosition(id, position)
    }

    override suspend fun deleteRoutine(id: Long) {
        routineDao.deleteRoutine(id)
    }

    override suspend fun replaceRoutineItems(routineId: Long, items: List<RoutineItem>) {
        db.withTransaction {
            routineDao.deleteItems(routineId)
            if (items.isNotEmpty()) routineDao.insertItems(items.map { it.toEntity() })
        }
    }

    override fun observeSessionsStartedBetween(start: Instant, end: Instant): Flow<List<Session>> =
        sessionDao.observeBetween(start.toEpochMilli(), end.toEpochMilli()).map { list -> list.map { it.toModel() } }

    override fun observeSetsOfSessionsStartedBetween(start: Instant, end: Instant): Flow<List<WorkoutSet>> =
        setDao.observeStartedBetween(start.toEpochMilli(), end.toEpochMilli()).map { list -> list.map { it.toModel() } }

    override fun observeSessionStarts(): Flow<List<Instant>> =
        sessionDao.observeStarts().map { list -> list.map(Instant::ofEpochMilli) }

    override fun observeSession(id: Long): Flow<Session?> = sessionDao.observeById(id).map { it?.toModel() }

    override suspend fun session(id: Long): Session? = sessionDao.byId(id)?.toModel()

    override fun observeAllRoutines(): Flow<List<Routine>> =
        routineDao.observeAllRoutines().map { list -> list.map { it.toModel() } }

    override suspend fun allRoutines(): List<Routine> = routineDao.allRoutines().map { it.toModel() }

    override suspend fun lastTimeBefore(sessionId: Long, machineId: Long): List<WorkoutSet> =
        setDao.lastTimeBefore(sessionId, machineId).map { it.toModel() }

    override suspend fun deleteSession(sessionId: Long) {
        sessionDao.delete(sessionId)
    }

    override suspend fun restoreSession(session: Session, sets: List<WorkoutSet>) {
        db.withTransaction {
            sessionDao.insert(session.toEntity())
            if (sets.isNotEmpty()) setDao.insertAll(sets.map { it.toEntity() })
        }
    }

    override suspend fun machines(): List<Machine> = machineDao.allNow().map { it.toModel() }

    override suspend fun machineSetCount(machineId: Long): Int = setDao.countAllForMachine(machineId)

    override suspend fun routineNamesWithMachine(machineId: Long): List<String> =
        routineDao.routineNamesWithMachine(machineId)

    override suspend fun deleteMachine(machineId: Long): Boolean = db.withTransaction {
        if (machineDao.count(machineId) == 0 || setDao.countAllForMachine(machineId) > 0) {
            false
        } else {
            routineDao.deleteItemsOfMachine(machineId)
            machineDao.delete(machineId)
            true
        }
    }

    override suspend fun routineItems(routineId: Long): List<RoutineItem> =
        routineDao.items(routineId).map { it.toModel() }

    override fun observeAllSets(): Flow<List<WorkoutSet>> =
        setDao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeSessions(): Flow<List<Session>> =
        sessionDao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun exportAll(): BackupData = db.withTransaction {
        BackupData(
            machines = backupDao.machines().map { it.toModel() },
            plans = backupDao.plans().map { it.toModel() },
            routines = backupDao.routines().map { it.toModel() },
            items = backupDao.items().map { it.toModel() },
            sessions = backupDao.sessions().map { it.toModel() },
            sets = backupDao.sets().map { it.toModel() },
        )
    }

    override suspend fun replaceAll(data: BackupData) {
        db.withTransaction {
            backupDao.deleteSets()
            backupDao.deleteSessions()
            backupDao.deleteItems()
            backupDao.deleteRoutines()
            backupDao.deletePlans()
            backupDao.deleteMachines()
            // AUTOINCREMENT keeps its counter across deletes, so new ids continue after the
            // highest id ever used, and inserting explicit ids raises it further when needed.
            backupDao.insertMachines(data.machines.map { it.toEntity() })
            backupDao.insertPlans(data.plans.map { it.toEntity() })
            backupDao.insertRoutines(data.routines.map { it.toEntity() })
            backupDao.insertItems(data.items.map { it.toEntity() })
            backupDao.insertSessions(data.sessions.map { it.toEntity() })
            backupDao.insertSets(data.sets.map { it.toEntity() })
        }
    }

    override suspend fun <T> inTransaction(block: suspend () -> T): T = db.withTransaction { block() }
}
