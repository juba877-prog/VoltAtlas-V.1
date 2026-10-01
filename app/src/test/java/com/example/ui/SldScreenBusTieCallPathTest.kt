package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 6 Batch 1 regression test — P0-8 (bus ties must actually reach the layout engine).
 *
 * The defect under test: `SldScreen` called `SldLayoutEngine.calculateLayout(...)` WITHOUT
 * the `busTies` parameter, so the engine's default `emptyList()` applied in production and
 * bus-tie synthesis/validation was dead on the real call path (direct-call tests only).
 *
 * This is a call-path conformance test on the production source: Room → SldScreen →
 * calculateLayout → busTies. Direct engine behavior with ties is covered separately by
 * [com.example.sld.SldOrthogonalRoutingInvariantTest] and
 * [com.example.sld.SldDxfRealWorldExportTest].
 */
class SldScreenBusTieCallPathTest {

    private val sldScreenSource: String by lazy {
        val candidates = listOf(
            File("src/main/java/com/example/ui/SldScreen.kt"),
            File("app/src/main/java/com/example/ui/SldScreen.kt"),
            File("../app/src/main/java/com/example/ui/SldScreen.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("SldScreen.kt not found from ${System.getProperty("user.dir")}")
        file.readText(Charsets.UTF_8)
    }

    @Test
    fun busTiesAreSourcedFromRoomForTheEffectiveProject() {
        assertTrue(
            "SldScreen must collect the active project's bus ties from Room (project-scoped)",
            sldScreenSource.contains("db.busTieDao().getBusTiesForProject(effectiveProjId)")
        )
    }

    @Test
    fun calculateLayoutReceivesTheBusTies() {
        val calls = Regex("""SldLayoutEngine\.calculateLayout\s*\(""").findAll(sldScreenSource).toList()
        assertEquals(
            "exactly one production calculateLayout call path must exist in SldScreen",
            1,
            calls.size
        )
        val callStart = calls.first().range.last
        val region = sldScreenSource.substring(callStart, minOf(sldScreenSource.length, callStart + 2500))
        assertTrue(
            "busTies must be passed inside the calculateLayout(...) call (previously absent ⇒ engine default emptyList)",
            region.contains("busTies = busTies")
        )
    }

    @Test
    fun layoutRecomputationKeyIncludesBusTies() {
        assertTrue(
            "the layout remember() key must include busTies so tie changes recompute the layout",
            sldScreenSource.lineSequence().any {
                it.contains("remember(") && it.contains("busTies)")
            }
        )
    }
}
