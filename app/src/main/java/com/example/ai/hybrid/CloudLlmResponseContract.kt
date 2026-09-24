package com.example.ai.hybrid

import kotlinx.serialization.json.JsonElement

/**
 * Strongly-typed states distinguishing Cloud LLM client outcomes (Gate 4B & 5).
 *
 * Avoids encoding failure modes or tool requests into magic strings.
 */
enum class CloudLlmResponseType {
    FINAL_TEXT,
    TOOL_CALL_REQUEST,
    PROVIDER_ERROR,
    MALFORMED_RESPONSE,
    SAFETY_BLOCK,
    CANCELLED,
    TIMEOUT
}

/**
 * Strongly-typed representation of an operational tool call emitted by the model.
 *
 * Invariants (Gate 5 Corrections):
 * - Preserves typed JsonElements (does NOT flatten all values to strings).
 * - No chain-of-thought or thoughtSummary fields stored or exposed.
 */
data class ToolCall(
    val callId: String = java.util.UUID.randomUUID().toString(),
    val toolName: String,
    val arguments: Map<String, JsonElement> = emptyMap()
)

/**
 * Strongly-typed sealed response hierarchy for Cloud LLM client interactions.
 */
sealed interface CloudLlmClientResponse {
    val responseType: CloudLlmResponseType

    data class FinalText(
        val text: String
    ) : CloudLlmClientResponse {
        override val responseType: CloudLlmResponseType get() = CloudLlmResponseType.FINAL_TEXT
    }

    data class ToolCallRequest(
        val calls: List<ToolCall>
    ) : CloudLlmClientResponse {
        constructor(call: ToolCall) : this(listOf(call))

        constructor(toolName: String, arguments: Map<String, JsonElement> = emptyMap(), callId: String = java.util.UUID.randomUUID().toString()) : this(
            calls = listOf(ToolCall(callId = callId, toolName = toolName, arguments = arguments))
        )

        val toolName: String get() = calls.firstOrNull()?.toolName.orEmpty()
        val arguments: Map<String, JsonElement> get() = calls.firstOrNull()?.arguments ?: emptyMap()

        override val responseType: CloudLlmResponseType get() = CloudLlmResponseType.TOOL_CALL_REQUEST
    }

    data class ProviderError(
        val message: String,
        val cause: Throwable? = null,
        val isRateLimited: Boolean = false
    ) : CloudLlmClientResponse {
        override val responseType: CloudLlmResponseType get() = CloudLlmResponseType.PROVIDER_ERROR
    }

    data class MalformedResponse(
        val reason: String
    ) : CloudLlmClientResponse {
        override val responseType: CloudLlmResponseType get() = CloudLlmResponseType.MALFORMED_RESPONSE
    }

    data class SafetyBlock(
        val reason: String = "Response blocked by safety filters."
    ) : CloudLlmClientResponse {
        override val responseType: CloudLlmResponseType get() = CloudLlmResponseType.SAFETY_BLOCK
    }

    data object Cancelled : CloudLlmClientResponse {
        override val responseType: CloudLlmResponseType get() = CloudLlmResponseType.CANCELLED
    }

    data class Timeout(
        val timeoutMs: Long
    ) : CloudLlmClientResponse {
        override val responseType: CloudLlmResponseType get() = CloudLlmResponseType.TIMEOUT
    }
}
