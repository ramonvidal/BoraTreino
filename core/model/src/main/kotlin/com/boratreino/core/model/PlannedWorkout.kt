package com.boratreino.core.model

import java.time.LocalDate

/** A routine the owner planned for one date; at most one per date. It beats the schedule for that date only. */
data class PlannedWorkout(val date: LocalDate, val routineId: Long)
