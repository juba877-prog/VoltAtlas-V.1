package com.example.ui.screens.circuits

import com.example.ui.designsystem.components.DsStatusVariant

/**
 * Presentation-only status representation for Circuit list items and summary badges.
 * Derived exhaustively and NON-LOSSILY from domain
 * [com.example.engine.SizingResultStatus]. Cards preserve the exact typed
 * label; the summary aggregates non-PASS/non-FAIL results into Standby.
 */
enum class CircuitUiStatus(
    val displayLabel: String,
    val dsVariant: DsStatusVariant
) {
    PASS("PASS", DsStatusVariant.Pass),
    WARNING("WARNING", DsStatusVariant.Warning),
    FAIL("FAIL", DsStatusVariant.Fail),
    INCOMPLETE("INCOMPLETE", DsStatusVariant.Neutral),
    ERROR("ERROR", DsStatusVariant.Neutral),
    STANDBY("STANDBY", DsStatusVariant.Neutral);

    fun toDsStatusVariant(): DsStatusVariant = dsVariant
}

/**
 * Presentation sort orders supported by the Circuits list screen.
 * All sort operations enforce circuit ID as the final stable tie-breaker.
 */
enum class CircuitSortOrder(val displayLabel: String) {
    SOURCE_ORDER("Order"),
    NAME_ASC("Name"),
    LOAD_DESC("Load"),
    VD_DESC("Voltage Drop"),
    STATUS("Status")
}

/**
 * Predicate-based Level filter options for the Circuits screen.
 * These are independent filter views, not mutually exclusive hierarchy tiers.
 */
enum class CircuitLevelFilter(val displayLabel: String) {
    ALL("All"),
    MAIN_FEEDERS("Main Feeders"),
    DOWNSTREAM("Downstream"),
    PANELS("Panels")
}

/**
 * Typed Status filter options for the Circuits screen.
 * Strictly Pass, Fail, and Standby.
 */
enum class CircuitStatusFilter(val displayLabel: String) {
    ALL("All"),
    PASS("Pass"),
    FAIL("Fail"),
    STANDBY("Standby")
}

/**
 * Immutable presentation model for a single Circuit card in the list.
 * Contains pre-formatted display strings and raw typed fields for deterministic sorting and filtering.
 */
data class CircuitCardUiModel(
    val id: Int,
    val circuitName: String,
    val isScheduleEligible: Boolean,
    val cableSizeDisplay: String,
    val status: CircuitUiStatus,
    val breadcrumbDisplay: String,
    val loadDisplay: String,
    val ibDisplay: String,
    val inDisplay: String,
    val vdDisplay: String,
    val vdLimitDisplay: String,
    val loadingDisplay: String,
    val izDisplay: String,
    val isc3phDisplay: String,
    val icuDisplay: String,
    val footerSpecDisplay: String,
    // Raw typed fields for filtering and sorting
    val rawLoadKVA: Double,
    val rawCumulativeVDpct: Double?,
    val rawUtilizationPct: Double?,
    val rawFedFrom: String,
    val rawParentId: Int?,
    val rawPanelType: String?,
    val rawCableType: String,
    val rawLoadType: String,
    val phasesDisplay: String = "3P+N",
    val isVdOk: Boolean = true,
    val isLoadingOk: Boolean = true,
    val isIscOk: Boolean = true
)

/**
 * Summary metrics model evaluated across all project circuits (whole-project collection).
 * Exactly 5 project health metrics: Total, Pass, Fail, Standby, Max Vd, plus Warning count.
 */
data class CircuitsSummaryUiModel(
    val totalCount: Int,
    val passCount: Int,
    val failCount: Int,
    val standbyCount: Int,
    val maxVdDisplay: String,
    val rawMaxVdPct: Double? = null,
    val warningCount: Int = 0
)
