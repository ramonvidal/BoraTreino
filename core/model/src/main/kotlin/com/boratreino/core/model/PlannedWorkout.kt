package com.boratreino.core.model

import java.time.LocalDate

/** A routine the owner planned for one date; several may share a date (unique per date and routine).
 * It beats the schedule for that date only.
 * [position] is the order inside the date (0-based); ties are broken by the routine's own position. */
data class PlannedWorkout(val date: LocalDate, val routineId: Long, val position: Int = 0)
