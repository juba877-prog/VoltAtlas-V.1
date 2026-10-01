package com.example.ai.hybrid

/**
 * Supported AI Providers in VoltAtlas.
 *
 * VoltAtlas supports Bring-Your-Own-Key (BYOK) public release architecture:
 * - [DIRECT_GEMINI]: User supplies their personal Gemini API key, stored encrypted locally
 *   and transmitted directly to Google's Gemini API endpoints.
 * - [FIREBASE_AI_LOGIC]: Managed enterprise Firebase AI Logic SDK path.
 * - [LOCAL_ONLY]: Offline deterministic rule engine only (zero network transmission).
 */
enum class AiProvider(val id: String, val displayName: String) {
    DIRECT_GEMINI("direct_gemini", "Gemini API (Personal Credential)"),
    FIREBASE_AI_LOGIC("firebase_ai_logic", "Firebase AI Logic"),
    LOCAL_ONLY("local_only", "Local Rule Engine (Offline)");

    companion object {
        fun fromId(id: String?): AiProvider = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DIRECT_GEMINI
    }
}

/**
 * Phase 6 Batch 4 / G-6 — what a "successful" connection test actually PROVED.
 *
 * The settings badge previously said "● Connected" after a tools-free ping, which reads as
 * "the assistant works". It only proves that a key authenticates and the endpoint is reachable.
 * This enum records the real scope so the claim cannot silently outrun the evidence.
 *
 * The hierarchy is cumulative; each level strictly requires the previous one:
 *   CONFIGURED           - a key is stored and readable (no network involved at all)
 *   CONNECTIVITY_OK      - a real authenticated request to the configured model succeeded
 *   ASSISTANT_RUNTIME_OK - the assistant request shape (system instruction + tools) was accepted
 *   TOOL_RUNTIME_OK      - Gemini requested a deterministic tool and its result returned cleanly
 *   GROUNDED_RESPONSE_OK - the final response satisfied the existing evidence/grounding rules
 *
 * `testConnection()` deliberately establishes CONNECTIVITY_OK and no more. Claiming a higher
 * scope without having executed the agentic loop would be exactly the "fake Connected" state
 * this batch exists to eliminate.
 */
enum class GeminiVerificationScope {
    CONFIGURED,
    CONNECTIVITY_OK,
    ASSISTANT_RUNTIME_OK,
    TOOL_RUNTIME_OK,
    GROUNDED_RESPONSE_OK
}

/**
 * Result status for testing provider connectivity.
 */
enum class ConnectionTestStatus {
    NOT_CONFIGURED,
    TESTING,
    CONNECTED,
    INVALID_CREDENTIAL,
    RATE_LIMITED,
    NETWORK_ERROR,
    TIMEOUT,
    MODEL_UNAVAILABLE,
    PROVIDER_ERROR
}

/**
 * Result of a connection test against the Gemini API.
 */
data class ConnectionTestResult(
    val status: ConnectionTestStatus,
    val message: String,
    /**
     * Phase 6 Batch 4 / G-6: the strongest statement this test actually established.
     *
     * `testConnection()` sets [GeminiVerificationScope.CONNECTIVITY_OK] on success — it performs
     * a minimal authenticated generateContent call. It deliberately does NOT claim
     * ASSISTANT_RUNTIME_OK / TOOL_RUNTIME_OK / GROUNDED_RESPONSE_OK, because it does not run the
     * agentic tool loop. Defaults to [GeminiVerificationScope.CONFIGURED] so every pre-existing
     * construction site keeps compiling with its previous (weaker, honest) meaning.
     */
    val verifiedScope: GeminiVerificationScope = GeminiVerificationScope.CONFIGURED
)

/**
 * Immutable UI state for the AI Settings surface.
 */
data class AiSettingsState(
    val isOpen: Boolean = false,
    val selectedProvider: AiProvider = AiProvider.DIRECT_GEMINI,
    val hasCredential: Boolean = false,
    val maskedCredential: String = "",
    val modelName: String = AiModelConfig.DEFAULT_MODEL_NAME,
    val connectionStatus: ConnectionTestStatus = ConnectionTestStatus.NOT_CONFIGURED,
    val statusMessage: String? = null,
    val isTestingConnection: Boolean = false
)
