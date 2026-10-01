package com.example.sld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 6 Batch 1 regression test — P0-6 (SLD PASS/FAIL verdict).
 *
 * The defect under test: the OVERVIEW feeder callout appended a literal " PASS" whenever
 * voltage-drop text existed, and the callout color used a *different* source (hardcoded
 * 5 %/3 % cumulative thresholds), so text and color could disagree and a FAIL could be
 * rendered as PASS.
 *
 * Contract: [computeVdVerdict] is the single verdict source —
 *   actual ≤ limit  → PASS
 *   actual >  limit → FAIL
 *   basis missing   → null (caller renders DATA REQUIRED, never a verdict)
 * Both the callout text and its color derive from this one value.
 */
class SldVdVerdictTest {

    @Test
    fun passWhenActualAtOrBelowLimit() {
        assertEquals("PASS", computeVdVerdict("0.85%", "3.00%"))
        assertEquals("PASS", computeVdVerdict("2.99%", "3.00%"))
        assertEquals("PASS", computeVdVerdict("3.00%", "3.00%")) // boundary is ≤
    }

    @Test
    fun failWhenActualExceedsLimit() {
        assertEquals("FAIL", computeVdVerdict("3.01%", "3.00%"))
        assertEquals("FAIL", computeVdVerdict("5.20%", "3.00%"))
        assertEquals("FAIL", computeVdVerdict("0.50%", "0.40%"))
    }

    @Test
    fun missingBasisYieldsNoVerdict_neverLiteralPass() {
        assertNull("no actual VD ⇒ no verdict", computeVdVerdict(null, "3.00%"))
        assertNull("no limit ⇒ no verdict", computeVdVerdict("0.85%", null))
        assertNull("blank actual ⇒ no verdict", computeVdVerdict("", "3.00%"))
        assertNull("blank limit ⇒ no verdict", computeVdVerdict("0.85%", ""))
        assertNull("whitespace limit ⇒ no verdict", computeVdVerdict("0.85%", "   "))
        assertNull("unparseable actual ⇒ no verdict", computeVdVerdict("N/A", "3.00%"))
        assertNull("unparseable limit ⇒ no verdict", computeVdVerdict("0.85%", "unknown"))
        assertNull("DATA REQUIRED actual ⇒ no verdict", computeVdVerdict("DATA REQUIRED", "3.00%"))
    }

    @Test
    fun formattingVariantsParse() {
        assertEquals("PASS", computeVdVerdict("2.5%", "3%"))
        assertEquals("FAIL", computeVdVerdict("4%", "3.00 %"))
        assertEquals("PASS", computeVdVerdict(" 1.20% ", " 3.00% "))
    }
}
