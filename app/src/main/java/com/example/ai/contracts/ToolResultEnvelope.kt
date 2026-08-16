package com.example.ai.contracts

import com.squareup.moshi.JsonClass

/**
 * Generic, typed, versionable result envelope for engineering tool outcomes.
 *
 * @param T The typed, serialization-safe result payload for the specific tool.
 */
@JsonClass(generateAdapter = true)
data class ToolResultEnvelope<T>(
    val contractVersion: Int = 1,
    val requestId: String,
    val toolName: String,
    val projectId: String,
    val projectRevision: Long? = null,
    val status: ToolStatus,
    val result: T? = null,
    val checks: List<EngineeringCheck> = emptyList(),
    val errors: List<StructuredError> = emptyList(),
    val assumptions: List<EngineeringAssumption> = emptyList(),
    val provenance: ProvenanceMetadata? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = System.currentTimeMillis()
) {
    init {
        when (status) {
            ToolStatus.PASS, ToolStatus.FAIL, ToolStatus.WARNING -> {
                require(result != null) {
                    "ToolResultEnvelope with status $status requires a non-null result payload."
                }
                require(errors.isEmpty()) {
                    "ToolResultEnvelope with status $status cannot carry runtime or execution errors."
                }
                require(completedAt != null) {
                    "ToolResultEnvelope with completed status $status requires a non-null completedAt timestamp."
                }
            }
            ToolStatus.ERROR,
            ToolStatus.INVALID_INPUT,
            ToolStatus.INCOMPLETE,
            ToolStatus.NOT_SUPPORTED -> {
                require(result == null) {
                    "ToolResultEnvelope with execution failure status $status must not contain a result payload."
                }
                require(errors.isNotEmpty()) {
                    "ToolResultEnvelope with status $status must contain at least one StructuredError."
                }
                require(completedAt != null) {
                    "ToolResultEnvelope with terminal status $status requires a non-null completedAt timestamp."
                }
            }
            ToolStatus.CANCELLED -> {
                require(result == null) {
                    "ToolResultEnvelope with status CANCELLED must not contain a result payload."
                }
                require(completedAt != null) {
                    "ToolResultEnvelope with status CANCELLED requires a non-null completedAt timestamp."
                }
            }
            ToolStatus.RUNNING, ToolStatus.REVALIDATING -> {
                require(result == null) {
                    "ToolResultEnvelope with in-progress status $status must not contain a result payload."
                }
                require(completedAt == null) {
                    "ToolResultEnvelope with in-progress status $status cannot have completedAt timestamp set."
                }
            }
            ToolStatus.STALE -> {
                if (result != null) {
                    require(provenance != null) {
                        "ToolResultEnvelope with status STALE carrying a cached result must include provenance metadata."
                    }
                }
            }
        }
    }

    companion object {
        /**
         * Factory for completed deterministic engineering executions (PASS, WARNING, FAIL).
         * Requires a non-null result and rejects execution failure statuses.
         */
        fun <T> completed(
            requestId: String,
            toolName: String,
            projectId: String,
            status: ToolStatus,
            result: T,
            projectRevision: Long? = null,
            checks: List<EngineeringCheck> = emptyList(),
            assumptions: List<EngineeringAssumption> = emptyList(),
            provenance: ProvenanceMetadata? = null,
            startedAt: Long? = null,
            completedAt: Long = System.currentTimeMillis()
        ): ToolResultEnvelope<T> {
            require(status == ToolStatus.PASS || status == ToolStatus.WARNING || status == ToolStatus.FAIL) {
                "completed factory only accepts completed engineering-outcome statuses (PASS, WARNING, FAIL), got: $status"
            }
            return ToolResultEnvelope(
                contractVersion = 1,
                requestId = requestId,
                toolName = toolName,
                projectId = projectId,
                projectRevision = projectRevision,
                status = status,
                result = result,
                checks = checks,
                errors = emptyList(),
                assumptions = assumptions,
                provenance = provenance,
                startedAt = startedAt,
                completedAt = completedAt
            )
        }

        /**
         * Factory for execution failures (ERROR, INVALID_INPUT, INCOMPLETE, NOT_SUPPORTED).
         * Rejects completed outcome statuses (PASS, WARNING) and in-progress statuses (RUNNING, REVALIDATING, STALE).
         */
        fun <T> executionFailure(
            requestId: String,
            toolName: String,
            projectId: String,
            status: ToolStatus,
            errors: List<StructuredError>,
            projectRevision: Long? = null,
            startedAt: Long? = null,
            completedAt: Long = System.currentTimeMillis()
        ): ToolResultEnvelope<T> {
            require(status != ToolStatus.PASS && status != ToolStatus.WARNING && status != ToolStatus.RUNNING && status != ToolStatus.REVALIDATING && status != ToolStatus.STALE) {
                "executionFailure factory rejects non-terminal or passing statuses (PASS, WARNING, RUNNING, REVALIDATING, STALE), got: $status"
            }
            require(errors.isNotEmpty()) {
                "executionFailure factory requires at least one StructuredError."
            }
            return ToolResultEnvelope(
                contractVersion = 1,
                requestId = requestId,
                toolName = toolName,
                projectId = projectId,
                projectRevision = projectRevision,
                status = status,
                result = null,
                checks = emptyList(),
                errors = errors,
                assumptions = emptyList(),
                provenance = null,
                startedAt = startedAt,
                completedAt = completedAt
            )
        }

        fun <T> pass(
            requestId: String,
            toolName: String,
            projectId: String,
            result: T,
            projectRevision: Long? = null,
            checks: List<EngineeringCheck> = emptyList(),
            assumptions: List<EngineeringAssumption> = emptyList(),
            provenance: ProvenanceMetadata? = null,
            startedAt: Long? = null,
            completedAt: Long = System.currentTimeMillis()
        ): ToolResultEnvelope<T> {
            return completed(
                requestId = requestId,
                toolName = toolName,
                projectId = projectId,
                status = ToolStatus.PASS,
                result = result,
                projectRevision = projectRevision,
                checks = checks,
                assumptions = assumptions,
                provenance = provenance,
                startedAt = startedAt,
                completedAt = completedAt
            )
        }

        fun <T> fail(
            requestId: String,
            toolName: String,
            projectId: String,
            result: T,
            projectRevision: Long? = null,
            checks: List<EngineeringCheck> = emptyList(),
            assumptions: List<EngineeringAssumption> = emptyList(),
            provenance: ProvenanceMetadata? = null,
            startedAt: Long? = null,
            completedAt: Long = System.currentTimeMillis()
        ): ToolResultEnvelope<T> {
            return completed(
                requestId = requestId,
                toolName = toolName,
                projectId = projectId,
                status = ToolStatus.FAIL,
                result = result,
                projectRevision = projectRevision,
                checks = checks,
                assumptions = assumptions,
                provenance = provenance,
                startedAt = startedAt,
                completedAt = completedAt
            )
        }

        fun <T> warning(
            requestId: String,
            toolName: String,
            projectId: String,
            result: T,
            projectRevision: Long? = null,
            checks: List<EngineeringCheck> = emptyList(),
            assumptions: List<EngineeringAssumption> = emptyList(),
            provenance: ProvenanceMetadata? = null,
            startedAt: Long? = null,
            completedAt: Long = System.currentTimeMillis()
        ): ToolResultEnvelope<T> {
            return completed(
                requestId = requestId,
                toolName = toolName,
                projectId = projectId,
                status = ToolStatus.WARNING,
                result = result,
                projectRevision = projectRevision,
                checks = checks,
                assumptions = assumptions,
                provenance = provenance,
                startedAt = startedAt,
                completedAt = completedAt
            )
        }

        fun <T> incomplete(
            requestId: String,
            toolName: String,
            projectId: String,
            missingDataReason: String,
            failingParameter: String? = null,
            projectRevision: Long? = null,
            startedAt: Long? = null,
            completedAt: Long = System.currentTimeMillis()
        ): ToolResultEnvelope<T> {
            val error = StructuredError(
                errorCode = "INCOMPLETE_PROJECT_DATA",
                message = missingDataReason,
                failingParameter = failingParameter,
                isRecoverable = true,
                remediationGuidance = "Provide the missing project input or upstream source."
            )
            return executionFailure(
                requestId = requestId,
                toolName = toolName,
                projectId = projectId,
                status = ToolStatus.INCOMPLETE,
                errors = listOf(error),
                projectRevision = projectRevision,
                startedAt = startedAt,
                completedAt = completedAt
            )
        }

        fun <T> invalidInput(
            requestId: String,
            toolName: String,
            projectId: String,
            errors: List<StructuredError>,
            projectRevision: Long? = null,
            startedAt: Long? = null,
            completedAt: Long = System.currentTimeMillis()
        ): ToolResultEnvelope<T> {
            return executionFailure(
                requestId = requestId,
                toolName = toolName,
                projectId = projectId,
                status = ToolStatus.INVALID_INPUT,
                errors = errors,
                projectRevision = projectRevision,
                startedAt = startedAt,
                completedAt = completedAt
            )
        }

        fun <T> error(
            requestId: String,
            toolName: String,
            projectId: String,
            errors: List<StructuredError>,
            projectRevision: Long? = null,
            startedAt: Long? = null,
            completedAt: Long = System.currentTimeMillis()
        ): ToolResultEnvelope<T> {
            return executionFailure(
                requestId = requestId,
                toolName = toolName,
                projectId = projectId,
                status = ToolStatus.ERROR,
                errors = errors,
                projectRevision = projectRevision,
                startedAt = startedAt,
                completedAt = completedAt
            )
        }

        fun <T> notSupported(
            requestId: String,
            toolName: String,
            projectId: String,
            reason: String,
            projectRevision: Long? = null,
            startedAt: Long? = null,
            completedAt: Long = System.currentTimeMillis()
        ): ToolResultEnvelope<T> {
            val error = StructuredError(
                errorCode = "OPERATION_NOT_SUPPORTED",
                message = reason,
                isRecoverable = false
            )
            return executionFailure(
                requestId = requestId,
                toolName = toolName,
                projectId = projectId,
                status = ToolStatus.NOT_SUPPORTED,
                errors = listOf(error),
                projectRevision = projectRevision,
                startedAt = startedAt,
                completedAt = completedAt
            )
        }

        fun <T> stale(
            requestId: String,
            toolName: String,
            projectId: String,
            result: T,
            projectRevision: Long? = null,
            checks: List<EngineeringCheck> = emptyList(),
            assumptions: List<EngineeringAssumption> = emptyList(),
            provenance: ProvenanceMetadata,
            startedAt: Long? = null,
            completedAt: Long = System.currentTimeMillis()
        ): ToolResultEnvelope<T> {
            return ToolResultEnvelope(
                contractVersion = 1,
                requestId = requestId,
                toolName = toolName,
                projectId = projectId,
                projectRevision = projectRevision,
                status = ToolStatus.STALE,
                result = result,
                checks = checks,
                errors = emptyList(),
                assumptions = assumptions,
                provenance = provenance,
                startedAt = startedAt,
                completedAt = completedAt
            )
        }

        fun <T> cancelled(
            requestId: String,
            toolName: String,
            projectId: String,
            projectRevision: Long? = null,
            startedAt: Long? = null,
            completedAt: Long = System.currentTimeMillis()
        ): ToolResultEnvelope<T> {
            return ToolResultEnvelope(
                contractVersion = 1,
                requestId = requestId,
                toolName = toolName,
                projectId = projectId,
                projectRevision = projectRevision,
                status = ToolStatus.CANCELLED,
                result = null,
                startedAt = startedAt,
                completedAt = completedAt
            )
        }
    }
}
