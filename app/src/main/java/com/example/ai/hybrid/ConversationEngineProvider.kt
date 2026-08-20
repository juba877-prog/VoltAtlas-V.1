package com.example.ai.hybrid

import com.example.ai.conversation.ConversationPolicyEngine

/**
 * Composition provider constructing the production [ConversationEngine] graph.
 */
object ConversationEngineProvider {

    fun provideConversationEngine(
        connectivityProbe: NetworkConnectivityProbe = DefaultNetworkConnectivityProbe(),
        cloudTimeoutMs: Long = 4_000L
    ): ConversationEngine {
        val localEngine = LocalRuleEngineAdapter(ConversationPolicyEngine)
        val cloudEngine = CloudLlmConversationEngine()
        return HybridConversationRouter(
            cloudEngine = cloudEngine,
            localEngine = localEngine,
            connectivityProbe = connectivityProbe,
            cloudTimeoutMs = cloudTimeoutMs
        )
    }
}
