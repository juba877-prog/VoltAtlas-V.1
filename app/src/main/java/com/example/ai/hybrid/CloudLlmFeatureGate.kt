package com.example.ai.hybrid

import com.example.BuildConfig

/**
 * Master kill-switch and configuration gate for Cloud LLM (Gemini) invocation.
 *
 * Invariants:
 * - Default Closed: Must evaluate to false unless explicitly configured with non-blank values.
 * - Dual Check: Both [isCloudLlmEnabled] and [getApiKey] must return valid/active values
 *   before any network client is constructed or invoked.
 */
object CloudLlmFeatureGate {

    // Configurable delegate for runtime injection / test overrides
    private var enabledSupplier: () -> Boolean = {
        try {
            BuildConfig.ENABLE_CLOUD_LLM
        } catch (_: Throwable) {
            false
        }
    }

    private var apiKeySupplier: () -> String? = {
        try {
            BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Returns true if Cloud LLM calls are explicitly authorized by configuration.
     * Defaults to false.
     */
    fun isCloudLlmEnabled(): Boolean = enabledSupplier()

    /**
     * Returns the configured Gemini API key if present and non-blank; otherwise null.
     */
    fun getApiKey(): String? = apiKeySupplier()

    /**
     * Resets suppliers to BuildConfig defaults.
     */
    fun resetToDefaults() {
        enabledSupplier = {
            try {
                BuildConfig.ENABLE_CLOUD_LLM
            } catch (_: Throwable) {
                false
            }
        }
        apiKeySupplier = {
            try {
                BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * Overrides configuration suppliers for isolated unit testing.
     */
    fun configureForTesting(enabled: Boolean, apiKey: String?) {
        enabledSupplier = { enabled }
        apiKeySupplier = { apiKey?.takeIf { it.isNotBlank() } }
    }
}
