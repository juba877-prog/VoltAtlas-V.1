package com.example.ai

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CI hardcode audit for the AI production package (main source set).
 *
 * Fails whenever forbidden engineering constants, fabricated fallbacks or a second
 * engineering table reappear anywhere in `com.example.ai` production code.
 *
 * Rules:
 * - Comment lines are excluded so that documents describing removed patterns are not
 *   themselves violations.
 * - Whitelist: UI dimension values, contract/schema versions, IDs and non-engineering
 *   operational constants (timeouts, sizes of buffers) are outside the forbidden list.
 * - Located by searching upward from the test working directory, so it works whether Gradle
 *   executes from the repository root or from the `app` module.
 */
class NoHardcodedEngineeringValuesTest {

    private val aiRoot: File = locateAiSourceRoot()

    private fun locateAiSourceRoot(): File {
        val relative = listOf(
            File("src/main/java/com/example/ai"),
            File("app/src/main/java/com/example/ai")
        )
        var cursor: File? = File(".").absoluteFile
        repeat(6) {
            val base = cursor ?: return@repeat
            for (rel in relative) {
                val candidate = File(base, rel.path)
                if (candidate.isDirectory) return candidate
            }
            cursor = cursor?.parentFile
        }
        throw AssertionError(
            "Could not locate com.example.ai source root from " + File(".").absolutePath
        )
    }

    /** Forbidden patterns: fabrication markers, assumed engineering defaults, and second tables. */
    private val forbiddenPatterns: List<Pair<String, Regex>> = listOf(
        "fabricated project id default" to Regex("DEFAULT_PROJ"),
        "assumed system voltage default" to Regex("""=\s*400\.0\b"""),
        "assumed system voltage default (230)" to Regex("""=\s*230\.0\b"""),
        "assumed cable length default" to Regex("""=\s*30\.0\b"""),
        "assumed power factor default" to Regex("""=\s*0\.8[0-9]\b"""),
        "assumed target power factor default" to Regex("""=\s*0\.9[0-9]\b"""),
        "assumed motor efficiency default" to Regex("""=\s*91\.0\b"""),
        "fabricated voltage drop" to Regex("""vdPct\s*=\s*1\.85"""),
        "fabricated ampacity in text" to Regex("""380 A continuous|315 A cable Iz"""),
        "fabricated remediation" to Regex("""Upsize conductor to 300 mm"""),
        "fabricated pass verdict in text" to Regex("""passes ampacity \(Iz"""),
        "hardcoded voltage chip" to Regex("""400V 3Ph|230V 1Ph"""),
        "second ampacity table" to Regex("""sizeMm2\s*<=\s*\d+"""),
        "second standard-size ladder" to Regex("""1\.5,\s*2\.5,\s*4\.0,\s*6\.0,\s*10\.0,\s*16\.0"""),
        "assumed fake revision default" to Regex("""\?\s*:\s*1L\b""")
    )

    private fun ktFiles(dir: File): List<File> =
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun isCommentLine(raw: String): Boolean {
        val line = raw.trim()
        return line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")
    }

    @Test
    fun aiProductionSources_containNoForbiddenEngineeringConstants() {
        val files = ktFiles(aiRoot)
        assertTrue("Expected AI production sources under ${aiRoot.path}", files.isNotEmpty())

        val violations = mutableListOf<String>()

        for (file in files) {
            val lines = file.readLines()
            lines.forEachIndexed { index, raw ->
                if (isCommentLine(raw)) return@forEachIndexed
                for ((label, regex) in forbiddenPatterns) {
                    if (regex.containsMatchIn(raw)) {
                        violations.add("${file.name}:${index + 1} [$label] ${raw.trim().take(140)}")
                        break
                    }
                }
            }
        }

        assertTrue(
            "Forbidden engineering constants found in the AI production path:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun aiProductionSources_defineNoDefaultProjectIdentity() {
        val offenders = ktFiles(aiRoot).flatMap { file ->
            file.readLines().mapIndexedNotNull { index, raw ->
                if (isCommentLine(raw)) return@mapIndexedNotNull null
                if (raw.contains("\"DEFAULT_PROJ\"") || raw.contains("\"DEFAULT_PROJECT\"")) {
                    "${file.name}:${index + 1}"
                } else null
            }
        }
        assertTrue("DEFAULT_PROJECT identity leaked into AI sources: $offenders", offenders.isEmpty())
    }
}
