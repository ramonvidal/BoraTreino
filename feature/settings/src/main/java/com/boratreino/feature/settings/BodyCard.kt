package com.boratreino.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.Sex
import com.boratreino.core.model.WeightEntry
import com.boratreino.core.ui.NumberPadDialog
import com.boratreino.core.ui.Stepper
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The values typed in the "Your body" card, one number pad each. */
internal enum class BodyField { BIRTH_YEAR, HEIGHT, WEIGHT, BASAL, LOG_WEIGHT }

/**
 * What has been typed so far. A [BodyProfile] exists (and is saved) only once every field
 * that has no default is filled in and valid.
 */
internal data class BodyDraft(
    val sex: Sex? = null,
    val birthYear: Int? = null,
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val goalPercent: Int = 0,
    val manualBmrKcal: Int? = null,
) {
    fun toProfile(): BodyProfile? {
        return BodyProfile(
            sex = sex ?: return null,
            birthYear = birthYear ?: return null,
            heightCm = heightCm ?: return null,
            weightKg = weightKg ?: return null,
            goalAdjustmentPercent = goalPercent,
            manualBmrKcal = manualBmrKcal,
        )
    }

    companion object {
        fun from(profile: BodyProfile?): BodyDraft = if (profile == null) {
            BodyDraft()
        } else {
            BodyDraft(
                sex = profile.sex,
                birthYear = profile.birthYear,
                heightCm = profile.heightCm,
                weightKg = profile.weightKg,
                goalPercent = profile.goalAdjustmentPercent,
                manualBmrKcal = profile.manualBmrKcal,
            )
        }
    }
}

/** What the "Your body" card shows. */
internal data class BodyCardState(
    val draft: BodyDraft = BodyDraft(),
    /** The latest weight entries, newest first (at most five). */
    val weights: List<WeightEntry> = emptyList(),
    val showEstimate: Boolean = true,
    /** Basal rate and rest-day need, rounded to 10 kcal; null until the profile is complete. */
    val basalKcal: Int? = null,
    val restDayKcal: Int? = null,
    val maxBirthYear: Int = BodyInput.MIN_BIRTH_YEAR,
    /** The number pad that is open, if any. */
    val dialog: BodyField? = null,
    val dialogError: Boolean = false,
)

/** The limits of each field, and turning typed text into a new draft. */
internal object BodyInput {
    const val MIN_BIRTH_YEAR = 1920
    const val MIN_HEIGHT = 120
    const val MAX_HEIGHT = 230
    const val MIN_WEIGHT = 30
    const val MAX_WEIGHT = 300
    const val MIN_BASAL = 500
    const val MAX_BASAL = 5000
    const val MIN_GOAL = -30
    const val MAX_GOAL = 30
    const val GOAL_STEP = 5

    /** The youngest birth year allowed: age 10. */
    fun maxBirthYear(today: LocalDate): Int = today.year - 10

    /** The new draft with [text] applied to [field], or null when the text is not valid. */
    fun apply(draft: BodyDraft, field: BodyField, text: String, maxBirthYear: Int): BodyDraft? {
        val clean = text.trim().replace(',', '.')
        return when (field) {
            BodyField.BIRTH_YEAR -> clean.toIntOrNull()
                ?.takeIf { it in MIN_BIRTH_YEAR..maxBirthYear }
                ?.let { draft.copy(birthYear = it) }
            BodyField.HEIGHT -> clean.toIntOrNull()
                ?.takeIf { it in MIN_HEIGHT..MAX_HEIGHT }
                ?.let { draft.copy(heightCm = it) }
            BodyField.WEIGHT, BodyField.LOG_WEIGHT -> clean.toDoubleOrNull()
                ?.takeIf { it >= MIN_WEIGHT && it <= MAX_WEIGHT }
                ?.let { draft.copy(weightKg = it) }
            BodyField.BASAL -> clean.toIntOrNull()?.let { kcal ->
                when {
                    kcal == 0 -> draft.copy(manualBmrKcal = null)
                    kcal in MIN_BASAL..MAX_BASAL -> draft.copy(manualBmrKcal = kcal)
                    else -> null
                }
            }
        }
    }

    /** The smallest and largest value of [field], for the error text. */
    fun range(field: BodyField, maxBirthYear: Int): Pair<Int, Int> = when (field) {
        BodyField.BIRTH_YEAR -> MIN_BIRTH_YEAR to maxBirthYear
        BodyField.HEIGHT -> MIN_HEIGHT to MAX_HEIGHT
        BodyField.WEIGHT, BodyField.LOG_WEIGHT -> MIN_WEIGHT to MAX_WEIGHT
        BodyField.BASAL -> MIN_BASAL to MAX_BASAL
    }
}

/** Turns a kcal figure into text with the phone's grouping, e.g. "1,780". */
private fun NumberFormat.kcal(value: Int): String = format(value)

