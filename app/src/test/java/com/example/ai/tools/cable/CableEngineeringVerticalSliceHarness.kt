package com.example.ai.tools.cable

import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.task.EngineeringTaskManager
import com.example.ai.task.TaskOwner
import com.example.ai.tools.read.DeterministicReadToolInvoker
import com.example.ai.tools.read.GetEntityRequest
import com.example.ai.tools.read.ReadToolNames
import java.util.UUID

/**
 * Typed internal vertical-slice harness for executing deterministic cable workflows without LLM/Gemini dependencies.
 */
class CableEngineeringVerticalSliceHarness(
    private val cableInvoker: DeterministicCableToolInvoker,
    private val readInvoker: DeterministicReadToolInvoker,
    private val taskManager: EngineeringTaskManager
) {

    /**
     * Reads current authoritative entity state for a circuit.
     */
    suspend fun readCurrentCableResult(
        projectId: String,
        circuitId: String,
        owner: TaskOwner = TaskOwner.SYSTEM
    ): ToolResultEnvelope<*> {
        val request = ToolRequestEnvelope(
            requestId = "REQ-READ-${UUID.randomUUID()}",
            projectId = projectId,
            toolName = ReadToolNames.GET_ENTITY,
            entityType = "CIRCUIT",
            entityId = circuitId,
            parameters = GetEntityRequest(
                projectId = projectId,
                entityType = "CIRCUIT",
                entityId = circuitId
            )
        )
        return readInvoker.executeManaged<GetEntityRequest, Any>(request, taskManager, owner)
    }

    /**
     * Executes authoritative cable sizing calculation.
     */
    suspend fun calculateCableSizing(
        projectId: String,
        circuitId: String,
        forceRecalculate: Boolean = false,
        owner: TaskOwner = TaskOwner.SYSTEM
    ): ToolResultEnvelope<CableSizingExecutionResult> {
        val request = ToolRequestEnvelope(
            requestId = "REQ-CALC-${UUID.randomUUID()}",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            entityType = "CIRCUIT",
            entityId = circuitId,
            parameters = CalculateCableSizingRequest(
                projectId = projectId,
                circuitId = circuitId,
                forceRecalculate = forceRecalculate
            )
        )
        return cableInvoker.executeManaged(request, taskManager, owner)
    }

    /**
     * Evaluates a specific candidate conductor size or material side-effect free.
     */
    suspend fun evaluateCandidate(
        projectId: String,
        circuitId: String,
        candidateSizeMm2: Double? = null,
        candidateSizeText: String? = null,
        candidateMaterial: String? = null,
        candidateParallelRuns: Int? = null,
        owner: TaskOwner = TaskOwner.SYSTEM
    ): ToolResultEnvelope<CableCandidateEvaluationResult> {
        val request = ToolRequestEnvelope(
            requestId = "REQ-EVAL-${UUID.randomUUID()}",
            projectId = projectId,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            entityType = "CIRCUIT",
            entityId = circuitId,
            parameters = EvaluateCableCandidateRequest(
                projectId = projectId,
                circuitId = circuitId,
                candidateSizeMm2 = candidateSizeMm2,
                candidateSizeText = candidateSizeText,
                candidateMaterial = candidateMaterial,
                candidateParallelRuns = candidateParallelRuns
            )
        )
        return cableInvoker.executeManaged(request, taskManager, owner)
    }

    /**
     * Compares multiple candidate alternatives side-effect free.
     */
    suspend fun compareCandidates(
        projectId: String,
        circuitId: String,
        candidates: List<CableCandidateSpecDto>,
        sortPolicy: CandidateSortPolicy = CandidateSortPolicy.INPUT_ORDER,
        owner: TaskOwner = TaskOwner.SYSTEM
    ): ToolResultEnvelope<CableCandidatesComparisonResult> {
        val request = ToolRequestEnvelope(
            requestId = "REQ-COMP-${UUID.randomUUID()}",
            projectId = projectId,
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            entityType = "CIRCUIT",
            entityId = circuitId,
            parameters = CompareCableCandidatesRequest(
                projectId = projectId,
                circuitId = circuitId,
                candidates = candidates,
                sortPolicy = sortPolicy
            )
        )
        return cableInvoker.executeManaged(request, taskManager, owner)
    }

    /**
     * Evaluates a temporary what-if scenario with field overrides side-effect free.
     */
    suspend fun runWhatIf(
        projectId: String,
        circuitId: String,
        overrides: CableWhatIfOverridesDto,
        owner: TaskOwner = TaskOwner.SYSTEM
    ): ToolResultEnvelope<CableWhatIfResult> {
        val request = ToolRequestEnvelope(
            requestId = "REQ-WHATIF-${UUID.randomUUID()}",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            entityType = "CIRCUIT",
            entityId = circuitId,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = circuitId,
                overrides = overrides
            )
        )
        return cableInvoker.executeManaged(request, taskManager, owner)
    }
}
