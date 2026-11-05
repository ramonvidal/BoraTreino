package com.boratreino.core.data

import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StatsReport
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    /**
     * Statistics for [period], from working sets, sessions and machines (archived included).
     * Re-emits when any of them changes. Dates use the Clock's zone; [weeklyGoal] is the
     * training days per week goal (a parameter because :core:data can't read settings).
     */
    fun observeStats(period: StatsPeriod, weeklyGoal: Int): Flow<StatsReport>
}
