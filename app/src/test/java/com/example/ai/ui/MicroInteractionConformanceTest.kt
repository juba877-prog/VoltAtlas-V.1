package com.example.ai.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 8 Slice 8E — selected micro-interaction conformance tests.
 *
 * Proves the assistant entrance is an additive, token-driven emphasis and
 * that native Material3 modal sheet behavior was NOT overridden.
 */
class MicroInteractionConformanceTest {

    private val moduleRoot: File by lazy {
        listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.exists() }
            ?: error("Could not locate src/main/java from ${System.getProperty("user.dir")}")
    }

    private val sheetSource: String by lazy {
        val f = File(moduleRoot, "com/example/ai/ui/EngineeringAssistantSheet.kt")
        assertTrue("EngineeringAssistantSheet.kt must exist", f.exists())
        f.readText()
    }

    @Test
    fun testAssistantSheetStillUsesNativeModalBottomSheet() {
        // Native MD3 component motion, drag handle area, and predictive back
        // must remain untouched — no custom replacement.
        assertTrue(sheetSource.contains("ModalBottomSheet("))
        assertTrue(sheetSource.contains("PredictiveBackHandler"))
        assertTrue(sheetSource.contains("rememberModalBottomSheetState"))
    }

    @Test
    fun testAssistantEntranceUsesCentralEmphasizedToken() {
        // The only added motion is inner-content entrance via the centralized
        // contract — no ad-hoc spring/easing literals.
        assertTrue(sheetSource.contains("VoltAtlasMotion.EmphasizedSpatial.floatSpec"))
        assertTrue(!Regex("""dampingRatio\s*=\s*0?\.\d+f""").containsMatchIn(sheetSource))
    }

    @Test
    fun testAssistantEntranceRespectsReducedMotion() {
        assertTrue(sheetSource.contains("LocalReduceMotion.current"))
        assertTrue(sheetSource.contains("contentEntrance.snapTo(1f)"))
    }
}