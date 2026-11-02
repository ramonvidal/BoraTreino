package com.boratreino.core.data

import com.boratreino.core.model.BackupData
import com.boratreino.core.model.BackupSummary

interface BackupRepository {
    /** The whole history as backup file text. */
    suspend fun export(): String
    /** For example "boratreino-backup-2026-11-02.json", dated in the clock's zone. */
    fun suggestedFileName(): String
    /** Reads backup file text without changing anything. */
    fun preview(text: String): BackupPreview
    /** Replaces everything on this phone with [data], an open session included. */
    suspend fun import(data: BackupData)
}

sealed interface BackupPreview {
    data class Ready(val data: BackupData, val summary: BackupSummary) : BackupPreview
    data class Invalid(val reason: String) : BackupPreview
    data class TooNew(val version: Int) : BackupPreview
}
