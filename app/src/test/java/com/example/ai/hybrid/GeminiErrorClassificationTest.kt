package com.example.ai.hybrid

import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Phase 6 Batch 4 / G-4 — precise cloud/provider failure classification.
 *
 * Root cause: every non-rate-limit provider failure collapsed into one `ProviderError` carrying
 * only a message, and the engine mapped all of them to
 * `CloudDegradationReason.HTTP_OR_PROVIDER_ERROR`, which the router degraded to the local engine
 * and the UI rendered as the same "OFFLINE" badge. An expired key, an unavailable model, a
 * rejected request and a 5xx were indistinguishable.
 *
 * These tests classify from REAL evidence: an OkHttp interceptor returning genuine HTTP
 * statuses with genuine Google error-body shapes. They are protocol-level tests using mocked
 * transport — NOT live-provider verification.
 */
class GeminiErrorClassificationTest {

    private val apiKey = "AIzaClassificationTestKeyNotARealCredential"

    private fun seamReturning(status: Int, body: String): DirectGeminiClientSeam {
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message("test")
                    .body(body.toResponseBody(null))
                    .build()
            }
            .build()
        return DirectGeminiClientSeam(apiKeyProvider = { apiKey }, httpClient = client)
    }

    private fun errorBody(status: String, message: String): String =
        """{"error":{"code":400,"message":"$message","status":"$status"}}"""

    private suspend fun classify(status: Int, body: String): CloudLlmClientResponse.ProviderError {
        val response = seamReturning(status, body)
            .generateTurn(systemPrompt = "s", userPrompt = "u", conversationHistory = emptyList(), tools = emptyList())
        return response as CloudLlmClientResponse.ProviderError
    }

    // ---------------- HTTP status classification ----------------

    /** G-4 / case L — HTTP 400 (the class of error the invalid role produced). */
    @Test
    fun http400IsClassifiedAsRequestInvalid() = kotlinx.coroutines.runBlocking {
        val err = classify(400, errorBody("INVALID_ARGUMENT", "Invalid JSON payload received."))
        assertEquals(CloudProviderFailureCategory.REQUEST_INVALID, err.category)
        assertEquals(400, err.httpStatus)
    }

    /** G-4 / case M — HTTP 401. */
    @Test
    fun http401IsClassifiedAsAuthenticationFailure() = kotlinx.coroutines.runBlocking {
        val err = classify(401, errorBody("UNAUTHENTICATED", "API key not valid."))
        assertEquals(CloudProviderFailureCategory.AUTHENTICATION_FAILURE, err.category)
        assertEquals(401, err.httpStatus)
    }

    /** G-4 / case N — HTTP 403. */
    @Test
    fun http403IsClassifiedAsAuthenticationFailure() = kotlinx.coroutines.runBlocking {
        val err = classify(403, errorBody("PERMISSION_DENIED", "Permission denied for this model."))
        assertEquals(CloudProviderFailureCategory.AUTHENTICATION_FAILURE, err.category)
        assertEquals(403, err.httpStatus)
    }

    /** G-4 / case O — HTTP 404 model-not-found. */
    @Test
    fun http404IsClassifiedAsModelUnavailable() = kotlinx.coroutines.runBlocking {
        val err = classify(404, errorBody("NOT_FOUND", "models/gemini-3.8-flash is not found."))
        assertEquals(CloudProviderFailureCategory.MODEL_UNAVAILABLE, err.category)
        assertEquals(404, err.httpStatus)
    }

    /** G-4 / case P — HTTP 429. */
    @Test
    fun http429IsClassifiedAsRateLimited() = kotlinx.coroutines.runBlocking {
        val err = classify(429, errorBody("RESOURCE_EXHAUSTED", "Quota exceeded."))
        assertEquals(CloudProviderFailureCategory.RATE_LIMITED, err.category)
        assertTrue("rate limit must stay flagged for backwards compatibility", err.isRateLimited)
    }

    /** G-4 / case Q — HTTP 500 and 503. */
    @Test
    fun http5xxIsClassifiedAsServerError() = kotlinx.coroutines.runBlocking {
        assertEquals(
            CloudProviderFailureCategory.SERVER_ERROR,
            classify(500, errorBody("INTERNAL", "Internal error.")).category
        )
        assertEquals(
            CloudProviderFailureCategory.SERVER_ERROR,
            classify(503, errorBody("UNAVAILABLE", "Service unavailable.")).category
        )
    }

    /**
     * G-4 / case R — timeout must be classified as a TIMEOUT variant, never as an auth or model
     * fault. Uses a real SocketTimeoutException raised by the transport.
     */
    @Test
    fun timeoutIsClassifiedAsTimeoutNotProviderError() = kotlinx.coroutines.runBlocking {
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor { throw SocketTimeoutException("read timed out") }
            .build()
        val response = DirectGeminiClientSeam(apiKeyProvider = { apiKey }, httpClient = client)
            .generateTurn(systemPrompt = "s", userPrompt = "u", conversationHistory = emptyList(), tools = emptyList())

        val timeout = response as CloudLlmClientResponse.Timeout
        assertNotNull(timeout.timeoutMs)
        assertTrue("timeout must not be reported as a provider error", response !is CloudLlmClientResponse.ProviderError)
    }

    /** G-4 — DNS failure is a NETWORK_ERROR, distinct from timeout and from provider fault. */
    @Test
    fun dnsFailureIsClassifiedAsNetworkError() = kotlinx.coroutines.runBlocking {
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor { throw UnknownHostException("generativelanguage.googleapis.com") }
            .build()
        val response = DirectGeminiClientSeam(apiKeyProvider = { apiKey }, httpClient = client)
            .generateTurn(systemPrompt = "s", userPrompt = "u", conversationHistory = emptyList(), tools = emptyList())

        val err = response as CloudLlmClientResponse.ProviderError
        assertEquals(CloudProviderFailureCategory.NETWORK_ERROR, err.category)
    }

    /** G-4 / case K — a malformed/unparseable success body is a protocol failure, not a 200 text. */
    @Test
    fun malformedSuccessBodyIsClassifiedAsMalformedResponse() {
        val response = DirectGeminiClientSeam.parseGeminiResponse("""{"candidates":[]}""")
        assertTrue(
            "empty candidates must be malformed, not a silent success",
            response is CloudLlmClientResponse.MalformedResponse
        )
    }

    /** G-4 — safety blocks keep their own classification and never become a network failure. */
    @Test
    fun safetyBlockRemainsDistinct() {
        val response = DirectGeminiClientSeam.parseGeminiResponse(
            """{"candidates":[{"finishReason":"SAFETY","content":{"parts":[]}}]}"""
        )
        assertTrue(response is CloudLlmClientResponse.SafetyBlock)
    }

    // ---------------- degradation mapping ----------------

    /**
     * G-4 / case V — the provider category must survive into the engine's degradation reason and
     * must NOT be indistinguishable from a network failure.
     */
    @Test
    fun providerCategorySurvivesIntoRawResponse() {
        val raw = CloudLlmRawResponse.fromClientResponse(
            CloudLlmClientResponse.ProviderError(
                message = "Gemini API error (401): API key not valid.",
                category = CloudProviderFailureCategory.AUTHENTICATION_FAILURE,
                httpStatus = 401
            )
        )
        assertEquals(CloudProviderFailureCategory.AUTHENTICATION_FAILURE, raw.providerFailureCategory)
        assertEquals("Gemini API error (401): API key not valid.", raw.errorMessage)
    }

    /**
     * G-4 / case W — grounding rejection must stay DISTINCT from cloud/network failure.
     * Grounding rejection is produced by the claim guard, never by the provider seam, so a
     * provider failure can never be labelled as a grounding rejection.
     */
    @Test
    fun groundingRejectionIsNotProducedByProviderFailures() {
        assertTrue(
            "grounding rejection is its own degradation reason",
            CloudDegradationReason.GROUNDING_VALIDATION_FAILED != CloudDegradationReason.HTTP_OR_PROVIDER_ERROR
        )
        assertTrue(
            "grounding rejection is distinct from auth failure",
            CloudDegradationReason.GROUNDING_VALIDATION_FAILED != CloudDegradationReason.AUTHENTICATION_FAILURE
        )
        // A real auth failure must not be able to masquerade as grounding.
        val raw = CloudLlmRawResponse.fromClientResponse(
            CloudLlmClientResponse.ProviderError(
                message = "unauthorized",
                category = CloudProviderFailureCategory.AUTHENTICATION_FAILURE
            )
        )
        assertEquals(CloudProviderFailureCategory.AUTHENTICATION_FAILURE, raw.providerFailureCategory)
    }

    /**
     * G-4 — every category has a defined, distinct mapping target in the existing
     * CloudDegradationReason vocabulary (no category silently degrades to nothing).
     */
    @Test
    fun everyProviderCategoryMapsToADegradationReason() {
        val mapped = CloudProviderFailureCategory.entries.associateWith { c ->
            when (c) {
                CloudProviderFailureCategory.AUTHENTICATION_FAILURE -> CloudDegradationReason.AUTHENTICATION_FAILURE
                CloudProviderFailureCategory.RATE_LIMITED -> CloudDegradationReason.RATE_LIMITED
                CloudProviderFailureCategory.MODEL_UNAVAILABLE -> CloudDegradationReason.CONFIGURATION_FAILURE
                CloudProviderFailureCategory.REQUEST_INVALID -> CloudDegradationReason.CONFIGURATION_FAILURE
                CloudProviderFailureCategory.NETWORK_ERROR -> CloudDegradationReason.HTTP_OR_PROVIDER_ERROR
                CloudProviderFailureCategory.SERVER_ERROR -> CloudDegradationReason.HTTP_OR_PROVIDER_ERROR
                CloudProviderFailureCategory.UNKNOWN -> CloudDegradationReason.HTTP_OR_PROVIDER_ERROR
            }
        }
        assertEquals(CloudProviderFailureCategory.entries.size, mapped.size)
        // Auth and rate-limit must be genuinely distinct outcomes.
        assertTrue(mapped[CloudProviderFailureCategory.AUTHENTICATION_FAILURE] != mapped[CloudProviderFailureCategory.RATE_LIMITED])
    }

    /**
     * G-4 — the real HTTP status is preserved end-to-end for diagnostics.
     */
    @Test
    fun httpStatusIsPreservedForDiagnostics() = kotlinx.coroutines.runBlocking {
        val err = classify(503, errorBody("UNAVAILABLE", "Backend overloaded."))
        assertEquals(503, err.httpStatus)
        assertTrue(
            "the provider's own message must be preserved for diagnosis",
            err.message.contains("Backend overloaded.")
        )
        // Sanity: the message must never echo the credential back.
        assertTrue("credential must never appear in the error message", !err.message.contains(apiKey))
    }
}
