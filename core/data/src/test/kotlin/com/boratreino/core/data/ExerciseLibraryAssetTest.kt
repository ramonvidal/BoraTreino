package com.boratreino.core.data

import com.boratreino.core.model.ExerciseLibraryRules
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.StarterLibraryLinks
import com.boratreino.core.model.StarterMachines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the exercise library asset the app ships with. Gradle runs this from the module directory. */
class ExerciseLibraryAssetTest {
    private val file = File("src/main/assets/exercise_library.json")
    private val text: String by lazy { file.readText(Charsets.UTF_8) }
    private val entries: List<LibraryExercise> by lazy { ExerciseLibraryRules.parse(text) }
    private val ids: Set<String> by lazy { entries.map { it.id }.toSet() }

    @Test
    fun `the asset is one JSON array of 753 entries`() {
        assertTrue(text.trim().startsWith("[") && text.trim().endsWith("]"))
        assertEquals(753, entries.size)
    }

    @Test
    fun `every raw entry is read, none is skipped as malformed`() {
        assertEquals(Regex("\"id\":\"").findAll(text).count(), entries.size)
    }

    @Test
    fun `the asset stays under 1300 kilobytes`() {
        assertTrue("asset is ${file.length()} bytes", file.length() < 1_300_000L)
    }

    @Test
    fun `every id is unique`() {
        assertEquals(entries.size, ids.size)
    }

    @Test
    fun `every entry has a name and a Portuguese name`() {
        assertTrue(entries.all { it.name.isNotBlank() })
        assertEquals(entries.size, Regex("\"pt\":\"").findAll(text).count())
        assertEquals(0, Regex("\"pt\":\"\\s*\"").findAll(text).count())
        assertTrue(entries.all { it.namePt.isNotBlank() })
    }

    @Test
    fun `no step is blank`() {
        val blank = entries.filter { e -> e.steps.any { it.isBlank() } }.map { it.id }

        assertTrue("entries with a blank step: $blank", blank.isEmpty())
    }

    @Test
    fun `every entry has at least one step`() {
        val withoutSteps = entries.filter { it.steps.isEmpty() }.map { it.id }

        assertTrue("entries without steps: $withoutSteps", withoutSteps.isEmpty())
    }

    @Test
    fun `no stretching entries are in the library`() {
        assertTrue(entries.none { it.category == "stretching" })
    }

    @Test
    fun `every category is one the library uses`() {
        val categories = setOf("strength", "powerlifting", "olympic weightlifting", "strongman", "plyometrics", "cardio")

        assertTrue(entries.all { it.category in categories })
    }

    @Test
    fun `cardio entries are cardio machines`() {
        val cardio = entries.filter { it.category == "cardio" }

        assertTrue(cardio.isNotEmpty())
        assertTrue(cardio.all { it.kind == MachineKind.CARDIO })
    }

    @Test
    fun `every starter link points to an entry in the library`() {
        val missing = StarterLibraryLinks.ids.filterValues { it !in ids }

        assertTrue("links to unknown ids: $missing", missing.isEmpty())
    }

    @Test
    fun `every starter link is keyed by a starter machine name`() {
        val starterNames = StarterMachines.all.map { it.name }.toSet()

        assertTrue(StarterLibraryLinks.ids.keys.all { it in starterNames })
    }
}
