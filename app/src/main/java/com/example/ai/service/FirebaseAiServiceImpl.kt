package com.example.ai.service

import com.example.ai.context.EngineeringContext

/**
 * Baseline implementation of EngineeringAiService.
 * In Phase 1 (Infrastructure), this acts as a compilable service boundary.
 * Full Gemini / Firebase Vertex AI SDK integration and streaming will be wired in Phase 2+.
 */
class FirebaseAiServiceImpl : EngineeringAiService {

    override suspend fun sendUserMessage(
        context: EngineeringContext,
        conversationHistory: List<AssistantMessage>,
        userText: String
    ): AssistantResponse {
        val trimmed = userText.trim()
        if (trimmed.isBlank()) {
            return AssistantResponse.Error("Message cannot be empty.")
        }

        // Phase 1 infrastructure response acknowledging active engineering context
        val contextInfo = when {
            context.activeEntityType != null -> "focusing on ${context.activeEntityType} '${context.activeEntityName ?: context.activeEntityId}'"
            context.projectId != null -> "for project '${context.projectName}' (${context.currentScreen} screen)"
            else -> "ready to assist with electrical engineering calculations"
        }

        return AssistantResponse.Text(
            "VoltAtlas Engineering Assistant initialized ($contextInfo).\n\n" +
            "Received query: \"$trimmed\"\n\n" +
            "(AI Infrastructure Phase 1 ready. Deterministic calculation tool wiring will be active in Phase 2.)"
        )
    }
}
