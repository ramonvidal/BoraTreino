package com.boratreino.core.model

import java.text.Normalizer
import java.util.Locale

/** A ready-made machine or exercise the user can add from the list. */
data class StarterMachine(
    val name: String,
    val muscleGroup: MuscleGroup,
    val kind: MachineKind,
    val stepKg: Double,
    val namePt: String = name,
    /** The illustration's file slug (assets are `<slug>-1.webp` and `<slug>-2.webp`), or null when there is none. */
    val image: String? = null,
) {
    /** The Portuguese name when [portuguese], else the English one. */
    fun displayName(portuguese: Boolean): String = if (portuguese) namePt else name
}

/** The built-in list of common gym machines and exercises (strength and cardio). */
object StarterMachines {
    private fun w(name: String, pt: String, group: MuscleGroup, step: Double = 2.5) =
        StarterMachine(name, group, MachineKind.WEIGHT_REPS, step, pt)

    private fun bw(name: String, pt: String, group: MuscleGroup) =
        StarterMachine(name, group, MachineKind.BODYWEIGHT_REPS, 2.5, pt)

    private fun cardio(name: String, pt: String) =
        StarterMachine(name, MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, pt)

    private val L = MuscleGroup.LEGS
    private val C = MuscleGroup.CHEST
    private val B = MuscleGroup.BACK
    private val S = MuscleGroup.SHOULDERS
    private val A = MuscleGroup.ARMS
    private val K = MuscleGroup.CORE

    /** Illustration slug per English starter name (assets `<slug>-1.webp` and `<slug>-2.webp`). */
    private val images: Map<String, String> = mapOf(
        "Leg press" to "leg-press",
        "Leg extension" to "leg-extensions",
        "Lying leg curl" to "lying-leg-curl-machine",
        "Seated leg curl" to "seated-leg-curl",
        "Hip abductor" to "thigh-abductor",
        "Hip adductor" to "thigh-adductor",
        "Hack squat" to "hack-squat-machine",
        "Smith machine squat" to "smith-machine-squats",
        "Glute kickback" to "one-legged-cable-kickback",
        "Barbell back squat" to "barbell-squat",
        "Front squat" to "front-squat-with-barbell",
        "Walking lunges" to "walking-lunges",
        "Reverse lunge" to "rear-lunges-with-dumbbell",
        "Step-up" to "step-ups-with-dumbbells",
        "Standing leg curl" to "standing-leg-curls",
        "Romanian deadlift" to "romanian-dead-lift",
        "Glute bridge" to "bridging",
        "Standing calf raise" to "standing-calf-raises-using-machine",
        "Seated calf raise" to "seated-calf-raise-using-machine",
        "Chest press" to "machine-bench-press",
        "Cable crossover" to "cable-crossover",
        "Dips" to "chest-dips",
        "Barbell bench press" to "bench-press",
        "Incline barbell bench press" to "incline-bench-press",
        "Decline bench press" to "decline-barbell-bench-press",
        "Dumbbell bench press" to "bench-press-dumbbell",
        "Incline dumbbell press" to "incline-dumbbell-press",
        "Dumbbell fly" to "dumbbell-flys",
        "Push-ups" to "push-ups",
        "Seated row" to "seated-cable-rows",
        "Back extension" to "hyperextensions",
        "Pull-ups" to "pull-ups",
        "Close-grip pulldown" to "v-bar-pull-down",
        "Straight-arm pulldown" to "straight-arm-push-down",
        "T-bar row" to "t-bar-rows",
        "Deadlift" to "barbell-dead-lifts",
        "Good morning" to "barbell-good-mornings",
        "Dumbbell lateral raise" to "lateral-dumbbell-raises",
        "Front raise" to "front-dumbbell-raise",
        "Biceps curl machine" to "biceps-curl-with-machine",
        "Cable biceps curl" to "standing-biceps-curl-with-cable",
        "Triceps pushdown" to "triceps-pushdown-with-cable",
        "Triceps extension machine" to "triceps-extensions-using-machine",
        "Barbell curl" to "biceps-curls-with-barbell",
        "EZ-bar curl" to "ez-bar-curl-with-barbell",
        "Dumbbell curl" to "biceps-curl-with-dumbbell",
        "Hammer curl" to "biceps-hammer-curl-with-dumbbell",
        "Preacher curl" to "preacher-curl-with-barbell",
        "Concentration curl" to "concentration-curls-with-dumbbell",
        "Cable curl" to "standing-biceps-curl-with-cable",
        "Rope triceps pushdown" to "triceps-pushdown-with-rope-and-cable",
        "Close-grip bench press" to "close-grip-barbell-bench-press",
        "Bench dips" to "bench-dips",
        "Crunch" to "crunches",
    )

