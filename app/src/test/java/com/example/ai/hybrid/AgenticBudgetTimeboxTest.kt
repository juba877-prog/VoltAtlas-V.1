package com.example.ai.hybrid

import com.example.ai.conversation.ConversationAction
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationPolicyEngine
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationTurnDecision
import com.example.ai.conversation.ConversationTurnInput
import com.example.ai.contracts.ToolStatus
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.orchestrator.AgenticSafetyBudget
import com.example.ai.runtime.BoundedProjectContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P1-4 (J-01) regression suite: the router's OUTER cloud timebox must be derived from the single
 * authoritative [AgenticSafetyBudget] instead of a hardcoded 15 s constant that fired before the
 * orchestrator's own 30 s budget, making the budget branch unreachable and discarding its
 * structured result + evidence as a local NETWORK_TIMEOUT fallback.
 *
 * Both tests construct [HybridConversationRouter] WITHOUT an explicit `cloudTimeoutMs`, so they
 * exercise the production default derivation itself.
 */
class AgenticBudgetTimeboxTest {

    private fun sampleContext() = BoundedProjectContext(
        projectId = "PROJ-P1-4",
        projectName = "P1-4 Budget Project",
        liveRevision = 7L
    )

    private fun sampleInput() = ConversationTurnInput(
        userUtterance = "Explain the sizing result for this feeder.",
        projectContext = sampleContext(),
        priorState = ConversationTaskState(
            taskId = "TASK-P1-4",
            projectId = "PROJ-P1-4",
            baseProjectRevision = 7L
        )
    )

    private fun sampleEvidence() = EngineeringEvidenceRecord(
        evidenceId = "EVD-P1-4",
        requestId = "REQ-P1-4",
        toolName = "calculate_cable_sizing",
        projectId = "PROJ-P1-4",
        executionStatus = ToolStatus.PASS
    )

    /**
     * Cloud engine that returns a fully-backed, non-fabricated result after [delayMs].
     * Models the agentic orchestrator spending its budget and then returning its structured result.
     */
    private fun backedCloudEngine(delayMs: Long, evidence: List<EngineeringEvidenceRecord>) =
        object : ConversationEngine {
            override suspend fun evaluateTurn(
                input: ConversationTurnInput,
                decisionIdProvider: () -> String
            ): HybridEngineResult {
                delay(delayMs)
                return HybridEngineResult(
                    decision = ConversationTurnDecision(
                        decisionId = decisionIdProvider(),
                        action = ConversationAction(
                            actionType = ConversationActionType.EXPLAIN_RESULT,
                            reasonCode = "AGENTIC_BUDGET_RESULT"
                        ),
                        updatedState = input.priorState
                            ?: error("test input must carry a prior state"),
                        explanationSummary = "The bounded agentic turn completed with tool-backed evidence."
                    ),
                    source = ConversationEngineSource.CLOUD_GEMINI,
                    cloudDegradationReason = null,
                    hasBackingToolResult = true,
                    evidence = evidence
                )
            }
        }

    @Test
    fun routerDefaultOutlivesTheOrchestratorSafetyBudget() = runTest {
        val budget = AgenticSafetyBudget()
        val evidence = listOf(sampleEvidence())
        val router = HybridConversationRouter(
            cloudEngine = backedCloudEngine(delayMs = budget.totalTimeoutMs, evidence = evidence),
            localEngine = LocalRuleEngineAdapter(ConversationPolicyEngine)
            // cloudTimeoutMs intentionally omitted: the budget-derived default is under test.
        )

        val result = router.evaluateTurn(sampleInput())

        assertEquals(
            "A cloud turn that finishes exactly at the orchestrator's own budget must NOT be " +
                "cancelled by the outer bound (the old 15s constant fired first and discarded it).",
            ConversationEngineSource.CLOUD_GEMINI,
            result.source
        )
        assertEquals(
            "Evidence produced before the timeout must survive the router.",
            evidence,
            result.evidence
        )
    }

    @Test
    fun routerDefaultStillBoundsRunawayCloudTurns() = runTest {
        val budget = AgenticSafetyBudget()
        val runawayDelay = budget.totalTimeoutMs + budget.iterationTimeoutMs + 5_000L
        val router = HybridConversationRouter(
            cloudEngine = backedCloudEngine(delayMs = runawayDelay, evidence = listOf(sampleEvidence())),
            localEngine = LocalRuleEngineAdapter(ConversationPolicyEngine)
            // cloudTimeoutMs intentionally omitted: the bound must remain the last-resort guard.
        )

        val result = router.evaluateTurn(sampleInput())

        assertEquals(
            "A cloud turn exceeding the outer bound must still fall back locally.",
            ConversationEngineSource.LOCAL_RULE_ENGINE,
            result.source
        )
        assertEquals(
            CloudDegradationReason.NETWORK_TIMEOUT,
            result.cloudDegradationReason
        )
    }
}
