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
) {
    /** The Portuguese name when [portuguese], else the English one. */
    fun displayName(portuguese: Boolean): String = if (portuguese) namePt else name
}

/** The built-in list of common gym machines and exercises (no cardio yet). */
object StarterMachines {
    private fun w(name: String, pt: String, group: MuscleGroup, step: Double = 2.5) =
        StarterMachine(name, group, MachineKind.WEIGHT_REPS, step, pt)

    private fun bw(name: String, pt: String, group: MuscleGroup) =
        StarterMachine(name, group, MachineKind.BODYWEIGHT_REPS, 2.5, pt)

    private val L = MuscleGroup.LEGS
    private val C = MuscleGroup.CHEST
    private val B = MuscleGroup.BACK
    private val S = MuscleGroup.SHOULDERS
    private val A = MuscleGroup.ARMS
    private val K = MuscleGroup.CORE

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
    )

    /** Lower case, accents removed, trimmed. */
    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)

    /** Keeps order. Blank query -> [list] unchanged. Case- and accent-insensitive "contains" over name and namePt (trimmed query). */
    fun search(query: String, list: List<StarterMachine> = all): List<StarterMachine> {
        val q = normalize(query)
        if (q.isEmpty()) return list
        return list.filter { normalize(it.name).contains(q) || normalize(it.namePt).contains(q) }
    }

    /** True when [machineName] (trimmed, case/accent-insensitive) equals the starter's English or Portuguese name. */
    fun matches(starter: StarterMachine, machineName: String): Boolean {
        val n = normalize(machineName)
        return n == normalize(starter.name) || n == normalize(starter.namePt)
    }
}
