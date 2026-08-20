package com.example.ai.hybrid

import com.example.ai.conversation.ConversationAction
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationTurnDecision
import com.example.ai.conversation.ConversationTurnInput
import com.example.ai.conversation.ConversationPolicyEngine

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
 * - Lazy / Conditional Construction: [CloudLlmConversationEngine] is ONLY constructed
 *   when [CloudLlmFeatureGate] is explicitly enabled AND a non-blank API key is present.
 * - Otherwise, provides [DisabledCloudLlmEngine] with zero SDK initialization.
 */
object ConversationEngineProvider {

    fun provideConversationEngine(
        connectivityProbe: NetworkConnectivityProbe = DefaultNetworkConnectivityProbe(),
        cloudTimeoutMs: Long = 4_000L,
        cloudEngineFactory: () -> ConversationEngine = { CloudLlmConversationEngine() }
    ): ConversationEngine {
        val localEngine = LocalRuleEngineAdapter(ConversationPolicyEngine)
        val isCloudConfigured = CloudLlmFeatureGate.isCloudLlmEnabled() && !CloudLlmFeatureGate.getApiKey().isNullOrBlank()
        val cloudEngine = if (isCloudConfigured) {
            cloudEngineFactory()
        } else {
            DisabledCloudLlmEngine
        }
        return HybridConversationRouter(
            cloudEngine = cloudEngine,
            localEngine = localEngine,
            connectivityProbe = connectivityProbe,
            cloudTimeoutMs = cloudTimeoutMs
        )
    }
}
