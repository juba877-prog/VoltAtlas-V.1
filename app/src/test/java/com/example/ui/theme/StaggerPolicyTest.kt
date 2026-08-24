package com.example.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 8 Slice 8D — expand/collapse and list choreography policy tests.
 *
 * Proves:
 *  - stagger delays are capped so the last tile is never noticeably withheld;
 *  - reduced motion removes ALL stagger;
 *  - stagger is applied ONLY to the approved small tile group
 *    (Quick Engineering Hub) — never to circuit / BOQ / panel / catalog lists.
 */
class StaggerPolicyTest {

    private val moduleRoot: File by lazy {
        listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.exists() }
            ?: error("Could not locate src/main/java from ${System.getProperty("user.dir")}")
    }

    // ── Cap math ─────────────────────────────────────────────────────────────

    @Test
    fun testStaggerDelaysGrowLinearlyThenCap() {
        assertEquals(0, StaggerPolicy.delayForIndex(0))
        assertEquals(40, StaggerPolicy.delayForIndex(1))
        assertEquals(80, StaggerPolicy.delayForIndex(2))
        assertEquals(120, StaggerPolicy.delayForIndex(3))
        // Cap: last item never noticeably withheld.
        assertEquals(StaggerPolicy.MaxTotalStaggerMillis, StaggerPolicy.delayForIndex(7))
        assertEquals(StaggerPolicy.MaxTotalStaggerMillis, StaggerPolicy.delayForIndex(50))
        assertTrue(
            "Cap must not exceed one standard duration plus one increment",
            StaggerPolicy.MaxTotalStaggerMillis <= VoltAtlasMotion.StandardSpatial.DurationMillis + StaggerPolicy.PerItemDelayMillis
        )
    }

    @Test
    fun testReducedMotionRemovesAllStagger() {
        for (index in 0..20) {
            assertEquals(
                "Reduced motion must publish every tile immediately",
                0,
                StaggerPolicy.delayForIndex(index, reduceMotion = true)
            )
        }
    }

    @Test
    fun testNegativeIndexIsSafe() {
        assertEquals(0, StaggerPolicy.delayForIndex(-1))
        assertEquals(0, StaggerPolicy.delayForIndex(-1, reduceMotion = true))
    }

    // ── Scope: small groups only ────────────────────────────────────────────

    @Test
    fun testFirstEntryStaggerIsUsedOnlyInQuickEngineeringHub() {
        val usages = File(moduleRoot, "com/example")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    if (line.contains("FirstEntryStagger(") && !line.contains("fun FirstEntryStagger")) {
                        file.name to (i + 1)
                    } else null
                }
            }
            .toList()

        val definitionFile = "MotionTransitions.kt"
        val nonDefinitionUsages = usages.filter { it.first != definitionFile }
        assertTrue(
            "FirstEntryStagger must exist in MotionTransitions.kt",
            usages.any { it.first == definitionFile } ||
                File(moduleRoot, "com/example/ui/theme/MotionTransitions.kt").readText().contains("fun FirstEntryStagger(")
        )
        for ((file, line) in nonDefinitionUsages) {
            assertEquals(
                "Stagger is only approved for the Quick Engineering Hub tiles",
                "QuickEngineeringHubScreen.kt",
                file
            )
        }
    }

    @Test
    fun testHubGridUsesStableItemKeys() {
        val hub = File(moduleRoot, "com/example/ui/screens/QuickEngineeringHubScreen.kt")
        assertTrue(hub.exists())
        val text = hub.readText()
        assertTrue(
            "Hub tool tiles must use stable keys for identity preservation",
            text.contains("key = { it.id }")
        )
    }
}