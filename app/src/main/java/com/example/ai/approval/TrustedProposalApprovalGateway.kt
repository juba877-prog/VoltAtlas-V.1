package com.example.ai.approval

import com.example.ai.proposal.*
import com.example.data.ai.ApprovalGrantDao
import com.example.data.ai.ApprovalGrantEntity
import java.util.UUID

/**
 * Dedicated, authoritative application gateway connecting explicit UI approval actions
 * to the internal [DurableMutationWorkflow].
 *
 * Invariants:
 * - Application Scoped: Never registered as an LLM tool.
 * - Trusted Provenance: Requires non-blank [userActionId] generated from explicit UI confirmation.
 * - Pre-Issuance Revalidation: Verifies proposal policy, digests, semantic keys, and optimistic revision CAS.
 * - Single-Use: Inserts a single durable [ApprovalGrantEntity] before invoking [DurableMutationWorkflow].
 */
internal class TrustedProposalApprovalGateway(
    private val approvalGrantDao: ApprovalGrantDao,
    private val durableWorkflow: DurableMutationWorkflow,
    private val policyValidator: ProposalPolicyValidator = DefaultProposalPolicyValidator(),
    private val digestEncoder: ProposalDigestEncoder = DefaultProposalDigestEncoder(),
    private val semanticKeyEncoder: ProposalSemanticKeyEncoder = DefaultProposalSemanticKeyEncoder(),
    private val principalProvider: TrustedPrincipalProvider = DefaultTrustedPrincipalProvider(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun processApprovalAndExecute(
        request: TrustedApprovalRequest
    ): TrustedApprovalGateResult {
        val now = clock()
        val proposal = request.proposal
        val ctx = request.projectContext

        // 1. Validate User Action ID Provenance
        if (request.userActionId.isBlank()) {
            return TrustedApprovalGateResult.ValidationFailed("User action ID is missing or blank.")
        }

        // 2. Validate Trusted Principal Identity (Fail-Closed)
        val principalId = principalProvider.getTrustedPrincipalId()
        if (principalId.isNullOrBlank()) {
            return TrustedApprovalGateResult.PrincipalMissing("Authenticated engineer principal could not be resolved.")
        }

        // 3. Project Identity Verification
        if (proposal.projectId != ctx.projectId) {
            return TrustedApprovalGateResult.ValidationFailed(
                "Proposal project ID '${proposal.projectId}' does not match active project ID '${ctx.projectId}'."
            )
        }

        // 4. Optimistic Revision CAS Pre-Validation
        if (proposal.baseProjectRevision != ctx.liveRevision) {
            return TrustedApprovalGateResult.StaleProposal(
                baseRevision = proposal.baseProjectRevision,
                liveRevision = ctx.liveRevision,
                reason = "Proposal base revision ${proposal.baseProjectRevision} is stale compared to live revision ${ctx.liveRevision}."
            )
        }

        // 5. Pure Policy & Intent Revalidation
        val policyResult = policyValidator.validateCableProposal(proposal, now)
        if (policyResult is ProposalPolicyResult.Failure) {
            val errorMsg = policyResult.errors.joinToString("; ") { it.message }
            return TrustedApprovalGateResult.ValidationFailed("Proposal policy validation failed: $errorMsg")
        }
        val validatedIntent = (policyResult as ProposalPolicyResult.Success).validatedIntent

        // 6. Compute Digest & Semantic Key
        val computedDigest = digestEncoder.computeDigest(proposal)
        val computedSemanticKey = semanticKeyEncoder.computeSemanticKey(proposal)

        // 7. Check for Active Approval Grant Conflict
        val existingActiveGrant = approvalGrantDao.getActiveGrantBySemanticKey(computedSemanticKey)
        val grantToUse = if (existingActiveGrant != null && existingActiveGrant.expiresAt > now && existingActiveGrant.status == ApprovalStatus.ISSUED) {
            existingActiveGrant
        } else {
            val issuedGrant = ApprovalGrantEntity.createIssued(
                approvalId = request.approvalId,
                proposalId = proposal.proposalId,
                proposalDigest = computedDigest,
                proposalSemanticKey = computedSemanticKey,
                projectId = proposal.projectId,
                targetEntityType = proposal.targetEntityType,
                targetEntityId = proposal.targetEntityId,
                baseProjectRevision = proposal.baseProjectRevision,
                userActionId = request.userActionId,
                approvedBy = principalId,
                issuedAt = now,
                expiresAt = now + request.proposalValidityDurationMs
            )
            try {
                approvalGrantDao.insertGrant(issuedGrant)
                issuedGrant
            } catch (e: Exception) {
                // If unique index on activeProposalSemanticKey collides concurrently, load active
                approvalGrantDao.getActiveGrantBySemanticKey(computedSemanticKey)
                    ?: return TrustedApprovalGateResult.Failure(
                        errorCode = "GRANT_INSERTION_FAILED",
                        message = "Unable to record durable approval grant: ${e.message}"
                    )
            }
        }

        // 8. Execute Internal Durable Mutation Workflow
        val workflowOutcome = durableWorkflow.execute(
            DurableMutationRequest(
                intent = validatedIntent,
                approvalId = grantToUse.approvalId,
                executionId = request.userActionId
            )
        )

        return TrustedApprovalGateResult.Success(
            outcome = workflowOutcome,
            approvalId = grantToUse.approvalId,
            userActionId = request.userActionId
        )
    }
}
