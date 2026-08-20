package com.example.ai.hybrid

import com.example.ai.conversation.*
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.RuntimePromptCompiler
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Test double for the Cloud LLM conversation engine.
 */
class FakeCloudLlmConversationEngine(
    private val responseMode: FakeResponseMode = FakeResponseMode.SUCCESS
) : ConversationEngine {

    enum class FakeResponseMode {
        SUCCESS,
        FABRICATED_CLAIM,
        TIMEOUT,
        SAFETY_BLOCKED,
        RATE_LIMITED,
        HTTP_ERROR
    }

    var callCount = 0
        private set

    override suspend fun evaluateTurn(
        input: ConversationTurnInput,
        decisionIdProvider: () -> String
    ): HybridEngineResult {
        callCount++
        return when (responseMode) {
            FakeResponseMode.SUCCESS -> {
                HybridEngineResult(
                    decision = ConversationTurnDecision(
                        decisionId = decisionIdProvider(),
                        action = ConversationAction(
                            actionType = ConversationActionType.EXPLAIN_RESULT,
                            reasonCode = "CLOUD_EXPLANATION"
                        ),
                        updatedState = input.priorState ?: ConversationTaskState(
                            taskId = "TASK-CLOUD-1",
                            projectId = input.projectContext.projectId,
                            baseProjectRevision = input.projectContext.liveRevision
                        ),
                        explanationSummary = "Verified compliant with standards."
                    ),
                    source = ConversationEngineSource.CLOUD_GEMINI,
                    cloudDegradationReason = null,
                    hasBackingToolResult = true
                )
            }
            FakeResponseMode.FABRICATED_CLAIM -> {
                HybridEngineResult(
                    decision = ConversationTurnDecision(
                        decisionId = decisionIdProvider(),
                        action = ConversationAction(
                            actionType = ConversationActionType.EXPLAIN_RESULT,
                            reasonCode = "FABRICATED_UNBACKED"
                        ),
                        updatedState = input.priorState ?: ConversationTaskState(
                            taskId = "TASK-CLOUD-1",
                            projectId = input.projectContext.projectId,
                            baseProjectRevision = input.projectContext.liveRevision
                        ),
                        explanationSummary = "I recommend you use 300 mm² copper cable for this feeder."
                    ),
                    source = ConversationEngineSource.CLOUD_GEMINI,
                    cloudDegradationReason = null,
                    hasBackingToolResult = false // Unbacked fabrication
                )
            }
            FakeResponseMode.TIMEOUT -> {
                delay(10_000L) // Exceeds cloud timeout
                throw IllegalStateException("Should have timed out")
            }
            FakeResponseMode.SAFETY_BLOCKED -> {
                HybridEngineResult(
                    decision = ConversationTurnDecision(
                        decisionId = decisionIdProvider(),
                        action = ConversationAction(ConversationActionType.SAFE_ERROR, "BLOCKED"),
                        updatedState = input.priorState ?: ConversationTaskState("T-1", "P-1", 1L)
                    ),
                    source = ConversationEngineSource.CLOUD_GEMINI,
                    cloudDegradationReason = CloudDegradationReason.SAFETY_BLOCKED
                )
            }
            FakeResponseMode.RATE_LIMITED -> {
                HybridEngineResult(
                    decision = ConversationTurnDecision(
                        decisionId = decisionIdProvider(),
                        action = ConversationAction(ConversationActionType.SAFE_ERROR, "RATE_LIMIT"),
                        updatedState = input.priorState ?: ConversationTaskState("T-1", "P-1", 1L)
                    ),
                    source = ConversationEngineSource.CLOUD_GEMINI,
                    cloudDegradationReason = CloudDegradationReason.RATE_LIMITED
                )
            }
            FakeResponseMode.HTTP_ERROR -> {
                throw RuntimeException("503 Service Unavailable")
            }
        }
    }
}

class HybridConversationRouterTest {

    private val sampleContext = BoundedProjectContext(
        projectId = "PROJ-HYBRID-1",
        projectName = "Hybrid Test Project",
        liveRevision = 1L,
        activeScreen = "CircuitsGridScreen",
        activeEntityType = "CIRCUIT",
        activeEntityId = "101",
        activeEntityName = "Feeder 101"
    )

    private val localEngine = LocalRuleEngineAdapter()

    // 1. Offline probe bypasses cloud entirely
    @Test
    fun offlineProbe_bypassesCloudEntirely() = runTest {
        val cloudEngine = FakeCloudLlmConversationEngine(FakeCloudLlmConversationEngine.FakeResponseMode.SUCCESS)
        val offlineProbe = DefaultNetworkConnectivityProbe { false }

        val router = HybridConversationRouter(
            cloudEngine = cloudEngine,
            localEngine = localEngine,
            connectivityProbe = offlineProbe
        )

        val input = ConversationTurnInput(
            userUtterance = "Why is this feeder sized at 300 mm²?",
            projectContext = sampleContext,
            priorState = null
        )

        val result = router.evaluateTurn(input)
        assertEquals(0, cloudEngine.callCount)
        assertEquals(ConversationEngineSource.LOCAL_RULE_ENGINE, result.source)
        assertEquals(CloudDegradationReason.OFFLINE_PROBE_FAILED, result.cloudDegradationReason)
    }

