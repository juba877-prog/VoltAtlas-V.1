package com.example.ai.hybrid

import com.example.ai.conversation.ConversationAction
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationTurnDecision
import com.example.ai.conversation.ConversationTurnInput
import com.example.ai.conversation.ConversationPolicyEngine

import com.example.ai.orchestrator.AgenticSafetyBudget
import com.example.ai.orchestrator.AgenticToolLoopOrchestrator
import com.example.ai.tools.AssistantToolRuntime
import kotlinx.coroutines.CancellationException

/**
 * Inert, zero-dependency engine used when Cloud LLM is disabled by configuration.
 * Instantiating or calling this causes zero SDK client construction and zero credential reads.
 */
object DisabledCloudLlmEngine : ConversationEngine {
    override suspend fun evaluateTurn(
        input: ConversationTurnInput,
        decisionIdProvider: () -> String
    ): HybridEngineResult {
        return HybridEngineResult(
            decision = ConversationTurnDecision(
                decisionId = decisionIdProvider(),
                action = ConversationAction(
                    actionType = ConversationActionType.SAFE_ERROR,
                    reasonCode = "CLOUD_DISABLED_BY_CONFIGURATION"
                ),
                updatedState = input.priorState ?: ConversationTaskState(
                    taskId = "DISABLED",
                    projectId = input.projectContext.projectId,
                    baseProjectRevision = input.projectContext.liveRevision
                )
            ),
            source = ConversationEngineSource.CLOUD_GEMINI,
            cloudDegradationReason = CloudDegradationReason.HTTP_OR_PROVIDER_ERROR,
            hasBackingToolResult = false
        )
    }
}

/**
 * Composition provider constructing the production [ConversationEngine] graph.
 *
 * Invariants:
 * - BYOK Architecture: Supports [AiProvider.DIRECT_GEMINI] with user-provided, locally encrypted credentials.
 * - Lazy / Conditional Construction: [CloudLlmConversationEngine] is ONLY constructed
 *   when the selected provider has valid, active credentials.
 * - Otherwise, provides [DisabledCloudLlmEngine] with honest configuration failure and safe local fallback.
 */
object ConversationEngineProvider {

    fun provideConversationEngine(
        context: android.content.Context? = null,
        connectivityProbe: NetworkConnectivityProbe = DefaultNetworkConnectivityProbe(),
        /**
         * Outer timebox for one cloud turn. Derived from [AgenticSafetyBudget] (P1-4 / J-01):
         * it must strictly exceed the orchestrator's own total budget plus one in-flight iteration,
         * otherwise this outer bound cancels the agentic loop first, its budget branch becomes
         * unreachable, and the structured result + evidence are discarded on the router's local
         * fallback. This bound stays as the last-resort guard, not the primary budget.
         */
        cloudTimeoutMs: Long = AgenticSafetyBudget().totalTimeoutMs + AgenticSafetyBudget().iterationTimeoutMs,
        clientSeam: GeminiClientSeam? = null,
        toolRuntimeProvider: (suspend () -> AssistantToolRuntime?)? = null,
        cloudEngineFactory: (() -> ConversationEngine)? = null,
        credentialStorage: SecureCredentialStorage? = null,
        aiProvider: AiProvider? = null
    ): ConversationEngine {
        val localEngine = LocalRuleEngineAdapter(ConversationPolicyEngine)

        val secureStorage = credentialStorage
            ?: context?.let { DefaultSecureCredentialStorage.getInstance(it) }

        if (secureStorage != null) {
            CloudLlmFeatureGate.setCredentialSupplier { secureStorage.getCredential() }
        }

        val selectedProvider = aiProvider
            ?: context?.let { UserAiPreferences(it).getSelectedProvider() }
            ?: AiProvider.DIRECT_GEMINI

        val activeCredential = secureStorage?.getCredential()
            ?: CloudLlmFeatureGate.getApiKey()

        val isDirectGeminiConfigured = selectedProvider == AiProvider.DIRECT_GEMINI &&
                (!activeCredential.isNullOrBlank() || secureStorage?.hasCredential() == true) &&
                activeCredential != "placeholder_for_secrets"

        val isFirebaseConfigured = selectedProvider == AiProvider.FIREBASE_AI_LOGIC &&
                CloudLlmFeatureGate.isCloudLlmEnabled() &&
                !CloudLlmFeatureGate.getApiKey().isNullOrBlank()

        val isCloudConfigured = (isDirectGeminiConfigured || isFirebaseConfigured)

        val resolvedSeam: GeminiClientSeam = clientSeam ?: when (selectedProvider) {
            AiProvider.DIRECT_GEMINI -> DirectGeminiClientSeam(
                apiKeyProvider = { secureStorage?.getCredential() ?: CloudLlmFeatureGate.getApiKey() },
                config = AiModelConfig.current()
            )
            AiProvider.FIREBASE_AI_LOGIC -> RealGoogleGenAiClientSeam(
                config = AiModelConfig.current()
            )
            AiProvider.LOCAL_ONLY -> RealGoogleGenAiClientSeam()
        }

        val cloudEngine = if (isCloudConfigured && selectedProvider != AiProvider.LOCAL_ONLY) {
            cloudEngineFactory?.invoke() ?: CloudLlmConversationEngine(
                clientSeam = resolvedSeam,
                // P1-1 (K-01): suspend-native orchestrator resolution — no runBlocking. The
                // provider coroutine inherits the caller's context, stays cancellable (turn cancel,
                // close, withTimeout all reach it) and never parks the calling thread. Failures are
                // typed and logged; CancellationException is rethrown, never swallowed.
                orchestratorProvider = {
                    toolRuntimeProvider?.let { provider ->
                        try {
                            provider()?.let { rt ->
                                AgenticToolLoopOrchestrator(
                                    clientSeam = resolvedSeam,
                                    toolDispatcher = rt.dispatcher,
                                    toolRegistry = rt.descriptorRegistry
                                )
                            }
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (failure: Throwable) {
                            AiLogger.w(
                                "VoltAtlas_AI",
                                "AI_RUNTIME: agentic tool runtime resolution failed; continuing on the single-turn path: " +
                                    "${failure.javaClass.simpleName}: ${failure.message}",
                                failure
                            )
                            null
                        }
                    }
                }
            )
        } else {
            DisabledCloudLlmEngine
        }

        // P1-2 (L-01): the experimental on-device nano engine is deliberately NOT supplied
        // here — there is no provider/flag/settings surface for it yet, and its seam is a
        // keyword parser (fail-closed hardware default), not Gemini Nano hardware. The
        // router's nano branch stays dormant until wiring lands with provider + flag +
        // settings + tests. See OnDeviceNanoConversationEngine's KDoc.
        return HybridConversationRouter(
            cloudEngine = cloudEngine,
            localEngine = localEngine,
            connectivityProbe = connectivityProbe,
            cloudTimeoutMs = cloudTimeoutMs
        )
    }
}
