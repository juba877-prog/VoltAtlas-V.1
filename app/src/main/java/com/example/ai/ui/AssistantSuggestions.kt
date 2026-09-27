package com.example.ai.ui

/**
 * Presentation-only contextual suggestion for the Assistant SuggestionsBar.
 *
 * Suggestions are contextual ACTIONS, never history: they are derived from the current
 * Assistant state on every recomposition, they are not messages, and they are never
 * persisted into the conversation.
 *
 * Selecting a suggestion always dispatches through the existing execution path
 * (`EngineeringAssistantViewModel.sendMessage` / `onCardAction`) — there is no second
 * command architecture.
 */
data class AssistantSuggestionUi(
    val label: String,
    val kind: Kind,
    /** Full utterance executed for [Kind.PROMPT] suggestions. */
    val prompt: String? = null,
    /** Source card id for [Kind.ACTION] suggestions (sends back to the card's action handler). */
    val cardId: String? = null,
    val action: SuggestedActionUiModel? = null,
    val isPrimary: Boolean = false
) {
    enum class Kind { PROMPT, ACTION }
}

/**
 * Derives the current contextual suggestions.
 *
 * Rules:
 * - Idle conversation (no user turn yet): compact starter prompts for the active context.
 * - Otherwise: the suggested actions of the CURRENT turn's card, at most [MAX_VISIBLE].
 * - When the current turn carries no actionable card (for example a mutation receipt),
 *   suggestions are empty instead of stale.
 *
 * Pure function: it never mutates state, never creates messages, and never touches the
 * conversation list.
 */
object AssistantSuggestions {

    /** Maximum visible suggestions (compact single scrolling row, never a stack). */
    const val MAX_VISIBLE = 4

    private val idleSuggestions = listOf(
        AssistantSuggestionUi(
            label = "Size cable",
            kind = AssistantSuggestionUi.Kind.PROMPT,
            prompt = "Size and verify the cable for this circuit."
        ),
        AssistantSuggestionUi(
            label = "Diagnose",
            kind = AssistantSuggestionUi.Kind.PROMPT,
            prompt = "Diagnose the governing constraints and check failures."
        ),
        AssistantSuggestionUi(
            label = "What-if",
            kind = AssistantSuggestionUi.Kind.PROMPT,
            prompt = "What if we use a 240 mm² cable?"
        ),
        AssistantSuggestionUi(
            label = "Compare",
            kind = AssistantSuggestionUi.Kind.PROMPT,
            prompt = "Compare candidate cable sizes for this load."
        )
    )

    fun build(
        hasUserMessages: Boolean,
        currentCard: AssistantCardUiModel?
    ): List<AssistantSuggestionUi> {
        if (!hasUserMessages) return idleSuggestions.take(MAX_VISIBLE)

        val actions = suggestedActionsOf(currentCard).take(MAX_VISIBLE)
        if (actions.isEmpty()) return emptyList()

        return actions.map { action ->
            AssistantSuggestionUi(
                label = action.label,
                kind = AssistantSuggestionUi.Kind.ACTION,
                cardId = currentCard?.cardId,
                action = action,
                isPrimary = action.isPrimary
            )
        }
    }

    /**
     * Extracts the suggested actions from a card subtype that actually carries them.
     * Cards without a suggested-action surface (proposal review, approval confirmation,
     * mutation receipt, tool execution) intentionally expose none.
     */
    private fun suggestedActionsOf(card: AssistantCardUiModel?): List<SuggestedActionUiModel> =
        when (card) {
            is AssistantCardUiModel.DirectAnswerCard -> card.suggestedActions
            is AssistantCardUiModel.BlockingQuestionCard -> card.suggestedActions
            is AssistantCardUiModel.ResultSummaryCard -> card.suggestedActions
            is AssistantCardUiModel.FailureDiagnosisCard -> card.suggestedActions
            is AssistantCardUiModel.ComparisonCard -> card.suggestedActions
            is AssistantCardUiModel.WhatIfResultCard -> card.suggestedActions
            is AssistantCardUiModel.RecommendationCard -> card.suggestedActions
            is AssistantCardUiModel.SafeErrorCard -> card.suggestedActions
            is AssistantCardUiModel.ApprovalRequiredNonExecutableCard -> card.suggestedActions
            else -> emptyList()
        }
}
