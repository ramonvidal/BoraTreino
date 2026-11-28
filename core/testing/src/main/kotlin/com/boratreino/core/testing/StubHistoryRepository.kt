package com.boratreino.core.testing

import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.HealthExport
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionInfo
import com.boratreino.core.model.SessionSets
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.YearMonth

/**
 * A [HistoryRepository] for tests: an empty history (each observe emits one empty value), nothing to repeat or
 * delete, writes change nothing. Subclass it and override what a test needs.
 *
 * The writes that must return the saved set ([addSet], [addCardioSet], [addSessionWithSet],
 * [addSessionWithCardio]) have no neutral answer and throw [NotStubbedException].
 */
open class StubHistoryRepository : HistoryRepository {
    override fun observeMonth(month: YearMonth): Flow<Map<LocalDate, List<SessionInfo>>> = flowOf(emptyMap())
    override fun observeDay(date: LocalDate): Flow<List<SessionDetail>> = flowOf(emptyList())
    override fun observeSessionSets(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, List<SessionSets>>> =
        flowOf(emptyMap())
    override fun observeSession(sessionId: Long): Flow<SessionDetail?> = flowOf(null)
    override fun observeTrainingDates(): Flow<Set<LocalDate>> = flowOf(emptySet())
    override suspend fun addSessionWithSet(
        date: LocalDate,
        routineId: Long?,
        isExtra: Boolean,
        machineId: Long,
        draft: SetDraft,
    ): WorkoutSet = notStubbed("addSessionWithSet")
    override suspend fun addSessionWithCardio(
        date: LocalDate,
        routineId: Long?,
        isExtra: Boolean,
        machineId: Long,
        draft: CardioDraft,
    ): WorkoutSet = notStubbed("addSessionWithCardio")
    override suspend fun addSessionRepeating(
        date: LocalDate,
        routineId: Long?,
        isExtra: Boolean,
        mode: RepeatMode,
    ): List<WorkoutSet> = emptyList()
    override suspend fun setSessionRoutine(sessionId: Long, routineId: Long?) = Unit
    override suspend fun setSessionExtra(sessionId: Long, extra: Boolean) = Unit
    override suspend fun addSet(sessionId: Long, machineId: Long, draft: SetDraft): WorkoutSet = notStubbed("addSet")
    override suspend fun addCardioSet(sessionId: Long, machineId: Long, draft: CardioDraft): WorkoutSet =
        notStubbed("addCardioSet")
    override suspend fun lastTimeBefore(sessionId: Long, machineId: Long): List<WorkoutSet> = emptyList()
    override suspend fun lastTimeBeforeDate(date: LocalDate, machineId: Long): List<WorkoutSet> = emptyList()
    override fun observeClosedSessionExports(sinceDays: Int): Flow<List<HealthExport>> = flowOf(emptyList())
    override fun observeSessionIds(): Flow<Set<Long>> = flowOf(emptySet())
    override suspend fun deleteSession(sessionId: Long): SessionDetail? = null
    override suspend fun restoreSession(detail: SessionDetail) = Unit
    override suspend fun repeatLastSession(sessionId: Long, mode: RepeatMode): List<WorkoutSet> = emptyList()
}
