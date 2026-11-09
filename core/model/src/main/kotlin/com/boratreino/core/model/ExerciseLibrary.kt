package com.boratreino.core.model

import java.text.Normalizer
import java.util.Locale

enum class LibraryLevel { BEGINNER, INTERMEDIATE, EXPERT }

enum class LibraryEquipment {
    BARBELL, DUMBBELL, BODY_ONLY, CABLE, MACHINE, KETTLEBELLS, BANDS, EZ_BAR, MEDICINE_BALL, EXERCISE_BALL,
    FOAM_ROLL, OTHER,
}

/** One entry of the exercise library (public-domain dataset, English instructions, Portuguese names). */
data class LibraryExercise(
    val id: String,
    val name: String,
    val namePt: String,
    val equipment: LibraryEquipment,
    val level: LibraryLevel,
    val category: String,
    val mechanic: String?,
    val force: String?,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val steps: List<String>,
) {
    val muscleGroup: MuscleGroup
        get() = if (category == CARDIO_CATEGORY) {
            MuscleGroup.CARDIO
        } else {
            when (primaryMuscles.firstOrNull()?.trim()?.lowercase(Locale.ROOT)) {
                "quadriceps", "hamstrings", "calves", "glutes", "adductors", "abductors" -> MuscleGroup.LEGS
                "chest" -> MuscleGroup.CHEST
                "lats", "middle back", "lower back", "traps" -> MuscleGroup.BACK
                "shoulders", "neck" -> MuscleGroup.SHOULDERS
                "biceps", "triceps", "forearms" -> MuscleGroup.ARMS
                "abdominals" -> MuscleGroup.CORE
                else -> MuscleGroup.CORE
            }
        }

    val kind: MachineKind
        get() = when {
            category == CARDIO_CATEGORY -> MachineKind.CARDIO
            equipment == LibraryEquipment.BODY_ONLY ||
                (equipment == LibraryEquipment.OTHER && category == "plyometrics") -> MachineKind.BODYWEIGHT_REPS
            else -> MachineKind.WEIGHT_REPS
        }

    val stepKg: Double
        get() = if (kind == MachineKind.CARDIO) 1.0 else 2.5

    fun displayName(portuguese: Boolean): String =
        if (portuguese && namePt.isNotBlank()) namePt else name

    private companion object {
        const val CARDIO_CATEGORY = "cardio"
    }
}

data class LibraryFilter(
    val query: String = "",
    val group: MuscleGroup? = null,
    val equipment: LibraryEquipment? = null,
    val level: LibraryLevel? = null,
) {
    val isActive: Boolean get() = group != null || equipment != null || level != null
}

object ExerciseLibraryRules {
    /** Reads the asset JSON. Entries that are malformed are skipped; never throws. */
    fun parse(json: String): List<LibraryExercise> {
        val root = try {
            Json.parse(json) as? List<*>
        } catch (e: Exception) {
            null
        } catch (e: StackOverflowError) {
            null
        } ?: return emptyList()
        return root.mapNotNull { entry ->
            try {
                parseEntry(entry as? Map<*, *> ?: return@mapNotNull null)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parseEntry(m: Map<*, *>): LibraryExercise? {
        val id = (m["id"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val name = (m["name"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val level = enumOrNull<LibraryLevel>(m["level"]) ?: return null
        return LibraryExercise(
            id = id,
            name = name,
            namePt = (m["pt"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: name,
            equipment = enumOrNull<LibraryEquipment>(m["equip"]) ?: LibraryEquipment.OTHER,
            level = level,
            category = (m["cat"] as? String)?.lowercase(Locale.ROOT) ?: "strength",
            mechanic = m["mech"] as? String,
            force = m["force"] as? String,
            primaryMuscles = strings(m["pm"]),
            secondaryMuscles = strings(m["sm"]),
            steps = strings(m["steps"]),
        )
    }

    private fun strings(raw: Any?): List<String> = (raw as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    private inline fun <reified E : Enum<E>> enumOrNull(raw: Any?): E? =
        (raw as? String)?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

    /** Case/accent-insensitive search over both names, then group, equipment and level (AND); sorted by display name. */
    fun filter(all: List<LibraryExercise>, filter: LibraryFilter, portuguese: Boolean): List<LibraryExercise> {
        val q = normalize(filter.query)
        return all
            .filter { e ->
                (q.isEmpty() || normalize(e.name).contains(q) || normalize(e.namePt).contains(q)) &&
                    (filter.group == null || e.muscleGroup == filter.group) &&
                    (filter.equipment == null || e.equipment == filter.equipment) &&
                    (filter.level == null || e.level == filter.level)
            }
            .sortedBy { normalize(it.displayName(portuguese)) }
    }

    /** The library id the hand-made alias table knows for a starter's English name; null when none. */
    fun idForStarter(starterName: String): String? = StarterLibraryLinks.ids[starterName]

    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
}
