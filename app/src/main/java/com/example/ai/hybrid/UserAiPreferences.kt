package com.example.ai.hybrid

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user-configured AI preferences:
 * - Active [AiProvider] (Direct Gemini, Firebase AI Logic, Local Only)
 * - Target Gemini Model Identifier
 */
class UserAiPreferences(
    private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedProvider(): AiProvider {
        testProviderOverride?.let { return it }
        val id = prefs.getString(KEY_AI_PROVIDER, AiProvider.DIRECT_GEMINI.id)
        return AiProvider.fromId(id)
    }

    fun setSelectedProvider(provider: AiProvider) {
        prefs.edit().putString(KEY_AI_PROVIDER, provider.id).apply()
    }

    fun getSelectedModel(): String {
        testModelOverride?.let { return it }
        return prefs.getString(KEY_AI_MODEL, AiModelConfig.DEFAULT_MODEL_NAME)
            ?: AiModelConfig.DEFAULT_MODEL_NAME
    }

    fun setSelectedModel(model: String) {
        val trimmed = model.trim().ifEmpty { AiModelConfig.DEFAULT_MODEL_NAME }
        prefs.edit().putString(KEY_AI_MODEL, trimmed).apply()
    }

    companion object {
        private const val PREFS_NAME = "voltatlas_user_ai_preferences"
        private const val KEY_AI_PROVIDER = "pref_ai_provider"
        private const val KEY_AI_MODEL = "pref_ai_model"

        private var testProviderOverride: AiProvider? = null
        private var testModelOverride: String? = null

        fun setTestOverrides(provider: AiProvider? = null, model: String? = null) {
            testProviderOverride = provider
            testModelOverride = model
        }

        fun resetTestOverrides() {
            testProviderOverride = null
            testModelOverride = null
        }
    }
}
