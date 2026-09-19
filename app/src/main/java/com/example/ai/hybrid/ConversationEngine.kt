package com.example.ai.hybrid

import com.example.ai.conversation.ConversationTurnDecision
import com.example.ai.conversation.ConversationTurnInput

/**
 * Common conversational engine interface implemented by both Cloud and Local paths.
 */
interface ConversationEngine {
    suspend fun evaluateTurn(
        input: ConversationTurnInput,
        decisionIdProvider: () -> String = { "DEC-" + java.util.UUID.randomUUID().toString().take(8) }
    ): HybridEngineResult
}

/**
 * Authoritative outcome container for a single conversational turn evaluation across hybrid engines.
 */
data class HybridEngineResult(
    val decision: ConversationTurnDecision,
    val source: ConversationEngineSource,
    val cloudDegradationReason: CloudDegradationReason? = null,
    val hasBackingToolResult: Boolean = false
)

/**
 * Identification of the source that produced the decision.
 */
enum class ConversationEngineSource {
    CLOUD_GEMINI,
    ON_DEVICE_GEMINI_NANO,
    LOCAL_RULE_ENGINE
}

/**
 * Explicit reasons for falling back from the Cloud LLM path to the local deterministic rule engine.
 */
enum class CloudDegradationReason {
    OFFLINE_PROBE_FAILED,
    NETWORK_TIMEOUT,
    HTTP_OR_PROVIDER_ERROR,
    SAFETY_BLOCKED,
    INVALID_TOOL_CALL,
    RATE_LIMITED,
    UNSUPPORTED_ENGINEERING_CLAIM
}
