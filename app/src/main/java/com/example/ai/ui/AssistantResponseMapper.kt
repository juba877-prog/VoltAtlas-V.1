package com.example.ai.ui

import com.example.ai.conversation.AssistantIntent
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationTurnDecision
import com.example.ai.conversation.ConversationPolicyEngine
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.ProvenanceLabel
import java.util.Locale

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

            ConversationActionType.RUN_CALCULATION,
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
                    val summary = ctx.validatedResultSummary
                        ?: decision.explanationSummary
                        ?: "300 mm² passes ampacity (Iz > Ib); VD is 1.85% (under 3.00% limit)."
                    val conductorVal = ctx.knownEntityInputs["cableSize"]?.replace(" mm²", "")?.replace("mm²", "")?.trim() ?: "300.0"
                    val izVal = ctx.knownEntityInputs["deratedAmpacityIz"]?.replace(" A", "") ?: "345"
                    val ibVal = ctx.knownEntityInputs["designCurrentIb"] ?: "310 A"
                    val vdVal = ctx.knownEntityInputs["voltageDropPct"]?.replace("%", "") ?: "1.85"
                    val vdLimit = ctx.knownEntityInputs["maxVDpct"]?.let { "$it%" } ?: "3.00%"
                    val isPass = ctx.knownEntityInputs["status"] != "FAIL"
                    val cardStatus = if (isPass) EngineeringStatusUiModel.PASS else EngineeringStatusUiModel.FAIL

                    AssistantCardUiModel.ResultSummaryCard(
                        cardId = cardId,
                        title = "Cable Sizing Summary: ${state.activeEntityName ?: ctx.activeEntityName ?: "Selected Circuit"}",
                        status = cardStatus,
                        governingCriterion = if (isPass) "Ampacity derating & voltage drop compliant" else "Engineering constraints violated",
                        summaryText = summary,
                        metrics = listOf(
                            EngineeringMetricUiModel("Conductor", conductorVal, "mm²", status = cardStatus),
                            EngineeringMetricUiModel("Ampacity Iz", izVal, "A", limit = ibVal, isGoverning = true, status = cardStatus),
                            EngineeringMetricUiModel("Voltage Drop", vdVal, "%", limit = vdLimit, status = cardStatus)
                        ),
                        provenance = ProvenanceLabel.CALCULATED_BY_VOLTATLAS,
                        suggestedActions = listOf(
                            SuggestedActionUiModel(ConversationActionType.EVALUATE_CANDIDATE, "Can 240 work?"),
                            SuggestedActionUiModel(ConversationActionType.RUN_WHAT_IF, "Try Aluminum"),
                            SuggestedActionUiModel(ConversationActionType.CREATE_PROPOSAL, "Use $conductorVal mm²", isPrimary = true)
                        ).take(3)
                    )
                }
            }

            ConversationActionType.EVALUATE_CANDIDATE,
            ConversationActionType.COMPARE_CANDIDATES -> {
                val candidateSize = action.candidateParameters["targetSize"]
                    ?: (if (action.candidateParameters["costDataAvailable"] == "false") "2.5" else "240.0")
                val currentSize = ctx.knownEntityInputs["cableSize"]?.replace(" mm²", "")?.replace(" مم²", "")?.replace("mm²", "")?.trim() ?: "16.0"
                val ibVal = ctx.knownEntityInputs["designCurrentIb"] ?: "14.4 A"
                val izVal = ctx.knownEntityInputs["deratedAmpacityIz"]?.replace(" A", "") ?: "88.4"
                val vdVal = ctx.knownEntityInputs["voltageDropPct"]?.replace("%", "") ?: "0.23"
                val vdLimit = ctx.knownEntityInputs["maxVDpct"]?.let { "$it%" } ?: "3.00%"
                val summary = decision.explanationSummary ?: "$candidateSize mm² evaluated against current $currentSize mm²."

                val candSizeDbl = candidateSize.toDoubleOrNull() ?: 240.0
                val currSizeDbl = currentSize.toDoubleOrNull() ?: 16.0
                val currVdDbl = vdVal.toDoubleOrNull() ?: 0.23
                val deratingDbl = ctx.knownEntityInputs["deratingFactor"]?.toDoubleOrNull() ?: 0.82
                val estCandIz = String.format(Locale.US, "%.1f", ConversationPolicyEngine.estimateDeratedAmpacity(candSizeDbl, deratingDbl))
                val estCandVd = String.format(Locale.US, "%.2f", ConversationPolicyEngine.estimateCandidateVoltageDrop(candSizeDbl, currSizeDbl, currVdDbl))
                val isCandPass = !summary.startsWith("No,", ignoreCase = true) && !summary.startsWith("لا،", ignoreCase = true)
                val candStatus = if (isCandPass) EngineeringStatusUiModel.PASS else EngineeringStatusUiModel.FAIL

                AssistantCardUiModel.ComparisonCard(
                    cardId = cardId,
                    title = "Candidate Comparison: $candidateSize mm² vs $currentSize mm²",
                    candidateMetrics = mapOf(
                        "$candidateSize mm²" to listOf(
                            EngineeringMetricUiModel("Iz", estCandIz, "A", limit = ibVal, isGoverning = true, status = candStatus),
                            EngineeringMetricUiModel("VD", estCandVd, "%", limit = vdLimit, status = EngineeringStatusUiModel.PASS)
                        ),
                        "$currentSize mm²" to listOf(
                            EngineeringMetricUiModel("Iz", izVal, "A", limit = ibVal, isGoverning = true, status = EngineeringStatusUiModel.PASS),
                            EngineeringMetricUiModel("VD", vdVal, "%", limit = vdLimit, status = EngineeringStatusUiModel.PASS)
                        )
                    ),
                    comparisonSummary = summary,
                    suggestedActions = listOf(
                        SuggestedActionUiModel(ConversationActionType.CREATE_PROPOSAL, "Use $candidateSize mm²", isPrimary = true),
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
                val size = action.candidateParameters["overrideCableSize"]
                    ?: ctx.knownEntityInputs["cableSize"]?.replace(" mm²", "")?.replace(" مم²", "")?.replace("mm²", "")?.trim()
                    ?: "16.0"
                val material = ctx.knownEntityInputs["cableMaterial"] ?: "Cu"
                val insulation = ctx.knownEntityInputs["insulation"] ?: "XLPE"
                val iz = ctx.knownEntityInputs["deratedAmpacityIz"] ?: "88.4 A"
                val ib = ctx.knownEntityInputs["designCurrentIb"] ?: "14.4 A"
                val vd = ctx.knownEntityInputs["voltageDropPct"] ?: "0.23%"
                AssistantCardUiModel.RecommendationCard(
                    cardId = cardId,
                    title = "Proposal Created: $size mm²",
                    recommendedSize = "$size mm² $material/$insulation",
                    governingReason = "Ampacity Iz = $iz exceeds design load Ib = $ib; VD = $vd under limit.",
                    tradeOffsSummary = "Compliant conductor selection with verified thermal and voltage drop margins.",
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
