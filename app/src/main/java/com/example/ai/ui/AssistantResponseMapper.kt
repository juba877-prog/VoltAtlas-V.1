package com.example.ai.ui

import com.example.ai.contracts.ToolStatus
import com.example.ai.conversation.AssistantIntent
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationTurnDecision
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.ProvenanceLabel
import com.example.ai.tools.cable.CableCandidateEvaluationResult
import com.example.ai.tools.cable.CableCandidatesComparisonResult
import com.example.ai.tools.cable.CableSizingExecutionResult
import com.example.ai.tools.cable.CableWhatIfResult
import com.example.ai.tools.cable.CheckStatus
import com.example.ai.tools.cable.GoverningCriterion

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
        existingState: AssistantSheetUiState? = null,
        evidence: List<EngineeringEvidenceRecord> = emptyList()
    ): AssistantSheetUiState {
        val header = buildHeader(state, projectContext)
        val card = buildCard(decision, state, projectContext, evidence)
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
        ctx: BoundedProjectContext,
        evidence: List<EngineeringEvidenceRecord> = emptyList()
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
                val relevantEvidence = evidence.firstOrNull {
                    it.isRelevantForCableSizing(ctx.projectId, state.activeEntityId ?: ctx.activeEntityId)
                }
                val cableResult = relevantEvidence?.result as? CableSizingExecutionResult

                val isStale = if (relevantEvidence != null) {
                    relevantEvidence.freshnessState.equals("STALE", ignoreCase = true) || relevantEvidence.executionStatus == ToolStatus.STALE
                } else {
                    ctx.freshnessSummary["CABLE_SIZING"] == "STALE"
                }

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
                    val conductorVal = cableResult?.details?.cableSize_mm2
                        ?: ctx.input("cableSize")?.replace(" mm²", "")?.replace("mm²", "")?.trim()
                    val izVal = cableResult?.let { it.details.capacity_derated_Iz_A.toString() }
                        ?: ctx.input("deratedAmpacityIz")?.replace(" A", "")
                    val ibVal = cableResult?.let { it.details.Ib_A.toString() }
                        ?: ctx.input("designCurrentIb")
                    val vdVal = cableResult?.let { it.details.vdPct.toString() }
                        ?: ctx.input("voltageDropPct")?.replace("%", "")
                    val vdLimit = ctx.input("maxVDpct")?.let { "$it%" }

                    // Status must come from authoritative project data or evidence. Its absence is reported
                    // as unknown; it is never reinterpreted as PASS.
                    val cardStatus = when {
                        cableResult != null -> when (cableResult.overallStatus) {
                            CheckStatus.PASS -> EngineeringStatusUiModel.PASS
                            CheckStatus.FAIL -> EngineeringStatusUiModel.FAIL
                            CheckStatus.WARNING -> EngineeringStatusUiModel.WARNING
                            CheckStatus.INCOMPLETE -> EngineeringStatusUiModel.STALE
                        }
                        else -> statusFrom(ctx.input("status")) ?: EngineeringStatusUiModel.INFO
                    }
                    val governingCriterion = when {
                        cableResult != null -> when (cardStatus) {
                            EngineeringStatusUiModel.PASS -> if (cableResult.governingCriterion != GoverningCriterion.NONE) cableResult.governingReason.ifBlank { "Compliance confirmed by authoritative calculation." } else "Compliance confirmed by authoritative calculation."
                            EngineeringStatusUiModel.FAIL -> cableResult.governingReason.ifBlank { "Engineering constraints violated" }
                            EngineeringStatusUiModel.WARNING -> "Marginal compliance reported by authoritative calculation"
                            else -> UNAVAILABLE
                        }
                        else -> when (cardStatus) {
                            EngineeringStatusUiModel.PASS ->
                                ctx.input("governingCriterion") ?: "Compliance confirmed by authoritative calculation."
                            EngineeringStatusUiModel.FAIL ->
                                ctx.input("failReason") ?: "Engineering constraints violated"
                            EngineeringStatusUiModel.WARNING ->
                                "Marginal compliance reported by authoritative calculation"
                            else -> UNAVAILABLE
                        }
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
                val comparisonResult = evidence.mapNotNull { it.result as? CableCandidatesComparisonResult }.lastOrNull()
                val whatIfResult = evidence.mapNotNull { it.result as? CableWhatIfResult }.lastOrNull()
                val evalResult = evidence.mapNotNull { it.result as? CableCandidateEvaluationResult }.lastOrNull()

                if (comparisonResult != null) {
                    val candidateMetrics = buildMap {
                        for (item in comparisonResult.candidateResults) {
                            val candStatus = when (item.overallStatus) {
                                CheckStatus.PASS -> EngineeringStatusUiModel.PASS
                                CheckStatus.FAIL -> EngineeringStatusUiModel.FAIL
                                CheckStatus.WARNING -> EngineeringStatusUiModel.WARNING
                                CheckStatus.INCOMPLETE -> EngineeringStatusUiModel.STALE
                            }
                            val metrics = listOfNotNull(
                                metricOrNull("Conductor", item.sizeFormatted, status = candStatus),
                                metricOrNull("Utilization", "${item.utilizationPct}%", status = candStatus),
                                metricOrNull("VD", "${item.voltDropPct}%", status = candStatus),
                                metricOrNull("Cumulative VD", "${item.cumulativeVdPct}%", status = candStatus)
                            )
                            put(item.sizeFormatted, metrics)
                        }
                    }

                    val summary = decision.explanationSummary?.takeIf { it.isNotBlank() }
                        ?: comparisonResult.recommendedCandidateId?.let { recId ->
                            "Recommended candidate: $recId (${comparisonResult.passingCount}/${comparisonResult.evaluatedCount} passing)."
                        }
                        ?: comparisonResult.noRecommendationReason
                        ?: "Evaluated ${comparisonResult.evaluatedCount} candidates: ${comparisonResult.passingCount} passing, ${comparisonResult.failingCount} failing."

                    val bestSize = comparisonResult.recommendedCandidateId
                        ?: comparisonResult.smallestPassingCandidateId

                    AssistantCardUiModel.ComparisonCard(
                        cardId = cardId,
                        title = "Candidate Comparison: ${comparisonResult.circuitName.ifBlank { "Circuit" }} (${comparisonResult.evaluatedCount} evaluated)",
                        candidateMetrics = candidateMetrics,
                        comparisonSummary = summary,
                        suggestedActions = listOfNotNull(
                            bestSize?.let {
                                SuggestedActionUiModel(ConversationActionType.CREATE_PROPOSAL, "Propose $it", isPrimary = true)
                            },
                            SuggestedActionUiModel(ConversationActionType.RUN_WHAT_IF, "Run what-if scenario"),
                            SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Dismiss")
                        )
                    )
                } else if (whatIfResult != null) {
                    val currentSize = whatIfResult.baselineSize ?: ctx.input("cableSize")?.replace(" mm²", "")?.trim()
                    val currentLabel = currentSize?.let { "$it mm²" } ?: "Baseline"
                    val scenarioLabel = "${whatIfResult.scenarioSize} mm²"

                    val candidateMetrics = buildMap {
                        val currentStatus = when (whatIfResult.baselineStatus) {
                            CheckStatus.PASS -> EngineeringStatusUiModel.PASS
                            CheckStatus.FAIL -> EngineeringStatusUiModel.FAIL
                            CheckStatus.WARNING -> EngineeringStatusUiModel.WARNING
                            else -> statusFrom(ctx.input("status")) ?: EngineeringStatusUiModel.INFO
                        }
                        put(currentLabel, listOfNotNull(
                            metricOrNull("Conductor", currentSize, "mm²", status = currentStatus),
                            ctx.input("deratedAmpacityIz")?.replace(" A", "")?.let {
                                metricOrNull("Iz", it, "A", limit = ctx.input("designCurrentIb"), isGoverning = true, status = currentStatus)
                            },
                            ctx.input("voltageDropPct")?.replace("%", "")?.let {
                                metricOrNull("VD", it, "%", limit = ctx.input("maxVDpct")?.let { m -> "$m%" }, status = currentStatus)
                            }
                        ))

                        val scenarioStatus = when (whatIfResult.scenarioStatus) {
                            CheckStatus.PASS -> EngineeringStatusUiModel.PASS
                            CheckStatus.FAIL -> EngineeringStatusUiModel.FAIL
                            CheckStatus.WARNING -> EngineeringStatusUiModel.WARNING
                            CheckStatus.INCOMPLETE -> EngineeringStatusUiModel.STALE
                        }
                        put(scenarioLabel, listOfNotNull(
                            metricOrNull("Conductor", whatIfResult.scenarioSize, "mm²", status = scenarioStatus),
                            metricOrNull("Iz", whatIfResult.scenarioDetails.capacity_derated_Iz_A.toString(), "A", limit = whatIfResult.scenarioDetails.Ib_A.toString(), isGoverning = true, status = scenarioStatus),
                            metricOrNull("VD", "${whatIfResult.scenarioDetails.vdPct}%", limit = ctx.input("maxVDpct")?.let { m -> "$m%" }, status = scenarioStatus)
                        ))
                    }

                    val summary = decision.explanationSummary?.takeIf { it.isNotBlank() }
                        ?: "Scenario comparison: Baseline $currentLabel (${whatIfResult.baselineStatus}) vs Scenario $scenarioLabel (${whatIfResult.scenarioStatus})."

                    AssistantCardUiModel.ComparisonCard(
                        cardId = cardId,
                        title = "Scenario Comparison: $scenarioLabel vs $currentLabel",
                        candidateMetrics = candidateMetrics,
                        comparisonSummary = summary,
                        suggestedActions = listOfNotNull(
                            SuggestedActionUiModel(ConversationActionType.CREATE_PROPOSAL, "Propose ${whatIfResult.scenarioSize} mm²", isPrimary = true),
                            SuggestedActionUiModel(ConversationActionType.RUN_WHAT_IF, "Modify Scenario"),
                            SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Dismiss")
                        )
                    )
                } else {
                    val candidateSize = evalResult?.candidateSizeMm2?.toString()
                        ?: action.candidateParameters["targetSize"]
                        ?: action.candidateParameters["candidateSizeMm2"]
                        ?: action.candidateParameters["candidateSizeText"]
                    val currentSize = ctx.input("cableSize")
                        ?.replace(" mm²", "")?.replace(" مم²", "")?.replace("mm²", "")?.trim()
                    val ibVal = ctx.input("designCurrentIb")
                    val izVal = ctx.input("deratedAmpacityIz")?.replace(" A", "")
                    val vdVal = ctx.input("voltageDropPct")?.replace("%", "")
                    val vdLimit = ctx.input("maxVDpct")?.let { "$it%" }
                    val summary = decision.explanationSummary ?: UNAVAILABLE

                    val currentStatus = statusFrom(ctx.input("status")) ?: EngineeringStatusUiModel.INFO
                    val currentLabel = currentSize?.let { "$it mm²" } ?: "Current"
                    val candidateLabel = candidateSize?.let { "$it mm²" } ?: "Candidate"

                    val currentMetrics = listOfNotNull(
                        metricOrNull("Iz", izVal, "A", limit = ibVal, isGoverning = true, status = currentStatus),
                        metricOrNull("VD", vdVal, "%", limit = vdLimit, status = currentStatus)
                    )
                    val candidateMetrics = buildMap {
                        if (currentMetrics.isNotEmpty()) put(currentLabel, currentMetrics)
                        if (evalResult != null) {
                            val candStatus = when (evalResult.overallStatus) {
                                CheckStatus.PASS -> EngineeringStatusUiModel.PASS
                                CheckStatus.FAIL -> EngineeringStatusUiModel.FAIL
                                CheckStatus.WARNING -> EngineeringStatusUiModel.WARNING
                                CheckStatus.INCOMPLETE -> EngineeringStatusUiModel.STALE
                            }
                            val candMetrics = listOfNotNull(
                                metricOrNull("Iz", evalResult.details.capacity_derated_Iz_A.toString(), "A", limit = ibVal, isGoverning = true, status = candStatus),
                                metricOrNull("VD", evalResult.details.vdPct.toString(), "%", limit = vdLimit, status = candStatus)
                            )
                            put("${evalResult.candidateSizeMm2} mm²", candMetrics)
                        }
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
            }

            ConversationActionType.RUN_WHAT_IF -> {
                val whatIfResult = evidence.mapNotNull { it.result as? CableWhatIfResult }.lastOrNull()
                if (whatIfResult != null) {
                    val changedParams = whatIfResult.inputDifferences.associate {
                        it.fieldName to "${it.baselineValue} -> ${it.scenarioValue}"
                    }.ifEmpty { action.candidateParameters }

                    val scenarioStatusUi = when (whatIfResult.scenarioStatus) {
                        CheckStatus.PASS -> EngineeringStatusUiModel.PASS
                        CheckStatus.FAIL -> EngineeringStatusUiModel.FAIL
                        CheckStatus.WARNING -> EngineeringStatusUiModel.WARNING
                        CheckStatus.INCOMPLETE -> EngineeringStatusUiModel.STALE
                    }

                    val scenarioMetrics = listOfNotNull(
                        metricOrNull("Scenario Conductor", whatIfResult.scenarioSize, "mm²", status = scenarioStatusUi),
                        metricOrNull("Scenario Iz", whatIfResult.scenarioDetails.capacity_derated_Iz_A.toString(), "A", limit = whatIfResult.scenarioDetails.Ib_A.toString(), isGoverning = true, status = scenarioStatusUi),
                        metricOrNull("Scenario VD", "${whatIfResult.scenarioDetails.vdPct}%", status = scenarioStatusUi),
                        metricOrNull("Utilization", "${whatIfResult.scenarioDetails.utilizationPct}%", status = scenarioStatusUi)
                    )

                    val deltaSummary = "Baseline: ${whatIfResult.baselineSize ?: "N/A"} mm² (${whatIfResult.baselineStatus}) -> Scenario: ${whatIfResult.scenarioSize} mm² (${whatIfResult.scenarioStatus}). Baseline project state remains unchanged."

                    AssistantCardUiModel.WhatIfResultCard(
                        cardId = cardId,
                        scenarioTitle = "What-If Scenario: ${whatIfResult.scenarioSize} mm² (${whatIfResult.scenarioStatus})",
                        changedParameters = changedParams,
                        baseRevision = whatIfResult.sourceRevision,
                        deltaSummary = deltaSummary,
                        metrics = scenarioMetrics,
                        suggestedActions = listOfNotNull(
                            SuggestedActionUiModel(ConversationActionType.COMPARE_CANDIDATES, "Compare with current"),
                            SuggestedActionUiModel(ConversationActionType.CREATE_PROPOSAL, "Propose ${whatIfResult.scenarioSize} mm²", isPrimary = true),
                            SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Exit Scenario")
                        )
                    )
                } else {
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
            }

            ConversationActionType.CREATE_PROPOSAL,
            ConversationActionType.REVIEW_PROPOSAL -> {
                val size = action.candidateParameters["overrideCableSize"]
                    ?: (evidence.mapNotNull { it.result as? CableSizingExecutionResult }.firstOrNull()?.details?.cableSize_mm2)
                    ?: (evidence.mapNotNull { it.result as? CableWhatIfResult }.firstOrNull()?.scenarioSize)
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
                val sizingResult = evidence.mapNotNull { it.result as? CableSizingExecutionResult }.lastOrNull()
                val evalResult = evidence.mapNotNull { it.result as? CableCandidateEvaluationResult }.lastOrNull()

                val failedChecks = sizingResult?.checks?.filter { it.status == CheckStatus.FAIL }
                    ?: evalResult?.checks?.filter { it.status == CheckStatus.FAIL }
                    ?: emptyList()
                val isRealFail = sizingResult?.overallStatus == CheckStatus.FAIL ||
                        evalResult?.overallStatus == CheckStatus.FAIL ||
                        failedChecks.isNotEmpty()

                if (isRealFail && sizingResult != null && sizingResult.governingCriterion != GoverningCriterion.INDETERMINATE) {
                    val criterionName = sizingResult.governingCriterion.name
                    val primaryFailed = failedChecks.firstOrNull { it.checkName.equals(criterionName, ignoreCase = true) }
                        ?: failedChecks.firstOrNull()

                    val actualVal = primaryFailed?.actualDisplay?.ifBlank { null }
                        ?: primaryFailed?.actualValue?.formattedDisplay
                        ?: when (sizingResult.governingCriterion) {
                            GoverningCriterion.VOLTAGE_DROP, GoverningCriterion.CUMULATIVE_VOLTAGE_DROP -> "${sizingResult.details.vdPct}%"
                            GoverningCriterion.AMPACITY -> "${sizingResult.details.Ib_A} A"
                            else -> UNAVAILABLE
                        }

                    val limitVal = primaryFailed?.limitDisplay?.ifBlank { null }
                        ?: primaryFailed?.limitValue?.formattedDisplay
                        ?: when (sizingResult.governingCriterion) {
                            GoverningCriterion.VOLTAGE_DROP -> ctx.input("maxVDpct")?.let { "$it%" } ?: UNAVAILABLE
                            GoverningCriterion.AMPACITY -> "${sizingResult.details.capacity_derated_Iz_A} A"
                            else -> UNAVAILABLE
                        }

                    val diagnosisText = when {
                        failedChecks.size > 1 -> {
                            "Multiple constraints violated: " + failedChecks.joinToString("; ") {
                                "${it.checkName} (Actual: ${it.actualDisplay.ifBlank { it.actualValue?.formattedDisplay ?: "" }}, Limit: ${it.limitDisplay.ifBlank { it.limitValue?.formattedDisplay ?: "" }})"
                            }
                        }
                        sizingResult.governingReason.isNotBlank() -> sizingResult.governingReason
                        primaryFailed != null -> primaryFailed.message
                        else -> decision.explanationSummary ?: "Engineering criteria violated."
                    }

                    val remediation = mutableListOf<String>()
                    for (fc in failedChecks) {
                        when {
                            fc.checkName.contains("VOLTAGE_DROP", ignoreCase = true) -> {
                                remediation.add("Increase conductor cross-section to reduce voltage drop")
                                remediation.add("Shorten route length or elevate distribution voltage if feasible")
                            }
                            fc.checkName.contains("AMPACITY", ignoreCase = true) -> {
                                remediation.add("Upsize conductor cross-section for higher thermal ampacity")
                                remediation.add("Evaluate parallel conductor runs or improve installation grouping derating")
                            }
                            fc.checkName.contains("SHORT_CIRCUIT", ignoreCase = true) -> {
                                remediation.add("Increase conductor size to meet minimum thermal short-circuit withstand")
                            }
                            else -> {
                                remediation.add("Review circuit installation parameters and manufacturer sizing tables")
                            }
                        }
                    }
                    if (remediation.isEmpty()) {
                        when (sizingResult.governingCriterion) {
                            GoverningCriterion.VOLTAGE_DROP -> {
                                remediation.add("Increase conductor cross-section to reduce voltage drop")
                                remediation.add("Shorten route length or elevate distribution voltage if feasible")
                            }
                            GoverningCriterion.AMPACITY -> {
                                remediation.add("Upsize conductor cross-section for higher thermal ampacity")
                                remediation.add("Evaluate parallel conductor runs or improve installation grouping derating")
                            }
                            else -> remediation.add("Inspect circuit design criteria and consider larger conductor cross-section")
                        }
                    }

                    AssistantCardUiModel.FailureDiagnosisCard(
                        cardId = cardId,
                        title = "Failure Diagnostic: ${sizingResult.circuitName.ifBlank { state.activeEntityName ?: ctx.activeEntityName ?: "Circuit" }} ($criterionName)",
                        violatedCriterion = criterionName,
                        actualValue = actualVal,
                        limitValue = limitVal,
                        diagnosisText = diagnosisText,
                        remediationOptions = remediation.distinct(),
                        suggestedActions = listOf(
                            SuggestedActionUiModel(ConversationActionType.RUN_WHAT_IF, "Try what-if scenario", isPrimary = true),
                            SuggestedActionUiModel(ConversationActionType.COMPARE_CANDIDATES, "Compare candidate sizes"),
                            SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Dismiss")
                        )
                    )
                } else {
                    AssistantCardUiModel.SafeErrorCard(
                        cardId = cardId,
                        userFacingMessage = decision.explanationSummary?.takeIf { it.isNotBlank() }
                            ?: "Incomplete diagnosis: verified failure evidence is unavailable or indeterminate.",
                        isRecoverable = true,
                        dataUnchangedConfirmed = true,
                        suggestedActions = listOf(
                            SuggestedActionUiModel(ConversationActionType.RUN_CALCULATION, "Inspect calculation", isPrimary = true),
                            SuggestedActionUiModel(ConversationActionType.CANCEL_TASK, "Dismiss")
                        )
                    )
                }
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
