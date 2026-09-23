package com.example.ai.hybrid

import com.example.ai.conversation.ConversationPolicyEngine
import com.example.ai.conversation.ConversationTurnInput

/**
 * Adapter wrapping the existing Phase 7F-2 [ConversationPolicyEngine]
 * as a local deterministic implementation of [ConversationEngine].
 *
 * Evidence invariant: this adapter produces a *policy decision only*. It executes no
 * deterministic calculation tool, so it has no engineering evidence to offer and must
 * report `hasBackingToolResult = false`. Previously this was hardcoded to `true`, which
 * asserted tool backing that never existed and would have let a pure policy heuristic
 * pass any evidence-based claim check.
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
            hasBackingToolResult = false
        )
    }
}