    val all: List<StarterMachine> = listOf(
        // Legs
        w("Leg press", "Leg press", L, 5.0),
        w("Leg extension", "Cadeira extensora", L),
        w("Lying leg curl", "Mesa flexora", L),
        w("Seated leg curl", "Cadeira flexora", L),
        w("Hip abductor", "Cadeira abdutora", L),
        w("Hip adductor", "Cadeira adutora", L),
        w("Calf raise", "Panturrilha", L, 5.0),
        w("Hack squat", "Hack", L, 5.0),
        w("Smith machine squat", "Agachamento no Smith", L, 5.0),
        w("Glute kickback", "Coice no cabo", L),
        w("Barbell back squat", "Agachamento livre", L, 5.0),
        w("Front squat", "Agachamento frontal", L),
        w("Goblet squat", "Agachamento goblet", L, 2.0),
        w("Bulgarian split squat", "Agachamento búlgaro", L, 2.0),
        w("Walking lunges", "Passada", L, 2.0),
        w("Reverse lunge", "Afundo", L, 2.0),
        w("Step-up", "Step-up", L, 2.0),
        w("Standing leg curl", "Flexora em pé", L),
        w("Romanian deadlift", "Stiff com barra", L),
        w("Stiff-leg deadlift", "Stiff com halteres", L, 2.0),
        w("Hip thrust", "Elevação pélvica", L),
        bw("Glute bridge", "Ponte de glúteo", L),
        w("Standing calf raise", "Panturrilha em pé", L, 5.0),
        w("Seated calf raise", "Panturrilha sentado", L, 5.0),
        bw("Bodyweight squat", "Agachamento livre sem peso", L),
        // Chest
        w("Chest press", "Supino máquina", C),
        w("Incline chest press", "Supino inclinado máquina", C),
        w("Pec deck", "Voador (peck deck)", C),
        w("Cable crossover", "Crossover", C),
        bw("Dips", "Paralelas", C),
        w("Barbell bench press", "Supino reto", C),
        w("Incline barbell bench press", "Supino inclinado", C),
        w("Decline bench press", "Supino declinado", C),
        w("Dumbbell bench press", "Supino com halteres", C, 2.0),
        w("Incline dumbbell press", "Supino inclinado com halteres", C, 2.0),
        w("Dumbbell fly", "Crucifixo", C, 2.0),
        w("Low cable fly", "Crucifixo no cabo", C),
        bw("Push-ups", "Flexão de braço", C),
        // Back
        w("Lat pulldown", "Puxada frontal", B),
        w("Seated row", "Remada baixa", B),
        w("Assisted pull-up", "Barra assistida", B),
        bw("Back extension", "Extensão lombar", B),
        bw("Pull-ups", "Barra fixa", B),
        w("Wide-grip lat pulldown", "Puxada aberta", B),
        w("Close-grip pulldown", "Puxada fechada", B),
        w("Straight-arm pulldown", "Pulldown com corda", B),
        w("Barbell row", "Remada curvada", B),
        w("Dumbbell row", "Remada unilateral", B, 2.0),
        w("T-bar row", "Remada cavalinho", B),
        w("Machine row", "Remada máquina", B),
        w("Deadlift", "Levantamento terra", B),
        w("Good morning", "Bom dia", B),
        bw("Chin-ups", "Barra fixa supinada", B),
        // Shoulders
        w("Shoulder press", "Desenvolvimento máquina", S),
        w("Lateral raise machine", "Elevação lateral máquina", S),
        w("Rear delt fly", "Crucifixo inverso", S),
        w("Shrugs", "Encolhimento", S),
        w("Dumbbell shoulder press", "Desenvolvimento com halteres", S, 2.0),
        w("Barbell overhead press", "Desenvolvimento com barra", S),
        w("Arnold press", "Desenvolvimento Arnold", S, 2.0),
        w("Dumbbell lateral raise", "Elevação lateral", S, 2.0),
        w("Cable lateral raise", "Elevação lateral no cabo", S),
        w("Front raise", "Elevação frontal", S, 2.0),
        w("Face pull", "Face pull", S),
        w("Upright row", "Remada alta", S),
        w("Landmine press", "Landmine press", S),
        // Arms
        w("Biceps curl machine", "Rosca máquina", A),
        w("Cable biceps curl", "Rosca no cabo (barra reta)", A),
        w("Triceps pushdown", "Tríceps pulley", A),
        w("Triceps extension machine", "Tríceps máquina", A),
        w("Barbell curl", "Rosca direta", A),
        w("EZ-bar curl", "Rosca com barra W", A),
        w("Dumbbell curl", "Rosca alternada", A, 2.0),
        w("Hammer curl", "Rosca martelo", A, 2.0),
        w("Preacher curl", "Rosca Scott", A),
        w("Concentration curl", "Rosca concentrada", A, 2.0),
        w("Cable curl", "Rosca no cabo", A),
        w("Reverse curl", "Rosca inversa", A),
        w("Forearm curl", "Rosca de punho", A),
        w("Rope triceps pushdown", "Tríceps corda", A),
        w("Overhead dumbbell extension", "Tríceps francês", A, 2.0),
        w("Overhead cable extension", "Tríceps francês no cabo", A),
        w("Skull crusher", "Tríceps testa", A),
        w("Close-grip bench press", "Supino fechado", A),
        bw("Bench dips", "Tríceps banco", A),
        // Core
        w("Ab crunch machine", "Abdominal máquina", K),
        w("Rotary torso", "Rotação de tronco", K),
        bw("Crunch", "Abdominal", K),
        bw("Sit-ups", "Abdominal completo", K),
        w("Cable crunch", "Abdominal no cabo", K),
        bw("Hanging leg raise", "Elevação de pernas na barra", K),
        bw("Captain's chair knee raise", "Elevação de joelhos", K),
        w("Russian twist", "Rotação russa", K, 2.0),
        w("Wood chopper", "Lenhador no cabo", K),
        bw("Ab wheel", "Roda abdominal", K),
        // Cardio
        cardio("Treadmill", "Esteira"),
        cardio("Stationary bike", "Bicicleta ergométrica"),
        cardio("Spin bike", "Bicicleta de spinning"),
        cardio("Elliptical", "Elíptico"),
        cardio("Rowing machine", "Remo ergométrico"),
        cardio("Stair climber", "Escada (simulador)"),
        cardio("Walking (outdoor)", "Caminhada"),
        cardio("Running (outdoor)", "Corrida"),
    ).map { it.copy(image = images[it.name]) }

