package com.boratreino.core.model

/** The set of routines and how they are scheduled. */
data class RoutinePlan(
    val id: Long,
    val scheduleMode: ScheduleMode,
)
