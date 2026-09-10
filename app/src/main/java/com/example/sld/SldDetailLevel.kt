package com.example.sld

/**
 * DataBlock-style annotation density and Adaptive Level of Detail (LoD) tiers.
 *
 * Controls how many engineering data lines render per node on the SLD canvas,
 * without changing layout geometry: node widths/tiers are computed by the
 * engines before this filter applies, so toggling or scaling the level can never
 * shift the diagram — it only adds/removes callout text (the ETAP "DataBlock"
 * pattern: show/hide data without moving equipment).
 *
 * Zoom Bands (Phase 4 §4.2):
 *  - [MACRO]      — zoom < 0.35x: colored equipment boxes, major busbars, zero micro-text.
 *  - [STANDARD]   — 0.35x <= zoom < 0.85x: standard vector symbols, panel names, bus ratings.
 *  - [INSPECTION] — zoom >= 0.85x: full engineering fidelity (Isc, VD %, cable specs, meter badges).
 *
 * User overrides from the toolbar:
 *  - [COMPACT]    — device + trip rating only (clutter-free wide panels).
 *  - [DETAILED]   — STANDARD plus advanced lines: route length and device Icu.
 */
enum class SldDetailLevel {
    MACRO,
    STANDARD,
    INSPECTION,
    COMPACT,
    DETAILED
}

object SldDetailLevelPolicy {

    const val MACRO_ZOOM_MAX = 0.35f
    const val STANDARD_ZOOM_MAX = 0.85f

    /**
     * Phase 4 §4.2 — Dynamically resolve the active rendering tier from [zoomScale]
     * and optional [userOverride].
     *
     * Rules:
     * 1. Macro view (< 0.35x): Always resolves to [SldDetailLevel.MACRO] to suppress
     *    microscopic text smudges and preserve GPU rasterization budget on large plants.
     * 2. Standard view (0.35x <= z < 0.85x): Resolves to [SldDetailLevel.STANDARD], or honors
     *    user overrides [SldDetailLevel.COMPACT] / [SldDetailLevel.DETAILED].
     * 3. Inspection view (>= 0.85x): Resolves to [SldDetailLevel.INSPECTION] (or [SldDetailLevel.DETAILED]),
     *    or honors [SldDetailLevel.COMPACT] if explicitly set.
     */
    fun resolveEffectiveDetailLevel(
        zoomScale: Float,
        userOverride: SldDetailLevel? = null
    ): SldDetailLevel {
        if (zoomScale < MACRO_ZOOM_MAX) {
            return SldDetailLevel.MACRO
        }
        if (zoomScale < STANDARD_ZOOM_MAX) {
            return when (userOverride) {
                SldDetailLevel.COMPACT, SldDetailLevel.MACRO -> SldDetailLevel.COMPACT
                SldDetailLevel.DETAILED, SldDetailLevel.INSPECTION -> SldDetailLevel.DETAILED
                else -> SldDetailLevel.STANDARD
            }
        }
        return when (userOverride) {
            SldDetailLevel.COMPACT, SldDetailLevel.MACRO -> SldDetailLevel.COMPACT
            SldDetailLevel.STANDARD -> SldDetailLevel.STANDARD
            SldDetailLevel.DETAILED -> SldDetailLevel.DETAILED
            else -> SldDetailLevel.INSPECTION
        }
    }

    /** Which feeder callout lines the canvas draws at a given detail level. */
    data class FeederCalloutPolicy(
        /** Line 1: protective device type + trip rating (e.g. "400A MCCB"). */
        val breakerLine: Boolean,
        /** Line 2: cable / busway specification (with parallel-run prefix). */
        val cableLine: Boolean,
        /** Line 3: display-mode engineering metric or validation status. */
        val modeMetricLine: Boolean,
        /** Extra DETAILED-only lines: route length, device Icu, Ib/Iz, utilization, installation. */
        val advancedLines: Boolean,
        /** Full INSPECTION-only lines: neutral/CPC, VD limit, protection verdicts, ADS loop. */
        val inspectionLines: Boolean = false
    )

    fun feederCallout(level: SldDetailLevel): FeederCalloutPolicy = when (level) {
        SldDetailLevel.MACRO -> FeederCalloutPolicy(breakerLine = false, cableLine = false, modeMetricLine = false, advancedLines = false)
        SldDetailLevel.COMPACT -> FeederCalloutPolicy(breakerLine = true, cableLine = false, modeMetricLine = false, advancedLines = false)
        SldDetailLevel.STANDARD -> FeederCalloutPolicy(breakerLine = true, cableLine = true, modeMetricLine = true, advancedLines = false)
        SldDetailLevel.DETAILED -> FeederCalloutPolicy(breakerLine = true, cableLine = true, modeMetricLine = true, advancedLines = true)
        SldDetailLevel.INSPECTION -> FeederCalloutPolicy(breakerLine = true, cableLine = true, modeMetricLine = true, advancedLines = true, inspectionLines = true)
    }

    /** Cycles COMPACT → STANDARD → DETAILED → COMPACT (toolbar overflow action). */
    fun next(level: SldDetailLevel): SldDetailLevel = when (level) {
        SldDetailLevel.MACRO, SldDetailLevel.COMPACT -> SldDetailLevel.STANDARD
        SldDetailLevel.STANDARD -> SldDetailLevel.DETAILED
        SldDetailLevel.DETAILED, SldDetailLevel.INSPECTION -> SldDetailLevel.COMPACT
    }

    /** Human-readable label for the toolbar overflow item. */
    fun label(level: SldDetailLevel): String = when (level) {
        SldDetailLevel.MACRO -> "Macro"
        SldDetailLevel.COMPACT -> "Compact"
        SldDetailLevel.STANDARD -> "Standard"
        SldDetailLevel.DETAILED -> "Detailed"
        SldDetailLevel.INSPECTION -> "Inspection"
    }
}