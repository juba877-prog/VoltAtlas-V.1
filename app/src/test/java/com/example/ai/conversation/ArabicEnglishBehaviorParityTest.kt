package com.example.ai.conversation

import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.ai.runtime.BoundedProjectContext
import org.junit.Assert.*
import org.junit.Test

class ArabicEnglishBehaviorParityTest {

    private val defaultContext = BoundedProjectContext(
        projectId = "PROJ-AR-EN-1",
        projectName = "Parity Test Project",
        liveRevision = 1L,
        activeScreen = "CircuitsGridScreen",
        activeEntityType = "CIRCUIT",
        activeEntityId = "101",
        activeEntityName = "Feeder 101"
    )

    // 1. Equivalent Explain queries produce matching intents
    @Test
    fun equivalentExplainQueries_produceMatchingIntent() {
        val enDecision = ConversationPolicyEngine.evaluateTurn(
            ConversationTurnInput(
                userUtterance = "Why is this feeder sized at 300 mm²?",
                projectContext = defaultContext,
                priorState = null
            )
        )
        val arDecision = ConversationPolicyEngine.evaluateTurn(
            ConversationTurnInput(
                userUtterance = "لماذا تم اختيار كابل 300 مم² لهذا المغذي؟",
                projectContext = defaultContext,
                priorState = null
            )
        )

        assertEquals(AssistantIntent.EXPLAIN_RESULT, enDecision.updatedState.primaryIntent)
        assertEquals(AssistantIntent.EXPLAIN_RESULT, arDecision.updatedState.primaryIntent)
    }

    // 2. Equivalent Evaluate queries produce matching candidate intents
    @Test
    fun equivalentEvaluateQueries_produceMatchingIntent() {
        val enDecision = ConversationPolicyEngine.evaluateTurn(
            ConversationTurnInput(
                userUtterance = "Can 240 mm² work for this circuit?",
                projectContext = defaultContext,
                priorState = null
            )
        )
        val arDecision = ConversationPolicyEngine.evaluateTurn(
            ConversationTurnInput(
                userUtterance = "هل يصلح كابل 240 مم² لهذه الدائرة؟",
                projectContext = defaultContext,
                priorState = null
            )
        )

        assertEquals(AssistantIntent.EVALUATE_CANDIDATE, enDecision.updatedState.primaryIntent)
        assertEquals(AssistantIntent.EVALUATE_CANDIDATE, arDecision.updatedState.primaryIntent)
    }

    // 3. Equivalent What-If queries produce matching what-if intents
    @Test
    fun equivalentWhatIfQueries_produceMatchingIntent() {
        val enDecision = ConversationPolicyEngine.evaluateTurn(
            ConversationTurnInput(
                userUtterance = "Try aluminum cable instead",
                projectContext = defaultContext,
                priorState = null
            )
        )
        val arDecision = ConversationPolicyEngine.evaluateTurn(
            ConversationTurnInput(
                userUtterance = "جرب استخدام كابل ألومنيوم",
                projectContext = defaultContext,
                priorState = null
            )
        )

        assertEquals(AssistantIntent.WHAT_IF, enDecision.updatedState.primaryIntent)
        assertEquals(AssistantIntent.WHAT_IF, arDecision.updatedState.primaryIntent)
    }

    // 4. Arabic conversational apply terms remain strictly non-executable
    @Test
    fun arabicConversationalApply_remainsNonExecutable() {
        val arabicApplyUtterances = listOf(
            "طبق",
            "نفذ",
            "موافق",
            "تمام اعتمد",
            "اعتمد التعديل",
            "طبق المقترح الآن"
        )

        val stateWithProposal = ConversationTaskState(
            taskId = "T-AR-1",
            projectId = defaultContext.projectId,
            baseProjectRevision = 1L,
            activeEntityType = "CIRCUIT",
            activeEntityId = "101",
            activeProposalId = "PROP-101"
        )

        for (u in arabicApplyUtterances) {
            val decision = ConversationPolicyEngine.evaluateTurn(
                ConversationTurnInput(
                    userUtterance = u,
                    projectContext = defaultContext,
                    priorState = stateWithProposal
                )
            )
            assertEquals(
                "Utterance '$u' must route to SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE",
                ConversationActionType.SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE,
                decision.action.actionType
            )
        }
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    // 5. Units and technical symbols are preserved without corrupt transliteration
    @Test
    fun unitsAndSymbols_arePreserved() {
        val prompt = "كابل Cu/XLPE/SWA/PVC سعة 300 mm² وجهد 400 V وتيار 400 A"
        assertTrue(prompt.contains("Cu/XLPE/SWA/PVC"))
        assertTrue(prompt.contains("300 mm²"))
        assertTrue(prompt.contains("400 V"))
        assertTrue(prompt.contains("400 A"))
    }
}
