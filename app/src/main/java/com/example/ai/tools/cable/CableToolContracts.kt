package com.example.ai.tools.cable

import com.example.ai.contracts.EngineeringValue
import com.squareup.moshi.JsonClass

/**
 * Standard tool names for Phase 6 deterministic cable engineering tools.
 */
object CableToolNames {
    const val CALCULATE_CABLE_SIZING = "calculate_cable_sizing"
    const val EVALUATE_CABLE_CANDIDATE = "evaluate_cable_candidate"
    const val COMPARE_CABLE_CANDIDATES = "compare_cable_candidates"
    const val RUN_WHAT_IF = "run_what_if"
}

/**
 * Governing engineering criterion classifying why a specific cable/conductor size was selected
 * or why a candidate failed.
 *
 * Supported in Phase 6:
 * - NONE: Clean pass without governing failure.
 * - AMPACITY: Continuous current / protective device thermal ampacity failure.
 * - VOLTAGE_DROP: Local circuit voltage drop exceeded limit.
 * - CUMULATIVE_VOLTAGE_DROP: Cumulative voltage drop from supply exceeded limit.
 * - CATALOG_AVAILABILITY: Requested cable type or size missing from manufacturer catalog.
 * - INSTALLATION_DERATING: Total derating factor zero or invalid.
 * - INCOMPLETE_INPUT: Missing required project data (e.g. source transformer or invalid input).
 * - MULTIPLE_CONSTRAINTS: Multiple distinct checks failed simultaneously.
 *
 * NOT_SUPPORTED_IN_PHASE_6 (No authoritative producer in single-circuit cable sizing result path):
 * - SHORT_CIRCUIT_WITHSTAND: Requires full short-circuit system network solver (Phase 8).
 * - DEVICE_COMPATIBILITY: Requires full protective device coordination engine (Phase 8).
 * - MANUAL_OVERRIDE: Maintained as selectionSource attribute, never hides engineering failure.
 */
enum class GoverningCriterion {
    NONE,
    AMPACITY,
    VOLTAGE_DROP,
    CUMULATIVE_VOLTAGE_DROP,
    SHORT_CIRCUIT_WITHSTAND,
    DEVICE_COMPATIBILITY,
    CATALOG_AVAILABILITY,
    INSTALLATION_DERATING,
    MANUAL_OVERRIDE,
    MULTIPLE_CONSTRAINTS,
    INCOMPLETE_INPUT,
    INDETERMINATE
}

enum class CheckStatus {
    PASS,
    WARNING,
    FAIL,
    INCOMPLETE
}

enum class CandidateSortPolicy {
    INPUT_ORDER,
    SIZE_ASCENDING,
    UTILIZATION_ASCENDING
}

@JsonClass(generateAdapter = true)
data class EngineeringCheckItemDto(
    val checkName: String,
    val status: CheckStatus,
    val actualValue: EngineeringValue? = null,
    val limitValue: EngineeringValue? = null,
    val actualDisplay: String = "",
    val limitDisplay: String = "",
    val unit: String = "",
    val message: String
)

@JsonClass(generateAdapter = true)
data class CableSizingDetailsDto(
    val Ib_A: Double,
    val deviceSize_A: Double,
    val deviceType: String,
    val cTotalDerating: Double,
    val iRequired_perCable_A: Double,
    val productCode: String,
    val cableSize_mm2: String,
    val ratedI_table_A: Double,
    val capacity_derated_Iz_A: Double,
    val utilizationPct: Double,
    val neutral_mm2: String,
    val earthCPC_mm2: String,
    val voltDrop_V: Double,
    val vdPct: Double,
    val cumulativeVDpct: Double,
    val status: String,
    val failReason: String = "",
    val cableMaterial: String = "Cu",
    val insulation: String = "XLPE",
    val kFactor: Double = 143.0,
    val deviceBreakingKA: Double = 36.0,
    val cableOD_mm: Double = 0.0,
    val earthOD_mm: Double = 0.0,
    val rUsed: Double = 0.0,
    val xUsed: Double = 0.0,
    val clearingTime_s: Double = 0.1,
    val odSourceMfg: String = "",
    val isODApproximated: Boolean = false
)

// -----------------------------------------------------------------------------
// 1. calculate_cable_sizing
// -----------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class CalculateCableSizingRequest(
    val projectId: String,
    val circuitId: String,
    val forceRecalculate: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CableSizingExecutionResult(
    val projectId: String,
    val circuitId: String,
    val circuitName: String,
    val projectRevision: Long,
    val freshnessState: String,
    val isRecalculated: Boolean,
    val selectionSource: String = "AUTO",
    val overallStatus: CheckStatus,
    val governingCriterion: GoverningCriterion,
    val governingReason: String,
    val details: CableSizingDetailsDto,
    val checks: List<EngineeringCheckItemDto> = emptyList()
)