    /** Lower case, accents removed, trimmed. */
    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)

    /**
     * Keeps order. Blank query -> [list] unchanged. Case- and accent-insensitive "contains" (trimmed query) over
     * the name shown: [portuguese] null matches the English or the Portuguese name, true only namePt, false only name.
     */
    fun search(query: String, list: List<StarterMachine> = all, portuguese: Boolean? = null): List<StarterMachine> {
        val q = normalize(query)
        if (q.isEmpty()) return list
        return list.filter {
            when (portuguese) {
                null -> normalize(it.name).contains(q) || normalize(it.namePt).contains(q)
                true -> normalize(it.namePt).contains(q)
                false -> normalize(it.name).contains(q)
            }
        }
    }

    /** The picture slug of the first starter that [matches] [machineName]; null when none or it has no picture. */
    fun imageFor(machineName: String): String? = all.firstOrNull { matches(it, machineName) }?.image

    /** True when [machineName] (trimmed, case/accent-insensitive) equals the starter's English or Portuguese name. */
    fun matches(starter: StarterMachine, machineName: String): Boolean {
        val n = normalize(machineName)
        return n == normalize(starter.name) || n == normalize(starter.namePt)
    }
}

/** Credit for the exercise illustrations. */
object ExerciseImages {
    const val AUTHOR = "Greg Priday (Everkinetic)"
    const val SOURCE_URL = "https://github.com/everkinetic/data"
    const val LICENSE_NAME = "CC BY-SA 4.0"
    const val LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"
}
