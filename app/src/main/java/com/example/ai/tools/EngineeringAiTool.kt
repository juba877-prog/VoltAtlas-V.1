package com.example.ai.tools

import com.example.ai.context.EngineeringContext

/**
 * Execution result returned by an Engineering AI Tool wrapper.
 */
sealed class ToolExecutionResult {
    data class Success(val resultJson: String) : ToolExecutionResult()
    data class Failure(val errorMessage: String) : ToolExecutionResult()
}

/**
 * Interface definition for deterministic calculation tools exposed to the AI orchestrator.
 * Each tool wraps an existing Kotlin engine in com.example.engine with strict input validation.
 */
interface EngineeringAiTool {
    val name: String
    val description: String
    val parameterSchemaJson: String

    suspend fun execute(
        context: EngineeringContext,
        arguments: Map<String, Any?>
    ): ToolExecutionResult
}
