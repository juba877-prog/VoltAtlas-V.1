package com.example.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 8 Slice 8A — motion source conformance architecture test.
 *
 * Proves production UI/SLD-toolbar code does not introduce ad-hoc duplicated
 * spring/easing constants: all animation specs must reference the centralized
 * [VoltAtlasMotion] tokens (or Compose defaults that resolve through them).
 *
 * The theme contract file itself is exempt (it defines the tokens).
 */
class MotionSourceConformanceTest {

    private val moduleRoot: File by lazy {
        // Unit tests run with CWD = app module; fall back to repo root layout.
        val candidates = listOf(File("src/main/java"), File("app/src/main/java"))
        candidates.firstOrNull { it.exists() }
            ?: error("Could not locate src/main/java from ${System.getProperty("user.dir")}")
    }

    /** Production sources allowed to declare raw animation primitives with mandatory written rationale. */
    private val allowlist: Map<String, String> = mapOf(
        "com/example/ui/theme/VoltAtlasMotion.kt" to "Centralized motion system authority defining design tokens",
        "com/example/ui/circuit/CircuitEditorParallelEntry.kt" to "Group A file: strictly exempt per architectural policy",
        "com/example/ui/circuit/CircuitEditorShell.kt" to "Group A file: strictly exempt per architectural policy"
    )

    /** Raw spring/easing literals that indicate a bypassed motion contract. */
    private val adHocPatterns = listOf(
        Regex("""dampingRatio\s*=\s*0?\.\d+f"""),          // e.g. dampingRatio = 0.7f
        Regex("""stiffness\s*=\s*\d{2,}f"""),               // e.g. stiffness = 400f
        Regex("""EaseOut(Bounce|Back|Elastic|Circ|Quart|Quint)"""),
        Regex("""EaseIn(Bounce|Back|Elastic|Circ)"""),
        Regex("""keyframes\s*\{"""),
        Regex("""\bspring\s*\([^)]*StiffnessLow"""),
        Regex("""\bSpring\.StiffnessLow\b"""),
        Regex("""\btween\s*\(\s*(durationMillis\s*=\s*)?\d+""")
    )

    private fun walkSources(fileOrDir: File): List<File> =
        if (fileOrDir.isFile) {
            if (fileOrDir.extension == "kt") listOf(fileOrDir) else emptyList()
        } else if (fileOrDir.isDirectory) {
            fileOrDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        } else {
            emptyList()
        }

    @Test
    fun testAllowlistIntegrity() {
        for ((path, rationale) in allowlist) {
            assertTrue("Allowlist entry '$path' must have non-empty rationale", rationale.isNotBlank())
        }
    }

    @Test
    fun testNoAdHocSpringOrEasingConstantsInProductionMotionScope() {
        val violations = mutableListOf<String>()

        val scopeRoots = listOf(
            File(moduleRoot, "com/example/ui"),
            File(moduleRoot, "com/example/ai/ui"),
            File(moduleRoot, "com/example/sld"),
            File(moduleRoot, "com/example/boot"),
            File(moduleRoot, "com/example/MainActivity.kt")
        ).filter { it.exists() }

        for (root in scopeRoots) {
            for (file in walkSources(root)) {
                val relative = file.invariantSeparatorsPath
                    .substringAfter("com/example/", missingDelimiterValue = "")
                    .let { if (it.isEmpty()) "" else "com/example/$it" }
                if (relative in allowlist) continue
                val text = file.readText()
                for (pattern in adHocPatterns) {
                    pattern.findAll(text).forEach { match ->
                        violations.add("$relative :: '${match.value}'")
                    }
                }
            }
        }

        assertTrue(
            "Ad-hoc motion constants found outside VoltAtlasMotion contract:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun testCentralContractFileDefinesAllSevenTokens() {
        val contractFile = File(moduleRoot, "com/example/ui/theme/VoltAtlasMotion.kt")
        assertTrue("VoltAtlasMotion.kt must exist", contractFile.exists())
        val text = contractFile.readText()
        for (token in listOf(
            "object StandardSpatial",
            "object FastSpatial",
            "object EmphasizedSpatial",
            "object FastEffects",
            "object DefaultEffects",
            "object SlowEffects",
            "object ReducedMotion"
        )) {
            assertTrue("Contract must define $token", text.contains(token))
        }
    }
}