package com.boratreino.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The whole training history, as stored in a backup file. */
data class BackupData(
    val machines: List<Machine>,
    val plans: List<RoutinePlan>,
    val routines: List<Routine>,
    val items: List<RoutineItem>,
    val sessions: List<Session>,
    val sets: List<WorkoutSet>,
)

/** Counts and date range of a backup; days come from each session's startedAt in the given zone. */
data class BackupSummary(
    val machines: Int,
    val routines: Int,
    val sessions: Int,
    val sets: Int,
    val firstDay: LocalDate?,
    val lastDay: LocalDate?,
)

fun BackupData.summary(zone: ZoneId): BackupSummary {
    val days = sessions.map { it.startedAt.atZone(zone).toLocalDate() }
    return BackupSummary(
        machines = machines.size,
        routines = routines.size,
        sessions = sessions.size,
        sets = sets.size,
        firstDay = days.minOrNull(),
        lastDay = days.maxOrNull(),
    )
}

sealed interface BackupDecodeResult {
    data class Ok(val data: BackupData, val exportedAt: Instant?) : BackupDecodeResult

    /** Not JSON, wrong "app", missing or odd fields, broken references. */
    data class Invalid(val reason: String) : BackupDecodeResult

    data class TooNew(val version: Int) : BackupDecodeResult
}

object BackupCodec {
    const val FORMAT_VERSION = 1
    private const val APP = "boratreino"

