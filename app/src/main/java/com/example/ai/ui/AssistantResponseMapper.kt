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

    /**
     * Rendered whenever an authoritative value is absent. The UI must never substitute a
     * plausible engineering number for missing evidence, and must never render a metric
     * whose value it cannot source.
     */
    private const val UNAVAILABLE = "Verified engineering result unavailable."

    /** Maps a project-supplied status string to a typed UI status; null when unknown. */
    private fun statusFrom(raw: String?): EngineeringStatusUiModel? = when (raw?.trim()?.uppercase()) {
        "PASS" -> EngineeringStatusUiModel.PASS
        "FAIL" -> EngineeringStatusUiModel.FAIL
        "WARNING" -> EngineeringStatusUiModel.WARNING
        "STALE" -> EngineeringStatusUiModel.STALE
        else -> null
    }

    /** Reads a known entity input, returning null when absent or blank. */
    private fun BoundedProjectContext.input(key: String): String? =
        knownEntityInputs[key]?.takeIf { it.isNotBlank() }

    /** Builds a metric only when a real value exists; absent values are never rendered. */
    private fun metricOrNull(
        label: String,
        raw: String?,
        unit: String? = null,
        limit: String? = null,
        isGoverning: Boolean = false,
        status: EngineeringStatusUiModel? = null
    ): EngineeringMetricUiModel? {
        val value = raw?.takeIf { it.isNotBlank() } ?: return null
        return EngineeringMetricUiModel(
            label = label,
            value = value,
            unit = unit,
            limit = limit,
            isGoverning = isGoverning,
            status = status ?: EngineeringStatusUiModel.INFO
        )
    }

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
                // No hardcoded option chips: installation methods and system voltages are
                // engineering choices that must come from project data or the catalog, never
                // from the UI layer.
                val chips = emptyList<String>()

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
                        ?: UNAVAILABLE
                    val conductorVal = ctx.input("cableSize")?.replace(" mm²", "")?.replace("mm²", "")?.trim()
                    val izVal = ctx.input("deratedAmpacityIz")?.replace(" A", "")
                    val ibVal = ctx.input("designCurrentIb")
                    val vdVal = ctx.input("voltageDropPct")?.replace("%", "")
                    val vdLimit = ctx.input("maxVDpct")?.let { "$it%" }

                    // Status must come from authoritative project data. Its absence is reported
                    // as unknown; it is never reinterpreted as PASS.
                    val cardStatus = statusFrom(ctx.input("status")) ?: EngineeringStatusUiModel.INFO
                    val governingCriterion = when (cardStatus) {
                        EngineeringStatusUiModel.PASS ->
                            ctx.input("governingCriterion") ?: "Compliance confirmed by authoritative calculation."
                        EngineeringStatusUiModel.FAIL ->
                            ctx.input("failReason") ?: "Engineering constraints violated"
                        EngineeringStatusUiModel.WARNING ->
                            "Marginal compliance reported by authoritative calculation"
                        else -> UNAVAILABLE
                    }

                    val metrics = listOfNotNull(
                        metricOrNull("Conductor", conductorVal, "mm²", status = cardStatus),
                        metricOrNull("Ampacity Iz", izVal, "A", limit = ibVal, isGoverning = true, status = cardStatus),
                        metricOrNull("Voltage Drop", vdVal, "%", limit = vdLimit, status = cardStatus)
                    )

                    AssistantCardUiModel.ResultSummaryCard(
                        cardId = cardId,
                        title = "Cable Sizing Summary: ${state.activeEntityName ?: ctx.activeEntityName ?: "Selected Circuit"}",
                        status = cardStatus,
                        governingCriterion = governingCriterion,
                        summaryText = summary,
                        metrics = metrics,
                        provenance = if (summary == UNAVAILABLE) ProvenanceLabel.UNAVAILABLE
                                     else ProvenanceLabel.CALCULATED_BY_VOLTATLAS,
                        suggestedActions = listOfNotNull(
                            SuggestedActionUiModel(ConversationActionType.EVALUATE_CANDIDATE, "Evaluate a candidate"),
                            SuggestedActionUiModel(ConversationActionType.RUN_WHAT_IF, "Run what-if"),
                            conductorVal?.let {
                                SuggestedActionUiModel(ConversationActionType.CREATE_PROPOSAL, "Propose $it mm²", isPrimary = true)
                            }
                        )
                    )
                }
            }

            ConversationActionType.EVALUATE_CANDIDATE,
            ConversationActionType.COMPARE_CANDIDATES -> {
                val candidateSize = action.candidateParameters["targetSize"]
                    ?: action.candidateParameters["candidateSizeMm2"]
                    ?: action.candidateParameters["candidateSizeText"]
                val currentSize = ctx.input("cableSize")
                    ?.replace(" mm²", "")?.replace(" مم²", "")?.replace("mm²", "")?.trim()
                val ibVal = ctx.input("designCurrentIb")
                val izVal = ctx.input("deratedAmpacityIz")?.replace(" A", "")
                val vdVal = ctx.input("voltageDropPct")?.replace("%", "")
                val vdLimit = ctx.input("maxVDpct")?.let { "$it%" }
                val summary = decision.explanationSummary ?: UNAVAILABLE

                // Candidate ampacity and voltage drop are NEVER estimated here. Estimating them
                // would be a second engineering implementation living in the UI. Candidate
                // metrics must arrive as tool evidence; absent evidence renders no metric.
                val currentStatus = statusFrom(ctx.input("status")) ?: EngineeringStatusUiModel.INFO
                val currentLabel = currentSize?.let { "$it mm²" } ?: "Current"
                val candidateLabel = candidateSize?.let { "$it mm²" } ?: "Candidate"

                val currentMetrics = listOfNotNull(
                    metricOrNull("Iz", izVal, "A", limit = ibVal, isGoverning = true, status = currentStatus),
                    metricOrNull("VD", vdVal, "%", limit = vdLimit, status = currentStatus)
                )
                val candidateMetrics = buildMap {
                    if (currentMetrics.isNotEmpty()) put(currentLabel, currentMetrics)
                }

                AssistantCardUiModel.ComparisonCard(
                    cardId = cardId,
                    title = "Candidate Comparison: $candidateLabel vs $currentLabel",
                    candidateMetrics = candidateMetrics,
                    comparisonSummary = summary,
                    suggestedActions = listOfNotNull(
                        candidateSize?.let {
                            SuggestedActionUiModel(ConversationActionType.CREATE_PROPOSAL, "Propose $it mm²", isPrimary = true)
                        },
                        SuggestedActionUiModel(ConversationActionType.RUN_WHAT_IF, "Run what-if")
                    )
                )
            }

            ConversationActionType.RUN_WHAT_IF -> {
                val scenarioMetrics = listOfNotNull(
                    action.candidateParameters["conductorMaterial"]?.takeIf { it.isNotBlank() }?.let {
                        EngineeringMetricUiModel("Conductor Material", it, status = EngineeringStatusUiModel.INFO)
                    },
                    action.candidateParameters["overrideCableSize"]?.takeIf { it.isNotBlank() }?.let {
                        EngineeringMetricUiModel("Simulated Size", it, "mm²", status = EngineeringStatusUiModel.INFO)
                    }
                )
                AssistantCardUiModel.WhatIfResultCard(
                    cardId = cardId,
                    scenarioTitle = "What-If Scenario (In-Memory Exploration)",
                    changedParameters = action.candidateParameters,
                    baseRevision = ctx.liveRevision,
                    deltaSummary = "Simulated with ${action.candidateParameters}. Baseline project state remains unchanged.",
                    metrics = scenarioMetrics,
                    suggestedActions = listOf(
                        SuggestedActionUiModel(ConversationActionType.CREATE_PROPOSAL, "Propose This Size"),
                        SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Exit Scenario")
                    )
                )
            }

            ConversationActionType.CREATE_PROPOSAL,
            ConversationActionType.REVIEW_PROPOSAL -> {
                val size = action.candidateParameters["overrideCableSize"]
                    ?: ctx.input("cableSize")?.replace(" mm²", "")?.replace(" مم²", "")?.replace("mm²", "")?.trim()

                if (size == null) {
                    // No authoritative proposed size exists: never fabricate one.
                    AssistantCardUiModel.SafeErrorCard(
                        cardId = cardId,
                        userFacingMessage = UNAVAILABLE,
                        isRecoverable = true,
                        dataUnchangedConfirmed = true,
                        suggestedActions = listOf(
                            SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Dismiss")
                        )
                    )
                } else {
                    val material = ctx.input("cableMaterial")
                    val insulation = ctx.input("insulation")
                    val sizeLabel = "$size mm²" + (material?.let { "/$it" } ?: "")
                    // Rationale comes from the originating decision or project data; it is never
                    // an ampacity/VD claim re-derived in the UI.
                    val governingReason = decision.explanationSummary
                        ?: ctx.input("governingCriterion")
                        ?: "Proposal rationale supplied by the originating calculation result."
                    AssistantCardUiModel.RecommendationCard(
                        cardId = cardId,
                        title = "Proposal Created: $sizeLabel",
                        recommendedSize = if (insulation != null) "$sizeLabel/$insulation" else sizeLabel,
                        governingReason = governingReason,
                        tradeOffsSummary = "Proposed change prepared for review. Nothing has been applied.",
                        suggestedActions = listOf(
                            SuggestedActionUiModel(ConversationActionType.SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE, "Review & Apply", isPrimary = true),
                            SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Discard")
                        )
                    )
                }
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
                // Diagnosis facts (violated criterion, actual vs limit values, remediation
                // options) must be produced by inspecting a deterministic calculation result.
                // Rendering a canned diagnosis here was fabricating engineering output, so this
                // path now surfaces the decision's own explanation when present and otherwise
                // reports that verified evidence is unavailable.
                AssistantCardUiModel.SafeErrorCard(
                    cardId = cardId,
                    userFacingMessage = decision.explanationSummary ?: UNAVAILABLE,
                    isRecoverable = true,
                    dataUnchangedConfirmed = true,
                    suggestedActions = listOf(
                        SuggestedActionUiModel(ConversationActionType.RUN_CALCULATION, "Inspect calculation", isPrimary = true),
                        SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Dismiss")
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
