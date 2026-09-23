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
 * Deterministic tool for evaluating a single specific cable candidate on a circuit without modifying project state.
 *
 * Guarantees:
 * 1. Zero mutation: Does NOT write to Room database.
 * 2. Zero revision increments: Project revision remains unchanged.
 * 3. Zero cache pollution: Does NOT replace live Coordinator cache or freshness records.
 * 4. Authoritative Sizing: Delegates directly to [CableCalculationService.evaluateCandidate] without duplicate formulas.
 */
class EvaluateCableCandidateTool(
    private val repository: EngineeringRepository,
    private val revisionProvider: ProjectRevisionProvider,
    private val coordinator: EngineeringCalculationCoordinator = EngineeringCalculationCoordinator
) : DeterministicCableTool<EvaluateCableCandidateRequest, CableCandidateEvaluationResult> {

    override val toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE
    override val description = "Evaluates a specific candidate conductor size or specification for a circuit side-effect free."

    override suspend fun execute(
        request: ToolRequestEnvelope<EvaluateCableCandidateRequest>
    ): ToolResultEnvelope<CableCandidateEvaluationResult> {
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

        val sizeDouble = request.parameters.candidateSizeMm2
            ?: request.parameters.candidateSizeText?.let { parseCableSize(it) }
            ?: return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "MISSING_CANDIDATE_SIZE",
                        message = "A candidate conductor size must be specified.",
                        isRecoverable = true
                    )
                )
            )

        val formattedSize = if (sizeDouble % 1.0 == 0.0) {
            "${sizeDouble.toInt()} mm\u00B2"
        } else {
            "$sizeDouble mm\u00B2"
        }

        val candidateMaterial = request.parameters.candidateMaterial?.trim() ?: if (circuit.cableType.contains("Al", ignoreCase = true)) "Al" else "Cu"
        val candidateParallelRuns = request.parameters.candidateParallelRuns ?: circuit.parallelRuns

        if (candidateParallelRuns <= 0) {
            return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_PARALLEL_RUNS",
                        message = "Parallel runs must be at least 1.",
                        isRecoverable = true
                    )
                )
            )
        }

        // Adjust cable type if material override was specified
        val resolvedCableType = when {
            candidateMaterial.equals("Al", ignoreCase = true) && !circuit.cableType.contains("Al", ignoreCase = true) -> {
                circuit.cableType.replace("Cu", "Al", ignoreCase = true)
            }
            candidateMaterial.equals("Cu", ignoreCase = true) && circuit.cableType.contains("Al", ignoreCase = true) -> {
                circuit.cableType.replace("Al", "Cu", ignoreCase = true)
            }
            else -> circuit.cableType
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

        // Pure in-memory calculation via public CableCalculationService API
        val sizingResult = CableCalculationService.evaluateCandidate(
            circuit = circuit,
            candidateSizeFormatted = formattedSize,
            parallelRuns = candidateParallelRuns,
            cableType = resolvedCableType,
            deviceSizeA = request.parameters.candidateDeviceSizeA ?: circuit.overrideDeviceSize,
            deviceType = request.parameters.candidateDeviceType ?: circuit.overrideDeviceType,
            parentCumulativeVD = parentCumulativeVD,
            upstreamCircuits = ancestors,
            transformers = transformers
        )

        val candidateCircuit = circuit.copy(
            overrideCableSize = formattedSize,
            parallelRuns = candidateParallelRuns,
            cableType = resolvedCableType,
            overrideDeviceSize = request.parameters.candidateDeviceSizeA ?: circuit.overrideDeviceSize,
            overrideDeviceType = request.parameters.candidateDeviceType ?: circuit.overrideDeviceType
        )

        val interpretation = GoverningCriterionInterpreter.interpret(sizingResult, candidateCircuit, parentCumulativeVD)
        val rev = revisionProvider.getCurrentRevision(request.projectId).revision

        val resultPayload = CableCandidateEvaluationResult(
            projectId = request.projectId,
            circuitId = circuitIdInt.toString(),
            circuitName = circuit.circuitName,
            candidateSizeMm2 = sizeDouble,
            candidateSizeFormatted = formattedSize,
            candidateMaterial = candidateMaterial,
            parallelRuns = candidateParallelRuns,
            selectionSource = interpretation.selectionSource,
            overallStatus = interpretation.overallStatus,
            governingCriterion = interpretation.governingCriterion,
            governingReason = interpretation.governingReason,
            details = interpretation.details,
            checks = interpretation.checks
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
