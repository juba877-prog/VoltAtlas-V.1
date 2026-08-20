package com.example.ai.ui

import com.example.ai.conversation.AssistantIntent
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.EngineeringObjective
import com.example.ai.runtime.ProvenanceLabel
import com.example.ai.runtime.ResponseDetailLevel

/**
 * Assistant modal container states.
 */
enum class AssistantSheetState {
    CLOSED,
    COMPACT,
    EXPANDED,
    FULL_TASK_RESULT
}

/**
 * High-level engineering compliance and calculation status.
 */
enum class EngineeringStatusUiModel(val label: String) {
    PASS("PASS"),
    FAIL("FAIL"),
    WARNING("WARNING"),
    STALE("STALE"),
    INFO("INFO")
}

/**
 * Individual engineering metric display model (e.g. Ib = 345 A, VD = 1.85%).
 */
data class EngineeringMetricUiModel(
    val label: String,
    val value: String,
    val unit: String? = null,
    val limit: String? = null,
    val isGoverning: Boolean = false,
    val status: EngineeringStatusUiModel = EngineeringStatusUiModel.INFO
)

/**
 * Typed contextual header data.
 */
data class ContextHeaderUiModel(
    val title: String = "VoltAtlas Assistant",
    val entityType: String? = null,
    val entityName: String? = null,
    val entityId: String? = null,
    val freshnessLabel: String? = null,
    val isStale: Boolean = false,
    val baseRevision: Long? = null
) {
    val hasResolvedEntity: Boolean
        get() = !entityType.isNullOrBlank() && (!entityName.isNullOrBlank() || !entityId.isNullOrBlank())

    val displayBadgeText: String?
        get() = if (hasResolvedEntity) {
            "${entityType?.uppercase()} · ${entityName ?: entityId}"
        } else null
}

/**
 * Typed suggested action model for non-executing UI chips.
 */
data class SuggestedActionUiModel(
    val actionType: ConversationActionType,
    val label: String,
    val isPrimary: Boolean = false,
    val payload: Map<String, String> = emptyMap()
)

/**
 * Sealed hierarchy of typed response presentation cards.
 */
sealed interface AssistantCardUiModel {
    val cardId: String

    data class DirectAnswerCard(
        override val cardId: String,
        val text: String,
        val suggestedActions: List<SuggestedActionUiModel> = emptyList()
    ) : AssistantCardUiModel

    data class BlockingQuestionCard(
        override val cardId: String,
        val questionText: String,
        val requiredReason: String,
        val missingSlots: List<String>,
        val suggestedChips: List<String> = emptyList(),
        val suggestedActions: List<SuggestedActionUiModel> = emptyList()
    ) : AssistantCardUiModel

    data class ResultSummaryCard(
        override val cardId: String,
        val title: String,
        val status: EngineeringStatusUiModel,
        val governingCriterion: String,
        val summaryText: String,
        val metrics: List<EngineeringMetricUiModel> = emptyList(),
        val provenance: ProvenanceLabel = ProvenanceLabel.CALCULATED_BY_VOLTATLAS,
        val isStale: Boolean = false,
        val suggestedActions: List<SuggestedActionUiModel> = emptyList()
    ) : AssistantCardUiModel

    data class FailureDiagnosisCard(
        override val cardId: String,
        val title: String,
        val violatedCriterion: String,
        val actualValue: String,
        val limitValue: String,
        val diagnosisText: String,
        val remediationOptions: List<String> = emptyList(),
        val suggestedActions: List<SuggestedActionUiModel> = emptyList()
    ) : AssistantCardUiModel

    data class ComparisonCard(
        override val cardId: String,
        val title: String,
        val candidateMetrics: Map<String, List<EngineeringMetricUiModel>>,
        val comparisonSummary: String,
        val suggestedActions: List<SuggestedActionUiModel> = emptyList()
    ) : AssistantCardUiModel

    data class WhatIfResultCard(
        override val cardId: String,
        val scenarioTitle: String,
        val changedParameters: Map<String, String>,
        val baseRevision: Long,
        val deltaSummary: String,
        val metrics: List<EngineeringMetricUiModel> = emptyList(),
        val suggestedActions: List<SuggestedActionUiModel> = emptyList()
    ) : AssistantCardUiModel

    data class RecommendationCard(
        override val cardId: String,
        val title: String,
        val recommendedSize: String,
        val governingReason: String,
        val tradeOffsSummary: String,
        val suggestedActions: List<SuggestedActionUiModel> = emptyList()
    ) : AssistantCardUiModel

    data class SafeErrorCard(
        override val cardId: String,
        val userFacingMessage: String,
        val isRecoverable: Boolean = true,
        val dataUnchangedConfirmed: Boolean = true,
        val suggestedActions: List<SuggestedActionUiModel> = emptyList()
    ) : AssistantCardUiModel

    data class ApprovalRequiredNonExecutableCard(
        override val cardId: String,
        val proposalId: String,
        val promptText: String,
        val confirmationRequiredNote: String = "Proposal requires explicit confirmation via dedicated UI action. Conversational text cannot apply changes.",
        val suggestedActions: List<SuggestedActionUiModel> = emptyList()
    ) : AssistantCardUiModel
}

/**
 * Full immutable UI state consumed by [EngineeringAssistantSheet].
 */
data class AssistantSheetUiState(
    val sheetState: AssistantSheetState = AssistantSheetState.CLOSED,
    val header: ContextHeaderUiModel = ContextHeaderUiModel(),
    val activeCard: AssistantCardUiModel? = null,
    val allCards: List<AssistantCardUiModel> = emptyList(),
    val inputText: String = "",
    val isThinking: Boolean = false,
    val detailLevel: ResponseDetailLevel = ResponseDetailLevel.CONCISE
) {
    val isOpen: Boolean
        get() = sheetState != AssistantSheetState.CLOSED

    val isExpanded: Boolean
        get() = sheetState == AssistantSheetState.EXPANDED || sheetState == AssistantSheetState.FULL_TASK_RESULT
}
