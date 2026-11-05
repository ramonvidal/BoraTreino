package com.boratreino.feature.settings

import com.boratreino.core.model.Sex
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Parsing and limits of the "Your body" number pads. */
class BodyInputTest {
    private val draft = BodyDraft(Sex.MALE, 1990, 180, 80.0)
    private val maxYear = 2016

    private fun apply(field: BodyField, text: String) = BodyInput.apply(draft, field, text, maxYear)

    @Test
    fun `the youngest birth year is ten years back`() {
        assertEquals(2016, BodyInput.maxBirthYear(LocalDate.of(2026, 11, 5)))
        assertEquals(2020, BodyInput.maxBirthYear(LocalDate.of(2030, 1, 1)))
    }

    @Test
    fun `birth year from 1920 to the maximum`() {
        assertEquals(1920, apply(BodyField.BIRTH_YEAR, "1920")?.birthYear)
        assertEquals(2016, apply(BodyField.BIRTH_YEAR, "2016")?.birthYear)
        assertNull(apply(BodyField.BIRTH_YEAR, "1919"))
        assertNull(apply(BodyField.BIRTH_YEAR, "2017"))
    }

    @Test
    fun `height from 120 to 230`() {
        assertEquals(120, apply(BodyField.HEIGHT, "120")?.heightCm)
        assertEquals(230, apply(BodyField.HEIGHT, "230")?.heightCm)
        assertNull(apply(BodyField.HEIGHT, "119"))
        assertNull(apply(BodyField.HEIGHT, "231"))
    }

    @Test
    fun `weight from 30 to 300`() {
        assertEquals(30.0, apply(BodyField.WEIGHT, "30")?.weightKg!!, 1e-9)
        assertEquals(300.0, apply(BodyField.WEIGHT, "300")?.weightKg!!, 1e-9)
        assertNull(apply(BodyField.WEIGHT, "29.9"))
        assertNull(apply(BodyField.WEIGHT, "300.1"))
    }

    @Test
    fun `the logged weight follows the same limits`() {
        assertEquals(79.5, apply(BodyField.LOG_WEIGHT, "79.5")?.weightKg!!, 1e-9)
        assertNull(apply(BodyField.LOG_WEIGHT, "29"))
        assertNull(apply(BodyField.LOG_WEIGHT, "301"))
    }

    @Test
    fun `basal rate from 500 to 5000 and zero clears it`() {
        assertEquals(500, apply(BodyField.BASAL, "500")?.manualBmrKcal)
        assertEquals(5000, apply(BodyField.BASAL, "5000")?.manualBmrKcal)
        assertNull(apply(BodyField.BASAL, "499"))
        assertNull(apply(BodyField.BASAL, "5001"))
        assertNull(apply(BodyField.BASAL, "1"))
        val cleared = BodyInput.apply(draft.copy(manualBmrKcal = 1500), BodyField.BASAL, "0", maxYear)
        assertEquals(draft, cleared)
    }

    @Test
    fun `a comma is a decimal point and spaces are ignored`() {
        assertEquals(80.5, apply(BodyField.WEIGHT, " 80,5 ")?.weightKg!!, 1e-9)
    }

    @Test
    fun `text that is not a number is refused`() {
        assertNull(apply(BodyField.WEIGHT, ""))
        assertNull(apply(BodyField.WEIGHT, "abc"))
        assertNull(apply(BodyField.HEIGHT, ""))
        assertNull(apply(BodyField.BIRTH_YEAR, "19x0"))
        assertNull(apply(BodyField.BASAL, ""))
    }

    @Test
    fun `a whole number field refuses a decimal`() {
        assertNull(apply(BodyField.HEIGHT, "180.5"))
        assertNull(apply(BodyField.BIRTH_YEAR, "1990.5"))
    }

    @Test
    fun `applying a field leaves the others alone`() {
        assertEquals(draft.copy(heightCm = 170), apply(BodyField.HEIGHT, "170"))
    }

    @Test
    fun `the goal limits`() {
        assertEquals(-30, BodyInput.MIN_GOAL)
        assertEquals(30, BodyInput.MAX_GOAL)
        assertEquals(5, BodyInput.GOAL_STEP)
    }

    @Test
    fun `a draft becomes a profile only when complete`() {
        assertNull(BodyDraft().toProfile())
        assertNull(draft.copy(sex = null).toProfile())
        assertNull(draft.copy(birthYear = null).toProfile())
        assertNull(draft.copy(heightCm = null).toProfile())
        assertNull(draft.copy(weightKg = null).toProfile())
        assertEquals(Sex.MALE, draft.toProfile()?.sex)
    }

    @Test
    fun `a draft round trips through a profile`() {
        val full = draft.copy(goalPercent = -10, manualBmrKcal = 1600)
        assertEquals(full, BodyDraft.from(full.toProfile()))
        assertEquals(BodyDraft(), BodyDraft.from(null))
    }
}
