package com.example.ai.ui

import com.example.ai.service.AssistantMessage
import com.example.ai.service.MessageRole

/**
 * Presentation-only ordered entry of the Assistant conversation surface.
 *
 * The authoritative conversation state stays in the ViewModel
 * ([AssistantUiState.messages] + [AssistantUiState.typedCards]). This type is a derived,
 * read-only projection used exclusively to render ONE chronological chat list. It never
 * stores, mutates, or duplicates conversation/agent/tool state.
 */
sealed interface AssistantConversationEntry {
    /** Stable LazyColumn key — position independent, so scroll anchoring survives inserts. */
    val key: String

    data class MessageEntry(val message: AssistantMessage) : AssistantConversationEntry {
        override val key: String
            get() = "message:${message.id}"
    }

    data class CardEntry(val card: AssistantCardUiModel) : AssistantConversationEntry {
        override val key: String
            get() = "card:${card.cardId}"
    }
}

/**
 * Builds the single authoritative chronological order for the Assistant chat list.
 *
 * The ViewModel appends messages and typed cards into two independent lists, so the UI
 * derives one ordered view from them without changing either list:
 *
 * 1. Messages are split into turn segments at every [MessageRole.USER] message.
 *    Segment 0 holds any assistant message produced before the first user turn
 *    (the opening greeting).
 * 2. Every user turn produces at most one card (success, blocking question, safe error,
 *    proposal review, receipt — replacements such as review → confirmation → receipt keep
 *    the count stable). Cards are therefore assigned forward, one per turn:
 *    `card[k] -> turn[k]`.
 * 3. When fewer cards than turns exist (a card was dismissed or consumed without a
 *    replacement), the surplus is absorbed by the latest turns; when more cards than turns
 *    exist (a turn produced several cards), the surplus is appended to the most recent turn.
 *
 * Engineering meaning is untouched: no entry is created, removed, or altered — only grouped.
 */
object AssistantConversationOrder {

    fun build(
        messages: List<AssistantMessage>,
        cards: List<AssistantCardUiModel>
    ): List<AssistantConversationEntry> {
        if (messages.isEmpty() && cards.isEmpty()) return emptyList()

        // 1. Split messages into turn segments (segment 0 = pre-first-user, e.g. greeting).
        val segments = mutableListOf<MutableList<AssistantMessage>>()
        var current = mutableListOf<AssistantMessage>()
        segments += current
        messages.forEach { message ->
            if (message.role == MessageRole.USER) {
                current = mutableListOf()
                segments += current
            }
            current += message
        }

        // Turn segments are segments 1..n; segment 0 is the pre-turn greeting segment.
        val turnCount = (segments.size - 1).coerceAtLeast(0)

        // 2./3. Assign cards forward to turns, absorbing surplus on the latest turn.
        val cardsByTurn: List<List<AssistantCardUiModel>> = if (cards.isEmpty() || turnCount == 0) {
            emptyList()
        } else {
            List(turnCount) { turnIndex ->
                when {
                    cards.size <= turnCount ->
                        if (turnIndex < cards.size) listOf(cards[turnIndex]) else emptyList()
                    else ->
                        if (turnIndex == turnCount - 1) cards.drop(turnCount) else listOf(cards[turnIndex])
                }
            }
        }

        // Build the interleaved presentation order.
        val entries = ArrayList<AssistantConversationEntry>(messages.size + cards.size)
        segments.forEachIndexed { segmentIndex, segment ->
            segment.forEach { message ->
                entries += AssistantConversationEntry.MessageEntry(message)
            }
            if (segmentIndex >= 1) {
                cardsByTurn.getOrNull(segmentIndex - 1)?.forEach { card ->
                    entries += AssistantConversationEntry.CardEntry(card)
                }
            }
        }

        // Defensive: cards with no turn to attach to (state reset edge) render at the end.
        if (cards.isNotEmpty() && turnCount == 0) {
            cards.forEach { card -> entries += AssistantConversationEntry.CardEntry(card) }
        }

        return entries
    }

    /**
     * The card produced by the CURRENT (latest) turn, or null while the conversation is
     * still idle. This is the sole contextual source for the SuggestionsBar, so suggestions
     * always reflect the latest assistant state and disappear when the latest turn has no
     * actionable card (for example after a mutation receipt).
     */
    fun currentTurnCard(entries: List<AssistantConversationEntry>): AssistantCardUiModel? {
        val lastUserIndex = entries.indexOfLast {
            it is AssistantConversationEntry.MessageEntry && it.message.role == MessageRole.USER
        }
        if (lastUserIndex < 0) return null
        for (index in entries.lastIndex downTo lastUserIndex) {
            val entry = entries[index]
            if (entry is AssistantConversationEntry.CardEntry) return entry.card
        }
        return null
    }
}