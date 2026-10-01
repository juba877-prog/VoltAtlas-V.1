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
 * Phase 6 Batch 4 / G-4 — precise classification of a provider failure.
 *
 * Previously every non-rate-limit provider failure collapsed into a single
 * `ProviderError` carrying only a message, and the engine mapped all of them to
 * `CloudDegradationReason.HTTP_OR_PROVIDER_ERROR`, which the router then degraded to the local
 * engine and the UI rendered as the same "OFFLINE" badge. An expired key, an unavailable model,
 * a malformed request and a 5xx were therefore indistinguishable to both the user and the logs.
 *
 * This enum classifies the failure at the point where the real evidence exists (the HTTP status
 * and the provider's own error body) and travels with the error to the degradation mapping.
 * It does NOT introduce a parallel error system: the categories map onto the existing
 * [CloudDegradationReason] vocabulary.
 */
enum class CloudProviderFailureCategory {
    /** HTTP 401/403, or a provider body indicating an invalid/unauthorized key. */
    AUTHENTICATION_FAILURE,

    /** HTTP 429 / RESOURCE_EXHAUSTED. */
    RATE_LIMITED,

    /** HTTP 404, or a provider body indicating the requested model does not exist. */
    MODEL_UNAVAILABLE,

    /** HTTP 400 — the request itself was rejected (schema/parameter/protocol violation). */
    REQUEST_INVALID,

    /** HTTP 5xx — the provider failed to serve a valid request. */
    SERVER_ERROR,

    /** DNS/socket/connectivity failure that is not a timeout. */
    NETWORK_ERROR,

    /** Unclassified provider error. */
    UNKNOWN
}

/**
 * Strongly-typed representation of an operational tool call emitted by the model.
 *
 * Invariants (Gate 5 Corrections):
 * - Preserves typed JsonElements (does NOT flatten all values to strings).
 * - No chain-of-thought or thoughtSummary fields stored or exposed.
 *
 * Phase 6 Batch 4 / G-2: [thoughtSignature] carries Gemini 3's opaque reasoning signature
 * verbatim. Google's `generateContent` `Part` schema defines `thoughtSignature` as a field of
 * the Part (a SIBLING of `functionCall`), documented as "An opaque signature for the thought so
 * it can be reused in subsequent requests. A base64-encoded string." Because VoltAtlas manages
 * conversation state manually (stateless REST calls), the signature MUST be captured from the
 * response and replayed byte-identically on the next request or Gemini 3 loses reasoning
 * continuity across tool-call rounds.
 *
 * It is stored as an OPAQUE string: never decoded, re-encoded, regenerated, or merged into any
 * other field. `null` means "the provider sent no signature for this call", which is a valid and
 * common state; absence is represented explicitly rather than fabricated.
 *
 * Note this is NOT chain-of-thought content — it is a cryptographic signature the client cannot
 * read, and the "no thoughtSummary exposed" invariant above is preserved.
 */
data class ToolCall(
    val callId: String = java.util.UUID.randomUUID().toString(),
    val toolName: String,
    val arguments: Map<String, JsonElement> = emptyMap(),
    val thoughtSignature: String? = null
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
        val isRateLimited: Boolean = false,
        /**
         * Phase 6 Batch 4 / G-4: precise failure classification. Defaults to [UNKNOWN] so any
         * existing construction site keeps compiling and behaves as before; the seam sets the
         * real value derived from the HTTP status and the provider's error body.
         */
        val category: CloudProviderFailureCategory = CloudProviderFailureCategory.UNKNOWN,
        /**
         * The real HTTP status when one was received, preserved for diagnostics (never logged
         * together with the credential).
         */
        val httpStatus: Int? = null
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
