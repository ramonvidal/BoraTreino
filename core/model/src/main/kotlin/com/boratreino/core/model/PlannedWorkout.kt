package com.boratreino.core.model

import java.time.LocalDate

/** A routine the owner planned for one date; several may share a date (unique per date and routine).
 * It beats the schedule for that date only. */
data class PlannedWorkout(val date: LocalDate, val routineId: Long)
