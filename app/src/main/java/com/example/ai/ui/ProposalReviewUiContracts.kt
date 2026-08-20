package com.example.ai.ui

import com.example.ai.runtime.ProvenanceLabel

/**
 * Lifecycle state of the dedicated proposal review experience in Phase 7F-4.
 */
sealed interface ProposalReviewUiState {
    data object Hidden : ProposalReviewUiState
    data object Loading : ProposalReviewUiState
    data class Ready(val model: ProposalReviewUiModel) : ProposalReviewUiState
    data class Stale(val model: ProposalReviewUiModel, val reason: String) : ProposalReviewUiState
    data class Invalid(val safeMessage: String) : ProposalReviewUiState
    data class Cancelled(val proposalId: String) : ProposalReviewUiState
}

/**
 * Action type for individual proposed field changes.
 */
enum class ProposalChangeActionUi {
    SET,
    CLEAR_TO_AUTOMATIC
}

/**
 * Engineering significance level of a field change.
 */
enum class ProposalChangeSignificanceUi {
    CRITICAL,
    STANDARD,
    INFORMATIONAL
}

/**
 * Typed display value with units and automatic flags.
 */
data class EngineeringDisplayValueUi(
    val rawValue: String,
    val formattedText: String,
    val unit: String? = null,
    val isAutomatic: Boolean = false
)

/**
 * Exact Before/After diff item for a single proposed field modification.
 * UNCHANGED fields are strictly omitted.
 */
data class ProposalFieldChangeUiModel(
    val fieldKey: String,
    val label: String,
    val action: ProposalChangeActionUi,
    val currentValue: EngineeringDisplayValueUi?,
    val proposedValue: EngineeringDisplayValueUi?,
    val unit: String? = null,
    val significance: ProposalChangeSignificanceUi = ProposalChangeSignificanceUi.STANDARD
)

/**
 * Comprehensive review model presenting a validated engineering proposal without execution.
 */
data class ProposalReviewUiModel(
    val proposalId: String,
    val proposalArtifactDigest: String,
    val proposalSemanticKey: String? = null,
    val projectId: String,
    val projectLabel: String,
    val targetEntityType: String,
    val targetEntityId: String,
    val targetEntityLabel: String,
    val operation: String,
    val baseProjectRevision: Long,
    val currentLiveRevision: Long,
    val isStale: Boolean,
    val fieldChanges: List<ProposalFieldChangeUiModel>,
    val governingCriterion: String?,
    val evidenceSummary: String,
    val checkSummaries: List<EngineeringMetricUiModel> = emptyList(),
    val affectedDomains: List<String> = emptyList(),
    val expectedPostApplyBehavior: String,
    val provenance: ProvenanceLabel = ProvenanceLabel.PROPOSED
)

/**
 * Available UI action types in the Proposal Review container.
 */
enum class ProposalReviewActionType {
    EDIT_PROPOSAL,
    CANCEL_PROPOSAL_REVIEW,
    CONTINUE_TO_APPROVAL_NON_EXECUTABLE,
    RETURN_TO_COMPARISON,
    OPEN_TARGET_ENTITY
}

/**
 * Non-executable UI action button model for Proposal Review.
 */
data class ProposalReviewActionUiModel(
    val actionType: ProposalReviewActionType,
    val label: String,
    val isPrimary: Boolean = false,
    val isEnabled: Boolean = true
)
