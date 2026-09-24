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
class TrustedProposalApprovalGateway internal constructor(
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

        // 3. Project Identity Verification (Proposal vs Approval Surface)
        if (proposal.projectId != ctx.projectId) {
            return TrustedApprovalGateResult.ValidationFailed(
                reason = "Proposal project ID '${proposal.projectId}' does not match active project ID '${ctx.projectId}'.",
                code = TrustedMutationRejectionCode.WRONG_PROJECT
            )
        }

        // 4. Optimistic Revision CAS Pre-Validation (Caller-Supplied Approval Surface)
        if (proposal.baseProjectRevision != ctx.liveRevision) {
            return TrustedApprovalGateResult.StaleProposal(
                baseRevision = proposal.baseProjectRevision,
                liveRevision = ctx.liveRevision,
                reason = "Proposal base revision ${proposal.baseProjectRevision} is stale compared to live revision ${ctx.liveRevision}."
            )
        }

        // 5. Pure Policy Revalidation (Envelope, Operation, Patch Shape, Bounds, Expiry)
        val policyResult = policyValidator.validateCableProposal(proposal, now)
        if (policyResult is ProposalPolicyResult.Failure) {
            val errorMsg = policyResult.errors.joinToString("; ") { it.message }
            return TrustedApprovalGateResult.ValidationFailed(
                reason = "Proposal policy validation failed: $errorMsg",
                code = TrustedMutationRejectionCode.INVALID_PATCH
            )
        }
        val validatedIntent = (policyResult as ProposalPolicyResult.Success).validatedIntent

        // 6. Compute Digest & Semantic Key from the reviewed proposal artifact
        val computedDigest = digestEncoder.computeDigest(proposal)
        val computedSemanticKey = semanticKeyEncoder.computeSemanticKey(proposal)

        // 7. Authoritative Approval-Time Context Re-Resolution (Fail-Closed)
        //    The caller-supplied project context is never the authority for project identity,
        //    entity identity, or revision. These are re-read from the durable store, so a stale,
        //    forged, or self-fulfilling projection cannot authorise a write.
        val authoritative = durableWorkflow.resolveAuthoritativeApprovalContext(
            projectId = proposal.projectId,
            entityType = proposal.targetEntityType,
            entityId = proposal.targetEntityId
        ) ?: return TrustedApprovalGateResult.ValidationFailed(
            reason = "Project '${proposal.projectId}' does not exist in the authoritative store.",
            code = TrustedMutationRejectionCode.WRONG_PROJECT
        )

        // 7a. Domain boundary: a proposal cannot change its mutation domain at approval time.
        if (proposal.targetEntityType != DurableMutationWorkflow.CIRCUIT_ENTITY_TYPE) {
            return TrustedApprovalGateResult.ValidationFailed(
                reason = "Proposal target domain '${proposal.targetEntityType}' is not an approved mutation domain.",
                code = TrustedMutationRejectionCode.WRONG_ENTITY
            )
        }

        // 7b. Cross-project boundary: the target entity must live inside the proposal project.
        if (authoritative.targetEntityProjectId != null &&
            authoritative.targetEntityProjectId != proposal.projectId
        ) {
            return TrustedApprovalGateResult.ValidationFailed(
                reason = "Target entity '${proposal.targetEntityId}' does not belong to project '${proposal.projectId}'.",
                code = TrustedMutationRejectionCode.WRONG_PROJECT
            )
        }

        // 7c. Entity boundary: the authoritative target entity must exist.
        if (!authoritative.entityFound) {
            return TrustedApprovalGateResult.ValidationFailed(
                reason = "Target entity 'CIRCUIT:${proposal.targetEntityId}' was not found in project '${proposal.projectId}'.",
                code = TrustedMutationRejectionCode.WRONG_ENTITY
            )
        }

        // 8. Duplicate-Execution / Idempotency Guard (Authoritative Signature Lookup)
        //    The same approved semantic mutation must never be executed twice. A committed receipt
        //    for the same semantic signature is returned instead of creating a second mutation,
        //    a second receipt, or a second revision advance.
        val existingReceipt = durableWorkflow.findExistingReceipt(
            projectId = proposal.projectId,
            proposalSemanticKey = computedSemanticKey
        )
        if (existingReceipt != null) {
            val appliedChanges = try {
                ReceiptPayloadCodec.decode(existingReceipt.appliedChangesJson)
            } catch (e: Exception) {
                emptyList()
            }
            return TrustedApprovalGateResult.Success(
                outcome = DurableMutationOutcome.ExistingReceiptReturned(
                    receipt = existingReceipt,
                    appliedChanges = appliedChanges,
                    message = "DUPLICATE_EXECUTION: this approved semantic change is already committed; " +
                        "the existing durable receipt is authoritative."
                ),
                approvalId = existingReceipt.approvalId,
                userActionId = request.userActionId
            )
        }

        // 9. Authoritative Revision CAS Re-Validation (Durable Live Revision)
        if (authoritative.liveRevision != proposal.baseProjectRevision) {
            return TrustedApprovalGateResult.StaleProposal(
                baseRevision = proposal.baseProjectRevision,
                liveRevision = authoritative.liveRevision,
                reason = "Proposal base revision ${proposal.baseProjectRevision} is stale compared to the " +
                    "authoritative live revision ${authoritative.liveRevision}."
            )
        }

        // 10. Single-Use Grant Issuance or Reuse of an Equivalent Active Grant
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
                // If unique index on activeProposalSemanticKey collides concurrently:
                // 1) Another thread holds an active grant: reuse it.
                // 2) Another thread already consumed the active grant and committed: return existing receipt.
                val active = approvalGrantDao.getActiveGrantBySemanticKey(computedSemanticKey)
                if (active != null) {
                    active
                } else {
                    val committedReceipt = durableWorkflow.findExistingReceipt(
                        projectId = proposal.projectId,
                        proposalSemanticKey = computedSemanticKey
                    )
                    if (committedReceipt != null) {
                        val appliedChanges = try {
                            ReceiptPayloadCodec.decode(committedReceipt.appliedChangesJson)
                        } catch (ex: Exception) {
                            emptyList()
                        }
                        return TrustedApprovalGateResult.Success(
                            outcome = DurableMutationOutcome.ExistingReceiptReturned(
                                receipt = committedReceipt,
                                appliedChanges = appliedChanges,
                                message = "DUPLICATE_EXECUTION: this approved semantic change was committed concurrently; " +
                                    "the existing durable receipt is authoritative."
                            ),
                            approvalId = committedReceipt.approvalId,
                            userActionId = request.userActionId
                        )
                    }
                    return TrustedApprovalGateResult.Failure(
                        errorCode = "GRANT_INSERTION_FAILED",
                        message = "Unable to record durable approval grant: ${e.message}"
                    )
                }
            }
        }

        // 11. Execute Internal Durable Mutation Workflow (deterministic validated patch only;
        //     the model never selects a field, a value, or an entity at this point).
        val workflowOutcome = durableWorkflow.execute(
            DurableMutationRequest(
                intent = validatedIntent,
                approvalId = grantToUse.approvalId,
                executionId = request.userActionId,
                expectedCurrentValues = request.expectedCurrentValues
            )
        )

        return TrustedApprovalGateResult.Success(
            outcome = workflowOutcome,
            approvalId = grantToUse.approvalId,
            userActionId = request.userActionId
        )
    }
}
