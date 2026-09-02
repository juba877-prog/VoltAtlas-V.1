package com.example.sld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 §4.2 — Adaptive Level of Detail (LoD) Unit Tests.
 */
class SldAdaptiveLoDTest {

    @Test
    fun zoomScale_resolvesToExpectedLoDTiers() {
        assertEquals(
            "zoomScale < 0.35f must resolve to MACRO",
            SldDetailLevel.MACRO,
            SldDetailLevelPolicy.resolveEffectiveDetailLevel(0.2f)
        )
        assertEquals(
            "0.35f <= zoomScale < 0.85f must resolve to STANDARD",
            SldDetailLevel.STANDARD,
            SldDetailLevelPolicy.resolveEffectiveDetailLevel(0.6f)
        )
        assertEquals(
            "zoomScale >= 0.85f must resolve to INSPECTION",
            SldDetailLevel.INSPECTION,
            SldDetailLevelPolicy.resolveEffectiveDetailLevel(1.2f)
        )
    }

    @Test
    fun macroZoom_suppressesMicroTextEvenWithUserOverride() {
        // Macro view (<0.35x) must suppress micro-text to avoid visual clutter regardless of toolbar override
        assertEquals(
            "Macro zoom overrides DETAILED user setting to MACRO",
            SldDetailLevel.MACRO,
            SldDetailLevelPolicy.resolveEffectiveDetailLevel(0.25f, userOverride = SldDetailLevel.DETAILED)
        )
        assertEquals(
            "Macro zoom overrides COMPACT user setting to MACRO",
            SldDetailLevel.MACRO,
            SldDetailLevelPolicy.resolveEffectiveDetailLevel(0.15f, userOverride = SldDetailLevel.COMPACT)
        )
    }

    @Test
    fun standardZoom_honorsUserOverride() {
        assertEquals(
            "Standard zoom honors COMPACT override",
            SldDetailLevel.COMPACT,
            SldDetailLevelPolicy.resolveEffectiveDetailLevel(0.5f, userOverride = SldDetailLevel.COMPACT)
        )
        assertEquals(
            "Standard zoom honors DETAILED override",
            SldDetailLevel.DETAILED,
            SldDetailLevelPolicy.resolveEffectiveDetailLevel(0.5f, userOverride = SldDetailLevel.DETAILED)
        )
        assertEquals(
            "Standard zoom defaults to STANDARD with no override",
            SldDetailLevel.STANDARD,
            SldDetailLevelPolicy.resolveEffectiveDetailLevel(0.5f, userOverride = null)
        )
    }

    @Test
    fun inspectionZoom_honorsUserOverride() {
        assertEquals(
            "Inspection zoom honors COMPACT override",
            SldDetailLevel.COMPACT,
            SldDetailLevelPolicy.resolveEffectiveDetailLevel(1.0f, userOverride = SldDetailLevel.COMPACT)
        )
        assertEquals(
            "Inspection zoom defaults to INSPECTION",
            SldDetailLevel.INSPECTION,
            SldDetailLevelPolicy.resolveEffectiveDetailLevel(1.0f, userOverride = null)
        )
    }

    @Test
    fun feederCalloutPolicy_matchesTierSpecifications() {
        val macro = SldDetailLevelPolicy.feederCallout(SldDetailLevel.MACRO)
        assertFalse("Macro must suppress breaker text", macro.breakerLine)
        assertFalse("Macro must suppress cable specs", macro.cableLine)
        assertFalse("Macro must suppress mode metric text", macro.modeMetricLine)
        assertFalse("Macro must suppress advanced lines", macro.advancedLines)

        val compact = SldDetailLevelPolicy.feederCallout(SldDetailLevel.COMPACT)
        assertTrue("Compact shows breaker line", compact.breakerLine)
        assertFalse("Compact suppresses cable line", compact.cableLine)
        assertFalse("Compact suppresses mode metric", compact.modeMetricLine)

        val standard = SldDetailLevelPolicy.feederCallout(SldDetailLevel.STANDARD)
        assertTrue("Standard shows breaker line", standard.breakerLine)
        assertTrue("Standard shows cable line", standard.cableLine)
        assertTrue("Standard shows mode metric", standard.modeMetricLine)
        assertFalse("Standard suppresses advanced lines", standard.advancedLines)

        val inspection = SldDetailLevelPolicy.feederCallout(SldDetailLevel.INSPECTION)
        assertTrue("Inspection shows breaker line", inspection.breakerLine)
        assertTrue("Inspection shows cable line", inspection.cableLine)
        assertTrue("Inspection shows mode metric", inspection.modeMetricLine)
        assertTrue("Inspection shows advanced lines", inspection.advancedLines)
    }
}
