package com.example.ai.tools.read

import com.example.ai.contracts.ProvenanceMetadata
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.context.revision.ProjectRevisionProvider

/**
 * Deterministic read tool to query project revision and change history state.
 *
 * Truthful Auditing Guarantee:
 * - Reads authoritative current revision snapshot from [ProjectRevisionProvider].
 * - Explicitly documents that fine-grained mutation event audit logging is deferred to Sub-Phase 3E.
 * - Never fabricates fake historical diffs or synthetic change entries.
 */
class GetChangeHistoryTool(
    private val revisionProvider: ProjectRevisionProvider
) : DeterministicReadTool<GetChangeHistoryRequest, ChangeHistoryResult> {

    override val toolName: String = ReadToolNames.GET_CHANGE_HISTORY
    override val description: String = "Queries current authoritative project revision head and history availability status."

    override suspend fun execute(
        request: ToolRequestEnvelope<GetChangeHistoryRequest>
    ): ToolResultEnvelope<ChangeHistoryResult> {
        val started = System.currentTimeMillis()
        val projectId = request.parameters.projectId.takeIf { it.isNotBlank() } ?: request.projectId

        if (projectId.isBlank()) {
            return ToolResultEnvelope.incomplete(
                requestId = request.requestId,
                toolName = toolName,
                projectId = projectId,
                missingDataReason = "projectId cannot be blank in GetChangeHistoryRequest.",
                startedAt = started
            )
        }

        val revisionSnapshot = try {
            revisionProvider.getCurrentRevision(projectId)
        } catch (e: NoSuchElementException) {
            null
        } catch (t: Throwable) {
            null
        }

        if (revisionSnapshot == null) {
            return ToolResultEnvelope.incomplete(
                requestId = request.requestId,
                toolName = toolName,
                projectId = projectId,
                missingDataReason = "Project '$projectId' not found or revision is unrecorded.",
                startedAt = started
            )
        }

        val result = ChangeHistoryResult(
            projectId = projectId,
            currentRevision = revisionSnapshot.revision,
            lastModifiedTimestamp = revisionSnapshot.updatedAt,
            isDetailedAuditLogAvailable = false,
            notice = "Granular historical audit event logging is deferred to Sub-Phase 3E. Current project revision head (${revisionSnapshot.revision}) is authoritative."
        )

        return ToolResultEnvelope.pass(
            requestId = request.requestId,
            toolName = toolName,
            projectId = projectId,
            projectRevision = revisionSnapshot.revision,
            result = result,
            provenance = ProvenanceMetadata(
                engineName = "REVISION_TRACKER",
                engineVersion = "1.0",
                projectId = projectId,
                projectRevision = revisionSnapshot.revision,
                calculatedAt = revisionSnapshot.updatedAt ?: System.currentTimeMillis()
            ),
            startedAt = started
        )
    }
}