    // 2. Cloud timeout falls back to local engine
    @Test
    fun cloudTimeout_fallsBackToLocal() = runTest {
        val cloudEngine = FakeCloudLlmConversationEngine(FakeCloudLlmConversationEngine.FakeResponseMode.TIMEOUT)
        val onlineProbe = DefaultNetworkConnectivityProbe { true }

        val router = HybridConversationRouter(
            cloudEngine = cloudEngine,
            localEngine = localEngine,
            connectivityProbe = onlineProbe,
            cloudTimeoutMs = 100L // Fast timeout for unit test
        )

        val input = ConversationTurnInput(
            userUtterance = "Can 240 mm² work?",
            projectContext = sampleContext,
            priorState = null
        )

        val result = router.evaluateTurn(input)
        assertEquals(ConversationEngineSource.LOCAL_RULE_ENGINE, result.source)
        assertEquals(CloudDegradationReason.NETWORK_TIMEOUT, result.cloudDegradationReason)
        assertEquals(AssistantIntent.EVALUATE_CANDIDATE, result.decision.updatedState.primaryIntent)
    }

    // 3. Cloud success does not invoke local engine
    @Test
    fun cloudSuccess_doesNotInvokeLocalEngine() = runTest {
        val cloudEngine = FakeCloudLlmConversationEngine(FakeCloudLlmConversationEngine.FakeResponseMode.SUCCESS)
        val onlineProbe = DefaultNetworkConnectivityProbe { true }

        val router = HybridConversationRouter(
            cloudEngine = cloudEngine,
            localEngine = localEngine,
            connectivityProbe = onlineProbe
        )

        val input = ConversationTurnInput(
            userUtterance = "Explain this sizing",
            projectContext = sampleContext,
            priorState = null
        )

        val result = router.evaluateTurn(input)
        assertEquals(1, cloudEngine.callCount)
        assertEquals(ConversationEngineSource.CLOUD_GEMINI, result.source)
        assertNull(result.cloudDegradationReason)
    }

    // 4. Fabricated unbacked claim is discarded and falls back to local engine
    @Test
    fun unbackedEngineeringClaim_isDiscardedAndFallsBackToLocal() = runTest {
        val cloudEngine = FakeCloudLlmConversationEngine(FakeCloudLlmConversationEngine.FakeResponseMode.FABRICATED_CLAIM)
        val onlineProbe = DefaultNetworkConnectivityProbe { true }

        val router = HybridConversationRouter(
            cloudEngine = cloudEngine,
            localEngine = localEngine,
            connectivityProbe = onlineProbe
        )

        val input = ConversationTurnInput(
            userUtterance = "What cable should I use?",
            projectContext = sampleContext,
            priorState = null
        )

        val result = router.evaluateTurn(input)
        assertEquals(ConversationEngineSource.LOCAL_RULE_ENGINE, result.source)
        assertEquals(CloudDegradationReason.UNSUPPORTED_ENGINEERING_CLAIM, result.cloudDegradationReason)
        assertNotEquals("I recommend you use 300 mm² copper cable for this feeder.", result.decision.explanationSummary)
    }

    // 5. Cloud safety blocked falls back to local engine
    @Test
    fun cloudSafetyBlocked_fallsBackToLocal() = runTest {
        val cloudEngine = FakeCloudLlmConversationEngine(FakeCloudLlmConversationEngine.FakeResponseMode.SAFETY_BLOCKED)
        val onlineProbe = DefaultNetworkConnectivityProbe { true }

        val router = HybridConversationRouter(
            cloudEngine = cloudEngine,
            localEngine = localEngine,
            connectivityProbe = onlineProbe
        )

        val input = ConversationTurnInput(
            userUtterance = "Try aluminum",
            projectContext = sampleContext,
            priorState = null
        )

        val result = router.evaluateTurn(input)
        assertEquals(ConversationEngineSource.LOCAL_RULE_ENGINE, result.source)
        assertEquals(CloudDegradationReason.SAFETY_BLOCKED, result.cloudDegradationReason)
    }

    // 6. Cloud rate limited falls back to local engine
    @Test
    fun cloudRateLimited_fallsBackToLocal() = runTest {
        val cloudEngine = FakeCloudLlmConversationEngine(FakeCloudLlmConversationEngine.FakeResponseMode.RATE_LIMITED)
        val onlineProbe = DefaultNetworkConnectivityProbe { true }

        val router = HybridConversationRouter(
            cloudEngine = cloudEngine,
            localEngine = localEngine,
            connectivityProbe = onlineProbe
        )

        val input = ConversationTurnInput(
            userUtterance = "Why 300?",
            projectContext = sampleContext,
            priorState = null
        )

        val result = router.evaluateTurn(input)
        assertEquals(ConversationEngineSource.LOCAL_RULE_ENGINE, result.source)
        assertEquals(CloudDegradationReason.RATE_LIMITED, result.cloudDegradationReason)
    }

    // 7. Prompt budget leaves safety margin headroom
    @Test
    fun promptBudget_reservesHeadroomMargin() {
        val defaultBudget = RuntimePromptCompiler.DEFAULT_MAX_BUDGET_CHARACTERS
        assertTrue("Budget must be at least 8000 characters", defaultBudget >= 8000)
    }
}
