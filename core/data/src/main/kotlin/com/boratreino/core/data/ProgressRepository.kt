package com.boratreino.core.data

import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.ProgressOverview
import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
    /**
     * Machines with history (archived included), last trained first, plus this month's counts.
     * Re-emits when sets, sessions or machines change. Month and dates use the Clock's zone.
     */
    fun observeOverview(): Flow<ProgressOverview>
    /** Null when the machine doesn't exist; the points may be empty. */
    fun observeMachineProgress(machineId: Long): Flow<MachineProgress?>
}
