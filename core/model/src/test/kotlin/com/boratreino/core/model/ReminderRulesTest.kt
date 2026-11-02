package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderRulesTest {
    private val saoPaulo: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val weekdays = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )

    private fun settings(
        enabled: Boolean = true,
        days: Set<DayOfWeek> = weekdays,
        time: LocalTime = LocalTime.of(18, 0),
    ) = ReminderSettings(enabled = enabled, weekdays = days, time = time)

    /** Monday 2 November 2026 at [time] in Sao Paulo. */
    private fun monday(time: String) = at("2026-11-02", time)

    private fun at(date: String, time: String, zone: ZoneId = saoPaulo): ZonedDateTime =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)

    // nextFireTime

    @Test
    fun `defaults are Monday to Friday at 18 00 and off`() {
        val defaults = ReminderSettings()

        assertEquals(false, defaults.enabled)
        assertEquals(weekdays, defaults.weekdays)
        assertEquals(LocalTime.of(18, 0), defaults.time)
    }

    @Test
    fun `later today gives today at the time`() {
        val next = ReminderRules.nextFireTime(settings(), monday("10:00"))

        assertEquals(at("2026-11-02", "18:00"), next)
    }

    @Test
    fun `a time already passed gives the next selected weekday`() {
        val next = ReminderRules.nextFireTime(settings(), monday("19:00"))

        assertEquals(at("2026-11-03", "18:00"), next)
    }

    @Test
    fun `exactly at the time gives the next occurrence, strictly after`() {
        val next = ReminderRules.nextFireTime(settings(), monday("18:00"))

        assertEquals(at("2026-11-03", "18:00"), next)
    }

    @Test
    fun `after the last weekday of the week wraps to the first one next week`() {
        val friday = at("2026-11-06", "19:00")

        assertEquals(at("2026-11-09", "18:00"), ReminderRules.nextFireTime(settings(), friday))
    }

    @Test
    fun `a weekend-only schedule from Sunday finds Saturday`() {
        val sunday = at("2026-11-01", "10:00")

        val next = ReminderRules.nextFireTime(settings(days = setOf(DayOfWeek.SATURDAY)), sunday)

        assertEquals(at("2026-11-07", "18:00"), next)
    }

    @Test
    fun `a single weekday already passed today is found a week later`() {
        val tuesday = at("2026-11-03", "19:00")

        val next = ReminderRules.nextFireTime(settings(days = setOf(DayOfWeek.TUESDAY)), tuesday)

        assertEquals(at("2026-11-10", "18:00"), next)
    }

    @Test
    fun `the result is in the zone of the moment it was asked from`() {
        val utc = at("2026-11-02", "10:00", ZoneId.of("UTC"))

        val next = requireNotNull(ReminderRules.nextFireTime(settings(), utc))

        assertEquals(ZoneId.of("UTC"), next.zone)
        assertEquals(LocalTime.of(18, 0), next.toLocalTime())
    }

    @Test
    fun `disabled gives no time`() {
        assertNull(ReminderRules.nextFireTime(settings(enabled = false), monday("10:00")))
    }

    @Test
    fun `no weekdays gives no time`() {
        assertNull(ReminderRules.nextFireTime(settings(days = emptySet()), monday("10:00")))
    }

    @Test
    fun `a day that does not exist in the zone is moved forward on the spring change`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val before = ZonedDateTime.of(LocalDate.of(2027, 3, 27), LocalTime.of(12, 0), berlin)

        val next = ReminderRules.nextFireTime(
            settings(days = setOf(DayOfWeek.SUNDAY), time = LocalTime.of(2, 30)),
            before,
        )

        val fire = requireNotNull(next)
        assertEquals(LocalDate.of(2027, 3, 28), fire.toLocalDate())
        assertTrue(fire.isAfter(before))
        assertEquals(LocalTime.of(3, 30), fire.toLocalTime())
    }

    // contentFor

    private fun content(
        trainedToday: Boolean = false,
        restDay: Boolean = false,
        routineName: String? = "Routine B",
        daysTrained: Int = 2,
        goal: Int = 4,
        today: LocalDate = LocalDate.of(2026, 11, 4), // Wednesday
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ) = ReminderRules.contentFor(
        trainedToday = trainedToday,
        restDay = restDay,
        routineName = routineName,
        daysTrainedThisWeek = daysTrained,
        goal = goal,
        today = today,
        firstDayOfWeek = firstDayOfWeek,
    )

    @Test
    fun `no reminder once today is trained`() {
        assertNull(content(trainedToday = true))
        assertNull(content(trainedToday = true, daysTrained = 0, goal = 7))
    }

    @Test
    fun `no reminder on a rest day`() {
        assertNull(content(restDay = true))
    }

    @Test
    fun `a normal day says the next routine`() {
        // Wednesday; 4 - 2 = 2 days needed, 5 days left in the week.
        assertEquals(ReminderContent.NextUp("Routine B"), content())
    }

    @Test
    fun `behind when the days left equal the days still needed`() {
        // Saturday: Saturday and Sunday are left, 2 days needed.
        val content = content(today = LocalDate.of(2026, 11, 7), daysTrained = 2, goal = 4)

        assertEquals(ReminderContent.BehindGoal(2, 4, "Routine B"), content)
    }

    @Test
    fun `not behind while there are more days left than days needed`() {
        // Saturday: 2 days left, only 1 needed.
        assertEquals(
            ReminderContent.NextUp("Routine B"),
            content(today = LocalDate.of(2026, 11, 7), daysTrained = 3, goal = 4),
        )
    }

    @Test
    fun `a goal already met is never behind`() {
        assertEquals(
            ReminderContent.NextUp("Routine B"),
            content(today = LocalDate.of(2026, 11, 7), daysTrained = 3, goal = 3),
        )
        assertEquals(
            ReminderContent.NextUp("Routine B"),
            content(today = LocalDate.of(2026, 11, 7), daysTrained = 5, goal = 3),
        )
    }

    @Test
    fun `a missing routine name is passed through as null`() {
        assertEquals(ReminderContent.NextUp(null), content(routineName = null))
        assertEquals(
            ReminderContent.BehindGoal(2, 4, null),
            content(today = LocalDate.of(2026, 11, 7), routineName = null),
        )
    }

    @Test
    fun `the week ends on the day before the first day of the next week`() {
        // Sunday-first week of 1-7 Nov: Saturday 7 Nov is its last day, so only one day is left.
        val sundayFirst = content(
            today = LocalDate.of(2026, 11, 7),
            daysTrained = 3,
            goal = 4,
            firstDayOfWeek = DayOfWeek.SUNDAY,
        )
        // The same day on a Monday-first week (2-8 Nov) still has Sunday after it.
        val mondayFirst = content(today = LocalDate.of(2026, 11, 7), daysTrained = 3, goal = 4)

        assertEquals(ReminderContent.BehindGoal(3, 4, "Routine B"), sundayFirst)
        assertEquals(ReminderContent.NextUp("Routine B"), mondayFirst)
    }
}
