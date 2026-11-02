package com.boratreino.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** When the training reminder fires. Off by default. */
data class ReminderSettings(
    val enabled: Boolean = false,
    val weekdays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    ),
    val time: LocalTime = LocalTime.of(18, 0),
)

/** What a training reminder says. */
sealed interface ReminderContent {
    data class NextUp(val routineName: String?) : ReminderContent

    data class BehindGoal(val daysTrained: Int, val goal: Int, val routineName: String?) : ReminderContent
}

object ReminderRules {
    /**
     * The first moment strictly after [after] that is on one of the weekdays at settings.time, in after's zone;
     * null when disabled or no weekdays.
     */
    fun nextFireTime(settings: ReminderSettings, after: ZonedDateTime): ZonedDateTime? {
        if (!settings.enabled || settings.weekdays.isEmpty()) return null
        val firstDate = after.toLocalDate()
        for (i in 0L..8L) {
            val date = firstDate.plusDays(i)
            if (date.dayOfWeek !in settings.weekdays) continue
            val candidate = date.atTime(settings.time).atZone(after.zone)
            if (candidate.isAfter(after)) return candidate
        }
        return null
    }

    /**
     * null = do not notify (trainedToday, or restDay). Otherwise: daysLeft = days from today through the last day of
     * the week inclusive; needed = goal - daysTrainedThisWeek; BehindGoal when needed > 0 && needed >= daysLeft,
     * else NextUp. routineName is passed through (null when no routine).
     */
    fun contentFor(
        trainedToday: Boolean,
        restDay: Boolean,
        routineName: String?,
        daysTrainedThisWeek: Int,
        goal: Int,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): ReminderContent? {
        if (trainedToday || restDay) return null
        val weekEnd = WeekGoalRules.weekStart(today, firstDayOfWeek).plusDays(6)
        val daysLeft = ChronoUnit.DAYS.between(today, weekEnd) + 1
        val needed = goal - daysTrainedThisWeek
        return if (needed > 0 && needed >= daysLeft) {
            ReminderContent.BehindGoal(daysTrainedThisWeek, goal, routineName)
        } else {
            ReminderContent.NextUp(routineName)
        }
    }
}
