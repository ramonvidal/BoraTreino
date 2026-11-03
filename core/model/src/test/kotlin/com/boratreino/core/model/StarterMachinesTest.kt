package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterMachinesTest {
    private val all = StarterMachines.all

    /** English name, Portuguese name, muscle group, kind (W or B), step in kg. From the feedback round's list. */
    private data class Row(val en: String, val pt: String, val group: MuscleGroup, val kind: MachineKind, val step: Double)

    private fun w(en: String, pt: String, group: MuscleGroup, step: Double = 2.5) = Row(en, pt, group, MachineKind.WEIGHT_REPS, step)
    private fun b(en: String, pt: String, group: MuscleGroup) = Row(en, pt, group, MachineKind.BODYWEIGHT_REPS, 2.5)

    private val L = MuscleGroup.LEGS
    private val C = MuscleGroup.CHEST
    private val B = MuscleGroup.BACK
    private val S = MuscleGroup.SHOULDERS
    private val A = MuscleGroup.ARMS
    private val K = MuscleGroup.CORE

    /** The starters the owner already had, with the Portuguese names the list gives them. */
    private val existing = listOf(
        w("Leg press", "Leg press", L, 5.0),
        w("Hack squat", "Hack", L, 5.0),
        w("Smith machine squat", "Agachamento no Smith", L, 5.0),
        w("Calf raise", "Panturrilha", L, 5.0),
        w("Glute kickback", "Coice no cabo", L),
        w("Leg extension", "Cadeira extensora", L),
        w("Lying leg curl", "Mesa flexora", L),
        w("Seated leg curl", "Cadeira flexora", L),
        w("Hip abductor", "Cadeira abdutora", L),
        w("Hip adductor", "Cadeira adutora", L),
        w("Chest press", "Supino máquina", C),
        w("Incline chest press", "Supino inclinado máquina", C),
        w("Pec deck", "Voador (peck deck)", C),
        w("Cable crossover", "Crossover", C),
        b("Dips", "Paralelas", C),
        w("Lat pulldown", "Puxada frontal", B),
        w("Seated row", "Remada baixa", B),
        w("Assisted pull-up", "Barra assistida", B),
        b("Back extension", "Extensão lombar", B),
        b("Pull-ups", "Barra fixa", B),
        w("Shoulder press", "Desenvolvimento máquina", S),
        w("Lateral raise machine", "Elevação lateral máquina", S),
        w("Rear delt fly", "Crucifixo inverso", S),
        w("Shrugs", "Encolhimento", S),
        w("Biceps curl machine", "Rosca máquina", A),
        w("Cable biceps curl", "Rosca no cabo (barra reta)", A),
        w("Triceps pushdown", "Tríceps pulley", A),
        w("Triceps extension machine", "Tríceps máquina", A),
        w("Ab crunch machine", "Abdominal máquina", K),
        w("Rotary torso", "Rotação de tronco", K),
    )

    /** The suggested additions, with their groups, kinds and steps. */
    private val added = listOf(
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
        b("Glute bridge", "Ponte de glúteo", L),
        w("Standing calf raise", "Panturrilha em pé", L, 5.0),
        w("Seated calf raise", "Panturrilha sentado", L, 5.0),
        b("Bodyweight squat", "Agachamento livre sem peso", L),
        w("Barbell bench press", "Supino reto", C),
        w("Incline barbell bench press", "Supino inclinado", C),
        w("Decline bench press", "Supino declinado", C),
        w("Dumbbell bench press", "Supino com halteres", C, 2.0),
        w("Incline dumbbell press", "Supino inclinado com halteres", C, 2.0),
        w("Dumbbell fly", "Crucifixo", C, 2.0),
        w("Low cable fly", "Crucifixo no cabo", C),
        b("Push-ups", "Flexão de braço", C),
        w("Wide-grip lat pulldown", "Puxada aberta", B),
        w("Close-grip pulldown", "Puxada fechada", B),
        w("Straight-arm pulldown", "Pulldown com corda", B),
        w("Barbell row", "Remada curvada", B),
        w("Dumbbell row", "Remada unilateral", B, 2.0),
        w("T-bar row", "Remada cavalinho", B),
        w("Machine row", "Remada máquina", B),
        w("Deadlift", "Levantamento terra", B),
        w("Good morning", "Bom dia", B),
        b("Chin-ups", "Barra fixa supinada", B),
        w("Dumbbell shoulder press", "Desenvolvimento com halteres", S, 2.0),
        w("Barbell overhead press", "Desenvolvimento com barra", S),
        w("Arnold press", "Desenvolvimento Arnold", S, 2.0),
        w("Dumbbell lateral raise", "Elevação lateral", S, 2.0),
        w("Cable lateral raise", "Elevação lateral no cabo", S),
        w("Front raise", "Elevação frontal", S, 2.0),
        w("Face pull", "Face pull", S),
        w("Upright row", "Remada alta", S),
        w("Landmine press", "Landmine press", S),
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
        b("Bench dips", "Tríceps banco", A),
        b("Crunch", "Abdominal", K),
        b("Sit-ups", "Abdominal completo", K),
        w("Cable crunch", "Abdominal no cabo", K),
        b("Hanging leg raise", "Elevação de pernas na barra", K),
        b("Captain's chair knee raise", "Elevação de joelhos", K),
        w("Russian twist", "Rotação russa", K, 2.0),
        w("Wood chopper", "Lenhador no cabo", K),
        b("Ab wheel", "Roda abdominal", K),
    )

    private val contract = existing + added

    @Test
    fun `every starter has an English name and a Portuguese name`() {
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.name.isNotBlank() })
        assertTrue(all.all { it.namePt.isNotBlank() })
    }

    @Test
    fun `English names are unique ignoring case`() {
        val names = all.map { it.name.lowercase() }

        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `there are about a hundred starters`() {
        assertTrue("was ${all.size}", all.size in 90..110)
    }

    @Test
    fun `no starter is cardio`() {
        assertFalse(all.any { it.muscleGroup == MuscleGroup.CARDIO })
        assertFalse(all.any { it.kind == MachineKind.CARDIO })
    }

    @Test
    fun `every muscle group except cardio has starters`() {
        MuscleGroup.entries.filter { it != MuscleGroup.CARDIO }.forEach { group ->
            assertTrue("no starter for $group", all.any { it.muscleGroup == group })
        }
    }

    @Test
    fun `the original English names are still there, unchanged`() {
        val names = all.map { it.name }.toSet()
        val original = listOf(
            "Leg press", "Hack squat", "Smith machine squat", "Calf raise", "Glute kickback", "Leg extension",
            "Lying leg curl", "Seated leg curl", "Hip abductor", "Hip adductor", "Chest press", "Incline chest press",
            "Pec deck", "Cable crossover", "Dips", "Lat pulldown", "Seated row", "Assisted pull-up", "Back extension",
            "Pull-ups", "Shoulder press", "Lateral raise machine", "Rear delt fly", "Shrugs", "Biceps curl machine",
            "Cable biceps curl", "Triceps pushdown", "Triceps extension machine", "Ab crunch machine", "Rotary torso",
        )

        original.forEach { assertTrue(it, it in names) }
    }

    @Test
    fun `the original starters keep their group, kind and step`() {
        existing.forEach { row ->
            val starter = all.single { it.name == row.en }
            assertEquals(row.en, row.group, starter.muscleGroup)
            assertEquals(row.en, row.kind, starter.kind)
            assertEquals(row.en, row.step, starter.stepKg, 0.0)
        }
    }

    @Test
    fun `the suggested additions are in the list with their Portuguese names, groups, kinds and steps`() {
        added.forEach { row ->
            val starter = all.single { it.name == row.en }
            assertEquals(row.en, row.pt, starter.namePt)
            assertEquals(row.en, row.group, starter.muscleGroup)
            assertEquals(row.en, row.kind, starter.kind)
            assertEquals(row.en, row.step, starter.stepKg, 0.0)
        }
    }

    @Test
    fun `the original starters have the Portuguese names the list gives them`() {
        existing.forEach { row ->
            assertEquals(row.en, row.pt, all.single { it.name == row.en }.namePt)
        }
    }

    @Test
    fun `every entry of the list is in StarterMachines`() {
        assertEquals(contract.size, contract.map { it.en }.toSet().size)
        contract.forEach { row -> assertTrue(row.en, all.any { it.name == row.en }) }
    }

    @Test
    fun `leg press, hack squat, smith squat, calf raise and the barbell squat step 5 kg`() {
        listOf("Leg press", "Hack squat", "Smith machine squat", "Calf raise", "Standing calf raise", "Seated calf raise", "Barbell back squat")
            .forEach { name ->
                assertEquals(name, 5.0, all.single { it.name == name }.stepKg, 0.0)
            }
    }

    @Test
    fun `dumbbell exercises step 2 kg`() {
        listOf("Dumbbell bench press", "Dumbbell fly", "Dumbbell row", "Dumbbell curl", "Dumbbell shoulder press").forEach { name ->
            assertEquals(name, 2.0, all.single { it.name == name }.stepKg, 0.0)
        }
    }

    @Test
    fun `no starter has a step other than 2, 2_5 or 5 kg`() {
        assertTrue(all.all { it.stepKg == 2.0 || it.stepKg == 2.5 || it.stepKg == 5.0 })
    }

    @Test
    fun `the bodyweight starters are the ones marked B`() {
        val bodyweight = contract.filter { it.kind == MachineKind.BODYWEIGHT_REPS }.map { it.en }.toSet()
        assertEquals(bodyweight, all.filter { it.kind == MachineKind.BODYWEIGHT_REPS }.map { it.name }.toSet())
    }

    @Test
    fun `dips and pull-ups are bodyweight`() {
        assertEquals(MachineKind.BODYWEIGHT_REPS, all.single { it.name == "Dips" }.kind)
        assertEquals(MachineKind.BODYWEIGHT_REPS, all.single { it.name == "Pull-ups" }.kind)
    }

    // displayName

    @Test
    fun `displayName gives the Portuguese name when asked and the English one otherwise`() {
        val bench = all.single { it.name == "Barbell bench press" }

        assertEquals("Supino reto", bench.displayName(portuguese = true))
        assertEquals("Barbell bench press", bench.displayName(portuguese = false))
    }

    @Test
    fun `a starter given only an English name uses it in both languages`() {
        val starter = StarterMachine("Cable row", MuscleGroup.BACK, MachineKind.WEIGHT_REPS, 2.5)

        assertEquals("Cable row", starter.namePt)
        assertEquals("Cable row", starter.displayName(portuguese = true))
        assertEquals("Cable row", starter.displayName(portuguese = false))
    }

    // search

    @Test
    fun `a blank query gives the list back unchanged and in order`() {
        assertEquals(all, StarterMachines.search(""))
        assertEquals(all, StarterMachines.search("   "))
    }

    @Test
    fun `search finds by the Portuguese name, ignoring case and accents`() {
        val names = StarterMachines.search("supino").map { it.name }

        assertTrue(names.contains("Barbell bench press"))
        assertTrue(names.contains("Chest press"))
        assertFalse(names.contains("Leg press"))
    }

    @Test
    fun `agachamento finds the squats in either language`() {
        val names = StarterMachines.search("agachamento").map { it.name }

        assertTrue(names.contains("Bulgarian split squat"))
        assertTrue(names.contains("Barbell back squat"))
        assertTrue(names.contains("Smith machine squat"))
    }

    @Test
    fun `accents are ignored in both directions`() {
        assertTrue(StarterMachines.search("bulgaro").any { it.name == "Bulgarian split squat" })
        assertTrue(StarterMachines.search("búlgaro").any { it.name == "Bulgarian split squat" })
    }

    @Test
    fun `search ignores case`() {
        assertTrue(StarterMachines.search("PECK").any { it.name == "Pec deck" })
        assertTrue(StarterMachines.search("peck").any { it.name == "Pec deck" })
    }

    @Test
    fun `search trims the query`() {
        assertEquals(StarterMachines.search("supino"), StarterMachines.search("  supino  "))
    }

    @Test
    fun `search keeps the order of the list`() {
        val found = StarterMachines.search("a")
        val positions = found.map { all.indexOf(it) }

        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun `a query with no match gives nothing`() {
        assertTrue(StarterMachines.search("zzzzzz").isEmpty())
    }

    @Test
    fun `search works on a list given to it`() {
        val small = listOf(all.single { it.name == "Pec deck" }, all.single { it.name == "Leg press" })

        assertEquals(listOf("Pec deck"), StarterMachines.search("peck", small).map { it.name })
    }

    // matches

    @Test
    fun `a machine matches a starter by its English or Portuguese name`() {
        val bench = all.single { it.name == "Barbell bench press" }

        assertTrue(StarterMachines.matches(bench, "Barbell bench press"))
        assertTrue(StarterMachines.matches(bench, "Supino reto"))
    }

    @Test
    fun `matching ignores case, accents and surrounding spaces`() {
        val bulgarian = all.single { it.name == "Bulgarian split squat" }

        assertTrue(StarterMachines.matches(bulgarian, "  AGACHAMENTO BULGARO  "))
        assertTrue(StarterMachines.matches(bulgarian, "bulgarian SPLIT squat"))
    }

    @Test
    fun `a different machine does not match`() {
        val bench = all.single { it.name == "Barbell bench press" }

        assertFalse(StarterMachines.matches(bench, "Supino inclinado"))
        assertFalse(StarterMachines.matches(bench, "Barbell"))
    }

    // search by language

    @Test
    fun `English only finds the English names, so sup misses Incline chest press`() {
        val found = StarterMachines.search("sup", all, portuguese = false).map { it.name }

        assertFalse(found.contains("Incline chest press"))
        assertEquals(all.filter { it.name.lowercase().contains("sup") }.map { it.name }, found)
    }

    @Test
    fun `Portuguese only finds supino in the Portuguese names`() {
        val found = StarterMachines.search("supino", all, portuguese = true)

        assertTrue(found.any { it.name == "Barbell bench press" })
        assertTrue(found.any { it.name == "Incline chest press" })
        assertEquals(all.filter { it.namePt.lowercase().contains("supino") }.map { it.name }, found.map { it.name })
        assertTrue(found.none { it.name.lowercase().contains("supino") })
    }

    @Test
    fun `no language given finds a match in either name`() {
        val names = StarterMachines.search("sup", all, portuguese = null).map { it.name }

        assertTrue(names.contains("Barbell bench press"))
        assertTrue(names.contains("Incline chest press"))
    }

    @Test
    fun `the default search matches either name as before`() {
        assertEquals(
            StarterMachines.search("sup", all, portuguese = null),
            StarterMachines.search("sup", all),
        )
    }

    @Test
    fun `accents are ignored in Portuguese only mode`() {
        assertTrue(StarterMachines.search("bulgaro", all, portuguese = true).any { it.name == "Bulgarian split squat" })
        assertTrue(StarterMachines.search("búlgaro", all, portuguese = true).any { it.name == "Bulgarian split squat" })
    }

    @Test
    fun `accents and case are ignored in English only mode`() {
        assertTrue(StarterMachines.search("BULGÁRIAN", all, portuguese = false).any { it.name == "Bulgarian split squat" })
        assertTrue(StarterMachines.search("bulgarian", all, portuguese = false).any { it.name == "Bulgarian split squat" })
    }

    @Test
    fun `a Portuguese-only search does not find English-only text`() {
        assertTrue(StarterMachines.search("bulgarian", all, portuguese = true).isEmpty())
    }
}
