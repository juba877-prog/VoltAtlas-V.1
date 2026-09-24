package com.example.ai.ui

import com.example.ai.proposal.CableEngineeringProposal

/**
 * State machine governing the explicit approval confirmation, submission, and post-commit receipt display.
 */
sealed interface ApprovalExecutionUiState {
    val isCommitted: Boolean
        get() = this is Completed || this is CommittedPartial || this is CommittedRevalidationFailed || this is AlreadyApplied

    val canDismiss: Boolean
        get() = this !is SubmittingApproval

    data object Idle : ApprovalExecutionUiState

    data class Confirming(
        val proposal: CableEngineeringProposal,
        val reviewModel: ProposalReviewUiModel
    ) : ApprovalExecutionUiState

    data class SubmittingApproval(
        val proposalId: String,
        val userActionId: String
    ) : ApprovalExecutionUiState

    data class Completed(
        val receiptId: String,
        val revisionBefore: Long,
        val revisionAfter: Long,
        val appliedChanges: List<ProposalFieldChangeUiModel>,
        val revalidationStatus: String
    ) : ApprovalExecutionUiState

    data class CommittedPartial(
        val receiptId: String,
        val revisionBefore: Long,
        val revisionAfter: Long,
        val appliedChanges: List<ProposalFieldChangeUiModel>,
        val revalidationNote: String
    ) : ApprovalExecutionUiState

    data class CommittedRevalidationFailed(
        val receiptId: String,
        val revisionBefore: Long,
        val revisionAfter: Long,
        val appliedChanges: List<ProposalFieldChangeUiModel>,
        val failureReason: String
    ) : ApprovalExecutionUiState

    data class AlreadyApplied(
        val receiptId: String,
        val revisionAfter: Long,
        val appliedChanges: List<ProposalFieldChangeUiModel>
    ) : ApprovalExecutionUiState

    data class StaleBeforeApproval(
        val reason: String
    ) : ApprovalExecutionUiState

    /**
     * The approved patch is already the persisted state. No mutation, no receipt, and no revision
     * advance occurred; the explicit "apply" request requires nothing further.
     */
    data class NoChangeRequired(
        val safeMessage: String
    ) : ApprovalExecutionUiState

    data class FailedPreCommit(
        val safeMessage: String
    ) : ApprovalExecutionUiState
}
