package com.example.ai.hybrid

import com.google.firebase.ai.type.Candidate
import com.google.firebase.ai.type.CitationMetadata
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GroundingMetadata
import com.google.firebase.ai.type.Part
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.SafetyRating
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.UrlContextMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException

/**
 * Unit tests for [RealGoogleGenAiClientSeam] verifying:
 * - Feature gate kill-switch enforcement.
 * - Response parsing for text, function calls, safety blocks, and malformed responses.
 * - Typed exception mapping.
 * - Cancellation propagation.
 * - Schema conversion from [CloudFunctionDeclaration] to Firebase SDK types.
 * - [CloudLlmRawResponse.fromClientResponse] type fidelity.
 */
class RealGoogleGenAiClientSeamTest {

    @Before
    fun setUp() {
        CloudLlmFeatureGate.configureForTesting(enabled = true, apiKey = "mock-api-key")
    }

    @After
    fun tearDown() {
        CloudLlmFeatureGate.resetToDefaults()
        AiModelConfig.resetToDefaults()
    }

    private fun createCandidate(
        parts: List<Part> = emptyList(),
        finishReason: FinishReason? = FinishReason.STOP,
        finishMessage: String? = null
    ): Candidate {
        val constructor = Candidate::class.java.declaredConstructors.first { it.parameterCount == 7 }
        constructor.isAccessible = true
        return constructor.newInstance(
            Content(role = "model", parts = parts),
            emptyList<SafetyRating>(),
            null as CitationMetadata?,
            finishReason,
            finishMessage,
            null as GroundingMetadata?,
            null as UrlContextMetadata?
        ) as Candidate
    }

    private fun createResponse(
        candidate: Candidate? = null,
        candidates: List<Candidate> = candidate?.let { listOf(it) } ?: emptyList()
    ): GenerateContentResponse {
        val constructor = GenerateContentResponse::class.java.declaredConstructors.first { it.parameterCount == 3 }
        constructor.isAccessible = true
        return constructor.newInstance(
            candidates,
            null,
            null
        ) as GenerateContentResponse
    }

    private fun newPromptBlockedException(message: String): PromptBlockedException {
        val ctor = PromptBlockedException::class.java.declaredConstructors.first {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java
        }
        ctor.isAccessible = true
        return ctor.newInstance(message, null) as PromptBlockedException
    }

    private fun newContentBlockedException(message: String): ContentBlockedException {
        val ctor = ContentBlockedException::class.java.declaredConstructors.first {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java
        }
        ctor.isAccessible = true
        return ctor.newInstance(message, null) as ContentBlockedException
    }

    private fun newRequestTimeoutException(message: String): RequestTimeoutException {
        val ctor = RequestTimeoutException::class.java.declaredConstructors.first {
            it.parameterTypes.size == 3 && it.parameterTypes[0] == String::class.java
        }
        ctor.isAccessible = true
        return ctor.newInstance(message, null, emptyList<Content>()) as RequestTimeoutException
    }

    private fun newQuotaExceededException(message: String): QuotaExceededException {
        val ctor = QuotaExceededException::class.java.declaredConstructors.first {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java
        }
        ctor.isAccessible = true
        return ctor.newInstance(message, null) as QuotaExceededException
    }

    @Suppress("UNCHECKED_CAST")
    private fun getFunctionDeclField(decl: FunctionDeclaration, fieldName: String): Any? {
        val field = FunctionDeclaration::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(decl)
    }

