package com.boratreino.core.ui

import com.boratreino.core.model.StarterMachines
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the exercise picture files against the starter list: names, sizes, no leftovers, and the licence text. */
class ExerciseAssetsTest {
    /** The module's `src/main/assets` folder, whether the tests run from the module or the repository root. */
    private val assets: File by lazy {
        val fromModule = File("src/main/assets")
        if (File(fromModule, "exercises").isDirectory) fromModule else File("core/ui/src/main/assets")
    }

    private val exercises: File get() = File(assets, "exercises")

    private val usedSlugs: Set<String> by lazy {
        StarterMachines.all.mapNotNull { it.image }.toSet()
    }

    /**
     * Slugs of the library pictures. The generated object has no way to list them, so they are read from its
     * source file (the `RAW` block: one `<id> <slug>` per line), from the module folder or the repository root.
     */
    private val librarySlugs: Set<String> by lazy {
        val source = listOf(
            File("../model/src/main/kotlin/com/boratreino/core/model/LibraryExerciseImages.kt"),
            File("core/model/src/main/kotlin/com/boratreino/core/model/LibraryExerciseImages.kt"),
        ).first { it.isFile }
        source.readText().substringAfter("\"\"\"").substringBefore("\"\"\"")
            .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .map { it.substringAfter(' ') }.toSet()
    }

    private val minBytes = 1024L
    private val maxBytes = 60L * 1024

    @Test
    fun `every starter picture has two frames of a sensible size`() {
        assertTrue("exercises folder missing: ${exercises.absolutePath}", exercises.isDirectory)
        usedSlugs.forEach { slug ->
            listOf("$slug-1.webp", "$slug-2.webp").forEach { name ->
                val file = File(exercises, name)
                assertTrue("missing $name", file.isFile)
                assertTrue("$name is not larger than 1 KB (${file.length()} bytes)", file.length() > minBytes)
                assertTrue("$name is not smaller than 60 KB (${file.length()} bytes)", file.length() < maxBytes)
            }
        }
    }

    @Test
    fun `every picture file belongs to a starter`() {
        val files = exercises.listFiles().orEmpty().filter { it.isFile }
        assertTrue("no picture files found", files.isNotEmpty())
        files.forEach { file ->
            val frame = file.name.endsWith("-1.webp") || file.name.endsWith("-2.webp")
            assertTrue("${file.name} is not a frame file", frame)
            val slug = file.name.removeSuffix("-1.webp").removeSuffix("-2.webp")
            assertTrue("${file.name} belongs to no starter or library exercise", slug in usedSlugs || slug in librarySlugs)
        }
    }

    @Test
    fun `the picture folder is under 5 MB`() {
        val total = exercises.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
        assertTrue("picture folder is $total bytes", total < 5L * 1024 * 1024)
    }

    @Test
    fun `the Everkinetic licence file names the licence and the author`() {
        val licence = File(assets, "licenses/everkinetic.txt")
        assertTrue("missing ${licence.path}", licence.isFile)
        val text = licence.readText()
        assertTrue("licence file does not mention CC BY-SA 4.0", text.contains("CC BY-SA 4.0"))
        assertTrue("licence file does not mention Priday", text.contains("Priday"))
    }
}
