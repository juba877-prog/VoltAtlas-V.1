package com.example.ai.tools.read

import com.example.ai.contracts.ProvenanceMetadata
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.context.provider.ProjectContextProvider

/**
 * Deterministic read tool to obtain the bounded project context and validation status.
 */
class GetCurrentProjectContextTool(
    private val projectContextProvider: ProjectContextProvider
) : DeterministicReadTool<GetProjectContextRequest, ProjectContextResult> {

    override val toolName: String = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT
    override val description: String = "Reads authoritative bounded project context and validation status."

    override suspend fun execute(
        request: ToolRequestEnvelope<GetProjectContextRequest>
    ): ToolResultEnvelope<ProjectContextResult> {
        val started = System.currentTimeMillis()
        val projectId = request.parameters.projectId.takeIf { it.isNotBlank() } ?: request.projectId

        if (projectId.isBlank()) {
            return ToolResultEnvelope.incomplete(
                requestId = request.requestId,
                toolName = toolName,
                projectId = projectId,
                missingDataReason = "projectId cannot be blank in GetProjectContextRequest.",
                startedAt = started
            )
        }

        val validationSnap = projectContextProvider.getValidationSnapshot(projectId)
        if (validationSnap == null) {
            return ToolResultEnvelope.incomplete(
                requestId = request.requestId,
                toolName = toolName,
                projectId = projectId,
                missingDataReason = "Project '$projectId' not found or revision is unavailable.",
                startedAt = started
            )
        }

        val result = ProjectContextResult(
            projectId = validationSnap.projectId,
            projectRevision = validationSnap.projectRevision,
            engineeringStatus = validationSnap.engineeringStatus.name,
            overallStatusSummary = validationSnap.overallStatusSummary,
            totalCircuits = validationSnap.totalCircuits,
            totalTransformers = validationSnap.totalTransformers,
            calculatedCircuitCount = validationSnap.calculatedCircuitCount,
            staleCircuitCount = validationSnap.staleCircuitCount,
            missingCircuitCount = validationSnap.missingCircuitCount,
            criticalFailureCount = validationSnap.criticalFailureCount,
            warningCount = validationSnap.warningCount,
            reviewRequiredCount = validationSnap.reviewRequiredCount,
            topIssues = validationSnap.topIssues.take(20).map {
                ValidationIssueItemDto(
                    entityId = it.entityId,
                    domain = it.domain,
                    severity = it.severity,
                    description = it.description
                )
            }
        )

        return ToolResultEnvelope.pass(
            requestId = request.requestId,
            toolName = toolName,
            projectId = projectId,
            projectRevision = validationSnap.projectRevision,
            result = result,
            provenance = ProvenanceMetadata(
                engineName = "VALIDATION_FACADE",
                engineVersion = "1.0",
                projectId = projectId,
                projectRevision = validationSnap.projectRevision,
                calculatedAt = System.currentTimeMillis()
            ),
            startedAt = started,
            completedAt = System.currentTimeMillis()
        )
    }
}
