package com.boratreino.core.testing

import com.boratreino.core.data.BackupPreview
import com.boratreino.core.data.BackupRepository
import com.boratreino.core.data.ExerciseLibraryRepository
import com.boratreino.core.data.MeasurementRepository
import com.boratreino.core.data.ProgressRepository
import com.boratreino.core.data.StatsRepository
import com.boratreino.core.model.BackupData
import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.MeasurementType
import com.boratreino.core.model.ProgressOverview
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StatsReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/** A [MeasurementRepository] for tests: no measurements; [add] returns id 0 and stores nothing. */
open class StubMeasurementRepository : MeasurementRepository {
    override fun observeAll(): Flow<List<BodyMeasurement>> = flowOf(emptyList())
    override suspend fun add(date: LocalDate, type: MeasurementType, valueCm: Double): Long = 0L
    override suspend fun delete(id: Long): BodyMeasurement? = null
    override suspend fun restore(measurement: BodyMeasurement) = Unit
}

/** A [StatsRepository] for tests: [report] for every period and goal; nothing is emitted when it is null. */
open class StubStatsRepository(private val report: StatsReport? = null) : StatsRepository {
    override fun observeStats(period: StatsPeriod, weeklyGoal: Int): Flow<StatsReport> =
        report?.let { flowOf(it) } ?: emptyFlow()
}

/** A [ProgressRepository] for tests: no machines with history, nothing this month, no machine progress. */
open class StubProgressRepository : ProgressRepository {
    override fun observeOverview(): Flow<ProgressOverview> = flowOf(ProgressOverview(emptyList(), 0, 0))
    override fun observeMachineProgress(machineId: Long): Flow<MachineProgress?> = flowOf(null)
}

/** A [BackupRepository] for tests: exports "", every file previews as invalid, [import] changes nothing. */
open class StubBackupRepository : BackupRepository {
    override suspend fun export(): String = ""
    override fun suggestedFileName(): String = "boratreino-backup.json"
    override fun preview(text: String): BackupPreview = BackupPreview.Invalid("")
    override suspend fun import(data: BackupData) = Unit
}

/** An [ExerciseLibraryRepository] for tests over [exercises] (empty by default). */
open class StubExerciseLibraryRepository(
    private val exercises: List<LibraryExercise> = emptyList(),
) : ExerciseLibraryRepository {
    override suspend fun all(): List<LibraryExercise> = exercises
    override suspend fun byId(id: String): LibraryExercise? = exercises.firstOrNull { it.id == id }
}
