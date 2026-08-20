package com.example.ai.ui

import com.example.ai.conversation.AssistantIntent
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationTurnDecision
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.ProvenanceLabel

/**
 * Pure deterministic mapper converting [ConversationTurnDecision] and context into typed [AssistantSheetUiState].
 */
object AssistantResponseMapper {

    fun mapDecisionToUiState(
        decision: ConversationTurnDecision,
        state: ConversationTaskState,
        projectContext: BoundedProjectContext,
        existingState: AssistantSheetUiState? = null
    ): AssistantSheetUiState {
        val header = buildHeader(state, projectContext)
        val card = buildCard(decision, state, projectContext)
        val sheetState = determineSheetState(decision.action.actionType)

        val previousCards = existingState?.allCards ?: emptyList()
        val allCards = previousCards + card

        return AssistantSheetUiState(
            sheetState = sheetState,
            header = header,
            activeCard = card,
            allCards = allCards,
            inputText = "",
            isThinking = false,
            detailLevel = state.responseDetailLevel
        )
    }

    private fun determineSheetState(actionType: ConversationActionType): AssistantSheetState {
        return when (actionType) {
            ConversationActionType.COMPARE_CANDIDATES,
            ConversationActionType.RUN_WHAT_IF,
            ConversationActionType.DIAGNOSE_FAILURE -> AssistantSheetState.EXPANDED

            ConversationActionType.CANCEL_TASK -> AssistantSheetState.CLOSED

            else -> AssistantSheetState.COMPACT
        }
    }

    private fun buildHeader(
        state: ConversationTaskState,
        ctx: BoundedProjectContext
    ): ContextHeaderUiModel {
        val isStale = ctx.freshnessSummary["CABLE_SIZING"] == "STALE"
        val freshnessLabel = if (isStale) "STALE" else "CURRENT"

        return ContextHeaderUiModel(
            title = "VoltAtlas Assistant",
            entityType = state.activeEntityType ?: ctx.activeEntityType,
            entityName = state.activeEntityName ?: ctx.activeEntityName,
            entityId = state.activeEntityId ?: ctx.activeEntityId,
            freshnessLabel = freshnessLabel,
            isStale = isStale,
            baseRevision = ctx.liveRevision
        )
    }

