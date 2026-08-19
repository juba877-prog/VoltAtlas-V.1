package com.example.ai.tools.read

import com.example.ai.contracts.ProvenanceMetadata
import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.context.provider.ParentResolution
import com.example.ai.context.provider.ProjectContextProvider

/**
 * Deterministic read tool to query immediate and ancestor dependency relationships
 * using the authoritative shared hierarchy and transformer resolvers.
 */
class GetDependenciesTool(
    private val projectContextProvider: ProjectContextProvider
) : DeterministicReadTool<GetDependenciesRequest, DependenciesResult> {

    override val toolName: String = ReadToolNames.GET_DEPENDENCIES
    override val description: String = "Resolves upstream parent, ancestor hierarchy chain, and transformer assignments for an entity."

    override suspend fun execute(
        request: ToolRequestEnvelope<GetDependenciesRequest>
    ): ToolResultEnvelope<DependenciesResult> {
        val started = System.currentTimeMillis()
        val params = request.parameters
        val projectId = params.projectId.takeIf { it.isNotBlank() } ?: request.projectId
        val entityId = params.entityId.trim()
        val entityType = params.entityType.trim().uppercase()

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
                        missingDataReason = "Circuit '$circuitId' not found in project '$projectId'.",
                        startedAt = started
                    )
                }

                val resolution = circuitContext.parentResolution
                val resolutionStatus: String
                val parentId: String?
                val parentName: String?

                when (resolution) {
                    is ParentResolution.Resolved -> {
                        resolutionStatus = "RESOLVED"
                        parentId = resolution.parent.id.toString()
                        parentName = resolution.parent.circuitName
                    }
                    is ParentResolution.InvalidCycle -> {
                        resolutionStatus = "CYCLE_DETECTED"
                        parentId = null
                        parentName = null
                    }
                    is ParentResolution.Ambiguous -> {
                        resolutionStatus = "AMBIGUOUS_PARENT"
                        parentId = null
                        parentName = null
                    }
                    is ParentResolution.MissingReference -> {
                        resolutionStatus = "MISSING_PARENT_REFERENCE"
                        parentId = null
                        parentName = null
                    }
                    is ParentResolution.Root -> {
                        resolutionStatus = "ROOT"
                        parentId = null
                        parentName = null
                    }
                }

                val result = DependenciesResult(
                    projectId = projectId,
                    entityType = "CIRCUIT",
                    entityId = entityId,
                    resolutionStatus = resolutionStatus,
                    parentId = parentId,
                    parentName = parentName,
                    ancestorChainIds = if (parentId != null) listOf(parentId) else emptyList(),
                    downstreamChildIds = emptyList(),
                    assignedTransformerId = circuitContext.sourceTransformerId
                )

                return ToolResultEnvelope.pass(
                    requestId = request.requestId,
                    toolName = toolName,
                    projectId = projectId,
                    result = result,
                    provenance = ProvenanceMetadata(
                        engineName = "HIERARCHY_RESOLVER",
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
                        missingDataReason = "Transformer '$entityId' not found in project '$projectId'.",
                        startedAt = started
                    )
                }

                val result = DependenciesResult(
                    projectId = projectId,
                    entityType = "TRANSFORMER",
                    entityId = entityId,
                    resolutionStatus = "ROOT_SOURCE",
                    parentId = null,
                    parentName = null,
                    ancestorChainIds = emptyList(),
                    downstreamChildIds = emptyList(),
                    assignedTransformerId = txContext.transformerId
                )

                return ToolResultEnvelope.pass(
                    requestId = request.requestId,
                    toolName = toolName,
                    projectId = projectId,
                    result = result,
                    provenance = ProvenanceMetadata(
                        engineName = "HIERARCHY_RESOLVER",
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
                            message = "Entity type '$entityType' is not supported for get_dependencies. Supported: CIRCUIT, TRANSFORMER.",
                            isRecoverable = false
                        )
                    ),
                    startedAt = started
                )
            }
        }
    }
}
