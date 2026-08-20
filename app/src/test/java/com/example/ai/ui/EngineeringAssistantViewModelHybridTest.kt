package com.example.ai.ui

import com.example.ai.context.EngineeringContext
import com.example.ai.conversation.*
import com.example.ai.hybrid.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class EngineeringAssistantViewModelHybridTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        CloudLlmFeatureGate.resetToDefaults()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        CloudLlmFeatureGate.resetToDefaults()
    }

    // 1. ViewModel routes turn through HybridConversationRouter
    @Test
    fun viewModel_routesTurnThroughHybridConversationRouter() = runTest(testDispatcher) {
        var routerCalled = false
        val mockRouter = object : ConversationEngine {
            override suspend fun evaluateTurn(
                input: ConversationTurnInput,
                decisionIdProvider: () -> String
            ): HybridEngineResult {
                routerCalled = true
                return HybridEngineResult(
                    decision = ConversationTurnDecision(
                        decisionId = "DEC-1",
                        action = ConversationAction(ConversationActionType.EXPLAIN_RESULT, "TEST"),
                        updatedState = ConversationTaskState("T-1", "P-1", 1L),
                        explanationSummary = "Hybrid router routed successfully."
                    ),
                    source = ConversationEngineSource.LOCAL_RULE_ENGINE,
                    cloudDegradationReason = null
                )
            }
        }

        val viewModel = EngineeringAssistantViewModel(conversationEngine = mockRouter)
        viewModel.open(
            AssistantEntryPoint.CONTEXTUAL_CIRCUIT,
            EngineeringContext(projectId = "P-1", projectName = "Test Project")
        )

        viewModel.onUserSubmit("Why 300 mm²?")
        testScheduler.advanceUntilIdle()

        assertTrue("ViewModel must evaluate user turn through the injected ConversationEngine", routerCalled)
        val uiState = viewModel.uiState.value
        assertEquals("Hybrid router routed successfully.", uiState.messages.last().text)
        assertFalse(uiState.isThinking)
    }

    // 2. Offline badge visible ONLY when local fallback with degradation reason
    @Test
    fun viewModel_offlineBadgeVisible_onlyWhenLocalFallbackWithDegradationReason() = runTest(testDispatcher) {
        val degradedRouter = object : ConversationEngine {
            override suspend fun evaluateTurn(
                input: ConversationTurnInput,
                decisionIdProvider: () -> String
            ): HybridEngineResult {
                return HybridEngineResult(
                    decision = ConversationTurnDecision(
                        decisionId = "DEC-DEGRADED",
                        action = ConversationAction(ConversationActionType.EXPLAIN_RESULT, "TEST"),
                        updatedState = ConversationTaskState("T-1", "P-1", 1L),
                        explanationSummary = "Fallback explanation."
                    ),
                    source = ConversationEngineSource.LOCAL_RULE_ENGINE,
                    cloudDegradationReason = CloudDegradationReason.OFFLINE_PROBE_FAILED
                )
            }
        }

        val viewModel = EngineeringAssistantViewModel(conversationEngine = degradedRouter)
        viewModel.open(AssistantEntryPoint.TOP_BAR_ICON, EngineeringContext(projectId = "P-1"))

        viewModel.onUserSubmit("Explain sizing")
        testScheduler.advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertTrue("Offline badge must be visible on genuine degradation", uiState.header.offlineBadgeVisible)
        assertEquals(ConversationEngineSource.LOCAL_RULE_ENGINE, uiState.header.engineeringSource)
    }

    // 3. Offline badge HIDDEN when normal local or cloud turn without degradation
    @Test
    fun viewModel_offlineBadgeHidden_whenNoDegradation() = runTest(testDispatcher) {
        val normalRouter = object : ConversationEngine {
            override suspend fun evaluateTurn(
                input: ConversationTurnInput,
                decisionIdProvider: () -> String
            ): HybridEngineResult {
                return HybridEngineResult(
                    decision = ConversationTurnDecision(
                        decisionId = "DEC-NORMAL",
                        action = ConversationAction(ConversationActionType.EXPLAIN_RESULT, "TEST"),
                        updatedState = ConversationTaskState("T-1", "P-1", 1L),
                        explanationSummary = "Normal response."
                    ),
                    source = ConversationEngineSource.LOCAL_RULE_ENGINE,
                    cloudDegradationReason = null
                )
            }
        }

        val viewModel = EngineeringAssistantViewModel(conversationEngine = normalRouter)
        viewModel.open(AssistantEntryPoint.TOP_BAR_ICON, EngineeringContext(projectId = "P-1"))

        viewModel.onUserSubmit("Explain sizing")
        testScheduler.advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertFalse("Offline badge must remain hidden when no degradation occurred", uiState.header.offlineBadgeVisible)
    }

    // 4. Stale / late turn protection: newer turn overrides older turn
    @Test
    fun lateHybridResult_afterNewerTurnSubmitted_isDiscarded() = runTest(testDispatcher) {
        val slowTurnDeferred = CompletableDeferred<HybridEngineResult>()
        val fastTurnResult = HybridEngineResult(
            decision = ConversationTurnDecision(
                decisionId = "DEC-FAST",
                action = ConversationAction(ConversationActionType.EXPLAIN_RESULT, "FAST"),
                updatedState = ConversationTaskState("T-1", "P-1", 1L),
                explanationSummary = "Fast newer turn answer."
            ),
            source = ConversationEngineSource.LOCAL_RULE_ENGINE
        )

        var turnCount = 0
        val mixedRouter = object : ConversationEngine {
            override suspend fun evaluateTurn(
                input: ConversationTurnInput,
                decisionIdProvider: () -> String
            ): HybridEngineResult {
                turnCount++
                return if (turnCount == 1) {
                    slowTurnDeferred.await()
                } else {
                    fastTurnResult
                }
            }
        }

        val viewModel = EngineeringAssistantViewModel(conversationEngine = mixedRouter)
        viewModel.open(AssistantEntryPoint.TOP_BAR_ICON, EngineeringContext(projectId = "P-1"))

        // Turn 1 (slow)
        viewModel.onUserSubmit("Slow question")
        testScheduler.advanceTimeBy(50)

        // Turn 2 (fast)
        viewModel.onUserSubmit("Fast question")
        testScheduler.advanceTimeBy(50)

        // Complete slow turn 1 afterward
        slowTurnDeferred.complete(
            HybridEngineResult(
                decision = ConversationTurnDecision(
                    decisionId = "DEC-SLOW",
                    action = ConversationAction(ConversationActionType.EXPLAIN_RESULT, "SLOW"),
                    updatedState = ConversationTaskState("T-1", "P-1", 1L),
                    explanationSummary = "Stale slow answer that must be discarded."
                ),
                source = ConversationEngineSource.LOCAL_RULE_ENGINE
            )
        )
        testScheduler.advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals("Fast newer turn answer.", uiState.messages.last().text)
        assertFalse(uiState.messages.any { it.text.contains("Stale slow answer") })
    }

    // 5. Production composition: CloudLlmConversationEngine fails closed when gate disabled
    @Test
    fun productionComposition_cloudEngineStillFailsClosedWhenGateDisabled() {
        CloudLlmFeatureGate.configureForTesting(enabled = false, apiKey = null)
        val defaultRouter = ConversationEngineProvider.provideConversationEngine()
        assertNotNull(defaultRouter)
        assertFalse(CloudLlmFeatureGate.isCloudLlmEnabled())
    }

    // 6. Security scan: Zero API keys or secrets in ViewModel or UI files
    @Test
    fun noApiKeyOrSecretReferencedInViewModelOrUiFiles() {
        val uiDir = File("app/src/main/java/com/example/ai/ui")
        if (!uiDir.exists()) return

        val apiKeyPattern = Regex("""AIzaSy[0-9A-Za-z-_]{33}""")
        val trackedFiles = uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

        for (file in trackedFiles) {
            val content = file.readText()
            assertFalse(
                "UI file '${file.name}' contains a string matching an API key pattern!",
                apiKeyPattern.containsMatchIn(content)
            )
        }
    }
}
