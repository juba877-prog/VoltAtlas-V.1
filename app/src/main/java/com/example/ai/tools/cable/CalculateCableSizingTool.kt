package com.example.ai.tools.cable

import com.example.ai.contracts.ProvenanceMetadata
import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.context.freshness.contracts.CalculationEntityType
import com.example.ai.context.freshness.contracts.CalculationFreshnessProvider
import com.example.ai.context.freshness.contracts.CalculationState
import com.example.ai.context.freshness.contracts.CalculationScope
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.data.EngineeringRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator

/**
 * Deterministic tool for calculating cable sizing for a specific circuit.
 *
 * Rules:
 * 1. Derives authoritative inputs from the project repository.
 * 2. Enforces strict project isolation (rejects cross-project circuit access).
 * 3. Checks blocking inputs (voltage, length, pf, parallel runs, max VD) before calculating.
 * 4. Reuses CURRENT valid results unless forceRecalculate is explicitly requested.
 * 5. When STALE and forceRecalculate=false, returns typed STALE envelope without claiming current validity.
 * 6. Executes calculations exclusively through targeted [EngineeringCalculationCoordinator] without whole-project sweeps.
 */
class CalculateCableSizingTool(
    private val repository: EngineeringRepository,
    private val revisionProvider: ProjectRevisionProvider,
    private val freshnessProvider: CalculationFreshnessProvider,
    private val coordinator: EngineeringCalculationCoordinator = EngineeringCalculationCoordinator
) : DeterministicCableTool<CalculateCableSizingRequest, CableSizingExecutionResult> {

    override val toolName = CableToolNames.CALCULATE_CABLE_SIZING
    override val description = "Calculates or retrieves authoritative cable sizing for a specific circuit."

    override suspend fun execute(
        request: ToolRequestEnvelope<CalculateCableSizingRequest>
    ): ToolResultEnvelope<CableSizingExecutionResult> {
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

        // Validate blocking inputs
        if (circuit.voltageV <= 0) {
            return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_VOLTAGE",
                        message = "Circuit voltage must be greater than zero.",
                        isRecoverable = true
                    )
                )
            )
        }
        if (circuit.length_m <= 0.0) {
            return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_LENGTH",
                        message = "Cable length must be greater than zero.",
                        isRecoverable = true
                    )
                )
            )
        }
        if (circuit.pf <= 0.0 || circuit.pf > 1.0) {
            return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_POWER_FACTOR",
                        message = "Power factor must be greater than 0 and at most 1.0.",
                        isRecoverable = true
                    )
                )
            )
        }
        if (circuit.maxVDpct <= 0.0) {
            return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_MAX_VD",
                        message = "Maximum voltage drop percentage limit must be greater than zero.",
                        isRecoverable = true
                    )
                )
            )
        }
        if (circuit.parallelRuns <= 0) {
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

        val scope = CalculationScope(
            projectId = request.projectId,
            entityType = CalculationEntityType.CIRCUIT,
            entityId = circuitIdInt.toString(),
            domain = CalculationDomain.CABLE_SIZING
        )

        val freshness = freshnessProvider.getCalculationState(scope)
        val cachedResult = coordinator.getCachedSizingResult(request.projectId, circuitIdInt)
        val rev = revisionProvider.getCurrentRevision(request.projectId).revision

        // Blocker H: STALE + forceRecalculate=false -> return typed STALE envelope
        if (freshness.state == CalculationState.STALE && !request.parameters.forceRecalculate && cachedResult != null) {
            val interpretation = GoverningCriterionInterpreter.interpret(cachedResult, circuit)
            val resultPayload = CableSizingExecutionResult(
                projectId = request.projectId,
                circuitId = circuitIdInt.toString(),
                circuitName = circuit.circuitName,
                projectRevision = rev,
                freshnessState = CalculationState.STALE.name,
                isRecalculated = false,
                overallStatus = interpretation.overallStatus,
                governingCriterion = interpretation.governingCriterion,
                governingReason = "Calculation inputs have changed since this result was recorded. Explicit recalculation is required.",
                details = interpretation.details,
                checks = interpretation.checks
            )
            return ToolResultEnvelope.stale(
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

        val isRecalculated: Boolean
        val sizingResult = if (freshness.state == CalculationState.CURRENT && cachedResult != null && !request.parameters.forceRecalculate) {
            isRecalculated = false
            cachedResult
        } else {
            isRecalculated = true
            val allCircuits = repository.circuitDao.getCircuitsListForProject(request.projectId)
            val transformers = repository.transformerDao.getTransformersListForProject(request.projectId)
            coordinator.calculateCableSizing(circuit, allCircuits, transformers)
        }

        val interpretation = GoverningCriterionInterpreter.interpret(sizingResult, circuit)

        val resultPayload = CableSizingExecutionResult(
            projectId = request.projectId,
            circuitId = circuitIdInt.toString(),
            circuitName = circuit.circuitName,
            projectRevision = rev,
            freshnessState = if (isRecalculated) CalculationState.CURRENT.name else freshness.state.name,
            isRecalculated = isRecalculated,
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
}
