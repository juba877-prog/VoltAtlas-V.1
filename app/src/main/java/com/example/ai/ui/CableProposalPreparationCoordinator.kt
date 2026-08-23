package com.example.ai.ui

import com.example.ai.conversation.ConversationAction
import com.example.ai.proposal.CableCircuitPatch
import com.example.ai.proposal.CableEngineeringProposal
import com.example.ai.proposal.DefaultProposalDigestEncoder
import com.example.ai.proposal.DefaultProposalPolicyValidator
import com.example.ai.proposal.DefaultProposalSemanticKeyEncoder
import com.example.ai.proposal.EngineeringProposal
import com.example.ai.proposal.PatchValue
import com.example.ai.proposal.ProposalOperation
import com.example.ai.proposal.ProposalPolicyResult
import com.example.ai.runtime.BoundedProjectContext
import com.example.data.CircuitDao
import com.example.data.ProjectDao
import java.util.UUID

/** Creates a reviewable cable proposal only from live persisted state and structured action data. */
class CableProposalPreparationCoordinator(
    private val projectDao: ProjectDao,
    private val circuitDao: CircuitDao,
    private val policyValidator: DefaultProposalPolicyValidator = DefaultProposalPolicyValidator(),
    private val digestEncoder: DefaultProposalDigestEncoder = DefaultProposalDigestEncoder(),
    private val semanticKeyEncoder: DefaultProposalSemanticKeyEncoder = DefaultProposalSemanticKeyEncoder(),
    private val proposalIdProvider: () -> String = { "PROP-" + UUID.randomUUID().toString() }
) {
    sealed interface Result {
        data class Ready(val proposal: CableEngineeringProposal, val review: ProposalReviewUiModel) : Result
        data class SafeError(val message: String) : Result
    }

    suspend fun prepare(
        action: ConversationAction,
        projectContext: BoundedProjectContext,
        decisionId: String
    ): Result {
        val projectId = projectContext.projectId.takeUnless { it.isBlank() }
            ?: return Result.SafeError("A live project is required before creating a proposal.")
        val circuitId = projectContext.activeEntityId?.toIntOrNull()
            ?: return Result.SafeError("Select a circuit before creating a cable proposal.")
        if (projectContext.activeEntityType != "CIRCUIT") {
            return Result.SafeError("Cable proposals are available only for a selected circuit.")
        }
        val proposedSize = action.candidateParameters["overrideCableSize"]?.trim()
        if (proposedSize.isNullOrBlank() || proposedSize.toDoubleOrNull()?.takeIf { it > 0.0 } == null) {
            return Result.SafeError("A validated positive cable-size candidate is required before creating a proposal.")
        }

        val revision = projectDao.getRevision(projectId)
            ?: return Result.SafeError("The active project could not be resolved.")
        val circuit = circuitDao.getCircuitByIdAndProject(projectId, circuitId)
            ?: return Result.SafeError("The selected circuit is no longer available in the active project.")

        val proposal = EngineeringProposal(
            proposalId = proposalIdProvider(),
            projectId = projectId,
            baseProjectRevision = revision,
            targetEntityType = "CIRCUIT",
            targetEntityId = circuit.id.toString(),
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = CableCircuitPatch(overrideCableSize = PatchValue.set(proposedSize)),
            rationale = "Structured cable-size candidate prepared for explicit engineer review.",
            sourceToolName = "ConversationAction.CREATE_PROPOSAL",
            sourceRequestId = decisionId
        )
        if (policyValidator.validateCableProposal(proposal) !is ProposalPolicyResult.Success) {
            return Result.SafeError("The proposed cable change failed policy validation.")
        }

        val digest = digestEncoder.computeDigest(proposal)
        val semanticKey = semanticKeyEncoder.computeSemanticKey(proposal)
        val reviewResult = ProposalReviewMapper.mapProposalToReviewUiState(
            proposal = proposal,
            currentValues = mapOf(
                "overrideCableSize" to (circuit.overrideCableSize ?: "Automatic"),
                "cableType" to circuit.cableType,
                "manufacturer" to circuit.manufacturer,
                "installation" to circuit.installation,
                "parallelRuns" to circuit.parallelRuns.toString(),
                "lengthM" to circuit.length_m.toString()
            ),
            projectContext = projectContext.copy(liveRevision = revision),
            proposalDigest = digest,
            proposalSemanticKey = semanticKey
        )
        return when (reviewResult) {
            is ProposalReviewUiState.Ready -> Result.Ready(proposal, reviewResult.model)
            is ProposalReviewUiState.Stale -> Result.SafeError(reviewResult.reason)
            is ProposalReviewUiState.Invalid -> Result.SafeError(reviewResult.safeMessage)
            else -> Result.SafeError("Proposal review could not be prepared.")
        }
    }
}
