package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.LibraryExercise
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
    private val library: ExerciseLibraryRepository,
) : StatsRepository {

    /** The exercise library never changes while the app runs; loaded on first use. */
    @Volatile
    private var libraryById: Map<String, LibraryExercise>? = null

    private suspend fun libraryMap(): Map<String, LibraryExercise> =
        libraryById ?: library.all().associateBy { it.id }.also { libraryById = it }

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
                library = libraryMap(),
            )
        }.flowOn(Dispatchers.Default)

    /** Same source the Calendar uses. */
    private fun firstDayOfWeek() = WeekFields.of(Locale.getDefault()).firstDayOfWeek
}
