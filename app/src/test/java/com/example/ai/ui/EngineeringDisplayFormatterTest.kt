package com.example.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 R-1: user-visible output contract for the assistant.
 *
 * The renderer is plain Compose `Text`, so every one of these is a literal character the
 * user would otherwise see. Each test asserts on the exact user-visible string.
 */
class EngineeringDisplayFormatterTest {

    private fun norm(s: String) = EngineeringDisplayFormatter.normalizeDisplayText(s)
    private fun join(v: String, u: String) = EngineeringDisplayFormatter.joinValueAndUnit(v, u)

    // --- 1. raw LaTeX never appears in supported UI output -------------------

    // '$' opens a Kotlin template, so every literal '$' in these samples is spliced via dol.
    private val dol = "$"

    @Test
    fun `inline latex math is rendered as plain engineering text`() {
        assertEquals("Iz = 158.6 A", norm("${dol}I_z = 158.6\\text{ A}${dol}"))
        assertEquals("Ib = 126.9 A", norm("${dol}I_b = 126.9\\text{ A}${dol}"))
        // \Delta becomes the real Unicode symbol and \% becomes a literal percent. Spacing inside
        // the expression is preserved exactly as written - the formatter never re-lays out text.
        assertEquals("\u0394 V = 2.18%", norm("${dol}\\Delta V = 2.18\\%${dol}"))
    }

    @Test
    fun `no latex delimiter or command survives normalization`() {
        val samples = listOf(
            "${dol}I_z = 158.6\\text{ A}${dol}",
            "${dol}${dol}\\frac{1}{2}${dol}${dol}",
            "\\(I_b = 126.9\\)",
            "\\[ S = 55.28 \\text{ mm}^2 \\]",
            "Cable **70 mm\u00b2** [CALCULATED_BY_VOLTATLAS]"
        )
        for (s in samples) {
            val out = norm(s)
            assertFalse("residual '$' in: $out", out.contains("$"))
            assertFalse("residual backslash in: $out", out.contains("\\"))
            assertFalse("residual 'text{}' in: $out", out.contains("text{"))
            assertFalse("residual 'frac' in: $out", out.contains("frac"))
        }
    }

    @Test
    fun `escaped percent underscore and ampersand become their literal characters`() {
        assertEquals("50% derated", norm("50\\% derated"))
        assertEquals("cable_size", norm("cable\\_size"))
        assertEquals("R & C", norm("R \\& C"))
    }

    // --- 2. engineering units never duplicate ---------------------------------

    @Test
    fun `unit already inside the authoritative value is never appended twice`() {
        // This is the exact production defect: cableSize_mm2 arrives as "70 mm²"
        // and the card used to render "70 mm² mm²".
        assertEquals("70 mm\u00b2", join("70 mm\u00b2", "mm\u00b2"))
        assertEquals("70 mm\u00b2", join("70mm2", "mm\u00b2"))
        assertEquals("70 mm\u00b2", join("70", "mm\u00b2"))
        assertEquals("300 mm\u00b2", join("300 mm\u00b2", "mm\u00b2"))
        assertEquals("160 A", join("160 A", "A"))
        assertEquals("126.9 A", join("126.9", "A"))
        assertEquals("95.0 m", join("95.0 m", "m"))
    }

    @Test
    fun `percent binds tight to its number and ohm is not doubled`() {
        assertEquals("2.18%", join("2.18", "%"))
        assertEquals("2.18%", join("2.18%", "%"))
        // "%" binds tight to its number, so the normalized form is "2.18%", not "2.18 %".
        assertEquals("2.18%", norm("2.18 % %"))
        assertEquals("2.18%", norm("2.18% %"))
        assertEquals("0.245 \u03a9", norm("0.245 \u03a9\u03a9"))
        assertEquals("70 mm\u00b2", norm("70 mm\u00b2 mm\u00b2"))
    }

    @Test
    fun `normalizeMetricValue is unit aware and leaves bare values intact`() {
        assertEquals("70", EngineeringDisplayFormatter.normalizeMetricValue("70 mm\u00b2", "mm\u00b2"))
        assertEquals("70", EngineeringDisplayFormatter.normalizeMetricValue("70mm2", "mm\u00b2"))
        assertEquals("70", EngineeringDisplayFormatter.normalizeMetricValue("70", "mm\u00b2"))
        // Declared unit is unknown -> value is passed through untouched.
        assertEquals("DP-G3", EngineeringDisplayFormatter.normalizeMetricValue("DP-G3", null))
    }

    @Test
    fun `a value ending in a letter is not mistaken for a unit`() {
        // "CABLE" ends with "A" but the trailing letter is preceded by a letter, so the
        // unit-aware strip must decline. This is the type/field-aware guarantee.
        assertEquals("CABLE", EngineeringDisplayFormatter.normalizeMetricValue("CABLE", "A"))
        assertEquals("70", EngineeringDisplayFormatter.normalizeMetricValue("70A", "A"))
    }

