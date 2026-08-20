package com.example.ai.ui

import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.ai.conversation.*
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.PromptPermissionMode
import com.example.ai.runtime.ProvenanceLabel
import com.example.ai.runtime.ResponseMode
import org.junit.Assert.*
import org.junit.Test

class AssistantResponseMapperTest {

    private fun createProjectContext(
        freshness: String = "CURRENT",
        entityName: String? = "Feeder C-07"
    ): BoundedProjectContext {
        return BoundedProjectContext(
            projectId = "PROJ-101",
            projectName = "Commercial Mall",
            liveRevision = 4L,
            activeScreen = "CircuitsGridScreen",
            activeEntityType = "CIRCUIT",
            activeEntityId = "201",
            activeEntityName = entityName,
            knownEntityInputs = mapOf(
                "lengthM" to "45.0",
                "installation" to "Perforated Cable Tray"
            ),
            freshnessSummary = mapOf("CABLE_SIZING" to freshness),
            validatedResultSummary = "300 mm² Cu, Iz=345A, VD=1.85%"
        )
    }

    private fun createTurnDecision(
        actionType: ConversationActionType,
        reasonCode: String = "TEST_DECISION",
        requiredSlots: List<String> = emptyList(),
        candidateParams: Map<String, String> = emptyMap(),
        responseMode: ResponseMode = ResponseMode.DIRECT_ANSWER
    ): ConversationTurnDecision {
        return ConversationTurnDecision(
            decisionId = "DEC-9001",
            action = ConversationAction(
                actionType = actionType,
                reasonCode = reasonCode,
                requiredPermission = PromptPermissionMode.READ,
                requiredSlots = requiredSlots,
                responseMode = responseMode,
                candidateParameters = candidateParams
            ),
            updatedState = ConversationTaskState(
                taskId = "TASK-9001",
                projectId = "PROJ-101",
                baseProjectRevision = 4L,
                activeEntityType = "CIRCUIT",
                activeEntityId = "201",
                activeEntityName = "Feeder C-07"
            )
        )
    }

    // 1. Direct answer maps to compact card
    @Test
    fun directAnswer_mapsToCompactCard() {
        val ctx = createProjectContext()
        val decision = createTurnDecision(ConversationActionType.OUT_OF_SCOPE)
        val uiState = AssistantResponseMapper.mapDecisionToUiState(decision, decision.updatedState, ctx)

        assertEquals(AssistantSheetState.COMPACT, uiState.sheetState)
        assertTrue(uiState.activeCard is AssistantCardUiModel.DirectAnswerCard)
    }

    // 2. Blocking question maps to compact card
    @Test
    fun blockingQuestion_mapsToCompactCard() {
        val ctx = createProjectContext()
        val decision = createTurnDecision(
            actionType = ConversationActionType.ASK_BLOCKING_QUESTION,
            requiredSlots = listOf("installationMethod")
        )
        val uiState = AssistantResponseMapper.mapDecisionToUiState(decision, decision.updatedState, ctx)

        assertEquals(AssistantSheetState.COMPACT, uiState.sheetState)
        assertTrue(uiState.activeCard is AssistantCardUiModel.BlockingQuestionCard)
        val card = uiState.activeCard as AssistantCardUiModel.BlockingQuestionCard
        assertEquals(listOf("installationMethod"), card.missingSlots)
    }

    // 3. Multiple blocking inputs are grouped in one card
    @Test
    fun multipleBlockingInputs_areGroupedInOneCard() {
        val ctx = createProjectContext()
        val decision = createTurnDecision(
            actionType = ConversationActionType.ASK_BLOCKING_QUESTION,
            requiredSlots = listOf("installationMethod", "lengthM")
        )
        val uiState = AssistantResponseMapper.mapDecisionToUiState(decision, decision.updatedState, ctx)

        assertTrue(uiState.activeCard is AssistantCardUiModel.BlockingQuestionCard)
        val card = uiState.activeCard as AssistantCardUiModel.BlockingQuestionCard
        assertEquals(2, card.missingSlots.size)
        assertTrue(card.questionText.contains("installationMethod"))
        assertTrue(card.questionText.contains("lengthM"))
    }

    // 4. Result summary shows authoritative metrics only
    @Test
    fun resultSummary_showsAuthoritativeMetricsOnly() {
        val ctx = createProjectContext(freshness = "CURRENT")
        val decision = createTurnDecision(ConversationActionType.EXPLAIN_RESULT)
        val uiState = AssistantResponseMapper.mapDecisionToUiState(decision, decision.updatedState, ctx)

        assertTrue(uiState.activeCard is AssistantCardUiModel.ResultSummaryCard)
        val card = uiState.activeCard as AssistantCardUiModel.ResultSummaryCard
        assertEquals(EngineeringStatusUiModel.PASS, card.status)
        assertEquals(ProvenanceLabel.CALCULATED_BY_VOLTATLAS, card.provenance)
        assertFalse(card.isStale)
        assertTrue(card.metrics.isNotEmpty())
    }

