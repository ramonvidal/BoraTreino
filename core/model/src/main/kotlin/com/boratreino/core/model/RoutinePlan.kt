package com.boratreino.core.model

import java.time.DayOfWeek

/** The set of routines and how they are scheduled. */
data class RoutinePlan(
    val id: Long,
    val scheduleMode: ScheduleMode,
    /** Weekdays the owner trains (used by IN_TURN); empty = every day. */
    val trainingDays: Set<DayOfWeek> = emptySet(),
)