    // 1. Feature Gate Enforcement
    @Test
    fun featureGateClosed_returnsProviderErrorWithoutInvokingModel() = runTest {
        CloudLlmFeatureGate.configureForTesting(enabled = false, apiKey = null)

        var factoryCalled = false
        val seam = RealGoogleGenAiClientSeam(
            modelFactory = { _, _, _ ->
                factoryCalled = true
                throw AssertionError("Factory must not be called when feature gate is disabled")
            }
        )

        val result = seam.generateTyped("System", "User", emptyList())

        assertFalse("Model factory must not be invoked", factoryCalled)
        assertTrue("Must return ProviderError", result is CloudLlmClientResponse.ProviderError)
        val providerError = result as CloudLlmClientResponse.ProviderError
        assertTrue(providerError.message.contains("disabled by configuration feature gate"))
        assertEquals(CloudLlmResponseType.PROVIDER_ERROR, result.responseType)

        // Raw response mapping
        val raw = seam.generate("System", "User", emptyList())
        assertEquals(CloudLlmResponseType.PROVIDER_ERROR, raw.responseType)
    }

    // 2. Plain Text Response Parsing
    @Test
    fun parseResponse_plainText_returnsFinalText() {
        val seam = RealGoogleGenAiClientSeam()
        val response = createResponse(
            createCandidate(parts = listOf(TextPart("The cable calculation completed successfully.")))
        )

        val result = seam.parseResponse(response)

        assertTrue(result is CloudLlmClientResponse.FinalText)
        assertEquals("The cable calculation completed successfully.", (result as CloudLlmClientResponse.FinalText).text)
        assertEquals(CloudLlmResponseType.FINAL_TEXT, result.responseType)
    }

    // 3. Structured Function Call Parsing
    @Test
    fun parseResponse_functionCall_returnsToolCallRequestWithArguments() {
        val seam = RealGoogleGenAiClientSeam()
        val response = createResponse(
            createCandidate(
                parts = listOf(
                    FunctionCallPart(
                        name = "calculate_cable_sizing",
                        args = mapOf(
                            "loadCurrentA" to JsonPrimitive(120.5),
                            "lengthM" to JsonPrimitive(45.0),
                            "method" to JsonPrimitive("B1")
                        )
                    )
                )
            )
        )

        val result = seam.parseResponse(response)

        assertTrue(result is CloudLlmClientResponse.ToolCallRequest)
        val request = result as CloudLlmClientResponse.ToolCallRequest
        assertEquals(JsonPrimitive(120.5), request.arguments["loadCurrentA"])
        assertEquals(JsonPrimitive(45.0), request.arguments["lengthM"])
        assertEquals(JsonPrimitive("B1"), request.arguments["method"])
        assertEquals(CloudLlmResponseType.TOOL_CALL_REQUEST, result.responseType)
    }

    // 4. Safety Block Handling across Finish Reasons
    @Test
    fun parseResponse_safetyFinishReasons_returnSafetyBlock() {
        val seam = RealGoogleGenAiClientSeam()
        val safetyReasons = listOf(
            FinishReason.SAFETY,
            FinishReason.BLOCKLIST,
            FinishReason.PROHIBITED_CONTENT,
            FinishReason.SPII
        )

        for (reason in safetyReasons) {
            val response = createResponse(
                createCandidate(finishReason = reason, finishMessage = "Content filtered for reason: ${reason.name}")
            )
            val result = seam.parseResponse(response)
            assertTrue("Expected SafetyBlock for $reason", result is CloudLlmClientResponse.SafetyBlock)
            assertEquals(CloudLlmResponseType.SAFETY_BLOCK, result.responseType)
            assertTrue((result as CloudLlmClientResponse.SafetyBlock).reason.contains(reason.name))
        }
    }

    // 5. Malformed Function Call Handling
    @Test
    fun parseResponse_malformedFunctionCall_returnsMalformedResponse() {
        val seam = RealGoogleGenAiClientSeam()
        val response = createResponse(
            createCandidate(
                finishReason = FinishReason.MALFORMED_FUNCTION_CALL,
                finishMessage = "Invalid JSON in arguments"
            )
        )

        val result = seam.parseResponse(response)

        assertTrue(result is CloudLlmClientResponse.MalformedResponse)
        assertEquals(CloudLlmResponseType.MALFORMED_RESPONSE, result.responseType)
        assertTrue((result as CloudLlmClientResponse.MalformedResponse).reason.contains("malformed function call"))
    }