// -----------------------------------------------------------------------------
// 2. evaluate_cable_candidate
// -----------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class EvaluateCableCandidateRequest(
    val projectId: String,
    val circuitId: String,
    val candidateSizeMm2: Double? = null,
    val candidateSizeText: String? = null,
    val candidateMaterial: String? = null,
    val candidateParallelRuns: Int? = null,
    val candidateProductCode: String? = null,
    val candidateDeviceSizeA: Double? = null,
    val candidateDeviceType: String? = null
)

@JsonClass(generateAdapter = true)
data class CableCandidateEvaluationResult(
    val projectId: String,
    val circuitId: String,
    val circuitName: String,
    val candidateSizeMm2: Double,
    val candidateSizeFormatted: String,
    val candidateMaterial: String,
    val parallelRuns: Int,
    val selectionSource: String = "AUTO",
    val overallStatus: CheckStatus,
    val governingCriterion: GoverningCriterion,
    val governingReason: String,
    val details: CableSizingDetailsDto,
    val checks: List<EngineeringCheckItemDto> = emptyList()
)

// -----------------------------------------------------------------------------
// 3. compare_cable_candidates
// -----------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class CableCandidateSpecDto(
    val candidateId: String? = null,
    val sizeMm2: Double? = null,
    val sizeText: String? = null,
    val material: String? = null,
    val parallelRuns: Int? = null,
    val deviceSizeA: Double? = null,
    val deviceType: String? = null
)

@JsonClass(generateAdapter = true)
data class CompareCableCandidatesRequest(
    val projectId: String,
    val circuitId: String,
    val candidates: List<CableCandidateSpecDto>,
    val sortPolicy: CandidateSortPolicy = CandidateSortPolicy.INPUT_ORDER
)

@JsonClass(generateAdapter = true)
data class CandidateComparisonItemDto(
    val candidateId: String,
    val sizeMm2: Double,
    val sizeFormatted: String,
    val material: String,
    val parallelRuns: Int,
    val overallStatus: CheckStatus,
    val utilizationPct: Double,
    val voltDropPct: Double,
    val cumulativeVdPct: Double,
    val governingCriterion: GoverningCriterion,
    val governingReason: String,
    val checks: List<EngineeringCheckItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CableCandidatesComparisonResult(
    val projectId: String,
    val circuitId: String,
    val circuitName: String,
    val currentAuthoritativeSize: String?,
    val evaluatedCount: Int,
    val passingCount: Int,
    val failingCount: Int,
    val smallestPassingCandidateId: String?,
    val lowestUtilizationPassingCandidateId: String?,
    val noRecommendationReason: String?,
    val recommendedCandidateId: String?,
    val sortPolicy: CandidateSortPolicy,
    val candidateResults: List<CandidateComparisonItemDto>
)

// -----------------------------------------------------------------------------
// 4. run_what_if
// -----------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class RunCableWhatIfRequest(
    val projectId: String,
    val circuitId: String,
    val overrides: CableWhatIfOverridesDto
)

@JsonClass(generateAdapter = true)
data class CableWhatIfOverridesDto(
    val length_m: Double? = null,
    val loadKVA: Double? = null,
    val pf: Double? = null,
    val voltageV: Int? = null,
    val maxVDpct: Double? = null,
    val cableType: String? = null,
    val manufacturer: String? = null,
    val installation: String? = null,
    val parallelRuns: Int? = null,
    val airTempC: Int? = null,
    val gndTempC: Int? = null,
    val burialDepth_m: Double? = null,
    val soilRes: Int? = null,
    val formation: String? = null,
    val spacing: String? = null,
    val noCircuits: Int? = null,
    val overrideCableSize: String? = null,
    val overrideDeviceSize: Double? = null,
    val overrideDeviceType: String? = null
)

@JsonClass(generateAdapter = true)
data class WhatIfFieldDiffDto(
    val fieldName: String,
    val baselineValue: String,
    val scenarioValue: String
)

@JsonClass(generateAdapter = true)
data class CableWhatIfResult(
    val projectId: String,
    val circuitId: String,
    val circuitName: String,
    val sourceRevision: Long,
    val baselineSize: String?,
    val scenarioSize: String,
    val baselineStatus: CheckStatus?,
    val scenarioStatus: CheckStatus,
    val baselineGoverningCriterion: GoverningCriterion?,
    val scenarioGoverningCriterion: GoverningCriterion,
    val baselineFreshness: String,
    val comparisonValidity: String,
    val inputDifferences: List<WhatIfFieldDiffDto>,
    val scenarioDetails: CableSizingDetailsDto,
    val scenarioChecks: List<EngineeringCheckItemDto>
)
