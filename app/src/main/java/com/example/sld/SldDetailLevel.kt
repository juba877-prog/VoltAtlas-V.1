package com.example.sld

/**
 * DataBlock-style annotation density (Roadmap §3.2).
 *
 * Controls how many engineering data lines render per node on the SLD canvas,
 * without changing layout geometry: node widths/tiers are computed by the
 * engines before this filter applies, so toggling the level can never shift
 * the diagram — it only adds/removes callout text (the ETAP "DataBlock"
 * pattern: show/hide data without moving equipment).
 *
 *  - [COMPACT]  — device + trip rating only (clutter-free wide panels).
 *  - [STANDARD] — today's default view (device, cable/busway, mode metric).
 *  - [DETAILED] — STANDARD plus advanced lines: route length and the §1.2
 *    fields (device interrupting rating Icu, etc.).
 *
 * Default is [STANDARD] everywhere, so existing behavior is byte-identical
 * until the user opts in.
 */
enum class SldDetailLevel {
    COMPACT,
    STANDARD,
    DETAILED
}

object SldDetailLevelPolicy {

    /** Which feeder callout lines the canvas draws at a given detail level. */
    data class FeederCalloutPolicy(
        /** Line 1: protective device type + trip rating (e.g. "400A MCCB"). */
        val breakerLine: Boolean,
        /** Line 2: cable / busway specification (with parallel-run prefix). */
        val cableLine: Boolean,
        /** Line 3: display-mode engineering metric or validation status. */
        val modeMetricLine: Boolean,
        /** Extra DETAILED-only lines: route length, device Icu. */
        val advancedLines: Boolean
    )

    fun feederCallout(level: SldDetailLevel): FeederCalloutPolicy = when (level) {
        SldDetailLevel.COMPACT -> FeederCalloutPolicy(breakerLine = true, cableLine = false, modeMetricLine = false, advancedLines = false)
        SldDetailLevel.STANDARD -> FeederCalloutPolicy(breakerLine = true, cableLine = true, modeMetricLine = true, advancedLines = false)
        SldDetailLevel.DETAILED -> FeederCalloutPolicy(breakerLine = true, cableLine = true, modeMetricLine = true, advancedLines = true)
    }

    /** Cycles COMPACT → STANDARD → DETAILED → COMPACT (toolbar overflow action). */
    fun next(level: SldDetailLevel): SldDetailLevel = when (level) {
        SldDetailLevel.COMPACT -> SldDetailLevel.STANDARD
        SldDetailLevel.STANDARD -> SldDetailLevel.DETAILED
        SldDetailLevel.DETAILED -> SldDetailLevel.COMPACT
    }

    /** Human-readable label for the toolbar overflow item. */
    fun label(level: SldDetailLevel): String = when (level) {
        SldDetailLevel.COMPACT -> "Compact"
        SldDetailLevel.STANDARD -> "Standard"
        SldDetailLevel.DETAILED -> "Detailed"
    }
}