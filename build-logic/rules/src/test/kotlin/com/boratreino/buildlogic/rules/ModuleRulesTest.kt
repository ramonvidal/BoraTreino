package com.boratreino.buildlogic.rules

import com.boratreino.buildlogic.rules.ModuleRules.Module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleRulesTest {
    /** The Phase 0 contract for what each core module may depend on. */
    private val contract: Map<String, Set<String>> = mapOf(
        ":core:model" to emptySet(),
        ":core:database" to setOf(":core:model"),
        ":core:data" to setOf(":core:model", ":core:database"),
        ":core:settings" to setOf(":core:model"),
        ":core:notify" to setOf(":core:model", ":core:data", ":core:settings"),
        ":core:ui" to setOf(":core:model"),
        ":core:update" to setOf(":core:model"),
    )

    private val features = listOf(
        ":feature:today",
        ":feature:calendar",
        ":feature:progress",
        ":feature:routines",
        ":feature:settings",
        ":feature:update",
    )

    private fun coreModule(path: String, dependsOn: Set<String> = contract.getValue(path)) =
        Module(path, usesAndroid = path != ":core:model", dependsOn = dependsOn)

    private fun featureModule(path: String, dependsOn: Set<String> = setOf(":core:model", ":core:ui")) =
        Module(path, usesAndroid = true, dependsOn = dependsOn)

    /** A structure that follows the contract in full. */
    private val valid: List<Module> =
        contract.map { (path, deps) -> coreModule(path, deps) } +
            listOf(
                featureModule(":feature:today", setOf(":core:model", ":core:data", ":core:settings", ":core:ui")),
                featureModule(":feature:calendar", setOf(":core:model", ":core:data", ":core:ui")),
                featureModule(":feature:progress", setOf(":core:model", ":core:data", ":core:ui")),
                featureModule(":feature:routines", setOf(":core:model", ":core:data", ":core:ui")),
                featureModule(":feature:settings", setOf(":core:model", ":core:settings", ":core:ui")),
                featureModule(":feature:update", setOf(":core:model", ":core:update", ":core:ui")),
                Module(
                    ":app",
                    usesAndroid = true,
                    dependsOn = (contract.keys + features).toSet(),
                ),
            )

    /** The valid structure with the module at the same path replaced. */
    private fun withModule(replacement: Module): List<Module> =
        valid.map { if (it.path == replacement.path) replacement else it }

    @Test
    fun `the structure allowed by the contract passes`() {
        assertEquals(emptyList<String>(), ModuleRules.violations(valid))
    }

    @Test
    fun `a core module may depend on each core module its row allows`() {
        for ((path, allowed) in contract) {
            for (dependency in allowed) {
                val violations = ModuleRules.violations(withModule(coreModule(path, setOf(dependency))))
                assertEquals("$path -> $dependency should pass", emptyList<String>(), violations)
            }
        }
    }

    @Test
    fun `a core module depending on a core module outside its allowed set fails`() {
        for ((path, allowed) in contract) {
            val notAllowed = contract.keys - path - allowed
            for (dependency in notAllowed) {
                val violations = ModuleRules.violations(withModule(coreModule(path, allowed + dependency)))
                assertTrue("$path -> $dependency should fail", violations.isNotEmpty())
            }
        }
    }

    @Test
    fun `a core module depending on a feature fails`() {
        for (path in contract.keys) {
            val violations = ModuleRules.violations(
                withModule(coreModule(path, contract.getValue(path) + ":feature:today")),
            )
            assertTrue("$path -> :feature:today should fail", violations.isNotEmpty())
        }
    }

    @Test
    fun `a feature depending on another feature fails`() {
        for (feature in features) {
            for (other in features - feature) {
                val violations = ModuleRules.violations(
                    withModule(featureModule(feature, setOf(":core:model", other))),
                )
                assertTrue("$feature -> $other should fail", violations.isNotEmpty())
            }
        }
    }

    @Test
    fun `nothing except app may depend on app`() {
        for (feature in features) {
            val violations = ModuleRules.violations(
                withModule(featureModule(feature, setOf(":core:model", ":app"))),
            )
            assertTrue("$feature -> :app should fail", violations.isNotEmpty())
        }
        for (path in contract.keys) {
            val violations = ModuleRules.violations(
                withModule(coreModule(path, contract.getValue(path) + ":app")),
            )
            assertTrue("$path -> :app should fail", violations.isNotEmpty())
        }
    }

    @Test
    fun `a module that is neither app, feature nor a listed core module fails`() {
        for (path in listOf(":utils", ":core:health", ":feature:home:extra")) {
            val violations = ModuleRules.violations(valid + Module(path, usesAndroid = true, dependsOn = setOf(":core:model")))
            assertTrue("$path should fail", violations.isNotEmpty())
        }
    }

    @Test
    fun `core model using Android fails`() {
        val violations = ModuleRules.violations(
            withModule(Module(":core:model", usesAndroid = true, dependsOn = emptySet())),
        )
        assertTrue(violations.isNotEmpty())
    }

    @Test
    fun `the core dependency map equals the contract`() {
        assertEquals(contract, ModuleRules.CORE_DEPENDENCIES)
    }
}
