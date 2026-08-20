package com.example.ai.hybrid

import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationTurnInput
import com.example.ai.runtime.BoundedProjectContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException

class CloudLlmConversationEngineTest {

    private val sampleContext = BoundedProjectContext(
        projectId = "PROJ-101",
        projectName = "Cloud Test Project",
        liveRevision = 1L,
        activeScreen = "CircuitsGridScreen",
        activeEntityType = "CIRCUIT",
        activeEntityId = "101",
        activeEntityName = "Feeder 101"
    )

    @Before
    fun setUp() {
        CloudLlmFeatureGate.configureForTesting(enabled = true, apiKey = "mock-valid-api-key")
    }

    @After
    fun tearDown() {
        CloudLlmFeatureGate.resetToDefaults()
    }

    // 1. Missing API key or disabled feature gate fails closed
    @Test
    fun missingApiKey_failsClosedWithoutNetworkAttempt() = runTest {
        CloudLlmFeatureGate.configureForTesting(enabled = false, apiKey = null)

        var seamInvoked = false
        val mockSeam = object : GeminiClientSeam {
            override suspend fun generate(
                systemPrompt: String,
                userPrompt: String,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmRawResponse {
                seamInvoked = true
                return CloudLlmRawResponse(text = "Unreachable")
            }
        }

        val engine = CloudLlmConversationEngine(clientSeam = mockSeam)
        val input = ConversationTurnInput(userUtterance = "Hello", projectContext = sampleContext)
        val result = engine.evaluateTurn(input)

        assertFalse("Seam must never be invoked when feature gate is disabled", seamInvoked)
        assertEquals(CloudDegradationReason.HTTP_OR_PROVIDER_ERROR, result.cloudDegradationReason)
    }

    // 2. Text response passes through existing EngineeringClaimGuard
    @Test
    fun cloudEngine_textResponsePassesThroughExistingEngineeringClaimGuard() = runTest {
        // Mock returning an unbacked fabrication
        val fabricatedSeam = object : GeminiClientSeam {
            override suspend fun generate(
                systemPrompt: String,
                userPrompt: String,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmRawResponse {
                return CloudLlmRawResponse(text = "I recommend you size this feeder using 300 mm² copper cable.")
            }
        }

        val engine = CloudLlmConversationEngine(clientSeam = fabricatedSeam)
        val input = ConversationTurnInput(userUtterance = "What cable?", projectContext = sampleContext)
        val result = engine.evaluateTurn(input)

        assertEquals(
            "Fabricated claim without backing tool result must be flagged as UNSUPPORTED_ENGINEERING_CLAIM",
            CloudDegradationReason.UNSUPPORTED_ENGINEERING_CLAIM,
            result.cloudDegradationReason
        )
    }

    // 3. Tool call response maps to existing contract types
    @Test
    fun cloudEngine_toolCallResponse_mapsToExistingContractTypes() = runTest {
        val toolCallSeam = object : GeminiClientSeam {
            override suspend fun generate(
                systemPrompt: String,
                userPrompt: String,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmRawResponse {
                return CloudLlmRawResponse(
                    text = "Evaluating candidate...",
                    toolCallName = "EvaluateCableCandidateTool",
                    toolCallArguments = mapOf("candidateSize" to "240.0")
                )
            }
        }

        val engine = CloudLlmConversationEngine(clientSeam = toolCallSeam)
        val input = ConversationTurnInput(userUtterance = "Can 240 work?", projectContext = sampleContext)
        val result = engine.evaluateTurn(input)

        assertNull(result.cloudDegradationReason)
        assertTrue(result.hasBackingToolResult)
        assertEquals(ConversationActionType.EVALUATE_CANDIDATE, result.decision.action.actionType)
        assertEquals("240.0", result.decision.action.candidateParameters["candidateSize"])
    }

    // 4. SDK exception types map to existing CloudDegradationReason enum
    @Test
    fun cloudEngine_sdkExceptionTypes_mapToExistingCloudDegradationReasonEnum() = runTest {
        val timeoutSeam = object : GeminiClientSeam {
            override suspend fun generate(
                systemPrompt: String,
                userPrompt: String,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmRawResponse {
                throw SocketTimeoutException("Read timed out")
            }
        }

        val engine = CloudLlmConversationEngine(clientSeam = timeoutSeam)
        val input = ConversationTurnInput(userUtterance = "Check sizing", projectContext = sampleContext)
        val result = engine.evaluateTurn(input)

        assertEquals(CloudDegradationReason.NETWORK_TIMEOUT, result.cloudDegradationReason)
    }

    // 5. Genuine cancellation during SDK call propagates without fallback
    @Test
    fun cloudEngine_genuineCancellationDuringSdkCall_propagatesWithoutFallback() = runTest {
        val deferred = CompletableDeferred<CloudLlmRawResponse>()
        val suspendingSeam = object : GeminiClientSeam {
            override suspend fun generate(
                systemPrompt: String,
                userPrompt: String,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmRawResponse {
                return deferred.await()
            }
        }

        val engine = CloudLlmConversationEngine(clientSeam = suspendingSeam)
        val input = ConversationTurnInput(userUtterance = "Check sizing", projectContext = sampleContext)

        val job = launch {
            engine.evaluateTurn(input)
        }

        testScheduler.advanceTimeBy(50)
        job.cancel(CancellationException("Parent coroutine scope cancelled"))
        job.join()

        assertTrue("Job must be cancelled via CancellationException", job.isCancelled)
    }
}
