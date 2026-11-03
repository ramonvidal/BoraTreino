package com.boratreino.core.settings

import kotlinx.coroutines.flow.Flow

/** This phone's Health Connect choices. */
interface HealthSettings {
    /** Send finished trainings to Health Connect; off by default. */
    val enabled: Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)

    /** Ids of the sessions already sent, so deleted ones can be removed from Health Connect. */
    val syncedSessionIds: Flow<Set<Long>>

    suspend fun setSyncedSessionIds(ids: Set<Long>)
}
