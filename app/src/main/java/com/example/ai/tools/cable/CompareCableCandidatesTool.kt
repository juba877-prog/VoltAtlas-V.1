package com.example.ai.tools.cable

import com.example.ai.contracts.ProvenanceMetadata
import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.data.EngineeringRepository
import com.example.engine.coordinator.CableCalculationService
import com.example.engine.coordinator.EngineeringCalculationCoordinator

/**
 * Deterministic tool for comparing multiple bounded cable candidates on a circuit.
 *
 * Guarantees:
 * 1. Bounded comparison: Enforces a maximum limit of 10 candidates.
 * 2. Side-effect free: Zero database mutations or cache pollution.
 * 3. Objective-Driven Recommendation: Distinguishes smallestPassingCandidate from lowestUtilizationCandidate
 *    without fabricating cost or claiming unsupported universal optimum.
 */
class CompareCableCandidatesTool(
    private val repository: EngineeringRepository,
    private val revisionProvider: ProjectRevisionProvider,
    private val coordinator: EngineeringCalculationCoordinator = EngineeringCalculationCoordinator
) : DeterministicCableTool<CompareCableCandidatesRequest, CableCandidatesComparisonResult> {

    companion object {
        const val MAX_CANDIDATES = 10
    }

    override val toolName = CableToolNames.COMPARE_CABLE_CANDIDATES
    override val description = "Compares a bounded list of candidate conductor sizes against authoritative engineering limits."

    override suspend fun execute(
        request: ToolRequestEnvelope<CompareCableCandidatesRequest>
    ): ToolResultEnvelope<CableCandidatesComparisonResult> {
        val circuitIdInt = request.parameters.circuitId.toIntOrNull()
            ?: return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_CIRCUIT_ID",
                        message = "Circuit ID '${request.parameters.circuitId}' is not a valid integer.",
                        isRecoverable = false
                    )
                )
            )

        val circuit = repository.circuitDao.getCircuitById(circuitIdInt)
            ?: return ToolResultEnvelope.incomplete(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                missingDataReason = "Circuit with ID $circuitIdInt was not found."
            )

        if (circuit.projectId != request.projectId) {
            return ToolResultEnvelope.incomplete(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                missingDataReason = "Circuit $circuitIdInt does not belong to project '${request.projectId}'."
            )
        }

        val candidateSpecs = request.parameters.candidates
        if (candidateSpecs.isEmpty()) {
            return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "EMPTY_CANDIDATE_LIST",
                        message = "Candidates list must contain at least one candidate.",
                        isRecoverable = true
                    )
                )
            )
        }

        if (candidateSpecs.size > MAX_CANDIDATES) {
            return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "EXCESSIVE_CANDIDATES_COUNT",
                        message = "Cannot evaluate more than $MAX_CANDIDATES candidates at once (received ${candidateSpecs.size}).",
                        isRecoverable = true
                    )
                )
            )
        }

        val allCircuits = repository.circuitDao.getCircuitsListForProject(request.projectId)
        val transformers = repository.transformerDao.getTransformersListForProject(request.projectId)
        val ancestors = coordinator.resolveAncestorChain(circuit, allCircuits)

        var parentCumulativeVD = 0.0
        for (ancestor in ancestors) {
            val ancestorResult = coordinator.getCachedSizingResult(request.projectId, ancestor.id)
                ?: CableCalculationService.calculateSingleCircuit(ancestor, parentCumulativeVD, ancestors, transformers)
            parentCumulativeVD = ancestorResult.cumulativeVDpct
        }

        val evaluatedItems = mutableListOf<CandidateComparisonItemDto>()

        for ((index, spec) in candidateSpecs.withIndex()) {
            val candidateId = spec.candidateId ?: "CANDIDATE_${index + 1}"
            val sizeDouble = spec.sizeMm2 ?: spec.sizeText?.let { parseCableSize(it) } ?: 0.0
            val formattedSize = if (sizeDouble > 0.0) {
                if (sizeDouble % 1.0 == 0.0) "${sizeDouble.toInt()} mm\u00B2" else "$sizeDouble mm\u00B2"
            } else "N/A"

            val candidateMaterial = spec.material?.trim() ?: if (circuit.cableType.contains("Al", ignoreCase = true)) "Al" else "Cu"
            val parallelRuns = spec.parallelRuns ?: circuit.parallelRuns

            val resolvedCableType = when {
                candidateMaterial.equals("Al", ignoreCase = true) && !circuit.cableType.contains("Al", ignoreCase = true) -> {
                    circuit.cableType.replace("Cu", "Al", ignoreCase = true)
                }
                candidateMaterial.equals("Cu", ignoreCase = true) && circuit.cableType.contains("Al", ignoreCase = true) -> {
                    circuit.cableType.replace("Al", "Cu", ignoreCase = true)
                }
                else -> circuit.cableType
            }

            val sizingResult = CableCalculationService.evaluateCandidate(
                circuit = circuit,
                candidateSizeFormatted = formattedSize,
                parallelRuns = parallelRuns,
                cableType = resolvedCableType,
                deviceSizeA = spec.deviceSizeA ?: circuit.overrideDeviceSize,
                deviceType = spec.deviceType ?: circuit.overrideDeviceType,
                parentCumulativeVD = parentCumulativeVD,
                upstreamCircuits = ancestors,
                transformers = transformers
            )

            val candidateCircuit = circuit.copy(
                overrideCableSize = formattedSize,
                parallelRuns = parallelRuns,
                cableType = resolvedCableType,
                overrideDeviceSize = spec.deviceSizeA ?: circuit.overrideDeviceSize,
                overrideDeviceType = spec.deviceType ?: circuit.overrideDeviceType
            )

            val interpretation = GoverningCriterionInterpreter.interpret(sizingResult, candidateCircuit, parentCumulativeVD)

            evaluatedItems.add(
                CandidateComparisonItemDto(
                    candidateId = candidateId,
                    sizeMm2 = sizeDouble,
                    sizeFormatted = formattedSize,
                    material = candidateMaterial,
                    parallelRuns = parallelRuns,
                    overallStatus = interpretation.overallStatus,
                    utilizationPct = interpretation.details.utilizationPct,
                    voltDropPct = interpretation.details.vdPct,
                    cumulativeVdPct = interpretation.details.cumulativeVDpct,
                    governingCriterion = interpretation.governingCriterion,
                    governingReason = interpretation.governingReason,
                    checks = interpretation.checks
                )
            )
        }

        val sortedItems = when (request.parameters.sortPolicy) {
            CandidateSortPolicy.INPUT_ORDER -> evaluatedItems
            CandidateSortPolicy.SIZE_ASCENDING -> evaluatedItems.sortedBy { it.sizeMm2 }
            CandidateSortPolicy.UTILIZATION_ASCENDING -> evaluatedItems.sortedBy { it.utilizationPct }
        }

        val passingCandidates = sortedItems.filter { it.overallStatus == CheckStatus.PASS }
        val passingCount = passingCandidates.size
        val failingCount = sortedItems.count { it.overallStatus == CheckStatus.FAIL }

        val smallestPassing = passingCandidates.minByOrNull { it.sizeMm2 }?.candidateId
        val lowestUtilizationPassing = passingCandidates.minByOrNull { it.utilizationPct }?.candidateId

        val noRecommendationReason = if (passingCount == 0) {
            "All evaluated candidates failed engineering constraints."
        } else {
            null
        }

        val currentCachedResult = coordinator.getCachedSizingResult(request.projectId, circuitIdInt)
        val currentSize = currentCachedResult?.cableSize_mm2 ?: circuit.overrideCableSize

        val rev = revisionProvider.getCurrentRevision(request.projectId).revision

        val resultPayload = CableCandidatesComparisonResult(
            projectId = request.projectId,
            circuitId = circuitIdInt.toString(),
            circuitName = circuit.circuitName,
            currentAuthoritativeSize = currentSize,
            evaluatedCount = sortedItems.size,
            passingCount = passingCount,
            failingCount = failingCount,
            smallestPassingCandidateId = smallestPassing,
            lowestUtilizationPassingCandidateId = lowestUtilizationPassing,
            noRecommendationReason = noRecommendationReason,
            recommendedCandidateId = smallestPassing,
            sortPolicy = request.parameters.sortPolicy,
            candidateResults = sortedItems
        )

        return ToolResultEnvelope.pass(
            requestId = request.requestId,
            toolName = toolName,
            projectId = request.projectId,
            result = resultPayload,
            projectRevision = rev,
            provenance = ProvenanceMetadata(
                engineName = "CableSizingEngine",
                methodologyReference = "IEC 60364-5-52",
                projectId = request.projectId,
                projectRevision = rev
            )
        )
    }

    private fun parseCableSize(value: String): Double? {
        val match = Regex("""^\s*([0-9]+(?:\.[0-9]+)?)""").find(value)
        return match?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }
}
