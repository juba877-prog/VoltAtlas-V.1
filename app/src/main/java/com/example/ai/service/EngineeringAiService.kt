package com.example.ai.service

import com.example.ai.context.EngineeringContext

/**
 * Message role in the assistant conversation stream.
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

/**
 * Represents a single message in the engineering assistant conversation.
 */
data class AssistantMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Result returned by the AI service abstraction.
 */
sealed class AssistantResponse {
    data class Text(val content: String) : AssistantResponse()
    data class Error(val message: String) : AssistantResponse()
}

/**
 * Interface abstraction for AI / LLM orchestration.
 * Strict boundary: The implementation receives an immutable EngineeringContext
 * and delegates deterministic calculations to local engine tools.
 */
interface EngineeringAiService {
    suspend fun sendUserMessage(
        context: EngineeringContext,
        conversationHistory: List<AssistantMessage>,
        userText: String
    ): AssistantResponse
}
