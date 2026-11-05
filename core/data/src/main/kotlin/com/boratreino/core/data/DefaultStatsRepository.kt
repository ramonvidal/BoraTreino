package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StatsReport
import com.boratreino.core.model.StatsRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

internal class DefaultStatsRepository @Inject constructor(
    private val store: WorkoutStore,
    private val clock: Clock,
) : StatsRepository {

    override fun observeStats(period: StatsPeriod, weeklyGoal: Int): Flow<StatsReport> =
        combine(
            store.observeAllSets(),
            store.observeSessions(),
            store.observeMachines(includeArchived = true),
        ) { sets, sessions, machines ->
            StatsRules.report(
                period = period,
                today = LocalDate.now(clock),
                firstDayOfWeek = firstDayOfWeek(),
                weeklyGoal = weeklyGoal,
                machines = machines,
                sessions = sessions,
                sets = sets,
                zone = clock.zone,
            )
        }.flowOn(Dispatchers.Default)

    /** Same source the Calendar uses. */
    private fun firstDayOfWeek() = WeekFields.of(Locale.getDefault()).firstDayOfWeek
}
