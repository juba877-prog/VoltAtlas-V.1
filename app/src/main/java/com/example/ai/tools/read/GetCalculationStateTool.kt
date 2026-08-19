package com.example.ai.tools.read

import com.example.ai.contracts.ProvenanceMetadata
import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.context.freshness.contracts.CalculationEntityType
import com.example.ai.context.freshness.contracts.CalculationFreshnessProvider
import com.example.ai.context.freshness.contracts.CalculationScope
import com.example.ai.context.freshness.contracts.CalculationState

/**
 * Deterministic read tool to query calculation state and freshness for a specific scope.
 */
class GetCalculationStateTool(
    private val freshnessProvider: CalculationFreshnessProvider
) : DeterministicReadTool<GetCalculationStateRequest, CalculationStateResult> {

    override val toolName: String = ReadToolNames.GET_CALCULATION_STATE
    override val description: String = "Queries the authoritative calculation state, freshness, and usability for a specific entity and domain."

    override suspend fun execute(
        request: ToolRequestEnvelope<GetCalculationStateRequest>
    ): ToolResultEnvelope<CalculationStateResult> {
        val started = System.currentTimeMillis()
        val params = request.parameters
        val projectId = params.projectId.takeIf { it.isNotBlank() } ?: request.projectId
        val entityId = params.entityId.trim()
        val entityTypeStr = params.entityType.trim().uppercase()

        if (projectId.isBlank() || entityId.isBlank()) {
            return ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = toolName,
                projectId = projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_REQUEST_PARAMETERS",
                        message = "Both projectId and entityId must be non-blank.",
                        isRecoverable = true
                    )
                ),
                startedAt = started
            )
        }

        val entityType = when (entityTypeStr) {
            "CIRCUIT" -> CalculationEntityType.CIRCUIT
            "TRANSFORMER" -> CalculationEntityType.TRANSFORMER
            else -> {
                return ToolResultEnvelope.invalidInput(
                    requestId = request.requestId,
                    toolName = toolName,
                    projectId = projectId,
                    errors = listOf(
                        StructuredError(
                            errorCode = "UNSUPPORTED_ENTITY_TYPE",
                            message = "Entity type '$entityTypeStr' is unsupported for freshness evaluation. Supported: CIRCUIT, TRANSFORMER.",
                            isRecoverable = false
                        )
                    ),
                    startedAt = started
                )
            }
        }

        val domain = params.domain ?: when (entityType) {
            CalculationEntityType.CIRCUIT -> CalculationDomain.CABLE_SIZING
            CalculationEntityType.TRANSFORMER -> CalculationDomain.TRANSFORMER
        }

        val scope = CalculationScope(
            projectId = projectId,
            entityType = entityType,
            entityId = entityId,
            domain = domain
        )

        val snapshot = freshnessProvider.getCalculationState(scope)

        val result = CalculationStateResult(
            projectId = projectId,
            entityType = entityType.name,
            entityId = entityId,
            domain = domain.name,
            state = snapshot.state.name,
            reasonCode = snapshot.reasonCode.name,
            recordedRevision = snapshot.recordedProjectRevision,
            currentRevision = snapshot.currentProjectRevision,
            isUsable = (snapshot.state == CalculationState.CURRENT),
            changedComponentCount = snapshot.changedComponents.size
        )

        return ToolResultEnvelope.pass(
            requestId = request.requestId,
            toolName = toolName,
            projectId = projectId,
            projectRevision = snapshot.currentProjectRevision,
            result = result,
            provenance = ProvenanceMetadata(
                engineName = "FRESHNESS_TRACKER",
                engineVersion = "1.0",
                projectId = projectId,
                projectRevision = snapshot.currentProjectRevision,
                calculatedAt = snapshot.lastCalculatedAt ?: System.currentTimeMillis()
            ),
            startedAt = started
        )
    }
}