private fun Double.plain(): String = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.US, this)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BodySection(body: BodyCardState, actions: SettingsActions) {
    val draft = body.draft
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()) }
    val notSet = stringResource(R.string.settings_body_not_set)
    SectionCard(stringResource(R.string.settings_body_title)) {
        Hint(stringResource(R.string.settings_body_hint))
        Text(
            stringResource(R.string.settings_body_sex),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsChip(stringResource(R.string.settings_body_sex_male), draft.sex == Sex.MALE) {
                actions.onBodySex(Sex.MALE)
            }
            SettingsChip(stringResource(R.string.settings_body_sex_female), draft.sex == Sex.FEMALE) {
                actions.onBodySex(Sex.FEMALE)
            }
        }
        ValueRow(
            stringResource(R.string.settings_body_birth_year),
            draft.birthYear?.toString() ?: notSet,
        ) { actions.onOpenBodyField(BodyField.BIRTH_YEAR) }
        ValueRow(
            stringResource(R.string.settings_body_height),
            draft.heightCm?.toString() ?: notSet,
        ) { actions.onOpenBodyField(BodyField.HEIGHT) }
        ValueRow(
            stringResource(R.string.settings_body_weight),
            draft.weightKg?.plain() ?: notSet,
        ) { actions.onOpenBodyField(BodyField.WEIGHT) }
        val goal = draft.goalPercent
        Stepper(
            label = when {
                goal > 0 -> stringResource(R.string.settings_body_goal_over, goal)
                goal < 0 -> stringResource(R.string.settings_body_goal_under, -goal)
                else -> stringResource(R.string.settings_body_goal_keep)
            },
            value = stringResource(R.string.settings_body_percent, if (goal > 0) "+$goal" else goal.toString()),
            onMinus = { actions.onBodyGoalStep(false) },
            onPlus = { actions.onBodyGoalStep(true) },
            onValueClick = {},
        )
        ValueRow(
            stringResource(R.string.settings_body_basal),
            draft.manualBmrKcal?.let { stringResource(R.string.settings_body_kcal, numbers.kcal(it)) }
                ?: stringResource(R.string.settings_body_use_formula),
        ) { actions.onOpenBodyField(BodyField.BASAL) }
        if (body.basalKcal != null && body.restDayKcal != null) {
            Text(
                stringResource(
                    R.string.settings_body_estimate,
                    numbers.kcal(body.basalKcal),
                    numbers.kcal(body.restDayKcal),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Hint(stringResource(R.string.settings_body_estimate_note))
        }
        ValueRow(
            stringResource(R.string.settings_body_log_weight),
            draft.weightKg?.let { stringResource(R.string.settings_body_kg, it.plain()) } ?: notSet,
        ) { actions.onOpenBodyField(BodyField.LOG_WEIGHT) }
        if (body.weights.isNotEmpty()) {
            Text(
                stringResource(R.string.settings_body_recent_weights),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            body.weights.forEach { entry ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.date.format(dateFormat),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.settings_body_kg, entry.kg.plain()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        SwitchRow(stringResource(R.string.settings_body_show_estimate), body.showEstimate, actions.onShowEnergyEstimate)
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** The number pad for the open field; an invalid value keeps it open and puts the allowed range in the title. */
@Composable
internal fun BodyDialog(body: BodyCardState, field: BodyField, actions: SettingsActions) {
    val draft = body.draft
    val initial = when (field) {
        BodyField.BIRTH_YEAR -> draft.birthYear?.toString().orEmpty()
        BodyField.HEIGHT -> draft.heightCm?.toString().orEmpty()
        BodyField.WEIGHT, BodyField.LOG_WEIGHT -> draft.weightKg?.plain().orEmpty()
        BodyField.BASAL -> draft.manualBmrKcal?.toString().orEmpty()
    }
    val (min, max) = BodyInput.range(field, body.maxBirthYear)
    val title = if (body.dialogError) {
        stringResource(R.string.settings_body_range_error, min.toString(), max.toString())
    } else {
        when (field) {
            BodyField.BIRTH_YEAR -> stringResource(R.string.settings_body_birth_year)
            BodyField.HEIGHT -> stringResource(R.string.settings_body_height)
            BodyField.WEIGHT -> stringResource(R.string.settings_body_weight)
            BodyField.BASAL -> stringResource(R.string.settings_body_basal_dialog)
            BodyField.LOG_WEIGHT -> stringResource(R.string.settings_body_log_weight_dialog)
        }
    }
    NumberPadDialog(
        title = title,
        initial = initial,
        allowDecimal = field == BodyField.WEIGHT || field == BodyField.LOG_WEIGHT,
        onConfirm = actions.onConfirmBodyValue,
        onDismiss = actions.onDismissBodyField,
    )
}