    // 6. Malformed Response Handling
    @Test
    fun parseResponse_malformedResponse_returnsMalformedResponse() {
        val seam = RealGoogleGenAiClientSeam()
        val response = createResponse(
            createCandidate(
                finishReason = FinishReason.MALFORMED_RESPONSE,
                finishMessage = "Invalid provider payload"
            )
        )

        val result = seam.parseResponse(response)

        assertTrue(result is CloudLlmClientResponse.MalformedResponse)
        assertEquals(CloudLlmResponseType.MALFORMED_RESPONSE, result.responseType)
    }

    // 7. Empty Response Handling
    @Test
    fun parseResponse_emptyResponse_returnsMalformedResponse() {
        val seam = RealGoogleGenAiClientSeam()
        val emptyResponse = createResponse(candidates = emptyList())

        val result = seam.parseResponse(emptyResponse)

        assertTrue(result is CloudLlmClientResponse.MalformedResponse)
        assertEquals(CloudLlmResponseType.MALFORMED_RESPONSE, result.responseType)
    }

    // 8. Exception Mapping: Safety Exceptions
    @Test
    fun exceptionMapping_safetyExceptions_returnSafetyBlock() = runTest {
        val promptBlockedSeam = RealGoogleGenAiClientSeam(
            modelFactory = { _, _, _ ->
                throw newPromptBlockedException("Harmful user input")
            }
        )
        val promptResult = promptBlockedSeam.generateTyped("System", "User", emptyList())
        assertTrue(promptResult is CloudLlmClientResponse.SafetyBlock)
        assertEquals(CloudLlmResponseType.SAFETY_BLOCK, promptResult.responseType)

        val contentBlockedSeam = RealGoogleGenAiClientSeam(
            modelFactory = { _, _, _ ->
                throw newContentBlockedException("Blocked model generation")
            }
        )
        val contentResult = contentBlockedSeam.generateTyped("System", "User", emptyList())
        assertTrue(contentResult is CloudLlmClientResponse.SafetyBlock)
        assertEquals(CloudLlmResponseType.SAFETY_BLOCK, contentResult.responseType)
    }

    // 9. Exception Mapping: Timeout Exceptions
    @Test
    fun exceptionMapping_timeoutExceptions_returnTimeout() = runTest {
        val requestTimeoutSeam = RealGoogleGenAiClientSeam(
            modelFactory = { _, _, _ ->
                throw newRequestTimeoutException("Gateway timeout")
            }
        )
        val result1 = requestTimeoutSeam.generateTyped("System", "User", emptyList())
        assertTrue(result1 is CloudLlmClientResponse.Timeout)
        assertEquals(CloudLlmResponseType.TIMEOUT, result1.responseType)

        val socketTimeoutSeam = RealGoogleGenAiClientSeam(
            modelFactory = { _, _, _ ->
                throw SocketTimeoutException("Read timed out")
            }
        )
        val result2 = socketTimeoutSeam.generateTyped("System", "User", emptyList())
        assertTrue(result2 is CloudLlmClientResponse.Timeout)

        // Coroutine timeout via configured model timeout
        val timeoutCancellationSeam = RealGoogleGenAiClientSeam(
            config = AiModelConfig(timeoutMs = 50L),
            contentGenerator = { _, _, _ ->
                delay(200L)
                createResponse()
            }
        )
        val result3 = timeoutCancellationSeam.generateTyped("System", "User", emptyList())
        assertTrue("Expected Timeout on delay exceeding timeoutMs", result3 is CloudLlmClientResponse.Timeout)
    }

