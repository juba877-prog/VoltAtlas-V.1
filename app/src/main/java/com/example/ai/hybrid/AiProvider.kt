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
    val message: String
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
