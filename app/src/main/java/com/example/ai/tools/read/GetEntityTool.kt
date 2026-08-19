package com.example.ai.tools.read

import com.example.ai.contracts.ProvenanceMetadata
import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.context.provider.ProjectContextProvider

/**
 * Deterministic read tool to obtain the bounded context snapshot of a specific project entity.
 * Supports explicit entity types ("CIRCUIT", "TRANSFORMER") with strict project isolation.
 */
class GetEntityTool(
    private val projectContextProvider: ProjectContextProvider
) : DeterministicReadTool<GetEntityRequest, EntityContextResult> {

    override val toolName: String = ReadToolNames.GET_ENTITY
    override val description: String = "Reads the complete typed context snapshot for an entity in a project."

    override suspend fun execute(
        request: ToolRequestEnvelope<GetEntityRequest>
    ): ToolResultEnvelope<EntityContextResult> {
        val started = System.currentTimeMillis()
        val params = request.parameters
        val projectId = params.projectId.takeIf { it.isNotBlank() } ?: request.projectId
        val entityType = params.entityType.trim().uppercase()
        val entityId = params.entityId.trim()

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

        when (entityType) {
            "CIRCUIT" -> {
                val circuitId = entityId.toIntOrNull()
                if (circuitId == null) {
                    return ToolResultEnvelope.invalidInput(
                        requestId = request.requestId,
                        toolName = toolName,
                        projectId = projectId,
                        errors = listOf(
                            StructuredError(
                                errorCode = "INVALID_ENTITY_ID",
                                message = "CIRCUIT entityId must be a valid numeric integer.",
                                isRecoverable = true
                            )
                        ),
                        startedAt = started
                    )
                }

                val circuitContext = projectContextProvider.getCircuitContext(projectId, circuitId)
                if (circuitContext == null) {
                    return ToolResultEnvelope.incomplete(
                        requestId = request.requestId,
                        toolName = toolName,
                        projectId = projectId,
                        missingDataReason = "Circuit entity '$circuitId' not found in project '$projectId'.",
                        startedAt = started
                    )
                }

                val result = EntityContextResult(
                    projectId = projectId,
                    entityType = "CIRCUIT",
                    entityId = circuitId.toString(),
                    projectRevision = null,
                    circuitContext = circuitContext,
                    transformerContext = null
                )

                return ToolResultEnvelope.pass(
                    requestId = request.requestId,
                    toolName = toolName,
                    projectId = projectId,
                    result = result,
                    provenance = ProvenanceMetadata(
                        engineName = "CONTEXT_FACADE",
                        engineVersion = "1.0",
                        projectId = projectId,
                        calculatedAt = System.currentTimeMillis()
                    ),
                    startedAt = started
                )
            }
            "TRANSFORMER" -> {
                val txContext = projectContextProvider.getTransformerContext(projectId, entityId)
                if (txContext == null) {
                    return ToolResultEnvelope.incomplete(
                        requestId = request.requestId,
                        toolName = toolName,
                        projectId = projectId,
                        missingDataReason = "Transformer entity '$entityId' not found in project '$projectId'.",
                        startedAt = started
                    )
                }

                val result = EntityContextResult(
                    projectId = projectId,
                    entityType = "TRANSFORMER",
                    entityId = entityId,
                    projectRevision = null,
                    circuitContext = null,
                    transformerContext = txContext
                )

                return ToolResultEnvelope.pass(
                    requestId = request.requestId,
                    toolName = toolName,
                    projectId = projectId,
                    result = result,
                    provenance = ProvenanceMetadata(
                        engineName = "CONTEXT_FACADE",
                        engineVersion = "1.0",
                        projectId = projectId,
                        calculatedAt = System.currentTimeMillis()
                    ),
                    startedAt = started
                )
            }
            else -> {
                return ToolResultEnvelope.invalidInput(
                    requestId = request.requestId,
                    toolName = toolName,
                    projectId = projectId,
                    errors = listOf(
                        StructuredError(
                            errorCode = "UNSUPPORTED_ENTITY_TYPE",
                            message = "Entity type is not supported for get_entity. Supported types are: CIRCUIT, TRANSFORMER.",
                            isRecoverable = false
                        )
                    ),
                    startedAt = started
                )
            }
        }
    }
}
