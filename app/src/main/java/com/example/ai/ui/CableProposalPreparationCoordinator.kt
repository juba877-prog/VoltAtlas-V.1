package com.example.ai.ui

import com.example.ai.contracts.EngineeringUnit
import com.example.ai.contracts.EngineeringValue
import com.example.ai.contracts.ToolStatus
import com.example.ai.conversation.ConversationAction
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.proposal.CableCircuitPatch
import com.example.ai.proposal.CableEngineeringProposal
import com.example.ai.proposal.DefaultProposalDigestEncoder
import com.example.ai.proposal.DefaultProposalPolicyValidator
import com.example.ai.proposal.DefaultProposalSemanticKeyEncoder
import com.example.ai.proposal.DefaultProposalValidator
import com.example.ai.proposal.EngineeringProposal
import com.example.ai.proposal.PatchValue
import com.example.ai.proposal.ProposalEvidenceDto
import com.example.ai.proposal.ProposalOperation
import com.example.ai.proposal.ProposalPolicyResult
import com.example.ai.proposal.ProposalValidator
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.cable.CableCandidateEvaluationResult
import com.example.ai.tools.cable.CableCandidatesComparisonResult
import com.example.ai.tools.cable.CableSizingExecutionResult
import com.example.ai.tools.cable.CableWhatIfResult
import com.example.ai.tools.cable.CheckStatus
import com.example.data.CircuitDao
import com.example.data.ProjectDao
import java.util.UUID

/**
 * Creates a reviewable cable engineering proposal only from live persisted state,
 * verified engineering evidence, and structured candidate data (Gate 9).
 *
 * Invariants:
 * - Read-Only CAS Basis: Queries Room/project state only; performs zero writes, zero revision increments.
 * - Evidence Lineage: Requires non-empty, current, domain-compatible evidence matching the active project and entity.
 * - Authorized Candidate: Prohibits invented or arbitrary sizes; candidate must be verified by calculation/what-if/comparison.
 * - Distinct Values: Verifies that proposed value modifies the current persisted state (no-op proposals rejected).
 * - Ambiguity Protection: Fails closed to AMBIGUOUS_SELECTION if multiple candidates exist and user gave no specific size.
 * - Deterministic Validation & Encodings: Validates envelope and patch, computing canonical SHA-256 digest and semantic key.
 */