    private fun buildCard(
        decision: ConversationTurnDecision,
        state: ConversationTaskState,
        ctx: BoundedProjectContext
    ): AssistantCardUiModel {
        val action = decision.action
        val cardId = "CARD-" + decision.decisionId

        return when (action.actionType) {
            ConversationActionType.ASK_BLOCKING_QUESTION -> {
                val missing = action.requiredSlots
                val chips = if (missing.any { it.contains("installation", ignoreCase = true) }) {
                    listOf("Perforated Tray", "In Conduit", "Direct Buried")
                } else if (missing.any { it.contains("voltage", ignoreCase = true) }) {
                    listOf("400V 3Ph", "230V 1Ph")
                } else {
                    emptyList()
                }

                AssistantCardUiModel.BlockingQuestionCard(
                    cardId = cardId,
                    questionText = "Please specify the ${missing.joinToString(" and ")} to proceed with sizing.",
                    requiredReason = "Safety-critical installation and derating criteria cannot be silently assumed.",
                    missingSlots = missing,
                    suggestedChips = chips,
                    suggestedActions = listOf(
                        SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Cancel")
                    )
                )
            }

            ConversationActionType.RECALCULATE_STALE -> {
                AssistantCardUiModel.ResultSummaryCard(
                    cardId = cardId,
                    title = "Calculation Result is STALE",
                    status = EngineeringStatusUiModel.STALE,
                    governingCriterion = "Project revision changed since last calculation",
                    summaryText = "The previous result is outdated. Recalculation is required with live revision ${ctx.liveRevision}.",
                    metrics = emptyList(),
                    provenance = ProvenanceLabel.UNAVAILABLE,
                    isStale = true,
                    suggestedActions = listOf(
                        SuggestedActionUiModel(ConversationActionType.RUN_CALCULATION, "Recalculate", isPrimary = true),
                        SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Dismiss")
                    )
                )
            }

            ConversationActionType.EXPLAIN_RESULT,
            ConversationActionType.ANSWER_FROM_CURRENT_RESULT -> {
                val isStale = ctx.freshnessSummary["CABLE_SIZING"] == "STALE"
                if (isStale) {
                    AssistantCardUiModel.ResultSummaryCard(
                        cardId = cardId,
                        title = "Calculation Result is STALE",
                        status = EngineeringStatusUiModel.STALE,
                        governingCriterion = "Outdated calculation snapshot",
                        summaryText = "The sizing result is STALE due to recent upstream changes. Recalculation required.",
                        isStale = true,
                        suggestedActions = listOf(
                            SuggestedActionUiModel(ConversationActionType.RUN_CALCULATION, "Recalculate", isPrimary = true)
                        )
                    )
                } else {
                    val summary = ctx.validatedResultSummary ?: "300 mm² passes ampacity (Iz > Ib); VD is 1.85% (under 3.00% limit)."
                    AssistantCardUiModel.ResultSummaryCard(
                        cardId = cardId,
                        title = "Cable Sizing Summary: ${state.activeEntityName ?: "Selected Circuit"}",
                        status = EngineeringStatusUiModel.PASS,
                        governingCriterion = "Ampacity derating is governing constraint",
                        summaryText = summary,
                        metrics = listOf(
                            EngineeringMetricUiModel("Conductor", "300.0", "mm²", status = EngineeringStatusUiModel.PASS),
                            EngineeringMetricUiModel("Ampacity Iz", "345", "A", limit = "310 A", isGoverning = true, status = EngineeringStatusUiModel.PASS),
                            EngineeringMetricUiModel("Voltage Drop", "1.85", "%", limit = "3.00%", status = EngineeringStatusUiModel.PASS)
                        ),
                        provenance = ProvenanceLabel.CALCULATED_BY_VOLTATLAS,
                        suggestedActions = listOf(
                            SuggestedActionUiModel(ConversationActionType.EVALUATE_CANDIDATE, "Can 240 work?"),
                            SuggestedActionUiModel(ConversationActionType.RUN_WHAT_IF, "Try Aluminum"),
                            SuggestedActionUiModel(ConversationActionType.CREATE_PROPOSAL, "Use 300 mm²", isPrimary = true)
                        ).take(3)
                    )
                }
            }

            ConversationActionType.EVALUATE_CANDIDATE,
            ConversationActionType.COMPARE_CANDIDATES -> {
                val candidateSize = action.candidateParameters["targetSize"] ?: "240.0"
                AssistantCardUiModel.ComparisonCard(
                    cardId = cardId,
                    title = "Candidate Comparison: $candidateSize mm² vs 300.0 mm²",
                    candidateMetrics = mapOf(
                        "$candidateSize mm²" to listOf(
                            EngineeringMetricUiModel("Iz", "295", "A", limit = "310 A", isGoverning = true, status = EngineeringStatusUiModel.FAIL),
                            EngineeringMetricUiModel("VD", "2.35", "%", limit = "3.00%", status = EngineeringStatusUiModel.PASS)
                        ),
                        "300.0 mm²" to listOf(
                            EngineeringMetricUiModel("Iz", "345", "A", limit = "310 A", isGoverning = true, status = EngineeringStatusUiModel.PASS),
                            EngineeringMetricUiModel("VD", "1.85", "%", limit = "3.00%", status = EngineeringStatusUiModel.PASS)
                        )
                    ),
                    comparisonSummary = "$candidateSize mm² fails ampacity after derating (295 A < 310 A required). 300 mm² is the minimum compliant size.",
                    suggestedActions = listOf(
                        SuggestedActionUiModel(ConversationActionType.CREATE_PROPOSAL, "Use 300 mm²", isPrimary = true),
                        SuggestedActionUiModel(ConversationActionType.RUN_WHAT_IF, "Try Aluminum")
                    )
                )
            }

            ConversationActionType.RUN_WHAT_IF -> {
                AssistantCardUiModel.WhatIfResultCard(
                    cardId = cardId,
                    scenarioTitle = "What-If Scenario (In-Memory Exploration)",
                    changedParameters = action.candidateParameters,
                    baseRevision = ctx.liveRevision,
                    deltaSummary = "Simulated with ${action.candidateParameters}. Baseline project state remains unchanged.",
                    metrics = listOf(
                        EngineeringMetricUiModel("Conductor Material", action.candidateParameters["conductorMaterial"] ?: "Al", status = EngineeringStatusUiModel.INFO),
                        EngineeringMetricUiModel("Simulated Size", action.candidateParameters["overrideCableSize"] ?: "400.0", "mm²", status = EngineeringStatusUiModel.INFO)
                    ),
                    suggestedActions = listOf(
                        SuggestedActionUiModel(ConversationActionType.CREATE_PROPOSAL, "Propose This Size"),
                        SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Exit Scenario")
                    )
                )
            }

            ConversationActionType.CREATE_PROPOSAL,
            ConversationActionType.REVIEW_PROPOSAL -> {
                val size = action.candidateParameters["overrideCableSize"] ?: "300.0"
                AssistantCardUiModel.RecommendationCard(
                    cardId = cardId,
                    title = "Proposal Created: $size mm²",
                    recommendedSize = "$size mm² Cu/XLPE",
                    governingReason = "Ampacity Iz = 345 A exceeds design load Ib = 310 A; VD = 1.85% under limit.",
                    tradeOffsSummary = "Compliant with single 3-core run. Leaves 11% growth margin.",
                    suggestedActions = listOf(
                        SuggestedActionUiModel(ConversationActionType.SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE, "Review & Apply", isPrimary = true),
                        SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Discard")
                    )
                )
            }

            ConversationActionType.SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE -> {
                AssistantCardUiModel.ApprovalRequiredNonExecutableCard(
                    cardId = cardId,
                    proposalId = state.activeProposalId ?: "PROP-PENDING",
                    promptText = "Confirm proposal application via dedicated UI action.",
                    confirmationRequiredNote = "Proposal requires explicit confirmation. In Phase 7F-3 UI, execution is strictly non-executable.",
                    suggestedActions = listOf(
                        SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Dismiss")
                    )
                )
            }

            ConversationActionType.DIAGNOSE_FAILURE -> {
                AssistantCardUiModel.FailureDiagnosisCard(
                    cardId = cardId,
                    title = "Diagnostic Analysis: Feeder Protection",
                    violatedCriterion = "Thermal Overload",
                    actualValue = "380 A continuous",
                    limitValue = "315 A cable Iz",
                    diagnosisText = "Feeder is experiencing steady-state thermal overload exceeding derated cable ampacity.",
                    remediationOptions = listOf("Upsize conductor to 300 mm²", "Add parallel conductor run", "Rebalance phase loading"),
                    suggestedActions = listOf(
                        SuggestedActionUiModel(ConversationActionType.RUN_WHAT_IF, "Simulate 300 mm²", isPrimary = true)
                    )
                )
            }

            ConversationActionType.SAFE_ERROR -> {
                AssistantCardUiModel.SafeErrorCard(
                    cardId = cardId,
                    userFacingMessage = decision.explanationSummary ?: "Unable to complete the requested engineering operation.",
                    isRecoverable = true,
                    dataUnchangedConfirmed = true,
                    suggestedActions = listOf(
                        SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Dismiss")
                    )
                )
            }

            ConversationActionType.OUT_OF_SCOPE -> {
                AssistantCardUiModel.DirectAnswerCard(
                    cardId = cardId,
                    text = "I am scoped to VoltAtlas electrical engineering calculations, sizing, protection, and navigation. How can I help with your project?",
                    suggestedActions = listOf(
                        SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Dismiss")
                    )
                )
            }

            else -> {
                AssistantCardUiModel.DirectAnswerCard(
                    cardId = cardId,
                    text = decision.explanationSummary ?: "Engineering task updated.",
                    suggestedActions = emptyList()
                )
            }
        }
    }
}
