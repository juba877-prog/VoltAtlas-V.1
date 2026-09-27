package com.example.ai.hybrid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hardening test for C2 and C3:
 * Verifies that GEMINI_API_KEY is never injected into release BuildConfig,
 * and build.gradle.kts strictly enforces empty keys and default-closed cloud LLM.
 */
class ReleaseBuildConfigSafetyTest {

    @Test
    fun releaseBuildConfig_mustExistAndHaveEmptyGeminiApiKeyAndDisabledCloudLlm() {
        // Assert that release BuildConfig was generated and exists
        val releaseBuildConfig = File("build/generated/source/buildConfig/release/com/example/BuildConfig.java")
        assertTrue(
            "release BuildConfig must exist at ${releaseBuildConfig.absolutePath}. Ensure generateReleaseBuildConfig runs before tests.",
            releaseBuildConfig.exists()
        )

        val content = releaseBuildConfig.readText()
        assertTrue(
            "Release BuildConfig must have empty GEMINI_API_KEY string but was:\n$content",
            content.contains("String GEMINI_API_KEY = \"\";")
        )
        assertTrue(
            "Release BuildConfig must have ENABLE_CLOUD_LLM = false but was:\n$content",
            content.contains("boolean ENABLE_CLOUD_LLM = false;")
        )
        assertFalse(
            "Release BuildConfig must never contain placeholder_for_secrets",
            content.contains("placeholder_for_secrets")
        )
    }

    @Test
    fun buildScript_releaseBuildType_hasNoGeminiApiKeyInjection() {
        val buildScript = File("build.gradle.kts")
        assertTrue("app/build.gradle.kts must exist", buildScript.exists())
        val content = buildScript.readText()

        // Extract release block in buildTypes
        val releaseIndex = content.indexOf("release {")
        assertTrue("buildTypes.release must be defined in build.gradle.kts", releaseIndex != -1)
        val releaseBlock = content.substring(releaseIndex, content.indexOf("}", releaseIndex))

        assertFalse(
            "buildTypes.release must not contain buildConfigField for GEMINI_API_KEY",
            releaseBlock.contains("GEMINI_API_KEY")
        )
    }
}
