package com.example.ai.approval

import com.example.ai.proposal.CableEngineeringProposal
import com.example.ai.proposal.DurableMutationOutcome
import com.example.ai.runtime.BoundedProjectContext
import java.util.UUID

/**
 * Request container for trusted approval gate invocation.
 */
data class TrustedApprovalRequest(
    val proposal: CableEngineeringProposal,
    val projectContext: BoundedProjectContext,
    val userActionId: String,
    val approvalId: String = UUID.randomUUID().toString(),
    val proposalValidityDurationMs: Long = 300_000L // 5 minutes
)

/**
 * Sealed result type returned by [TrustedProposalApprovalGateway].
 */
sealed class TrustedApprovalGateResult {
    data class Success(
        val outcome: DurableMutationOutcome,
        val approvalId: String,
        val userActionId: String
    ) : TrustedApprovalGateResult()

    data class StaleProposal(
        val baseRevision: Long,
        val liveRevision: Long,
        val reason: String
    ) : TrustedApprovalGateResult()

    data class ValidationFailed(
        val reason: String
    ) : TrustedApprovalGateResult()

    data class PrincipalMissing(
        val reason: String
    ) : TrustedApprovalGateResult()

    data class DuplicateActiveApproval(
        val existingApprovalId: String,
        val semanticKey: String
    ) : TrustedApprovalGateResult()

    data class Failure(
        val errorCode: String,
        val message: String
    ) : TrustedApprovalGateResult()
}
