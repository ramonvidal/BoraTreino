package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.ProgressOverview
import com.boratreino.core.model.ProgressRules
import com.boratreino.core.model.Session
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

internal class DefaultProgressRepository @Inject constructor(
    private val store: WorkoutStore,
    private val clock: Clock,
) : ProgressRepository {

    override fun observeOverview(): Flow<ProgressOverview> =
        combine(
            store.observeAllSets(),
            store.observeSessions(),
            store.observeMachines(includeArchived = true),
        ) { sets, sessions, machines ->
            val dates = sessionDates(sessions)
            val month = YearMonth.now(clock)
            val progress = machines
                .mapNotNull { machine -> progressFor(machine, sets, dates) }
                .filter { it.points.isNotEmpty() }
                .sortedWith(
                    compareByDescending<MachineProgress> { it.points.last().date }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.machine.name },
                )
            ProgressOverview(
                machines = progress,
                trainingsThisMonth = ProgressRules.trainingDaysInMonth(dates.values, month),
                recordsThisMonth = ProgressRules.recordsInMonth(sets, dates, month),
            )
        }.flowOn(Dispatchers.Default)

    override fun observeMachineProgress(machineId: Long): Flow<MachineProgress?> =
        combine(
            store.observeMachine(machineId),
            store.observeAllSets(),
            store.observeSessions(),
        ) { machine, sets, sessions ->
            machine?.let { progressFor(it, sets, sessionDates(sessions)) }
        }.flowOn(Dispatchers.Default)

    private fun sessionDates(sessions: List<Session>): Map<Long, LocalDate> =
        sessions.associate { it.id to it.startedAt.atZone(clock.zone).toLocalDate() }

    private fun progressFor(machine: Machine, allSets: List<WorkoutSet>, dates: Map<Long, LocalDate>): MachineProgress {
        val own = allSets.filter { it.machineId == machine.id }
        val metric = ProgressRules.metricFor(machine, own)
        return MachineProgress(machine, metric, ProgressRules.points(own, dates, metric))
    }
}
