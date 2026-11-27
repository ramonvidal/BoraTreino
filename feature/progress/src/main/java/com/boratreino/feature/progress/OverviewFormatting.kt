package com.boratreino.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.boratreino.core.model.Finding
import com.boratreino.core.model.Record
import com.boratreino.core.model.StallDirection
import com.boratreino.core.model.StallHint
import com.boratreino.core.ui.formatKg
import com.boratreino.core.ui.label
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Sunday": the weekday name in the app locale. */
internal fun formatWeekday(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("EEEE", locale).format(date)

/** "Sunday, 22 Nov". */
internal fun formatWeekdayDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("EEEE, d MMM", locale).format(date)

/** "2/11": the day and month of a week start, the label under a frequency bar. */
internal fun formatDayMonth(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("d/M", locale).format(date)

/** The bold line of a finding card. */
@Composable
internal fun findingTitle(finding: Finding): String = when (finding) {
    is Finding.FrequencyDrop ->
        stringResource(R.string.finding_frequency_title, formatDecimal(finding.recentPerWeek, 1))
    is Finding.LowVolume ->
        stringResource(R.string.finding_low_volume_title, finding.group.label(), formatDecimal(finding.setsPerWeek, 1))
    is Finding.Stalled -> {
        val name = finding.machine.name
        when (finding.direction) {
            StallDirection.FLAT -> pluralStringResource(
                R.plurals.finding_flat_title,
                finding.sessions,
                name,
                formatKg(finding.lastTop.weightKg ?: 0.0),
                finding.sessions,
            )
            StallDirection.FALLING ->
                pluralStringResource(R.plurals.finding_falling_title, finding.sessions, name, finding.sessions)
        }
    }
    is Finding.NewRecord -> {
        val event = finding.event
        // A property from another module can't be smart-cast: read it into a local first.
        val record = event?.record
        when {
            event == null -> pluralStringResource(R.plurals.finding_records_title, finding.others, finding.others)
            record is Record.HeaviestWeight ->
                stringResource(R.string.finding_record_title, event.machine.name, formatKg(record.weightKg))
            else -> stringResource(R.string.finding_record_title_other, event.machine.name)
        }
    }
}

/** The muted line under the title; null when the finding has none. */
@Composable
internal fun findingHint(finding: Finding): String? = when (finding) {
    is Finding.FrequencyDrop -> stringResource(
        R.string.finding_frequency_hint,
        formatDecimal(finding.usualPerWeek, 1),
        finding.goal,
    )
    is Finding.LowVolume -> {
        val routine = finding.routine
        if (routine == null) {
            stringResource(R.string.finding_low_volume_hint)
        } else {
            stringResource(
                R.string.finding_low_volume_hint_routine,
                finding.group.label().lowercase(Locale.getDefault()),
                routine.name,
            )
        }
    }
    is Finding.Stalled -> when (finding.direction) {
        StallDirection.FALLING -> stringResource(
            R.string.finding_falling_hint,
            formatEstimate(finding.bestEstimate),
            formatEstimate(finding.lastEstimate),
        )
        StallDirection.FLAT -> when (val hint = finding.hint) {
            is StallHint.MoreReps -> stringResource(R.string.finding_hint_more_reps, hint.reps)
            is StallHint.MoreWeight -> stringResource(R.string.finding_hint_more_weight, formatKg(hint.kg))
            null -> null
        }
    }
    is Finding.NewRecord -> finding.event?.let { event ->
        val date = formatWeekdayDate(event.date)
        if (finding.others > 0) {
            pluralStringResource(R.plurals.finding_record_more, finding.others, date, finding.others)
        } else {
            stringResource(R.string.finding_record_date, date)
        }
    }
}
