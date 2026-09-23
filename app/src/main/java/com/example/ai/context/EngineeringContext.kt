package com.example.ai.context

/**
 * Immutable snapshot of the active project and UI state provided to the AI Assistant.
 * Orchestrates contextual awareness without exposing mutable references to the UI or database.
 */
data class EngineeringContext(
    val projectId: String? = null,
    val projectName: String = "No Active Project",
    val currentScreen: String = "Dashboard",
    val activeEntityType: String? = null, // "CIRCUIT", "TRANSFORMER", "PANEL", "GENERATOR", "ATS", "UPS", "EARTHING", etc.
    val activeEntityId: String? = null,
    val activeEntityName: String? = null,
    val projectSummary: ProjectSummarySnapshot = ProjectSummarySnapshot(),
    val availableManufacturers: List<String> = listOf("Elsewedy", "Riyadh Cables", "Ducab", "IEC-Generic"),
    val knownEntityInputs: Map<String, String> = emptyMap(),
    val validatedResultSummary: String? = null
)

/**
 * High-level deterministic summary of project metrics for AI context grounding.
 */
data class ProjectSummarySnapshot(
    val totalCircuits: Int = 0,
    val totalTransformers: Int = 0,
    val totalConnectedKVA: Double = 0.0,
    val maxTxLoadingPct: Double = 0.0,
    val maxCumulativeVdPct: Double = 0.0,
    val activeIssuesCount: Int = 0,
    val complianceStatus: String = "NORMAL"
)
