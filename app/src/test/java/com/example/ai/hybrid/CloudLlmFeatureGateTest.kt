package com.example.ai.hybrid

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class CloudLlmFeatureGateTest {

    @Before
    fun setUp() {
        CloudLlmFeatureGate.resetToDefaults()
    }

    @After
    fun tearDown() {
        CloudLlmFeatureGate.resetToDefaults()
    }

    // 1. Feature gate defaults to false
    @Test
    fun cloudLlmFeatureGate_defaultsToDisabled() {
        assertFalse(
            "Cloud LLM feature gate must default to false without explicit configuration",
            CloudLlmFeatureGate.isCloudLlmEnabled()
        )
    }

    // 2. Requires explicit non-blank configuration
    @Test
    fun cloudLlmFeatureGate_requiresExplicitNonBlankConfiguration() {
        CloudLlmFeatureGate.configureForTesting(enabled = true, apiKey = "test-valid-key-123")
        assertTrue(CloudLlmFeatureGate.isCloudLlmEnabled())
        assertEquals("test-valid-key-123", CloudLlmFeatureGate.getApiKey())

        // Blank API key fails closed
        CloudLlmFeatureGate.configureForTesting(enabled = true, apiKey = "   ")
        assertNull(CloudLlmFeatureGate.getApiKey())
    }

    // 3. Missing API key fails closed
    @Test
    fun missingApiKey_failsClosed() {
        CloudLlmFeatureGate.configureForTesting(enabled = true, apiKey = null)
        assertNull(CloudLlmFeatureGate.getApiKey())
    }

    // 4. Source file static check: Zero literal API key patterns in tracked source files
    @Test
    fun noLiteralApiKeyPatternInTrackedSourceFiles() {
        val srcDir = File("app/src/main/java/com/example/ai")
        if (!srcDir.exists()) return

        val apiKeyPattern = Regex("""AIzaSy[0-9A-Za-z-_]{33}""")
        val trackedFiles = srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

        for (file in trackedFiles) {
            val content = file.readText()
            assertFalse(
                "Tracked file '${file.name}' contains a string matching an API key pattern!",
                apiKeyPattern.containsMatchIn(content)
            )
        }
    }
}