    fun encode(data: BackupData, exportedAt: Instant): String {
        val root = linkedMapOf<String, Any?>(
            "app" to APP,
            "version" to FORMAT_VERSION,
            "exportedAt" to exportedAt.toString(),
            "machines" to data.machines.map {
                linkedMapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "muscleGroup" to it.muscleGroup.name,
                    "kind" to it.kind.name,
                    "stepKg" to it.stepKg,
                    "setupNote" to it.setupNote,
                    "restSeconds" to it.restSeconds,
                    "archived" to it.archived,
                )
            },
            "plans" to data.plans.map {
                linkedMapOf("id" to it.id, "scheduleMode" to it.scheduleMode.name)
            },
            "routines" to data.routines.map {
                linkedMapOf(
                    "id" to it.id,
                    "planId" to it.planId,
                    "name" to it.name,
                    "shortName" to it.shortName,
                    "position" to it.position,
                    "weekdays" to it.weekdays.sortedBy { d -> d.value }.map { d -> d.name },
                )
            },
            "items" to data.items.map {
                linkedMapOf(
                    "routineId" to it.routineId,
                    "machineId" to it.machineId,
                    "position" to it.position,
                    "targetSets" to it.targetSets,
                    "repsMin" to it.repsMin,
                    "repsMax" to it.repsMax,
                )
            },
            "sessions" to data.sessions.map {
                linkedMapOf(
                    "id" to it.id,
                    "startedAt" to it.startedAt.toString(),
                    "endedAt" to it.endedAt?.toString(),
                    "routineId" to it.routineId,
                    "note" to it.note,
                )
            },
            "sets" to data.sets.map {
                linkedMapOf(
                    "id" to it.id,
                    "sessionId" to it.sessionId,
                    "machineId" to it.machineId,
                    "position" to it.position,
                    "weightKg" to it.weightKg,
                    "reps" to it.reps,
                    "seconds" to it.seconds,
                    "calories" to it.calories,
                    "distanceM" to it.distanceM,
                    "loggedAt" to it.loggedAt.toString(),
                )
            },
        )
        return Json.write(root)
    }

    /** Never throws. */
    fun decode(text: String): BackupDecodeResult =
        try {
            decodeOrThrow(text)
        } catch (e: BackupProblem) {
            BackupDecodeResult.Invalid(e.message ?: "Invalid backup")
        } catch (e: JsonException) {
            BackupDecodeResult.Invalid("Not valid JSON: ${e.message}")
        } catch (e: IllegalArgumentException) {
            BackupDecodeResult.Invalid(e.message ?: "Invalid value")
        } catch (e: Exception) {
            BackupDecodeResult.Invalid("Unreadable backup")
        } catch (e: StackOverflowError) {
            BackupDecodeResult.Invalid("Unreadable backup")
        }

    private class BackupProblem(message: String) : Exception(message)

    private fun problem(message: String): Nothing = throw BackupProblem(message)

    private fun decodeOrThrow(text: String): BackupDecodeResult {
        val root = Json.parse(text) as? Map<*, *> ?: problem("The file is not a JSON object")
        if (root["app"] != APP) problem("Not a $APP backup")
        val version = intOf(root, "version")
        if (version < 1) problem("Bad version $version")
        if (version > FORMAT_VERSION) return BackupDecodeResult.TooNew(version)

        val exportedAt = when (val raw = root["exportedAt"]) {
            null -> null
            is String -> instantOf(raw, "exportedAt")
            else -> problem("exportedAt must be text")
        }

        val machines = objects(root, "machines").map {
            Machine(
                id = longOf(it, "id"),
                name = stringOf(it, "name"),
                muscleGroup = enumOf<MuscleGroup>(it, "muscleGroup"),
                kind = enumOf<MachineKind>(it, "kind"),
                stepKg = doubleOf(it, "stepKg"),
                setupNote = optString(it, "setupNote"),
                restSeconds = optInt(it, "restSeconds"),
                archived = boolOf(it, "archived"),
            )
        }
        val plans = objects(root, "plans").map {
            RoutinePlan(longOf(it, "id"), enumOf<ScheduleMode>(it, "scheduleMode"))
        }
        val routines = objects(root, "routines").map {
            val days = (it["weekdays"] as? List<*> ?: problem("weekdays must be a list")).map { d ->
                val name = d as? String ?: problem("weekday must be text")
                try {
                    DayOfWeek.valueOf(name)
                } catch (e: IllegalArgumentException) {
                    problem("Unknown weekday $name")
                }
            }.toSet()
            Routine(
                id = longOf(it, "id"),
                planId = longOf(it, "planId"),
                name = stringOf(it, "name"),
                shortName = stringOf(it, "shortName"),
                position = intOf(it, "position"),
                weekdays = days,
            )
        }
        val items = objects(root, "items").map {
            RoutineItem(
                routineId = longOf(it, "routineId"),
                machineId = longOf(it, "machineId"),
                position = intOf(it, "position"),
                targetSets = intOf(it, "targetSets"),
                repsMin = intOf(it, "repsMin"),
                repsMax = intOf(it, "repsMax"),
            )
        }
        val sessions = objects(root, "sessions").map {
            Session(
                id = longOf(it, "id"),
                startedAt = instantOf(stringOf(it, "startedAt"), "startedAt"),
                endedAt = optString(it, "endedAt")?.let { s -> instantOf(s, "endedAt") },
                routineId = optLong(it, "routineId"),
                note = optString(it, "note"),
            )
        }
        val sets = objects(root, "sets").map {
            WorkoutSet(
                id = longOf(it, "id"),
                sessionId = longOf(it, "sessionId"),
                machineId = longOf(it, "machineId"),
                position = intOf(it, "position"),
                weightKg = optDouble(it, "weightKg"),
                reps = optInt(it, "reps"),
                seconds = optInt(it, "seconds"),
                calories = optInt(it, "calories"),
                distanceM = optInt(it, "distanceM"),
                loggedAt = instantOf(stringOf(it, "loggedAt"), "loggedAt"),
            )
        }

        val machineIds = machines.map { it.id }.toSet()
        val planIds = plans.map { it.id }.toSet()
        val routineIds = routines.map { it.id }.toSet()
        val sessionIds = sessions.map { it.id }.toSet()
        if (routines.any { it.planId !in planIds }) problem("A routine points to a missing plan")
        if (items.any { it.routineId !in routineIds }) problem("A routine item points to a missing routine")
        if (items.any { it.machineId !in machineIds }) problem("A routine item points to a missing machine")
        if (sessions.any { it.routineId != null && it.routineId !in routineIds }) {
            problem("A session points to a missing routine")
        }
        if (sets.any { it.sessionId !in sessionIds }) problem("A set points to a missing session")
        if (sets.any { it.machineId !in machineIds }) problem("A set points to a missing machine")

        return BackupDecodeResult.Ok(BackupData(machines, plans, routines, items, sessions, sets), exportedAt)
    }

    private fun objects(root: Map<*, *>, key: String): List<Map<*, *>> {
        val list = root[key] as? List<*> ?: problem("Missing list \"$key\"")
        return list.map { it as? Map<*, *> ?: problem("\"$key\" must hold objects") }
    }

    private fun text(map: Map<*, *>, key: String): String? = (map[key] as? Json.JsonNumber)?.text

    private fun longOf(map: Map<*, *>, key: String): Long =
        text(map, key)?.toLongOrNull() ?: problem("\"$key\" must be a whole number")

    private fun intOf(map: Map<*, *>, key: String): Int =
        text(map, key)?.toIntOrNull() ?: problem("\"$key\" must be a whole number")

    private fun doubleOf(map: Map<*, *>, key: String): Double =
        text(map, key)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: problem("\"$key\" must be a number")

    private fun optLong(map: Map<*, *>, key: String): Long? =
        if (map[key] == null) null else longOf(map, key)

    private fun optInt(map: Map<*, *>, key: String): Int? =
        if (map[key] == null) null else intOf(map, key)

    private fun optDouble(map: Map<*, *>, key: String): Double? =
        if (map[key] == null) null else doubleOf(map, key)

    private fun stringOf(map: Map<*, *>, key: String): String =
        map[key] as? String ?: problem("\"$key\" must be text")

    private fun optString(map: Map<*, *>, key: String): String? =
        if (map[key] == null) null else stringOf(map, key)

    private fun boolOf(map: Map<*, *>, key: String): Boolean =
        map[key] as? Boolean ?: problem("\"$key\" must be true or false")

    private inline fun <reified E : Enum<E>> enumOf(map: Map<*, *>, key: String): E {
        val name = stringOf(map, key)
        return enumValues<E>().firstOrNull { it.name == name } ?: problem("Unknown value \"$name\" for \"$key\"")
    }

    private fun instantOf(value: String, key: String): Instant =
        try {
            Instant.parse(value)
        } catch (e: Exception) {
            problem("\"$key\" is not a valid time")
        }
}
