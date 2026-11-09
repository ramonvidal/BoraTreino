package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseLibraryRulesTest {

    private fun exercise(
        id: String,
        name: String,
        namePt: String,
        equipment: LibraryEquipment = LibraryEquipment.BARBELL,
        level: LibraryLevel = LibraryLevel.INTERMEDIATE,
        category: String = "strength",
        primary: List<String> = listOf("chest"),
    ) = LibraryExercise(
        id = id,
        name = name,
        namePt = namePt,
        equipment = equipment,
        level = level,
        category = category,
        mechanic = null,
        force = null,
        primaryMuscles = primary,
        secondaryMuscles = emptyList(),
        steps = listOf("Do it."),
    )

    // parse

    private val sample = """
        [
         {"id":"Barbell_Bench_Press","name":"Barbell Bench Press","pt":"Supino reto com barra","equip":"BARBELL",
          "level":"INTERMEDIATE","cat":"strength","mech":"compound","force":"push","pm":["chest"],"sm":["shoulders","triceps"],
          "steps":["Lie down.","Press up."]},
         {"id":"Plank","name":"Plank","pt":"","equip":"BODY_ONLY","level":"BEGINNER","cat":"strength","mech":null,
          "force":null,"pm":["abdominals"],"sm":[],"steps":["Hold."]},
         {"id":"Mystery","name":"Mystery Gadget","pt":"Aparelho","equip":"JETPACK","level":"EXPERT","cat":"strength",
          "pm":["chest"],"sm":[],"steps":["Try it."]},
         {"id":"","name":"Empty id","pt":"x","equip":"BARBELL","level":"BEGINNER","cat":"strength","pm":[],"sm":[],"steps":[]},
         {"name":"No id key","pt":"x","equip":"BARBELL","level":"BEGINNER","cat":"strength","pm":[],"sm":[],"steps":[]},
         {"id":"Bad_Level","name":"Bad level","pt":"x","equip":"BARBELL","level":"ADVANCED","cat":"strength","pm":[],"sm":[],"steps":[]},
         {"id":"No_Name","pt":"x","equip":"BARBELL","level":"BEGINNER","cat":"strength","pm":[],"sm":[],"steps":[]},
         "not an object",
         42
        ]
    """.trimIndent()

    @Test
    fun `parse keeps the good entries and skips the bad ones`() {
        assertEquals(
            listOf("Barbell_Bench_Press", "Plank", "Mystery"),
            ExerciseLibraryRules.parse(sample).map { it.id },
        )
    }

    @Test
    fun `parse reads every field of an entry`() {
        val bench = ExerciseLibraryRules.parse(sample).first()

        assertEquals("Barbell Bench Press", bench.name)
        assertEquals("Supino reto com barra", bench.namePt)
        assertEquals(LibraryEquipment.BARBELL, bench.equipment)
        assertEquals(LibraryLevel.INTERMEDIATE, bench.level)
        assertEquals("strength", bench.category)
        assertEquals("compound", bench.mechanic)
        assertEquals("push", bench.force)
        assertEquals(listOf("chest"), bench.primaryMuscles)
        assertEquals(listOf("shoulders", "triceps"), bench.secondaryMuscles)
        assertEquals(listOf("Lie down.", "Press up."), bench.steps)
    }

    @Test
    fun `an empty Portuguese name falls back to the English one`() {
        val plank = ExerciseLibraryRules.parse(sample).single { it.id == "Plank" }

        assertEquals("Plank", plank.namePt)
        assertEquals("Plank", plank.displayName(portuguese = true))
        assertEquals(null, plank.mechanic)
        assertEquals(null, plank.force)
    }

    @Test
    fun `an unknown equipment name reads as other`() {
        val mystery = ExerciseLibraryRules.parse(sample).single { it.id == "Mystery" }

        assertEquals(LibraryEquipment.OTHER, mystery.equipment)
        assertEquals(LibraryLevel.EXPERT, mystery.level)
    }

    @Test
    fun `a missing equipment reads as other and a missing category as strength`() {
        val json = """[{"id":"X","name":"X","level":"BEGINNER"}]"""

        val x = ExerciseLibraryRules.parse(json).single()

        assertEquals(LibraryEquipment.OTHER, x.equipment)
        assertEquals("strength", x.category)
        assertEquals(emptyList<String>(), x.primaryMuscles)
        assertEquals(emptyList<String>(), x.steps)
    }

    @Test
    fun `non text steps are dropped and the rest kept`() {
        val json = """[{"id":"X","name":"X","level":"BEGINNER","equip":"BODY_ONLY","steps":["First.",3,null,"Second."]}]"""

        assertEquals(listOf("First.", "Second."), ExerciseLibraryRules.parse(json).single().steps)
    }

    @Test
    fun `unreadable text gives an empty list and never throws`() {
        assertEquals(emptyList<LibraryExercise>(), ExerciseLibraryRules.parse(""))
        assertEquals(emptyList<LibraryExercise>(), ExerciseLibraryRules.parse("not json at all"))
        assertEquals(emptyList<LibraryExercise>(), ExerciseLibraryRules.parse("{\"id\":\"X\"}"))
        assertEquals(emptyList<LibraryExercise>(), ExerciseLibraryRules.parse("[{\"id\":"))
        assertEquals(emptyList<LibraryExercise>(), ExerciseLibraryRules.parse("[" + "[".repeat(5_000)))
    }

    // Filter

    private val bench = exercise("Barbell_Bench_Press", "Barbell Bench Press", "Supino reto com barra")
    private val dumbbellBench = exercise(
        "Dumbbell_Bench_Press", "Dumbbell Bench Press", "Supino reto com halteres",
        equipment = LibraryEquipment.DUMBBELL, level = LibraryLevel.BEGINNER,
    )
    private val pullUp = exercise(
        "Pull_Up", "Pull-Up", "Barra fixa",
        equipment = LibraryEquipment.BODY_ONLY, primary = listOf("lats"),
    )
    private val raise = exercise(
        "Lateral_Raise", "Side Lateral Raise", "Elevação lateral",
        equipment = LibraryEquipment.DUMBBELL, level = LibraryLevel.BEGINNER, primary = listOf("shoulders"),
    )
    private val running = exercise(
        "Running", "Running", "Corrida",
        equipment = LibraryEquipment.OTHER, level = LibraryLevel.BEGINNER, category = "cardio", primary = emptyList(),
    )
    private val library = listOf(running, raise, pullUp, dumbbellBench, bench)

    private fun ids(list: List<LibraryExercise>) = list.map { it.id }

    @Test
    fun `an empty filter lists everything in display name order`() {
        assertEquals(
            listOf("Barbell_Bench_Press", "Dumbbell_Bench_Press", "Pull_Up", "Running", "Lateral_Raise"),
            ids(ExerciseLibraryRules.filter(library, LibraryFilter(), portuguese = false)),
        )
    }

    @Test
    fun `the order follows the language shown`() {
        assertEquals(
            listOf("Pull_Up", "Running", "Lateral_Raise", "Barbell_Bench_Press", "Dumbbell_Bench_Press"),
            ids(ExerciseLibraryRules.filter(library, LibraryFilter(), portuguese = true)),
        )
    }

    @Test
    fun `a blank query is the same as no query`() {
        assertEquals(
            ids(ExerciseLibraryRules.filter(library, LibraryFilter(), portuguese = false)),
            ids(ExerciseLibraryRules.filter(library, LibraryFilter(query = "   "), portuguese = false)),
        )
    }

    @Test
    fun `the query searches both names whatever language is shown`() {
        assertEquals(listOf("Barbell_Bench_Press", "Dumbbell_Bench_Press"),
            ids(ExerciseLibraryRules.filter(library, LibraryFilter(query = "bench"), portuguese = false)))
        assertEquals(listOf("Barbell_Bench_Press", "Dumbbell_Bench_Press"),
            ids(ExerciseLibraryRules.filter(library, LibraryFilter(query = "bench"), portuguese = true)))
        assertEquals(listOf("Barbell_Bench_Press", "Dumbbell_Bench_Press"),
            ids(ExerciseLibraryRules.filter(library, LibraryFilter(query = "supino"), portuguese = false)))
        assertEquals(listOf("Barbell_Bench_Press", "Dumbbell_Bench_Press"),
            ids(ExerciseLibraryRules.filter(library, LibraryFilter(query = "SUPINO"), portuguese = true)))
    }

    @Test
    fun `the query ignores case and accents`() {
        assertEquals(listOf("Lateral_Raise"), ids(ExerciseLibraryRules.filter(library, LibraryFilter(query = "ELEVACAO"), portuguese = true)))
        assertEquals(listOf("Lateral_Raise"), ids(ExerciseLibraryRules.filter(library, LibraryFilter(query = "elevação"), portuguese = true)))
        assertEquals(listOf("Lateral_Raise"), ids(ExerciseLibraryRules.filter(library, LibraryFilter(query = "  lateral "), portuguese = false)))
    }

    @Test
    fun `a query matching nothing gives an empty list`() {
        assertTrue(ExerciseLibraryRules.filter(library, LibraryFilter(query = "zzz"), portuguese = true).isEmpty())
    }

    @Test
    fun `the muscle group filter keeps that group`() {
        assertEquals(listOf("Barbell_Bench_Press", "Dumbbell_Bench_Press"),
            ids(ExerciseLibraryRules.filter(library, LibraryFilter(group = MuscleGroup.CHEST), portuguese = false)))
        assertEquals(listOf("Pull_Up"), ids(ExerciseLibraryRules.filter(library, LibraryFilter(group = MuscleGroup.BACK), portuguese = false)))
        assertEquals(listOf("Running"), ids(ExerciseLibraryRules.filter(library, LibraryFilter(group = MuscleGroup.CARDIO), portuguese = false)))
    }

    @Test
    fun `the equipment filter keeps that equipment`() {
        assertEquals(
            listOf("Barbell_Bench_Press"),
            ids(ExerciseLibraryRules.filter(library, LibraryFilter(equipment = LibraryEquipment.BARBELL), portuguese = false)),
        )
        assertEquals(
            listOf("Pull_Up"),
            ids(ExerciseLibraryRules.filter(library, LibraryFilter(equipment = LibraryEquipment.BODY_ONLY), portuguese = false)),
        )
    }

    @Test
    fun `the level filter keeps that level`() {
        assertEquals(
            listOf("Dumbbell_Bench_Press", "Running", "Lateral_Raise"),
            ids(ExerciseLibraryRules.filter(library, LibraryFilter(level = LibraryLevel.BEGINNER), portuguese = false)),
        )
        assertEquals(
            listOf("Barbell_Bench_Press", "Pull_Up"),
            ids(ExerciseLibraryRules.filter(library, LibraryFilter(level = LibraryLevel.INTERMEDIATE), portuguese = false)),
        )
        assertTrue(ExerciseLibraryRules.filter(library, LibraryFilter(level = LibraryLevel.EXPERT), portuguese = false).isEmpty())
    }

    @Test
    fun `the filters combine with AND`() {
        assertEquals(
            listOf("Dumbbell_Bench_Press"),
            ids(ExerciseLibraryRules.filter(library, LibraryFilter(group = MuscleGroup.CHEST, level = LibraryLevel.BEGINNER), portuguese = false)),
        )
        assertTrue(
            ExerciseLibraryRules.filter(
                library,
                LibraryFilter(equipment = LibraryEquipment.BARBELL, level = LibraryLevel.BEGINNER),
                portuguese = false,
            ).isEmpty(),
        )
        assertTrue(
            ExerciseLibraryRules.filter(
                library,
                LibraryFilter(query = "supino", group = MuscleGroup.BACK),
                portuguese = false,
            ).isEmpty(),
        )
    }

    @Test
    fun `a filter is active when a group, equipment or level is chosen`() {
        assertFalse(LibraryFilter().isActive)
        assertTrue(LibraryFilter(group = MuscleGroup.LEGS).isActive)
        assertTrue(LibraryFilter(equipment = LibraryEquipment.CABLE).isActive)
        assertTrue(LibraryFilter(level = LibraryLevel.EXPERT).isActive)
    }

    // Mapping to machines

    @Test
    fun `the primary muscle sets the muscle group`() {
        val groups = mapOf(
            "quadriceps" to MuscleGroup.LEGS,
            "hamstrings" to MuscleGroup.LEGS,
            "calves" to MuscleGroup.LEGS,
            "glutes" to MuscleGroup.LEGS,
            "adductors" to MuscleGroup.LEGS,
            "abductors" to MuscleGroup.LEGS,
            "chest" to MuscleGroup.CHEST,
            "lats" to MuscleGroup.BACK,
            "middle back" to MuscleGroup.BACK,
            "lower back" to MuscleGroup.BACK,
            "traps" to MuscleGroup.BACK,
            "shoulders" to MuscleGroup.SHOULDERS,
            "neck" to MuscleGroup.SHOULDERS,
            "biceps" to MuscleGroup.ARMS,
            "triceps" to MuscleGroup.ARMS,
            "forearms" to MuscleGroup.ARMS,
            "abdominals" to MuscleGroup.CORE,
        )
        for ((muscle, group) in groups) {
            assertEquals(muscle, group, exercise("X", "X", "X", primary = listOf(muscle)).muscleGroup)
        }
    }

    @Test
    fun `the primary muscle is matched ignoring case and spaces around it`() {
        assertEquals(MuscleGroup.CHEST, exercise("X", "X", "X", primary = listOf(" Chest ")).muscleGroup)
        assertEquals(MuscleGroup.BACK, exercise("X", "X", "X", primary = listOf("Middle Back")).muscleGroup)
    }

    @Test
    fun `an unknown or missing primary muscle is core`() {
        assertEquals(MuscleGroup.CORE, exercise("X", "X", "X", primary = listOf("abs")).muscleGroup)
        assertEquals(MuscleGroup.CORE, exercise("X", "X", "X", primary = emptyList()).muscleGroup)
    }

    @Test
    fun `only the first primary muscle counts`() {
        assertEquals(MuscleGroup.CHEST, exercise("X", "X", "X", primary = listOf("chest", "biceps")).muscleGroup)
    }

    @Test
    fun `cardio is cardio whatever its muscles`() {
        assertEquals(MuscleGroup.CARDIO, exercise("X", "X", "X", category = "cardio", primary = listOf("quadriceps")).muscleGroup)
        assertEquals(MuscleGroup.CARDIO, exercise("X", "X", "X", category = "cardio", primary = emptyList()).muscleGroup)
    }

    @Test
    fun `weight machines and free weights are weight and reps`() {
        assertEquals(MachineKind.WEIGHT_REPS, exercise("X", "X", "X", LibraryEquipment.BARBELL).kind)
        assertEquals(MachineKind.WEIGHT_REPS, exercise("X", "X", "X", LibraryEquipment.MACHINE).kind)
        assertEquals(MachineKind.WEIGHT_REPS, exercise("X", "X", "X", LibraryEquipment.DUMBBELL).kind)
        assertEquals(MachineKind.WEIGHT_REPS, exercise("X", "X", "X", LibraryEquipment.OTHER).kind)
    }

    @Test
    fun `body only exercises are bodyweight and reps`() {
        assertEquals(MachineKind.BODYWEIGHT_REPS, exercise("X", "X", "X", LibraryEquipment.BODY_ONLY).kind)
        assertEquals(
            MachineKind.BODYWEIGHT_REPS,
            exercise("X", "X", "X", LibraryEquipment.OTHER, category = "plyometrics").kind,
        )
    }

    @Test
    fun `cardio is cardio whatever its equipment`() {
        assertEquals(MachineKind.CARDIO, exercise("X", "X", "X", LibraryEquipment.OTHER, category = "cardio").kind)
        assertEquals(MachineKind.CARDIO, exercise("X", "X", "X", LibraryEquipment.BODY_ONLY, category = "cardio").kind)
        assertEquals(MachineKind.CARDIO, exercise("X", "X", "X", LibraryEquipment.BARBELL, category = "cardio").kind)
    }

    @Test
    fun `cardio steps by one unit and strength by two and a half`() {
        assertEquals(1.0, exercise("X", "X", "X", category = "cardio").stepKg, 0.0)
        assertEquals(2.5, exercise("X", "X", "X", LibraryEquipment.BARBELL).stepKg, 0.0)
        assertEquals(2.5, exercise("X", "X", "X", LibraryEquipment.BODY_ONLY).stepKg, 0.0)
    }

    @Test
    fun `the display name is the Portuguese one only when asked for it`() {
        assertEquals("Supino reto com barra", bench.displayName(portuguese = true))
        assertEquals("Barbell Bench Press", bench.displayName(portuguese = false))
        assertEquals("Plain", exercise("X", "Plain", " ").displayName(portuguese = true))
    }

    // Starter links

    @Test
    fun `a starter with a link resolves to its library id and one without resolves to none`() {
        assertEquals("Leg_Press", ExerciseLibraryRules.idForStarter("Leg press"))
        assertEquals(null, ExerciseLibraryRules.idForStarter("Not a starter at all"))
    }
}
