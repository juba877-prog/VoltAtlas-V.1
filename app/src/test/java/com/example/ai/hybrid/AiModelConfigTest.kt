package com.example.ai.hybrid

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AiModelConfig] verifying defaults, external configuration,
 * and test overrides.
 */
class AiModelConfigTest {

    @Before
    fun setUp() {
        System.clearProperty("voltatlas.ai.model")
        AiModelConfig.resetToDefaults()
    }

    @After
    fun tearDown() {
        System.clearProperty("voltatlas.ai.model")
        AiModelConfig.resetToDefaults()
    }

    @Test
    fun defaultConfiguration_hasExpectedConstants() {
        val config = AiModelConfig()

        assertEquals("gemini-2.5-flash", config.modelName)
        assertEquals(0.2f, config.temperature, 0.001f)
        assertEquals(0.95f, config.topP, 0.001f)
        assertEquals(40, config.topK)
        assertEquals(2048, config.maxOutputTokens)
        assertEquals(10_000L, config.timeoutMs)
        assertEquals("firebase_ai_logic", config.provider)
    }

    @Test
    fun current_resolvesDefault_whenNoEnvironmentOrPropertySet() {
        val config = AiModelConfig.current()

        assertEquals("gemini-2.5-flash", config.modelName)
    }

    @Test
    fun current_resolvesSystemPropertyOverride() {
        System.setProperty("voltatlas.ai.model", "gemini-2.5-pro")

        val config = AiModelConfig.current()

        assertEquals("gemini-2.5-pro", config.modelName)
    }

    @Test
    fun current_blankSystemProperty_fallsBackToDefault() {
        System.setProperty("voltatlas.ai.model", "   ")

        val config = AiModelConfig.current()

        assertEquals("gemini-2.5-flash", config.modelName)
    }

    @Test
    fun configureForTesting_overridesCurrent() {
        val testConfig = AiModelConfig(
            modelName = "custom-test-model",
            temperature = 0.7f,
            timeoutMs = 5000L
        )

        AiModelConfig.configureForTesting(testConfig)
        assertEquals("custom-test-model", AiModelConfig.current().modelName)
        assertEquals(0.7f, AiModelConfig.current().temperature, 0.001f)
        assertEquals(5000L, AiModelConfig.current().timeoutMs)

        AiModelConfig.resetToDefaults()
        assertEquals("gemini-2.5-flash", AiModelConfig.current().modelName)
    }

    @Test
    fun copy_preservesUnchangedFields() {
        val base = AiModelConfig()
        val copied = base.copy(modelName = "gemini-2.5-flash-lite")

        assertEquals("gemini-2.5-flash-lite", copied.modelName)
        assertEquals(base.temperature, copied.temperature, 0.001f)
        assertEquals(base.topP, copied.topP, 0.001f)
        assertEquals(base.topK, copied.topK)
        assertEquals(base.maxOutputTokens, copied.maxOutputTokens)
        assertEquals(base.timeoutMs, copied.timeoutMs)
        assertEquals(base.provider, copied.provider)
    }
}
