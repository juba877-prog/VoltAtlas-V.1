package com.example.ai.tools.read

import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.contracts.ToolStatus
import com.example.ai.task.EngineeringTaskKey
import com.example.ai.task.EngineeringTaskManager
import com.example.ai.task.EngineeringTaskType
import com.example.ai.task.TaskOwner

/**
 * Deterministic, no-LLM internal invoker for executing read tools without Gemini.
 *
 * Guarantees:
 * 1. Type safety: Type mismatches or malformed payloads return typed INVALID_INPUT / ERROR envelopes,
 *    never throwing ClassCastException or serialization errors through the public boundary.
 * 2. Canonical Request Signatures: Automatically computes cryptographic canonical deterministic request signatures
 *    using [ReadRequestSignatureEncoder] to eliminate false deduplication from 32-bit hash collisions.
 * 3. Fail-Closed Managed Execution: If signature encoding fails, managed execution immediately returns
 *    a typed ERROR envelope without submitting any task to [EngineeringTaskManager] or running tool execution.
 * 4. Managed Asynchronous Execution: Dispatches safely via [EngineeringTaskManager] with
 *    latest-task-wins supersession and multi-subscriber safety.
 */
class DeterministicReadToolInvoker(
    private val registry: DeterministicReadToolRegistry,
    private val signatureEncoder: ReadRequestSignatureEncoder = DefaultReadRequestSignatureEncoder()
) {

    /**
     * Executes a tool request synchronously/directly without TaskManager overhead.
     * Direct execution does not compute request signatures or perform deduplication.
     */
    suspend fun <TReq, TRes> executeDirect(
        request: ToolRequestEnvelope<TReq>
    ): ToolResultEnvelope<TRes> {
        val tool = registry.getTool<TReq, TRes>(request.toolName)
            ?: return ToolResultEnvelope.notSupported(
                requestId = request.requestId,
                toolName = request.toolName,
                projectId = request.projectId,
                reason = "Tool '${request.toolName}' is not registered in the deterministic read tool registry."
            )

        return try {
            tool.execute(request)
        } catch (e: ClassCastException) {
            ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = request.toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_REQUEST_PAYLOAD",
                        message = "Tool request payload does not match the expected contract for '${request.toolName}'.",
                        isRecoverable = false
                    )
                )
            )
        } catch (e: IllegalArgumentException) {
            ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = request.toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_REQUEST_PARAMETERS",
                        message = e.message ?: "Invalid request parameters.",
                        isRecoverable = true
                    )
                )
            )
        } catch (t: Throwable) {
            ToolResultEnvelope.executionFailure(
                requestId = request.requestId,
                toolName = request.toolName,
                projectId = request.projectId,
                status = ToolStatus.ERROR,
                errors = listOf(
                    StructuredError(
                        errorCode = "TOOL_EXECUTION_ERROR",
                        message = "An unexpected error occurred while executing read tool '${request.toolName}'.",
                        isRecoverable = false
                    )
                )
            )
        }
    }

    /**
     * Executes a tool request asynchronously through [EngineeringTaskManager] with
     * project-scoped deduplication and latest-task-wins supersession.
     *
     * Note: Caller-provided [requestSignature] parameter is ignored; canonical SHA-256
     * signatures are computed internally from typed request envelopes. If signature
     * computation fails, managed execution fails closed without submitting any task.
     */
    suspend fun <TReq, TRes> executeManaged(
        request: ToolRequestEnvelope<TReq>,
        taskManager: EngineeringTaskManager,
        owner: TaskOwner = TaskOwner.SYSTEM,
        requestSignature: String? = null
    ): ToolResultEnvelope<TRes> {
        val tool = registry.getTool<TReq, TRes>(request.toolName)
            ?: return ToolResultEnvelope.notSupported(
                requestId = request.requestId,
                toolName = request.toolName,
                projectId = request.projectId,
                reason = "Tool '${request.toolName}' is not registered in the deterministic read tool registry."
            )

        // Fail-closed signature computation: must succeed before any task is submitted
        val signatureResult = signatureEncoder.computeSignature(request)
        val canonicalSignature = when (signatureResult) {
            is RequestSignatureResult.Success -> signatureResult.signature
            is RequestSignatureResult.Failure -> {
                return ToolResultEnvelope.executionFailure(
                    requestId = request.requestId,
                    toolName = request.toolName,
                    projectId = request.projectId,
                    status = ToolStatus.ERROR,
                    errors = listOf(signatureResult.error)
                )
            }
        }

        val taskKey = EngineeringTaskKey(
            projectId = request.projectId,
            taskType = EngineeringTaskType.CUSTOM,
            entityType = request.entityType,
            entityId = request.entityId,
            requestVariant = request.toolName
        )

        val handle = taskManager.submit<TRes>(
            key = taskKey,
            owner = owner,
            requestSignature = canonicalSignature
        ) { _ ->
            executeDirect(request)
        }

        return handle.awaitResult()
    }
}
