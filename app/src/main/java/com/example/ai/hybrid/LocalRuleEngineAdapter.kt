package com.example.ai.hybrid

import com.example.ai.conversation.ConversationPolicyEngine
import com.example.ai.conversation.ConversationTurnInput

/**
 * Adapter wrapping the existing Phase 7F-2 [ConversationPolicyEngine]
 * as a local deterministic implementation of [ConversationEngine].
 */
class LocalRuleEngineAdapter(
    private val policyEngine: ConversationPolicyEngine = ConversationPolicyEngine
) : ConversationEngine {

    override suspend fun evaluateTurn(
        input: ConversationTurnInput,
        decisionIdProvider: () -> String
    ): HybridEngineResult {
        val decision = policyEngine.evaluateTurn(input, decisionIdProvider)
        return HybridEngineResult(
            decision = decision,
            source = ConversationEngineSource.LOCAL_RULE_ENGINE,
            cloudDegradationReason = null,
            hasBackingToolResult = true
        )
    }
}
