package com.example.ai.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 R-6: robustness of the display normalizer under realistic and hostile input.
 *
 * The normalizer runs on the main thread inside composition on EVERY assistant turn, so a
 * pathological input must not throw, recurse, or take unbounded time. This suite exists
 * because regex-based normalization is exactly the kind of code that can throw
 * StackOverflowError via catastrophic backtracking, which crashes the app rather than
 * merely rendering badly.
 */
class EngineeringDisplayFormatterStressTest {

    private val dol = "$"

    private fun norm(s: String) = EngineeringDisplayFormatter.normalizeDisplayText(s)

    @Test
    fun `a long realistic engineering answer is normalized without throwing`() {
        val sb = StringBuilder()
        repeat(60) { i ->
            sb.append("Circuit DP-G$i is selected at 70 mm² Cu/XLPE. ")
            sb.append("Verified facts: design current 158.6 A, breaker 160 A, ")
            sb.append("derated capacity 196.6 A (factor 0.87), voltage drop 1.59%. ")
            sb.append("${dol}I_z = 158.6\\text{ A}${dol} and ${dol}\\Delta V = 2.18\\%${dol} are cited. ")
        }
        val out = norm(sb.toString())
        assertTrue(out.isNotEmpty())
        assertTrue(!out.contains("$"))
        assertTrue(!out.contains("\\text"))
    }

    @Test
    fun `dense digit and unit runs do not blow up the unit deduper`() {
        // Catastrophic backtracking probe: many digits, unit-like tokens, no separators.
        val out = norm("1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 " +
                "A A A A m m m mm mm % % % Ω Ω Ω kVA kW kA " +
                "16 25 35 50 70 95 120 150 185 240 300 400 500 630 800 1000")
        assertTrue(out.isNotNotEmptyOrNull())
    }

    @Test
    fun `unterminated math delimiters do not hang or throw`() {
        for (s in listOf(dol, "${dol}${dol}", "\\(", "\\[", "\\text{", "${dol}${dol}${dol}${dol}${dol}${dol}${dol}",
                         "\\frac{1}{", dol + "a".repeat(5000))) {
            // An unterminated delimiter legitimately reduces to nothing (a bare "$"),
            // so the contract is "returns without throwing", not "returns non-empty".
            norm(s)
        }
    }

    @Test
    fun `deeply nested and repeated markup is handled`() {
        for (s in listOf("**" + "x".repeat(2000) + "**",
                         "\\text{" .repeat(200) + "v" + "}".repeat(200),
                         "_".repeat(1000),
                         "*".repeat(2000),
                         "%" + "1".repeat(2000),
                         "\\text{".repeat(300) + "v")) {
            assertTrue(norm(s).isNotEmpty())
        }
    }

    @Test
    fun `pathological repeated unit patterns stay fast`() {
        val started = System.nanoTime()
        // The exact shape that stresses a nested-quantifier unit-dedupe regex.
        norm("70 mm² mm² mm² mm² 95 mm² mm² 160 A A A 2.18% % % 0.245 ΩΩ ".repeat(40))
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("normalization took ${elapsedMs}ms", elapsedMs < 2000)
    }

    @Test
    fun `null and blank inputs are safe`() {
        assertTrue(EngineeringDisplayFormatter.normalizeMetricValue(null, "mm²").isEmpty())
        assertTrue(EngineeringDisplayFormatter.joinValueAndUnit(null, null).isEmpty())
        assertTrue(norm("").isEmpty())
    }

    private fun String.isNotNotEmptyOrNull(): Boolean = isNotEmpty()
}