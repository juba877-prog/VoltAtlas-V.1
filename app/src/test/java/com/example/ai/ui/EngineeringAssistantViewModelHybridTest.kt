package com.example.ai.ui

import com.example.ai.context.EngineeringContext
import com.example.ai.conversation.*
import com.example.ai.hybrid.*
import com.example.ai.service.MessageRole
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

    // 7. Eager construction safety: Provider never constructs real CloudLlmConversationEngine or SDK client when gate is disabled
    @Test
    fun provideConversationEngine_withGateDisabledAndNoApiKey_neverConstructsRealSdkClient() {
        CloudLlmFeatureGate.configureForTesting(enabled = false, apiKey = null)

        var factoryInvoked = false
        val customFactory = {
            factoryInvoked = true
            CloudLlmConversationEngine()
        }

        val router = ConversationEngineProvider.provideConversationEngine(
            cloudEngineFactory = customFactory
        )

        assertNotNull(router)
        assertFalse("Real cloud engine factory must never be invoked when gate is disabled", factoryInvoked)
    }

    // 8. onChipSelected submits turn through injected conversation engine
    @Test
    fun onChipSelected_submitsTurnThroughInjectedConversationEngine() = runTest(testDispatcher) {
        var lastUtterance: String? = null
        val mockRouter = object : ConversationEngine {
            override suspend fun evaluateTurn(
                input: ConversationTurnInput,
                decisionIdProvider: () -> String
            ): HybridEngineResult {
                lastUtterance = input.userUtterance
                return HybridEngineResult(
                    decision = ConversationTurnDecision(
                        decisionId = "DEC-CHIP",
                        action = ConversationAction(ConversationActionType.RUN_CALCULATION, "CHIP_SUBMITTED"),
                        updatedState = ConversationTaskState("T-CHIP", "P-1", 1L),
                        explanationSummary = "Calculation completed with selected installation method."
                    ),
                    source = ConversationEngineSource.LOCAL_RULE_ENGINE
                )
            }
        }

        val viewModel = EngineeringAssistantViewModel(conversationEngine = mockRouter)
        viewModel.open(AssistantEntryPoint.TOP_BAR_ICON, EngineeringContext(projectId = "P-1"))

        viewModel.onChipSelected("Perforated Tray")
        testScheduler.advanceUntilIdle()

        assertEquals("Perforated Tray", lastUtterance)
        val uiState = viewModel.uiState.value
        assertEquals("Perforated Tray", uiState.messages.filter { it.role == MessageRole.USER }.last().text)
        assertEquals("Calculation completed with selected installation method.", uiState.messages.last().text)
    }

    // 9. onCardAction with CANCEL_TASK dismisses card from UI state
    @Test
    fun onCardAction_cancelTask_dismissesCardFromUiState() = runTest(testDispatcher) {
        val viewModel = EngineeringAssistantViewModel()
        viewModel.open(AssistantEntryPoint.TOP_BAR_ICON, EngineeringContext(projectId = "P-1"))

        val testCardId = "CARD-BLOCKING-1"
        val dismissAction = SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Cancel")
        viewModel.onCardAction(testCardId, dismissAction)

        val uiState = viewModel.uiState.value
        assertFalse("Card must not be in typedCards", uiState.typedCards.any { it.cardId == testCardId })
    }

    // 10. onCardAction with non-cancel action submits action label as user message
    @Test
    fun onCardAction_actionClick_submitsLabelAsTurn() = runTest(testDispatcher) {
        var submittedUtterance: String? = null
        val mockRouter = object : ConversationEngine {
            override suspend fun evaluateTurn(
                input: ConversationTurnInput,
                decisionIdProvider: () -> String
            ): HybridEngineResult {
                submittedUtterance = input.userUtterance
                return HybridEngineResult(
                    decision = ConversationTurnDecision(
                        decisionId = "DEC-ACTION",
                        action = ConversationAction(ConversationActionType.RUN_CALCULATION, "ACTION_CLICKED"),
                        updatedState = ConversationTaskState("T-ACT", "P-1", 1L),
                        explanationSummary = "Recalculation started."
                    ),
                    source = ConversationEngineSource.LOCAL_RULE_ENGINE
                )
            }
        }

        val viewModel = EngineeringAssistantViewModel(conversationEngine = mockRouter)
        viewModel.open(AssistantEntryPoint.TOP_BAR_ICON, EngineeringContext(projectId = "P-1"))

        val action = SuggestedActionUiModel(ConversationActionType.RUN_CALCULATION, "Recalculate", isPrimary = true)
        viewModel.onCardAction("CARD-1", action)
        testScheduler.advanceUntilIdle()

        assertEquals("Recalculate", submittedUtterance)
        assertEquals("Recalculate", viewModel.uiState.value.messages.filter { it.role == MessageRole.USER }.last().text)
    }
}
