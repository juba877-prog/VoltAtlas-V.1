package com.example.ai.hybrid

import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 Batch 4 / G-6 — truthful connection status.
 *
 * Root cause: Settings displayed "● Connected" after a minimal ping. That proved only that a key
 * authenticates and the endpoint is reachable — NOT that the assistant works. In production the
 * same key produced OFFLINE because the agentic second turn was structurally invalid, so the
 * badge was actively misleading.
 *
 * These tests pin:
 *  - a successful test reports CONNECTIVITY_OK and explicitly NOT the assistant/tool scopes;
 *  - settings and the production assistant share the same model + endpoint + auth header
 *    + HTTP stack, so the test can never pass against a configuration the app would not use;
 *  - the configured model is retained (gemini-3.8-flash, no downgrade).
 *
 * Protocol-level tests with mocked transport — NOT live-provider verification.
 */
class GeminiConnectionStatusTruthTest {

    private val apiKey = "AIzaConnectionTruthTestKeyNotARealCredential"

    private fun recordingClient(
        status: Int = 200,
        body: String = """{"candidates":[{"content":{"role":"model","parts":[{"text":"pong"}]}}]}""",
        capture: MutableMap<String, String>? = null
    ) = okhttp3.OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request()
            capture?.put("url", req.url.toString())
            capture?.put("apiKeyHeader", req.header("x-goog-api-key").orEmpty())
            capture?.put("contentType", req.header("Content-Type").orEmpty())
            capture?.put("method", req.method)
            capture?.put("body", (req.body as? okhttp3.RequestBody)?.let { "captured" } ?: "none")
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("test")
                .body(body.toResponseBody(null))
                .build()
        }
        .build()

    /**
     * G-6.1 — A successful connection test must claim CONNECTIVITY_OK and nothing stronger.
     * If it ever claims TOOL_RUNTIME_OK / GROUNDED_RESPONSE_OK it would be over-claiming, since
     * this test never executes a deterministic VoltAtlas tool.
     */
    @Test
    fun successfulTestClaimsConnectivityOnly() = kotlinx.coroutines.runBlocking {
        val result = DirectGeminiClientSeam.testConnection(
            apiKey = apiKey,
            modelName = AiModelConfig.DEFAULT_MODEL_NAME,
            httpClient = recordingClient()
        )

        assertEquals(ConnectionTestStatus.CONNECTED, result.status)
        assertEquals(
            "a ping must not claim to have verified the assistant tool loop",
            GeminiVerificationScope.CONNECTIVITY_OK,
            result.verifiedScope
        )
        assertNotEquals(GeminiVerificationScope.TOOL_RUNTIME_OK, result.verifiedScope)
        assertNotEquals(GeminiVerificationScope.GROUNDED_RESPONSE_OK, result.verifiedScope)
        assertNotEquals(GeminiVerificationScope.ASSISTANT_RUNTIME_OK, result.verifiedScope)
        assertTrue(
            "the message must state what was actually verified",
            result.message.contains("not exercised")
        )
    }

    /**
     * G-6.2 — Settings and the production assistant must target the SAME model, endpoint family
     * and auth header. If these diverged, a green Settings badge would prove nothing about the
     * assistant.
     */
    @Test
    fun settingsAndProductionAssistantShareProviderConfiguration() = kotlinx.coroutines.runBlocking {
        val settingsCapture = mutableMapOf<String, String>()
        DirectGeminiClientSeam.testConnection(
            apiKey = apiKey,
            modelName = AiModelConfig.DEFAULT_MODEL_NAME,
            httpClient = recordingClient(capture = settingsCapture)
        )

        val assistantCapture = mutableMapOf<String, String>()
        DirectGeminiClientSeam(
            apiKeyProvider = { apiKey },
            httpClient = recordingClient(
                body = """{"candidates":[{"content":{"role":"model","parts":[{"text":"pong"}]}}]}""",
                capture = assistantCapture
            )
        ).generateTurn(systemPrompt = "s", userPrompt = "u", conversationHistory = emptyList(), tools = emptyList())

        assertEquals(
            "settings and assistant must use the same endpoint",
            settingsCapture["url"], assistantCapture["url"]
        )
        assertEquals(
            "settings and assistant must use the same auth header",
            settingsCapture["apiKeyHeader"], assistantCapture["apiKeyHeader"]
        )
        assertEquals(
            "settings and assistant must use the same content type",
            settingsCapture["contentType"], assistantCapture["contentType"]
        )
        assertEquals("POST", settingsCapture["method"])
        assertEquals("POST", assistantCapture["method"])
    }

    /**
     * G-6.3 — The endpoint must be the real generateContent endpoint for the configured model,
     * and the configured model must remain gemini-3.8-flash (no downgrade).
     */
    @Test
    fun endpointAndModelAreTheRealConfiguredOnes() = kotlinx.coroutines.runBlocking {
        val capture = mutableMapOf<String, String>()
        DirectGeminiClientSeam.testConnection(
            apiKey = apiKey,
            modelName = AiModelConfig.DEFAULT_MODEL_NAME,
            httpClient = recordingClient(capture = capture)
        )

        val url = capture.getValue("url")
        assertEquals(
            "gemini-3.8-flash must be retained — a downgrade is not an acceptable fix",
            "gemini-3.8-flash",
            AiModelConfig.DEFAULT_MODEL_NAME
        )
        assertTrue("endpoint must be generativelanguage.googleapis.com: $url",
            url.contains("generativelanguage.googleapis.com"))
        assertTrue("endpoint must call :generateContent: $url", url.contains(":generateContent"))
        assertTrue("endpoint must target the configured model: $url",
            url.contains(AiModelConfig.DEFAULT_MODEL_NAME))
    }

    /**
     * G-6.4 — A key that authenticates but whose MODEL is unavailable must be reported as
     * MODEL_UNAVAILABLE, not CONNECTED.
     */
    @Test
    fun modelNotFoundIsNotReportedAsConnected() = kotlinx.coroutines.runBlocking {
        val result = DirectGeminiClientSeam.testConnection(
            apiKey = apiKey,
            httpClient = recordingClient(
                status = 404,
                body = """{"error":{"code":404,"message":"models/x is not found.","status":"NOT_FOUND"}}"""
            )
        )
        assertEquals(ConnectionTestStatus.MODEL_UNAVAILABLE, result.status)
        assertEquals(
            "a failed test must not claim any verified scope",
            GeminiVerificationScope.CONFIGURED,
            result.verifiedScope
        )
    }

    /**
     * G-6.5 — A rejected key must be reported as INVALID_CREDENTIAL with no verified scope.
     */
    @Test
    fun rejectedKeyDoesNotClaimAnyVerifiedScope() = kotlinx.coroutines.runBlocking {
        val result = DirectGeminiClientSeam.testConnection(
            apiKey = apiKey,
            httpClient = recordingClient(
                status = 400,
                body = """{"error":{"code":400,"message":"API key not valid","status":"INVALID_ARGUMENT"}}"""
            )
        )
        assertTrue(
            "invalid key must not report CONNECTED",
            result.status != ConnectionTestStatus.CONNECTED
        )
        assertEquals(GeminiVerificationScope.CONFIGURED, result.verifiedScope)
    }

    /**
     * G-6.6 — "Configured" alone is the weakest scope: it involves no network at all. A stored
     * key must never by itself produce CONNECTIVITY_OK.
     */
    @Test
    fun configuredIsDistinctFromConnectivityOk() {
        assertNotEquals(GeminiVerificationScope.CONFIGURED, GeminiVerificationScope.CONNECTIVITY_OK)
        assertEquals(
            "default scope for an unproven result is CONFIGURED",
            GeminiVerificationScope.CONFIGURED,
            ConnectionTestResult(
                status = ConnectionTestStatus.PROVIDER_ERROR,
                message = "x"
            ).verifiedScope
        )
    }

    /**
     * G-6.7 — A missing key short-circuits before any network call and claims nothing.
     */
    @Test
    fun missingKeyClaimsNothingAndMakesNoRequest() = kotlinx.coroutines.runBlocking {
        var requestMade = false
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestMade = true
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("t").body("{}".toResponseBody(null)).build()
            }
            .build()

        val result = DirectGeminiClientSeam.testConnection(apiKey = "   ", httpClient = client)
        assertEquals(ConnectionTestStatus.NOT_CONFIGURED, result.status)
        assertEquals(GeminiVerificationScope.CONFIGURED, result.verifiedScope)
        assertTrue("no request may be attempted without a key", !requestMade)
    }
}
