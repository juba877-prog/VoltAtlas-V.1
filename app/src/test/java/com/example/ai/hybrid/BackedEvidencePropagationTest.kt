package com.example.ai.hybrid

import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationTurnInput
import com.example.ai.contracts.ToolStatus
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.runtime.BoundedProjectContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P1-5 (J-02) regression suite: the single-turn plain-text branch of
 * [CloudLlmConversationEngine] must propagate the evidence-backed flag
 * (`hasBackingToolResult = hasBackingEvidence`) instead of hardcoding `false`.
 *
 * The defect: a turn backed by prior tool evidence still returned `hasBackingToolResult = false`,
 * so [HybridConversationRouter]'s fabrication guard discarded BACKED text as an unbacked
 * engineering claim — over-rejection of legitimate, tool-backed answers.
 */
class BackedEvidencePropagationTest {

    @Before
    fun setUp() {
        CloudLlmFeatureGate.configureForTesting(enabled = true, apiKey = "mock-valid-api-key")
    }

    @After
    fun tearDown() {
        CloudLlmFeatureGate.resetToDefaults()
    }

    private class TextSeam(private val responseText: String) : GeminiClientSeam {
        override suspend fun generate(
            systemPrompt: String,
            userPrompt: String,
            tools: List<CloudFunctionDeclaration>
        ): CloudLlmRawResponse = CloudLlmRawResponse(text = responseText)
    }

    private fun sampleContext() = BoundedProjectContext(
        projectId = "PROJ-P1-5",
        projectName = "P1-5 Backing Project",
        liveRevision = 7L
    )

    private fun sampleInput(
        utterance: String,
        priorEvidence: List<EngineeringEvidenceRecord>
    ) = ConversationTurnInput(
        userUtterance = utterance,
        projectContext = sampleContext(),
        priorState = ConversationTaskState(
            taskId = "TASK-P1-5",
            projectId = "PROJ-P1-5",
            baseProjectRevision = 7L
        ),
        priorEvidence = priorEvidence
    )

    /** Domain-relevant, fresh, project-matching evidence (passes every GroundedResponseValidator stage). */
    private fun backingEvidence() = EngineeringEvidenceRecord(
        evidenceId = "EVD-P1-5",
        requestId = "REQ-P1-5",
        toolName = "calculate_cable_sizing",
        projectId = "PROJ-P1-5",
        executionStatus = ToolStatus.PASS
    )

    @Test
    fun backedTurnReportsItsBackingFlag() {
        val engine = CloudLlmConversationEngine(
            clientSeam = TextSeam("Hello! How can I help you today?")
        )
        val evidence = listOf(backingEvidence())

        val result = runBlocking {
            engine.evaluateTurn(sampleInput(utterance = "Thanks, continue please.", priorEvidence = evidence))
        }

        assertEquals(
            "A turn carrying prior tool evidence must report hasBackingToolResult=true; the " +
                "single-turn branch must propagate the flag, not hardcode false (J-02).",
            true,
            result.hasBackingToolResult
        )
        assertEquals(evidence, result.evidence)
    }

    @Test
    fun backedEngineeringClaimIsNotOverRejectedByTheRouter() {
        // Claim-bearing response text: numeric engineering unit + recommendation verb.
        val engine = ConversationEngineProvider.provideConversationEngine(
            clientSeam = TextSeam("Use 300 mm2 for this feeder."),
            toolRuntimeProvider = null
        )
        val evidence = listOf(backingEvidence())

        val result = runBlocking {
            engine.evaluateTurn(
                sampleInput(
                    utterance = "What size cable should I use for this feeder?",
                    priorEvidence = evidence
                )
            )
        }

        assertEquals(
            "A BACKED engineering claim must survive the router's fabrication guard and reach the " +
                "user on the cloud path; over-rejection to the local engine was the J-02 defect.",
            ConversationEngineSource.CLOUD_GEMINI,
            result.source
        )
        assertEquals(
            "The router must preserve the propagated backing flag on the returned result.",
            true,
            result.hasBackingToolResult
        )
        assertEquals(evidence, result.evidence)
        assertEquals(null, result.cloudDegradationReason)
    }

    @Test
    fun unbackedEngineeringClaimStillFailsClosed() {
        // No prior evidence: the same claim text must never be treated as backed.
        val engine = CloudLlmConversationEngine(
            clientSeam = TextSeam("Use 300 mm2 for this feeder.")
        )

        val result = runBlocking {
            engine.evaluateTurn(
                sampleInput(utterance = "Recommend a cable size.", priorEvidence = emptyList())
            )
        }

        assertEquals(
            "Unbacked engineering claims must keep failing closed (anti-weakening: the J-02 fix " +
                "propagates the flag, it never disables the guard).",
            CloudDegradationReason.UNSUPPORTED_ENGINEERING_CLAIM,
            result.cloudDegradationReason
        )
        assertEquals(false, result.hasBackingToolResult)
    }
}
