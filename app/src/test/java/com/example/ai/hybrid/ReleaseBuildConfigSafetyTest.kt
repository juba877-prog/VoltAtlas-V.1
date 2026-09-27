package com.example.ai.hybrid

import com.example.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hardening test for C2 and C3:
 * Verifies that GEMINI_API_KEY is not baked into the BuildConfig, and cloud LLM is disabled.
 */
class ReleaseBuildConfigSafetyTest {

    @Test
    fun buildConfig_geminiApiKey_isEmptyInBuildConfig() {
        assertEquals("", BuildConfig.GEMINI_API_KEY)
    }

    @Test
    fun buildConfig_cloudLlm_isDefaultClosed() {
        assertFalse(BuildConfig.ENABLE_CLOUD_LLM)
    }

    @Test
    fun releaseBuildConfig_whenGenerated_hasEmptyGeminiApiKeyAndDisabledCloudLlm() {
        // Check generated release BuildConfig.java if it exists in the build directory
        val releaseBuildConfig = File("build/generated/source/buildConfig/release/com/example/BuildConfig.java")
        if (releaseBuildConfig.exists()) {
            val content = releaseBuildConfig.readText()
            assertTrue(
                "Release BuildConfig must have empty GEMINI_API_KEY string",
                content.contains("String GEMINI_API_KEY = \"\";")
            )
            assertTrue(
                "Release BuildConfig must have ENABLE_CLOUD_LLM = false",
                content.contains("boolean ENABLE_CLOUD_LLM = false;")
            )
            assertFalse(
                "Release BuildConfig must never contain placeholder_for_secrets",
                content.contains("placeholder_for_secrets")
            )
        }
    }
}
