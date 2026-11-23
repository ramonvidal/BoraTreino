package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The number pad's cardio fields: which take a decimal, their starting text, and what typed text becomes. */
class CardioFieldTest {
    private val full = CardioDraft(minutes = 20, calories = 250, distanceM = 5000, intensity = 0.79)

    private val empty = CardioDraft(minutes = 0, calories = 0, distanceM = 0)

    private fun typed(field: CardioField, text: String, draft: CardioDraft = full): CardioDraft? =
        CardioRules.withField(draft, field, text)

    // The fields

    @Test
    fun `the cardio fields are minutes, distance, calories and intensity`() {
        assertEquals(
            listOf(CardioField.MINUTES, CardioField.DISTANCE_KM, CardioField.CALORIES, CardioField.INTENSITY),
            CardioField.entries,
        )
    }

    @Test
    fun `distance and intensity take a decimal, minutes and calories do not`() {
        assertTrue(CardioRules.allowsDecimal(CardioField.DISTANCE_KM))
        assertTrue(CardioRules.allowsDecimal(CardioField.INTENSITY))
        assertFalse(CardioRules.allowsDecimal(CardioField.MINUTES))
        assertFalse(CardioRules.allowsDecimal(CardioField.CALORIES))
    }

    // fieldText

    @Test
    fun `fieldText is empty for a value that is not set or is zero`() {
        val zeros = CardioDraft(minutes = 0, calories = 0, distanceM = 0, intensity = 0.0)

        CardioField.entries.forEach { field ->
            assertEquals("$field of an empty draft", "", CardioRules.fieldText(empty, field))
            assertEquals("$field of a zero draft", "", CardioRules.fieldText(zeros, field))
        }
    }

    @Test
    fun `fieldText shows whole minutes and calories`() {
        assertEquals("20", CardioRules.fieldText(full, CardioField.MINUTES))
        assertEquals("250", CardioRules.fieldText(full, CardioField.CALORIES))
    }

    @Test
    fun `fieldText shows distance in kilometres without trailing zeros`() {
        assertEquals("17.3", CardioRules.fieldText(empty.copy(distanceM = 17300), CardioField.DISTANCE_KM))
        assertEquals("17", CardioRules.fieldText(empty.copy(distanceM = 17000), CardioField.DISTANCE_KM))
        assertEquals("0.05", CardioRules.fieldText(empty.copy(distanceM = 50), CardioField.DISTANCE_KM))
    }

    @Test
    fun `fieldText shows intensity as a plain decimal without trailing zeros`() {
        assertEquals("0.79", CardioRules.fieldText(full, CardioField.INTENSITY))
        assertEquals("12", CardioRules.fieldText(empty.copy(intensity = 12.0), CardioField.INTENSITY))
        assertEquals("0.7", CardioRules.fieldText(empty.copy(intensity = 0.7), CardioField.INTENSITY))
        assertEquals("", CardioRules.fieldText(empty.copy(intensity = null), CardioField.INTENSITY))
    }

    // withField: decimals and separators

    @Test
    fun `intensity takes a comma or a point as the decimal separator`() {
        assertEquals(0.79, typed(CardioField.INTENSITY, "0,79")!!.intensity!!, 0.0)
        assertEquals(0.79, typed(CardioField.INTENSITY, "0.79")!!.intensity!!, 0.0)
    }

    @Test
    fun `intensity keeps whole numbers and zero`() {
        assertEquals(12.0, typed(CardioField.INTENSITY, "12")!!.intensity!!, 0.0)
        assertEquals(0.0, typed(CardioField.INTENSITY, "0")!!.intensity!!, 0.0)
    }

    @Test
    fun `intensity rounds half up to 2 decimals`() {
        assertEquals(0.79, typed(CardioField.INTENSITY, "0.785")!!.intensity!!, 1e-9)
        assertEquals(0.78, typed(CardioField.INTENSITY, "0.784")!!.intensity!!, 1e-9)
    }

    @Test
    fun `intensity is clamped to 0 to 100`() {
        assertEquals(100.0, typed(CardioField.INTENSITY, "150")!!.intensity!!, 0.0)
        assertEquals(100.0, typed(CardioField.INTENSITY, "100.5")!!.intensity!!, 0.0)
    }

    @Test
    fun `distance takes a comma or a point and is stored in metres`() {
        assertEquals(17300, typed(CardioField.DISTANCE_KM, "17,3", empty)!!.distanceM)
        assertEquals(17300, typed(CardioField.DISTANCE_KM, "17.3", empty)!!.distanceM)
    }

    @Test
    fun `whole minutes and calories are typed as they are`() {
        assertEquals(45, typed(CardioField.MINUTES, "45", empty)!!.minutes)
        assertEquals(520, typed(CardioField.CALORIES, "520", empty)!!.calories)
    }

    @Test
    fun `surrounding spaces are ignored`() {
        assertEquals(0.79, typed(CardioField.INTENSITY, "  0.79  ", empty)!!.intensity!!, 0.0)
        assertEquals(12, typed(CardioField.MINUTES, "  12  ", empty)!!.minutes)
    }

    @Test
    fun `typed text changes only its own field`() {
        val result = typed(CardioField.INTENSITY, "0.5")!!

        assertEquals(full.minutes, result.minutes)
        assertEquals(full.calories, result.calories)
        assertEquals(full.distanceM, result.distanceM)
    }

    // withField: blank clears

    @Test
    fun `blank text clears minutes, calories and distance to zero`() {
        assertEquals(0, typed(CardioField.MINUTES, "")!!.minutes)
        assertEquals(0, typed(CardioField.CALORIES, "")!!.calories)
        assertEquals(0, typed(CardioField.DISTANCE_KM, "")!!.distanceM)
        assertEquals(0, typed(CardioField.DISTANCE_KM, "   ")!!.distanceM)
    }

    @Test
    fun `blank text clears intensity to not logged`() {
        assertNull(typed(CardioField.INTENSITY, "")!!.intensity)
        assertNull(typed(CardioField.INTENSITY, "   ")!!.intensity)
    }

    // withField: garbage

    @Test
    fun `text that is not a plain number is ignored`() {
        for (text in listOf("abc", "-1", "1e3", "1.2.3", ".", "1 2", "0.7x")) {
            assertNull("intensity $text", typed(CardioField.INTENSITY, text))
            assertNull("minutes $text", typed(CardioField.MINUTES, text))
            assertNull("distance $text", typed(CardioField.DISTANCE_KM, text))
            assertNull("calories $text", typed(CardioField.CALORIES, text))
        }
    }

    @Test
    fun `garbage text never throws`() {
        listOf("", "abc", "9".repeat(60), "0,,5", "+3").forEach { text ->
            CardioField.entries.forEach { field -> typed(field, text) }
        }
    }

    // Draft to set and back

    @Test
    fun `typed intensity is what the set saves`() {
        val draft = typed(CardioField.INTENSITY, "0.79", empty)!!

        val saved = CardioRules.toSet(1L, 2L, 3L, 0, draft, java.time.Instant.EPOCH)

        assertEquals(0.79, saved.intensity!!, 0.0)
        assertEquals(0.79, CardioRules.toDraft(saved).intensity!!, 0.0)
    }
}
