package com.example.ai.tools.cable

import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.contracts.ToolStatus
import com.example.ai.task.EngineeringTaskKey
import com.example.ai.task.EngineeringTaskManager
import com.example.ai.task.EngineeringTaskType
import com.example.ai.task.TaskOwner
import com.example.ai.tools.read.RequestSignatureResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deterministic invoker for cable calculation, candidate evaluation, candidate comparison, and what-if tools.
 *
 * Guarantees:
 * 1. Type safety: Type mismatches or malformed payloads return typed INVALID_INPUT / ERROR envelopes.
 * 2. Fail-Closed Managed Execution: If signature encoding fails, managed execution immediately returns
 *    a typed ERROR envelope without submitting any task to [EngineeringTaskManager] or running tool execution.
 * 3. CPU Dispatching: Heavy calculations are dispatched to [Dispatchers.Default] off the UI thread.
 * 4. Project Isolation & Lifecycle: Managed asynchronous execution via [EngineeringTaskManager] with latest-task-wins.
 */
class DeterministicCableToolInvoker(
    private val registry: DeterministicCableToolRegistry,
    private val signatureEncoder: CableRequestSignatureEncoder = DefaultCableRequestSignatureEncoder()
) {

    /**
     * Executes a tool request synchronously/directly in memory without TaskManager overhead.
     */
    suspend fun <TReq, TRes> executeDirect(
        request: ToolRequestEnvelope<TReq>
    ): ToolResultEnvelope<TRes> = withContext(Dispatchers.Default) {
        val tool = registry.getTool<TReq, TRes>(request.toolName)
            ?: return@withContext ToolResultEnvelope.notSupported(
                requestId = request.requestId,
                toolName = request.toolName,
                projectId = request.projectId,
                reason = "Cable tool '${request.toolName}' is not registered in the deterministic cable tool registry."
            )

        try {
            tool.execute(request)
        } catch (e: ClassCastException) {
            ToolResultEnvelope.invalidInput(
                requestId = request.requestId,
                toolName = request.toolName,
                projectId = request.projectId,
                errors = listOf(
                    StructuredError(
                        errorCode = "INVALID_REQUEST_PAYLOAD",
                        message = "Tool request payload does not match expected contract for '${request.toolName}'.",
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
                        message = "An unexpected error occurred while executing cable tool '${request.toolName}'.",
                        isRecoverable = false
                    )
                )
            )
        }
    }

    /**
     * Executes a tool request asynchronously through [EngineeringTaskManager] with
     * project-scoped deduplication, latest-task-wins supersession, and fail-closed signature verification.
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
                reason = "Cable tool '${request.toolName}' is not registered in the deterministic cable tool registry."
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

        val taskType = when (request.toolName) {
            CableToolNames.CALCULATE_CABLE_SIZING -> EngineeringTaskType.CALCULATE_CABLE_SIZING
            else -> EngineeringTaskType.CUSTOM
        }

        val taskKey = EngineeringTaskKey(
            projectId = request.projectId,
            taskType = taskType,
            entityType = request.entityType ?: "CIRCUIT",
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