class CableProposalPreparationCoordinator(
    private val projectDao: ProjectDao,
    private val circuitDao: CircuitDao,
    private val policyValidator: DefaultProposalPolicyValidator = DefaultProposalPolicyValidator(),
    private val contractValidator: ProposalValidator = DefaultProposalValidator(),
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
        decisionId: String,
        evidence: List<EngineeringEvidenceRecord> = emptyList(),
        taskState: ConversationTaskState? = null
    ): Result {
        val projectId = projectContext.projectId.takeUnless { it.isBlank() }
            ?: return Result.SafeError("A live project is required before creating a proposal.")
        val circuitId = projectContext.activeEntityId?.toIntOrNull()
            ?: return Result.SafeError("Select a circuit before creating a cable proposal.")
        if (projectContext.activeEntityType != "CIRCUIT") {
            return Result.SafeError("Cable proposals are available only for a selected circuit.")
        }
        if (taskState != null && taskState.activeDomain != null && taskState.activeDomain != CalculationDomain.CABLE_SIZING) {
            return Result.SafeError("Evidence domain '${taskState.activeDomain}' is not compatible with cable proposal.")
        }

        // 1. Mandatory Evidence Requirement (Section 19, 39)
        if (evidence.isEmpty()) {
            return Result.SafeError("Proposal creation requires current verified engineering evidence.")
        }

        // 2. Candidate Size Resolution (Section 10, 12)
        var rawProposedSize = action.candidateParameters["overrideCableSize"]?.trim()
        if (rawProposedSize.isNullOrBlank()) {
            rawProposedSize = taskState?.typedWhatIfOverrides?.get("targetSize")?.decimalValue?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: taskState?.typedWhatIfOverrides?.get("overrideCableSize")?.textValue
        }
        if (rawProposedSize.isNullOrBlank()) {
            val whatIfRes = evidence.mapNotNull { it.result as? CableWhatIfResult }.lastOrNull()
            if (whatIfRes != null) {
                rawProposedSize = whatIfRes.scenarioSize
            }
        }
        if (rawProposedSize.isNullOrBlank()) {
            val compRes = evidence.mapNotNull { it.result as? CableCandidatesComparisonResult }.lastOrNull()
            if (compRes != null) {
                if (compRes.candidateResults.size > 1) {
                    return Result.SafeError("AMBIGUOUS_SELECTION: Multiple candidates available in comparison. Please specify which candidate to adopt.")
                } else if (compRes.candidateResults.size == 1) {
                    rawProposedSize = compRes.candidateResults.first().sizeFormatted
                }
            }
        }
        if (rawProposedSize.isNullOrBlank()) {
            return Result.SafeError("A validated positive cable-size candidate is required before creating a proposal.")
        }

        val proposedSizeClean = cleanCandidateSizeString(rawProposedSize) ?: ""
        val proposedSizeDouble = parseCandidateSizeDouble(proposedSizeClean)
        if (proposedSizeDouble == null || proposedSizeDouble <= 0.0) {
            return Result.SafeError("A validated positive cable-size candidate is required before creating a proposal.")
        }
        val normalizedProposedSize = if (proposedSizeDouble % 1.0 == 0.0) {
            proposedSizeDouble.toLong().toString()
        } else {
            proposedSizeDouble.toString()
        }

        // 3. Authoritative Room State Resolution (Read-Only CAS Basis)
        val revision = projectDao.getRevision(projectId)
            ?: return Result.SafeError("The active project could not be resolved.")
        val circuit = circuitDao.getCircuitByIdAndProject(projectId, circuitId)
            ?: return Result.SafeError("The selected circuit is no longer available in the active project.")

        // 4. Current Persisted vs Proposed Value Check (Section 9, 43)
        val currentPersistedOverride = circuit.overrideCableSize?.trim()
        val currentClean = cleanCandidateSizeString(currentPersistedOverride)
        val currentDouble = parseCandidateSizeDouble(currentPersistedOverride)
        if (currentPersistedOverride != null && (currentClean == normalizedProposedSize || (currentDouble != null && kotlin.math.abs(currentDouble - proposedSizeDouble) < 0.01))) {
            return Result.SafeError("Proposed cable size '$normalizedProposedSize mm²' matches the current persisted value. No modification required.")
        }

        // 5. Evidence Lineage & Authorization Validation (Section 10, 13, 26, 27, 28, 38, 41, 42)
        val projectEvidence = evidence.filter { it.projectId == projectId }
        if (projectEvidence.isEmpty()) {
            return Result.SafeError("Evidence project does not match active project '$projectId'.")
        }

        val entityEvidence = projectEvidence.filter { it.entityId == null || it.entityId == circuitId.toString() }
        if (entityEvidence.isEmpty()) {
            return Result.SafeError("Evidence entity does not match active circuit '$circuitId'.")
        }

        val domainEvidence = entityEvidence.filter { it.domain.equals("CABLE_SIZING", ignoreCase = true) }
        if (domainEvidence.isEmpty()) {
            return Result.SafeError("Evidence domain is not compatible with cable proposal.")
        }

        val freshEvidence = domainEvidence.filter { rec ->
            (rec.projectRevision == null || rec.projectRevision == revision) &&
                !rec.freshnessState.equals("STALE", ignoreCase = true) &&
                rec.executionStatus != ToolStatus.STALE
        }
        if (freshEvidence.isEmpty()) {
            return Result.SafeError("Evidence revision is stale against live revision $revision. Recalculation required.")
        }

        // 6. Verify Candidate was Evaluated & Authorized in Valid Evidence (Section 10, 38)
        var matchedEvidence: EngineeringEvidenceRecord? = null
        var matchedCriterion: String? = null
        var evidenceValues: Map<String, EngineeringValue> = emptyMap()

        for (rec in freshEvidence) {
            when (val res = rec.result) {
                is CableWhatIfResult -> {
                    if (isAuthorizedEngineeringCandidate(
                            rawCandidateSize = res.scenarioSize,
                            candidateSizeMm2 = null,
                            candidateId = null,
                            status = res.scenarioStatus,
                            normalizedProposedSize = normalizedProposedSize,
                            proposedSizeDouble = proposedSizeDouble
                        )
                    ) {
                        matchedEvidence = rec
                        matchedCriterion = res.scenarioGoverningCriterion.name
                        evidenceValues = mapOf(
                            "scenarioSize" to EngineeringValue.ofDecimal(proposedSizeDouble, EngineeringUnit.SQUARE_MILLIMETER),
                            "utilizationPct" to EngineeringValue.ofDecimal(res.scenarioDetails.utilizationPct, EngineeringUnit.PERCENT),
                            "vdPct" to EngineeringValue.ofDecimal(res.scenarioDetails.vdPct, EngineeringUnit.PERCENT),
                            "capacityIz" to EngineeringValue.ofDecimal(res.scenarioDetails.capacity_derated_Iz_A, EngineeringUnit.AMPERE),
                            "designCurrentIb" to EngineeringValue.ofDecimal(res.scenarioDetails.Ib_A, EngineeringUnit.AMPERE)
                        )
                        break
                    }
                }
                is CableCandidatesComparisonResult -> {
                    val candidate = res.candidateResults.firstOrNull { item ->
                        isAuthorizedEngineeringCandidate(
                            rawCandidateSize = item.sizeFormatted,
                            candidateSizeMm2 = item.sizeMm2,
                            candidateId = item.candidateId,
                            status = item.overallStatus,
                            normalizedProposedSize = normalizedProposedSize,
                            proposedSizeDouble = proposedSizeDouble
                        )
                    }
                    if (candidate != null) {
                        matchedEvidence = rec
                        matchedCriterion = candidate.governingCriterion.name
                        evidenceValues = mapOf(
                            "candidateSize" to EngineeringValue.ofDecimal(candidate.sizeMm2, EngineeringUnit.SQUARE_MILLIMETER),
                            "utilizationPct" to EngineeringValue.ofDecimal(candidate.utilizationPct, EngineeringUnit.PERCENT),
                            "vdPct" to EngineeringValue.ofDecimal(candidate.voltDropPct, EngineeringUnit.PERCENT)
                        )
                        break
                    }
                }
                is CableCandidateEvaluationResult -> {
                    if (isAuthorizedEngineeringCandidate(
                            rawCandidateSize = res.candidateSizeFormatted,
                            candidateSizeMm2 = res.candidateSizeMm2,
                            candidateId = null,
                            status = res.overallStatus,
                            normalizedProposedSize = normalizedProposedSize,
                            proposedSizeDouble = proposedSizeDouble
                        )
                    ) {
                        matchedEvidence = rec
                        matchedCriterion = res.governingCriterion.name
                        evidenceValues = mapOf(
                            "candidateSize" to EngineeringValue.ofDecimal(res.candidateSizeMm2, EngineeringUnit.SQUARE_MILLIMETER),
                            "utilizationPct" to EngineeringValue.ofDecimal(res.details.utilizationPct, EngineeringUnit.PERCENT),
                            "vdPct" to EngineeringValue.ofDecimal(res.details.vdPct, EngineeringUnit.PERCENT)
                        )
                        break
                    }
                }
                is CableSizingExecutionResult -> {
                    if (isAuthorizedEngineeringCandidate(
                            rawCandidateSize = res.details.cableSize_mm2,
                            candidateSizeMm2 = null,
                            candidateId = null,
                            status = res.overallStatus,
                            normalizedProposedSize = normalizedProposedSize,
                            proposedSizeDouble = proposedSizeDouble
                        )
                    ) {
                        matchedEvidence = rec
                        matchedCriterion = res.governingCriterion.name
                        evidenceValues = mapOf(
                            "cableSize" to EngineeringValue.ofDecimal(proposedSizeDouble, EngineeringUnit.SQUARE_MILLIMETER),
                            "utilizationPct" to EngineeringValue.ofDecimal(res.details.utilizationPct, EngineeringUnit.PERCENT),
                            "vdPct" to EngineeringValue.ofDecimal(res.details.vdPct, EngineeringUnit.PERCENT)
                        )
                        break
                    }
                }
            }
        }

        if (matchedEvidence == null) {
            return Result.SafeError("Candidate size '$normalizedProposedSize mm²' was not evaluated or authorized in current verified evidence.")
        }

        // 7. Assemble Proposal with Evidence Binding
        val evidenceDto = ProposalEvidenceDto(
            evidenceType = matchedEvidence.toolName,
            calculationReference = matchedEvidence.evidenceId,
            governingCriterion = matchedCriterion,
            values = evidenceValues
        )

        val proposal = EngineeringProposal(
            proposalId = proposalIdProvider(),
            projectId = projectId,
            baseProjectRevision = revision,
            targetEntityType = "CIRCUIT",
            targetEntityId = circuit.id.toString(),
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = CableCircuitPatch(overrideCableSize = PatchValue.set(normalizedProposedSize)),
            rationale = "Structured cable-size candidate ($normalizedProposedSize mm²) verified by ${matchedEvidence.toolName} for explicit engineer review.",
            sourceToolName = "ConversationAction.CREATE_PROPOSAL",
            sourceRequestId = decisionId,
            engineeringEvidence = evidenceDto
        )

        // 8. Contract & Policy Validation
        val contractValidation = contractValidator.validate(proposal)
        if (!contractValidation.isValid) {
            return Result.SafeError(contractValidation.errors.firstOrNull()?.message ?: "Contract validation failed.")
        }
        if (policyValidator.validateCableProposal(proposal) !is ProposalPolicyResult.Success) {
            return Result.SafeError("The proposed cable change failed policy validation.")
        }

        // 9. Digest & Semantic Key Computation
        val digest = digestEncoder.computeDigest(proposal)
        val semanticKey = semanticKeyEncoder.computeSemanticKey(proposal)

        // 10. Review UI Mapping
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

    private fun cleanCandidateSizeString(raw: String?): String? = raw
        ?.replace("mm²", "", ignoreCase = true)
        ?.replace("mm2", "", ignoreCase = true)
        ?.replace("مم²", "")
        ?.replace("مم2", "")
        ?.trim()

    private fun parseCandidateSizeDouble(raw: String?): Double? {
        val clean = cleanCandidateSizeString(raw) ?: return null
        return Regex("""([0-9]+(?:\.[0-9]+)?)""").find(clean)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun isAuthorizedEngineeringCandidate(
        rawCandidateSize: String?,
        candidateSizeMm2: Double?,
        candidateId: String?,
        status: CheckStatus,
        normalizedProposedSize: String,
        proposedSizeDouble: Double
    ): Boolean {
        // Must have passed or warning status (never FAIL, never INCOMPLETE)
        if (status == CheckStatus.FAIL || status == CheckStatus.INCOMPLETE) {
            return false
        }

        // Check candidateId match (e.g. "240", "CAND-240")
        if (candidateId != null) {
            val idTrim = candidateId.trim()
            if (idTrim.equals(normalizedProposedSize, ignoreCase = true)) return true
            if (idTrim.equals("CAND-$normalizedProposedSize", ignoreCase = true)) return true
            val idParsed = parseCandidateSizeDouble(idTrim)
            if (idParsed != null && kotlin.math.abs(idParsed - proposedSizeDouble) < 0.01) return true
        }

        // Check numeric mm2 match
        if (candidateSizeMm2 != null && kotlin.math.abs(candidateSizeMm2 - proposedSizeDouble) < 0.01) {
            return true
        }

        // Check raw candidate string (e.g. "240 mm²", "4x240 mm²", "240")
        val clean = cleanCandidateSizeString(rawCandidateSize)
        if (clean == normalizedProposedSize) return true

        val parsedDouble = parseCandidateSizeDouble(rawCandidateSize)
        if (parsedDouble != null && kotlin.math.abs(parsedDouble - proposedSizeDouble) < 0.01) {
            return true
        }

        return false
    }
}


