package com.example.ai.ui

import com.example.ai.conversation.ConversationActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contextual-suggestion model tests.
 *
 * Suggestions are contextual actions derived from the CURRENT assistant state — never
 * conversation history, never persisted, never unbounded.
 */
class AssistantSuggestionsModelTest {

    private fun resultCardWithActions(count: Int): AssistantCardUiModel.ResultSummaryCard =
        AssistantCardUiModel.ResultSummaryCard(
            cardId = "CARD-1",
            title = "Result",
            status = EngineeringStatusUiModel.PASS,
            governingCriterion = "Thermal ampacity",
            summaryText = "Summary",
            suggestedActions = List(count) { index ->
                SuggestedActionUiModel(
                    actionType = ConversationActionType.RUN_CALCULATION,
                    label = "Action $index",
                    isPrimary = index == 0
                )
            }
        )

    @Test
    fun idleConversation_offersStarterPrompts() {
        val suggestions = AssistantSuggestions.build(hasUserMessages = false, currentCard = null)

        assertEquals(AssistantSuggestions.MAX_VISIBLE, suggestions.size)
        assertTrue(suggestions.all { it.kind == AssistantSuggestionUi.Kind.PROMPT })
        assertTrue(suggestions.all { !it.prompt.isNullOrBlank() })
    }

    @Test
    fun maximumVisibleSuggestionsIsEnforced() {
        val suggestions = AssistantSuggestions.build(
            hasUserMessages = true,
            currentCard = resultCardWithActions(count = 9)
        )

        assertEquals(AssistantSuggestions.MAX_VISIBLE, suggestions.size)
        assertTrue(suggestions.size <= 4)
    }

    @Test
    fun contextualSuggestions_comeFromTheCurrentTurnCard() {
        val card = resultCardWithActions(count = 3)

        val suggestions = AssistantSuggestions.build(hasUserMessages = true, currentCard = card)

        assertEquals(3, suggestions.size)
        assertTrue(suggestions.all { it.kind == AssistantSuggestionUi.Kind.ACTION })
        assertTrue(suggestions.all { it.cardId == card.cardId })
        assertEquals(listOf("Action 0", "Action 1", "Action 2"), suggestions.map { it.label })
        assertTrue(suggestions.first().isPrimary)
    }

    @Test
    fun cardWithoutSuggestions_yieldsNoStaleSuggestions() {
        val receipt = AssistantCardUiModel.MutationReceiptCard(
            cardId = "RECEIPT-1",
            receiptId = "R-1",
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChanges = emptyList(),
            revalidationStatus = "PASS"
        )

        val suggestions = AssistantSuggestions.build(hasUserMessages = true, currentCard = receipt)

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun missingCard_yieldsNoSuggestionsForAnActiveConversation() {
        assertTrue(AssistantSuggestions.build(hasUserMessages = true, currentCard = null).isEmpty())
    }

    @Test
    fun suggestions_arePureAndDoNotCarryConversationState() {
        val card = resultCardWithActions(count = 2)

        val first = AssistantSuggestions.build(hasUserMessages = true, currentCard = card)
        val second = AssistantSuggestions.build(hasUserMessages = true, currentCard = card)

        // Same state -> same suggestions, with no side effects on any conversation state.
        assertEquals(first, second)
    }
}
