package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P1-12 test-profile contract guard.
 *
 * This repository has NO CI configuration (verified 2026-10-01: no .github/workflows,
 * .gitlab-ci.yml, Jenkinsfile, azure-pipelines.yml, .circleci) — `app/build.gradle.kts` is
 * therefore the single authority for how test profiles split the suite. This test pins the
 * three invariants that make the documented MANDATORY full-verification requirement real:
 *
 *  1. the fast/dev dev-loop profile keeps EXCLUDING com.example.ai.* (speed contract);
 *  2. the gate profile keeps INCLUDING com.example.ai.* (a green dev loop is never a gate —
 *     the AI safety tests are the point of the gate);
 *  3. the MANDATORY-FULL-VERIFICATION marker documenting the no-CI requirement stays present.
 *
 * If this test fails, the profiles changed: re-read the profile block comment, update the
 * contract consciously, and re-run a fresh `-Pprofile=gate` before claiming completeness.
 */
class ProfileContractTest {

    private fun buildFile(): File {
        val candidates = listOf(
            File("build.gradle.kts"),        // working dir = app module
            File("app/build.gradle.kts"),    // working dir = repo root
            File("../app/build.gradle.kts")  // working dir = sibling of app
        )
        return candidates.firstOrNull { it.exists() }
            ?: error(
                "app/build.gradle.kts not found from ${File(".").absolutePath} — " +
                    "ProfileContractTest needs the module build file to verify the profiles."
            )
    }

    private fun block(text: String, startMarker: String, endMarker: String): String {
        val start = text.indexOf(startMarker)
        require(start >= 0) { "profile start marker missing in app/build.gradle.kts: $startMarker" }
        val end = text.indexOf(endMarker, start)
        require(end > start) { "profile end marker missing in app/build.gradle.kts: $endMarker" }
        return text.substring(start, end)
    }

    @Test
    fun fastProfileKeepsExcludingAiSubsystem() {
        val text = buildFile().readText()
        val fast = block(text, "\"fast\", \"dev\" ->", "\"ai\" ->")
        assertTrue(
            "P1-12: the fast/dev profile must keep excluding com.example.ai.* (dev-loop " +
                "speed contract). AI tests belong to -Pprofile=ai; move them only together " +
                "with an updated verification plan.",
            fast.contains("excludeTestsMatching(\"com.example.ai.*\")")
        )
    }

    @Test
    fun gateProfileKeepsIncludingAiSubsystem() {
        val text = buildFile().readText()
        val gate = block(text, "\"gate\" ->", "\"diagnostic\" ->")
        assertFalse(
            "P1-12: the gate profile must NOT exclude com.example.ai.* — mutation-approval " +
                "security tests, grounded-response validation and schema contracts live there. " +
                "A green dev loop is never a release gate.",
            gate.contains("excludeTestsMatching(\"com.example.ai.*\")")
        )
    }

    @Test
    fun mandatoryFullVerificationMarkerIsDocumented() {
        assertTrue(
            "P1-12: this repo has no CI config — the profile block must keep documenting the " +
                "MANDATORY-FULL-VERIFICATION requirement (a fresh -Pprofile=gate run backs any " +
                "claim of complete verification).",
            buildFile().readText().contains("MANDATORY-FULL-VERIFICATION")
        )
    }
}
