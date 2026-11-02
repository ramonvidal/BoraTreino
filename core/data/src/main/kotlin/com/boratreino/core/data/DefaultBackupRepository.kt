package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.BackupCodec
import com.boratreino.core.model.BackupData
import com.boratreino.core.model.BackupDecodeResult
import com.boratreino.core.model.summary
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

internal class DefaultBackupRepository @Inject constructor(
    private val store: WorkoutStore,
    private val clock: Clock,
    private val training: DefaultTrainingRepository,
) : BackupRepository {
    override suspend fun export(): String = BackupCodec.encode(store.exportAll(), clock.instant())

    override fun suggestedFileName(): String = "boratreino-backup-${LocalDate.now(clock)}.json"

    override fun preview(text: String): BackupPreview =
        when (val result = BackupCodec.decode(text)) {
            is BackupDecodeResult.Ok -> BackupPreview.Ready(result.data, result.data.summary(clock.zone))
            is BackupDecodeResult.Invalid -> BackupPreview.Invalid(result.reason)
            is BackupDecodeResult.TooNew -> BackupPreview.TooNew(result.version)
        }

    override suspend fun import(data: BackupData) {
        store.replaceAll(data)
        // The open session, if any, is gone with everything else.
        training.resetChoice()
    }
}
