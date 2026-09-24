package com.example.ai.approval

import com.example.ai.proposal.CableEngineeringProposal
import com.example.ai.proposal.DurableMutationOutcome
import com.example.ai.runtime.BoundedProjectContext
import java.util.UUID

/**
 * Request container for trusted approval gate invocation.
 *
 * Trusted, structured approval input. Every field is produced by the explicit UI approval action
 * and the application layer that owns the reviewed proposal. The conversational model cannot
 * construct, rewrite, or supply any of these values.
 *
 * @param expectedCurrentValues Persisted values the reviewed proposal was prepared against,
 *   keyed by allowlisted patch field name. When supplied, the durable workflow re-reads the
 *   authoritative persisted value inside the mutation transaction and fails closed
 *   ([TrustedMutationRejectionCode.CURRENT_VALUE_CHANGED]) on any mismatch. An empty map means the
 *   proposal carries no recorded precondition; revision CAS and the no-op guard still apply.
 */
data class TrustedApprovalRequest(
    val proposal: CableEngineeringProposal,
    val projectContext: BoundedProjectContext,
    val userActionId: String,
    val approvalId: String = UUID.randomUUID().toString(),
    val proposalValidityDurationMs: Long = 300_000L, // 5 minutes
    val expectedCurrentValues: Map<String, String> = emptyMap()
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
        val reason: String,
        val code: TrustedMutationRejectionCode = TrustedMutationRejectionCode.STALE_PROPOSAL
    ) : TrustedApprovalGateResult()

    data class ValidationFailed(
        val reason: String,
        val code: TrustedMutationRejectionCode? = null
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
