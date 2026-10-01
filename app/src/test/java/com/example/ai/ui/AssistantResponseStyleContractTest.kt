package com.example.ai.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 R-4: response style contract.
 *
 * The deterministic policy layer (ConversationPolicyEngine) builds the fallback explanations
 * that reach the user when the language layer is unavailable. Those strings are the ones
 * users actually read most often, so the hierarchy rules are asserted against them here
 * rather than against prompt text: answer first, compact evidence, no internal markers,
 * no test-report tone.
 */
class AssistantResponseStyleContractTest {

    private fun style(s: String) = EngineeringDisplayFormatter.normalizeDisplayText(s)

    // --- 5. the direct answer comes first ------------------------------------

    @Test
    fun `an explanation never opens with internal bookkeeping`() {
        val explanations = listOf(
            "Current size 70 mm², derated capacity 196.6 A.",
            "Candidate 95 mm² has been recorded.",
            "Circuit DP-G3 is selected at 70 mm² Cu/XLPE."
        )
        for (raw in explanations) {
            val out = style(raw)
            assertFalse("internal marker leaked: $out", out.contains("CALCULATED_BY_VOLTATLAS"))
            assertFalse("provenance label leaked: $out", out.contains("KNOWN_FROM_PROJECT"))
            assertFalse("provenance label leaked: $out", out.contains("USER_PROVIDED"))
            assertTrue("empty output for: $raw", out.isNotBlank())
        }
    }

    @Test
    fun `policy-layer markdown emphasis is flattened for the plain-text renderer`() {
        // The policy engine emits **bold**; the renderer has no Markdown support.
        val out = style("Circuit **DP-G3** is selected at **70 mm² Cu/XLPE**.")
        assertFalse(out, out.contains("**"))
        assertTrue(out, out.contains("DP-G3"))
        assertTrue(out, out.contains("70 mm\u00b2"))
    }

    // --- 3. units inside real policy copy ------------------------------------

    @Test
    fun `policy-layer copy never duplicates a unit`() {
        val out = style(
            "Candidate 95 mm² recorded. Conductor 95 mm², derated capacity 233.0 A, " +
                "voltage drop 1.20% (limit 2.00%)."
        )
        assertTrue(out, out.contains("95 mm\u00b2"))
        assertFalse(out, out.contains("mm\u00b2 mm\u00b2"))
        assertFalse(out, out.contains("% %"))
        assertTrue(out, out.contains("233.0 A"))
    }

    // --- 10. concise answers remain possible ----------------------------------

    @Test
    fun `a short answer is not padded with report furniture`() {
        val concise = style("70 mm\u00b2 is the minimum compliant size.")
        assertEqualsExactly(concise, "70 mm\u00b2 is the minimum compliant size.")
        assertFalse(concise, concise.contains("Governing criterion"))
        assertFalse(concise, concise.contains("Verified facts"))
    }

    @Test
    fun `a detailed answer keeps its evidence intact`() {
        val detailed = style(
            "50 mm\u00b2 fails for DP-G3.\n" +
                "• Ampacity: 158.6 A < 160 A\n" +
                "• Voltage drop: 2.18% > 2.00%\n\n" +
                "70 mm\u00b2 is the minimum compliant size."
        )
        assertTrue(detailed, detailed.contains("158.6 A"))
        assertTrue(detailed, detailed.contains("2.18%"))
        assertTrue(detailed, detailed.contains("70 mm\u00b2"))
        assertTrue("direct answer must lead", detailed.startsWith("50 mm\u00b2 fails"))
    }

    @Test
    fun `bullet glyphs survive normalization`() {
        val out = style("- Ampacity: 158.6 A\n- Voltage drop: 2.18%")
        assertTrue(out, out.contains("\u2022 Ampacity"))
        assertTrue(out, out.contains("\u2022 Voltage drop"))
        assertFalse(out, out.contains("- Ampacity"))
    }

    // --- 19 / 25.9 / 25.10: action labels correspond to real actions ---------

    @Test
    fun `a chip label never renders markup`() {
        // SuggestedActionsRow normalizes each label before display; this pins the
        // transform that guarantees it, for any label the mapper might produce.
        val labels = listOf(
            "Use 70 mm\u00b2", "What-if", "Compare sizes", "Review & apply",
            "Use $50 mm\u00b2", "Use **70 mm²**", "Use `70 mm\u00b2`"
        )
        for (l in labels) {
            val shown = EngineeringDisplayFormatter.normalizeDisplayText(l)
            assertFalse("markup survived in '$shown'", shown.contains("*"))
            assertFalse("markup survived in '$shown'", shown.contains("`"))
            assertFalse("markup survived in '$shown'", shown.contains("$"))
            assertTrue("label collapsed to nothing: '$l'", shown.isNotBlank())
        }
    }

    @Test
    fun `an apply-style label keeps the size so the user knows what would change`() {
        val shown = EngineeringDisplayFormatter.normalizeDisplayText("Use 70 mm\u00b2")
        assertTrue(shown, shown.contains("70 mm\u00b2"))
        assertTrue("label must state the effect", shown.startsWith("Use"))
    }

    private fun assertEqualsExactly(actual: String, expected: String) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}