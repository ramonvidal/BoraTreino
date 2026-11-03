package com.boratreino.core.health

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.boratreino.core.model.HealthExport

/** The only door to Health Connect. Write-only: nothing is ever read. */
interface HealthConnectAccess {
    suspend fun availability(): HealthAvailability

    /** Exercise, active calories burned and distance, all write permissions. */
    val permissions: Set<String>

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>>

    /** Opens Health Connect's store page. */
    fun installIntent(): Intent

    /** Upserts one exercise session per export, plus calories and distance records when above zero. */
    suspend fun write(exports: List<HealthExport>)

    /** Removes the records of these sessions (all three kinds). */
    suspend fun delete(sessionIds: Set<Long>)
}