    // 10. Exception Mapping: Quota Exceeded Rate Limit
    @Test
    fun exceptionMapping_quotaExceeded_returnsRateLimitedProviderError() = runTest {
        val quotaSeam = RealGoogleGenAiClientSeam(
            modelFactory = { _, _, _ ->
                throw newQuotaExceededException("Rate limit 429")
            }
        )
        val result = quotaSeam.generateTyped("System", "User", emptyList())

        assertTrue(result is CloudLlmClientResponse.ProviderError)
        val error = result as CloudLlmClientResponse.ProviderError
        assertTrue(error.isRateLimited)
        assertEquals(CloudLlmResponseType.PROVIDER_ERROR, result.responseType)
    }

    // 10b. Exception Mapping: FirebaseAIException
    @Test
    fun exceptionMapping_firebaseAiException_returnsProviderError() = runTest {
        val firebaseExceptionSeam = RealGoogleGenAiClientSeam(
            modelFactory = { _, _, _ ->
                throw com.google.firebase.ai.type.FirebaseAutoFunctionException("Firebase service error")
            }
        )
        val result = firebaseExceptionSeam.generateTyped("System", "User", emptyList())

        assertTrue(result is CloudLlmClientResponse.ProviderError)
        val error = result as CloudLlmClientResponse.ProviderError
        assertFalse(error.isRateLimited)
        assertTrue(error.message.contains("Firebase service error"))
    }

    // 11. Cancellation Propagation
    @Test(expected = CancellationException::class)
    fun exceptionMapping_cancellationException_isPropagatedDirectly() = runTest {
        val cancellingSeam = RealGoogleGenAiClientSeam(
            modelFactory = { _, _, _ ->
                throw CancellationException("Scope cancelled")
            }
        )
        cancellingSeam.generateTyped("System", "User", emptyList())
    }

    // 12. Schema Translation to Firebase FunctionDeclaration
    @Test
    @Suppress("UNCHECKED_CAST")
    fun schemaTranslation_toFirebaseFunctionDeclaration_mapsCorrectly() {
        val declaration = CloudFunctionDeclaration(
            name = "calculate_cable_sizing",
            description = "Calculates compliant cable sizing.",
            parameters = listOf(
                CloudFunctionParameter(
                    name = "loadCurrentA",
                    type = "NUMBER",
                    description = "Operating load current in amperes",
                    required = true
                ),
                CloudFunctionParameter(
                    name = "installationMethod",
                    type = "STRING",
                    description = "Reference method code",
                    required = false
                )
            ),
            requiredParameters = listOf("loadCurrentA")
        )

        val fbDeclaration = RealGoogleGenAiClientSeam.toFirebaseFunctionDeclaration(declaration)

        val name = getFunctionDeclField(fbDeclaration, "name") as String
        val description = getFunctionDeclField(fbDeclaration, "description") as String
        val parameters = getFunctionDeclField(fbDeclaration, "parameters") as Map<String, Schema>
        val optionalParameters = getFunctionDeclField(fbDeclaration, "optionalParameters") as List<String>

        assertEquals("calculate_cable_sizing", name)
        assertEquals("Calculates compliant cable sizing.", description)
        assertTrue(parameters.containsKey("loadCurrentA"))
        assertTrue(parameters.containsKey("installationMethod"))
        assertEquals(listOf("installationMethod"), optionalParameters)
    }

