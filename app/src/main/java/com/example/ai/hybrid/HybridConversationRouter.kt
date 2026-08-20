package com.example.ai.hybrid

import com.example.ai.conversation.ConversationTurnInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Authoritative multiplexer routing between the Cloud LLM path and the local deterministic rule engine.
 *
 * Invariants:
 * - Offline Fast Probe: If [connectivityProbe] reports offline, immediately routes to local engine.
 * - Timebox & Headroom: Cloud call bounded by [cloudTimeoutMs]; on timeout, cancels cleanly and executes local engine.
 * - Engineering-Value Fabrication Guard: Plain text responses asserting unbacked values are discarded and rerouted to local engine.
 * - Structured Concurrency: Genuine external CancellationException propagates immediately and does not trigger local fallback.
 * - Consistent Contract: Both paths emit identical [HybridEngineResult] wrapping typed [com.example.ai.conversation.ConversationTurnDecision].
 */
class HybridConversationRouter(
    private val cloudEngine: ConversationEngine,
    private val localEngine: ConversationEngine,
    private val connectivityProbe: NetworkConnectivityProbe = DefaultNetworkConnectivityProbe(),
    private val cloudTimeoutMs: Long = 4_000L
) : ConversationEngine {

    override suspend fun evaluateTurn(
        input: ConversationTurnInput,
        decisionIdProvider: () -> String
    ): HybridEngineResult {
        // 1. Fast Offline Probe
        if (!connectivityProbe.isLikelyOnline()) {
            val localResult = localEngine.evaluateTurn(input, decisionIdProvider)
            return localResult.copy(
                source = ConversationEngineSource.LOCAL_RULE_ENGINE,
                cloudDegradationReason = CloudDegradationReason.OFFLINE_PROBE_FAILED
            )
        }

        // 2. Cloud Path with Timebox & Fallback
        return try {
            val cloudResult = withTimeout(cloudTimeoutMs) {
                cloudEngine.evaluateTurn(input, decisionIdProvider)
            }

            // Check if cloud engine itself flagged a degradation reason (e.g. rate limit, safety block)
            if (cloudResult.cloudDegradationReason != null) {
                val localFallback = localEngine.evaluateTurn(input, decisionIdProvider)
                return localFallback.copy(
                    source = ConversationEngineSource.LOCAL_RULE_ENGINE,
                    cloudDegradationReason = cloudResult.cloudDegradationReason
                )
            }

            // 3. Engineering-Value Fabrication Guard on Cloud Text
            val rawExplanation = cloudResult.decision.explanationSummary ?: ""
            val hasFabrication = EngineeringClaimGuard.containsUnsupportedEngineeringClaim(
                rawText = rawExplanation,
                hasBackingToolResult = cloudResult.hasBackingToolResult
            )

            if (hasFabrication) {
                val localFallback = localEngine.evaluateTurn(input, decisionIdProvider)
                localFallback.copy(
                    source = ConversationEngineSource.LOCAL_RULE_ENGINE,
                    cloudDegradationReason = CloudDegradationReason.UNSUPPORTED_ENGINEERING_CLAIM
                )
            } else {
                cloudResult.copy(source = ConversationEngineSource.CLOUD_GEMINI)
            }
        } catch (e: TimeoutCancellationException) {
            val localFallback = localEngine.evaluateTurn(input, decisionIdProvider)
            localFallback.copy(
                source = ConversationEngineSource.LOCAL_RULE_ENGINE,
                cloudDegradationReason = CloudDegradationReason.NETWORK_TIMEOUT
            )
        } catch (e: CancellationException) {
            // Genuine external cancellation (e.g. parent job cancelled / user dismissed sheet) must propagate
            throw e
        } catch (e: Exception) {
            val localFallback = localEngine.evaluateTurn(input, decisionIdProvider)
            localFallback.copy(
                source = ConversationEngineSource.LOCAL_RULE_ENGINE,
                cloudDegradationReason = CloudDegradationReason.HTTP_OR_PROVIDER_ERROR
            )
        }
    }
}
