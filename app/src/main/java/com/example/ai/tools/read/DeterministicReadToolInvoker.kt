package com.example.ai.tools.read

import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.task.EngineeringTaskKey
import com.example.ai.task.EngineeringTaskManager
import com.example.ai.task.EngineeringTaskType
import com.example.ai.task.TaskOwner

/**
 * Deterministic, no-LLM internal invoker for executing read tools without Gemini.
 *
 * Supports:
 * 1. Direct pure in-memory execution (zero TaskManager overhead).
 * 2. Managed asynchronous execution via [EngineeringTaskManager] with deduplication and latest-task-wins.
 */
class DeterministicReadToolInvoker(
    private val registry: DeterministicReadToolRegistry
) {

    /**
     * Executes a tool request synchronously/directly without TaskManager overhead.
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

        return tool.execute(request)
    }

    /**
     * Executes a tool request asynchronously through [EngineeringTaskManager] with
     * project-scoped deduplication and latest-task-wins supersession.
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
            requestSignature = requestSignature
        ) { _ ->
            tool.execute(request)
        }

        return handle.awaitResult()
    }
}