    // 5. Stale result never uses current recommendation style
    @Test
    fun staleResult_neverUsesCurrentRecommendationStyle() {
        val ctx = createProjectContext(freshness = "STALE")
        val decision = createTurnDecision(ConversationActionType.RECALCULATE_STALE)
        val uiState = AssistantResponseMapper.mapDecisionToUiState(decision, decision.updatedState, ctx)

        assertTrue(uiState.activeCard is AssistantCardUiModel.ResultSummaryCard)
        val card = uiState.activeCard as AssistantCardUiModel.ResultSummaryCard
        assertEquals(EngineeringStatusUiModel.STALE, card.status)
        assertTrue(card.isStale)
    }

    // 6. What-if card displays not applied label
    @Test
    fun whatIfCard_displaysNotAppliedLabel() {
        val ctx = createProjectContext()
        val decision = createTurnDecision(
            actionType = ConversationActionType.RUN_WHAT_IF,
            candidateParams = mapOf("conductorMaterial" to "Al", "overrideCableSize" to "400.0")
        )
        val uiState = AssistantResponseMapper.mapDecisionToUiState(decision, decision.updatedState, ctx)

        assertEquals(AssistantSheetState.EXPANDED, uiState.sheetState)
        assertTrue(uiState.activeCard is AssistantCardUiModel.WhatIfResultCard)
        val card = uiState.activeCard as AssistantCardUiModel.WhatIfResultCard
        assertEquals("Al", card.changedParameters["conductorMaterial"])
        assertTrue(card.deltaSummary.contains("Baseline project state remains unchanged"))
    }

    // 7. Approval required card has no executable callback
    @Test
    fun approvalRequiredCard_hasNoExecutableCallback() {
        val ctx = createProjectContext()
        val decision = createTurnDecision(ConversationActionType.SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE)
        val uiState = AssistantResponseMapper.mapDecisionToUiState(decision, decision.updatedState, ctx)

        assertTrue(uiState.activeCard is AssistantCardUiModel.ApprovalRequiredNonExecutableCard)
        val card = uiState.activeCard as AssistantCardUiModel.ApprovalRequiredNonExecutableCard
        assertTrue(card.confirmationRequiredNote.contains("non-executable"))
    }

    // 8. Suggested actions are limited to three
    @Test
    fun suggestedActions_areLimitedToThree() {
        val ctx = createProjectContext()
        val decision = createTurnDecision(ConversationActionType.EXPLAIN_RESULT)
        val uiState = AssistantResponseMapper.mapDecisionToUiState(decision, decision.updatedState, ctx)

        val card = uiState.activeCard as AssistantCardUiModel.ResultSummaryCard
        assertTrue(card.suggestedActions.size <= 3)
    }

    // 9. Safe error hides raw diagnostics
    @Test
    fun safeError_hidesRawDiagnostics() {
        val ctx = createProjectContext()
        val decision = createTurnDecision(ConversationActionType.SAFE_ERROR)
        val uiState = AssistantResponseMapper.mapDecisionToUiState(decision, decision.updatedState, ctx)

        assertTrue(uiState.activeCard is AssistantCardUiModel.SafeErrorCard)
        val card = uiState.activeCard as AssistantCardUiModel.SafeErrorCard
        assertFalse(card.userFacingMessage.contains("SQLException"))
        assertFalse(card.userFacingMessage.contains("NullPointerException"))
        assertTrue(card.dataUnchangedConfirmed)
    }

    // 10. Context header shows resolved entity and omits unresolved entity
    @Test
    fun contextHeader_showsResolvedEntity_andOmitsUnresolved() {
        val ctxResolved = createProjectContext(entityName = "Feeder C-07")
        val decision1 = createTurnDecision(ConversationActionType.EXPLAIN_RESULT)
        val uiState1 = AssistantResponseMapper.mapDecisionToUiState(decision1, decision1.updatedState, ctxResolved)

        assertTrue(uiState1.header.hasResolvedEntity)
        assertEquals("CIRCUIT · Feeder C-07", uiState1.header.displayBadgeText)

        val ctxUnresolved = BoundedProjectContext("P-1", "Mall", 1L)
        val stateUnresolved = ConversationTaskState("T-1", "P-1", 1L)
        val decision2 = createTurnDecision(ConversationActionType.OUT_OF_SCOPE)
        val uiState2 = AssistantResponseMapper.mapDecisionToUiState(decision2, stateUnresolved, ctxUnresolved)

        assertFalse(uiState2.header.hasResolvedEntity)
        assertNull(uiState2.header.displayBadgeText)
    }

    // 11. Comparison expands sheet
    @Test
    fun comparisonExpandsSheet() {
        val ctx = createProjectContext()
        val decision = createTurnDecision(ConversationActionType.COMPARE_CANDIDATES)
        val uiState = AssistantResponseMapper.mapDecisionToUiState(decision, decision.updatedState, ctx)

        assertEquals(AssistantSheetState.EXPANDED, uiState.sheetState)
        assertTrue(uiState.isExpanded)
        assertTrue(uiState.activeCard is AssistantCardUiModel.ComparisonCard)
    }
}