    // --- 3. Unicode engineering symbols render correctly ----------------------

    @Test
    fun `unicode engineering symbols survive normalization`() {
        for (sym in listOf("\u0394V", "\u03a9", "\u2265", "\u2264", "\u00d7", "%", "mm\u00b2", "kW", "kVA", "A", "V")) {
            val out = norm("Value: $sym here")
            assertTrue("symbol $sym lost in: $out", out.contains(sym))
        }
        assertEquals("\u2265 160 A", norm("$\\geq$ 160 A"))
        assertEquals("4 \u00d7 95 mm\u00b2", norm("4 \\times 95 mm\u00b2"))
    }

    // --- 4. no malformed Markdown --------------------------------------------

    @Test
    fun `unsupported markdown emphasis is flattened not shown literally`() {
        assertEquals("50 mm\u00b2 fails", norm("**50 mm²** fails"))
        assertEquals("50 mm\u00b2 fails", norm("__50 mm²__ fails"))
        assertEquals("Iz", norm("*Iz*"))
        assertEquals("70 mm\u00b2", norm("`70 mm²`"))
        assertEquals("PASS", norm("### PASS"))
        assertFalse(norm("**bold**").contains("*"))
    }

    @Test
    fun `underscored identifiers are not mangled by italic markdown`() {
        // cableSize_mm2 is an identifier, not emphasis; italics must not eat the underscores.
        assertTrue(norm("cableSize_mm2 is set").contains("cableSize_mm2"))
    }

    // --- 5. response starts with the direct answer ---------------------------

    @Test
    fun `the direct answer stays first and is not buried under markers`() {
        val raw = "[CALCULATED_BY_VOLTATLAS] 50 mm² fails for DP-G3.\n\nIz = 158.6 A"
        val out = norm(raw)
        assertTrue(out.startsWith("50 mm\u00b2 fails for DP-G3."))
        assertFalse(out.contains("CALCULATED_BY_VOLTATLAS"))
    }

    // --- 8. evidence markers are not leaked unnecessarily ---------------------

    @Test
    fun `internal provenance markers are removed from prose`() {
        assertEquals("70 mm\u00b2 is compliant.", norm("70 mm\u00b2 is compliant. [CALCULATED_BY_VOLTATLAS]"))
        assertEquals("Calculated result.", norm("[PROVENANCE_ENGINE] Calculated result."))
    }

    @Test
    fun `a leading marker does not swallow the sentence that follows it`() {
        val out = norm("[CALCULATED_BY_VOLTATLAS] 50 mm\u00b2 fails for DP-G3.")
        assertTrue(out.startsWith("50 mm\u00b2 fails for DP-G3."))
    }

    @Test
    fun `ordinary bracketed user text is not mistaken for an internal marker`() {
        assertEquals("Check [DP-G3] now", norm("Check [DP-G3] now"))
        assertEquals("value [ok]", norm("value [ok]"))
        // Lowercase and short tokens are never enum-shaped, so they must survive intact.
        assertTrue(norm("Circuit [DP-G3]").contains("[DP-G3]"))
    }

    // --- 16. normalization is stable and idempotent --------------------------

    @Test
    fun `normalization is idempotent so history reload matches what was displayed`() {
        val samples = listOf(
            "${dol}I_z = 158.6\\text{ A}${dol}",
            "**70 mm\u00b2 mm\u00b2** [CALCULATED_BY_VOLTATLAS]",
            "${dol}\\Delta V = 2.18\\%${dol}",
            "Cable **70 mm\u00b2** fails [CALCULATED_BY_VOLTATLAS]"
        )
        for (s in samples) {
            val once = norm(s)
            assertEquals("not idempotent for: $s", once, norm(once))
        }
    }

    @Test
    fun `blank and null inputs are safe`() {
        assertEquals("", norm(""))
        // A null value with a declared unit degrades to the bare unit symbol, not to "null".
        assertEquals("A", EngineeringDisplayFormatter.joinValueAndUnit(null, "A"))
        assertEquals("", EngineeringDisplayFormatter.normalizeMetricValue(null, "mm\u00b2"))
        assertEquals("", EngineeringDisplayFormatter.normalizeMetricValue("   ", "A"))
    }

    @Test
    fun `numeric precision and engineering values are never altered`() {
        // Presentation only: the number itself must pass through byte-identically.
        assertEquals("158.6 A", join("158.6", "A"))
        assertEquals("126.90 A", join("126.90", "A"))
        assertTrue(norm("Iz = 126.90 A").contains("126.90"))
        assertFalse(norm("Iz = 126.90 A").contains("126.9 A"))
    }
}