package com.boratreino.feature.settings

import com.boratreino.core.model.Sex
import com.boratreino.core.model.WeightEntry
import java.time.LocalDate

/** Fixed states of the "Your body" card for the screenshots. */
internal object SettingsBodySamples {
    private val filledDraft = BodyDraft(Sex.MALE, birthYear = 1990, heightCm = 180, weightKg = 80.0)

    val weights = listOf(
        WeightEntry(LocalDate.of(2026, 11, 5), 79.5),
        WeightEntry(LocalDate.of(2026, 11, 2), 80.0),
        WeightEntry(LocalDate.of(2026, 10, 29), 80.5),
        WeightEntry(LocalDate.of(2026, 10, 26), 81.0),
        WeightEntry(LocalDate.of(2026, 10, 22), 81.5),
    )

    /** Nothing typed yet. */
    val empty = BodyCardState(maxBirthYear = 2016)

    /** Part way: sex and height only, so no estimate yet. */
    val partial = BodyCardState(
        draft = BodyDraft(sex = Sex.FEMALE, heightCm = 165),
        maxBirthYear = 2016,
    )

    /** Complete, goal "keep weight". */
    val filled = BodyCardState(draft = filledDraft, basalKcal = 1750, restDayKcal = 2100, maxBirthYear = 2016)

    val goalOver = filled.copy(draft = filledDraft.copy(goalPercent = 10), restDayKcal = 2310)

    val goalUnder = filled.copy(draft = filledDraft.copy(goalPercent = -20), restDayKcal = 1680)

    val manualBasal = filled.copy(draft = filledDraft.copy(manualBmrKcal = 1500), basalKcal = 1500, restDayKcal = 1800)

    val withWeights = filled.copy(weights = weights)

    val switchedOff = withWeights.copy(showEstimate = false)

    // Number pad states

    val dialogBirthYear = empty.copy(dialog = BodyField.BIRTH_YEAR)

    val dialogHeightError = filled.copy(dialog = BodyField.HEIGHT, dialogError = true)

    val dialogWeight = filled.copy(dialog = BodyField.WEIGHT)

    val dialogBasal = filled.copy(dialog = BodyField.BASAL)

    val dialogLogWeight = withWeights.copy(dialog = BodyField.LOG_WEIGHT)
}
