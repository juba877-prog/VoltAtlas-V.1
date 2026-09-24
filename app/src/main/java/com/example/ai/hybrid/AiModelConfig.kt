package com.example.ai.hybrid

/**
 * Authoritative, strongly-typed configuration abstraction for Cloud AI (Gemini / Firebase AI Logic).
 *
 * Responsibilities:
 * - Owns model identity, generation parameters, network timeouts, and token limits.
 * - Single point of truth: Prevents scattering model names or generation constants across constructors.
 * - Externally configurable: Resolves model identifier via system properties, environment variables,
 *   or explicit runtime configuration.
 * - Security: Never contains secrets, keys, or credentials.
 */
data class AiModelConfig(
    val modelName: String = DEFAULT_MODEL_NAME,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val topP: Float = DEFAULT_TOP_P,
    val topK: Int = DEFAULT_TOP_K,
    val maxOutputTokens: Int? = DEFAULT_MAX_OUTPUT_TOKENS,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val provider: String = PROVIDER_FIREBASE_AI_LOGIC
) {
    companion object {
        const val PROVIDER_FIREBASE_AI_LOGIC = "firebase_ai_logic"

        /**
         * Default model identifier: Official Gemini Flash model supported by the Firebase AI Logic SDK.
         */
        const val DEFAULT_MODEL_NAME = "gemini-2.5-flash"

        const val DEFAULT_TEMPERATURE = 0.2f
        const val DEFAULT_TOP_P = 0.95f
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_MAX_OUTPUT_TOKENS = 2048
        const val DEFAULT_TIMEOUT_MS = 10_000L

        private var customSupplier: (() -> AiModelConfig)? = null

        /**
         * Resolves the active model configuration.
         * Checks custom supplier -> system property -> environment variable -> defaults.
         */
        fun current(): AiModelConfig {
            customSupplier?.let { return it() }

            val externalModel = System.getProperty("voltatlas.ai.model")
                ?: System.getenv("VOLTATLAS_AI_MODEL")
                ?: DEFAULT_MODEL_NAME

            return AiModelConfig(
                modelName = externalModel.ifBlank { DEFAULT_MODEL_NAME }
            )
        }

        fun configureForTesting(config: AiModelConfig) {
            customSupplier = { config }
        }

        fun resetToDefaults() {
            customSupplier = null
        }
    }
}
