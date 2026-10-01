package com.example.ai.service

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

// P1-3 (M-01..03, DC-1..3): the `EngineeringAiService` interface and its
// `FirebaseAiServiceImpl` echo implementation (canned "Phase 1 ready" text beside the real
// pipeline) were deleted here — both were unreachable (ref-search proof: zero references
// outside themselves across main/test/androidTest and all build files). This file is kept
// because [MessageRole], [AssistantMessage] and [AssistantResponse] are imported by the live
// assistant UI (AssistantConversationOrder, EngineeringAssistantSheet,
// EngineeringAssistantViewModel) and its tests. EchoStubArchitectureGuardTest asserts the
// echo surfaces never come back.