    // 13. Schema Translation for all Parameter Types
    @Test
    fun schemaTranslation_toFirebaseSchema_allTypesMappedCorrectly() {
        val strParam = CloudFunctionParameter(name = "pStr", type = "STRING", description = "text", required = true)
        val intParam = CloudFunctionParameter(name = "pInt", type = "INTEGER", description = "num", required = true)
        val numParam = CloudFunctionParameter(name = "pNum", type = "NUMBER", description = "real", required = false)
        val boolParam = CloudFunctionParameter(name = "pBool", type = "BOOLEAN", description = "flag", required = true)
        val arrStrParam = CloudFunctionParameter(name = "pArrStr", type = "ARRAY<STRING>", description = "list", required = true)
        val objParam = CloudFunctionParameter(
            name = "pObj",
            type = "OBJECT",
            description = "complex",
            required = true,
            fields = listOf(strParam, numParam)
        )
        val arrObjParam = CloudFunctionParameter(
            name = "pArrObj",
            type = "ARRAY<OBJECT>",
            description = "obj list",
            required = true,
            fields = listOf(strParam)
        )

        val strSchema = RealGoogleGenAiClientSeam.toFirebaseSchema(strParam)
        assertEquals("STRING", strSchema.type)
        assertEquals(false, strSchema.nullable)

        val intSchema = RealGoogleGenAiClientSeam.toFirebaseSchema(intParam)
        assertEquals("INTEGER", intSchema.type)

        val numSchema = RealGoogleGenAiClientSeam.toFirebaseSchema(numParam)
        assertEquals("NUMBER", numSchema.type)
        assertEquals(true, numSchema.nullable)

        val boolSchema = RealGoogleGenAiClientSeam.toFirebaseSchema(boolParam)
        assertEquals("BOOLEAN", boolSchema.type)

        val arrStrSchema = RealGoogleGenAiClientSeam.toFirebaseSchema(arrStrParam)
        assertEquals("ARRAY", arrStrSchema.type)
        assertNotNull(arrStrSchema.items)

        val objSchema = RealGoogleGenAiClientSeam.toFirebaseSchema(objParam)
        assertEquals("OBJECT", objSchema.type)
        assertNotNull(objSchema.properties)
        assertTrue(objSchema.properties!!.containsKey("pStr"))

        val arrObjSchema = RealGoogleGenAiClientSeam.toFirebaseSchema(arrObjParam)
        assertEquals("ARRAY", arrObjSchema.type)
        assertNotNull(arrObjSchema.items)
    }

    // 14. RawResponse Bidirectional Contract
    @Test
    fun rawResponse_fromClientResponse_roundTripsAllContractTypes() {
        val text = CloudLlmClientResponse.FinalText("Hello")
        val rawText = CloudLlmRawResponse.fromClientResponse(text)
        assertEquals(CloudLlmResponseType.FINAL_TEXT, rawText.responseType)
        assertEquals("Hello", rawText.text)

        val toolCall = CloudLlmClientResponse.ToolCallRequest("calc", mapOf("k" to JsonPrimitive("v")))
        val rawTool = CloudLlmRawResponse.fromClientResponse(toolCall)
        assertEquals(CloudLlmResponseType.TOOL_CALL_REQUEST, rawTool.responseType)
        assertEquals("calc", rawTool.toolCallName)
        assertEquals(JsonPrimitive("v"), rawTool.toolCallArguments["k"])

        val rateLimitError = CloudLlmClientResponse.ProviderError("Quota", isRateLimited = true)
        val rawRate = CloudLlmRawResponse.fromClientResponse(rateLimitError)
        assertEquals(CloudLlmResponseType.PROVIDER_ERROR, rawRate.responseType)
        assertTrue(rawRate.isRateLimited)

        val safety = CloudLlmClientResponse.SafetyBlock("Blocked")
        val rawSafety = CloudLlmRawResponse.fromClientResponse(safety)
        assertEquals(CloudLlmResponseType.SAFETY_BLOCK, rawSafety.responseType)
        assertTrue(rawSafety.isSafetyBlocked)

        val malformed = CloudLlmClientResponse.MalformedResponse("Bad JSON")
        val rawMalformed = CloudLlmRawResponse.fromClientResponse(malformed)
        assertEquals(CloudLlmResponseType.MALFORMED_RESPONSE, rawMalformed.responseType)

        val timeout = CloudLlmClientResponse.Timeout(10000L)
        val rawTimeout = CloudLlmRawResponse.fromClientResponse(timeout)
        assertEquals(CloudLlmResponseType.TIMEOUT, rawTimeout.responseType)

        val cancelled = CloudLlmClientResponse.Cancelled
        val rawCancelled = CloudLlmRawResponse.fromClientResponse(cancelled)
        assertEquals(CloudLlmResponseType.CANCELLED, rawCancelled.responseType)
    }
}
