package com.example.ai.ui

import com.example.ai.service.AssistantMessage
import com.example.ai.service.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Presentation-ordering tests for the single chronological conversation projection.
 *
 * These tests protect the chat architecture rule: messages and typed cards render as ONE
 * chronological list, and the projection never creates, drops, or alters conversation state.
 */
class AssistantConversationOrderTest {

    private var idCounter = 0

    private fun msg(role: MessageRole, text: String): AssistantMessage =
        AssistantMessage(id = "m-${idCounter++}", role = role, text = text)

    private fun card(id: String): AssistantCardUiModel =
        AssistantCardUiModel.DirectAnswerCard(cardId = id, text = "answer $id")

    private fun labels(entries: List<AssistantConversationEntry>): List<String> = entries.map { entry ->
        when (entry) {
            is AssistantConversationEntry.MessageEntry -> entry.message.text
            is AssistantConversationEntry.CardEntry -> entry.card.cardId
        }
    }

    @Test
    fun greetingOnlyConversation_rendersNoCards() {
        val entries = AssistantConversationOrder.build(
            messages = listOf(msg(MessageRole.ASSISTANT, "greeting")),
            cards = emptyList()
        )

        assertEquals(1, entries.size)
        assertTrue(entries[0] is AssistantConversationEntry.MessageEntry)
    }

    @Test
    fun eachTurn_interleavesUserMessageAssistantReplyAndResultCard() {
        val messages = listOf(
            msg(MessageRole.ASSISTANT, "greeting"),
            msg(MessageRole.USER, "u1"),
            msg(MessageRole.ASSISTANT, "a1"),
            msg(MessageRole.USER, "u2"),
            msg(MessageRole.ASSISTANT, "a2")
        )

        val entries = AssistantConversationOrder.build(messages, listOf(card("c1"), card("c2")))

        assertEquals(
            listOf("greeting", "u1", "a1", "c1", "u2", "a2", "c2"),
            labels(entries)
        )
    }

    @Test
    fun blockingTurn_cardWithoutReply_staysInsideItsOwnTurn() {
        val messages = listOf(
            msg(MessageRole.USER, "u1"),
            msg(MessageRole.USER, "u2"),
            msg(MessageRole.ASSISTANT, "a2")
        )

        val entries = AssistantConversationOrder.build(messages, listOf(card("c1"), card("c2")))

        assertEquals(listOf("u1", "c1", "u2", "a2", "c2"), labels(entries))
    }

    @Test
    fun latestTurnWithoutCard_leavesEarlierTurnsIntact() {
        val messages = listOf(
            msg(MessageRole.USER, "u1"),
            msg(MessageRole.ASSISTANT, "a1"),
            msg(MessageRole.USER, "u2")
        )

        val entries = AssistantConversationOrder.build(messages, listOf(card("c1")))

        assertEquals(listOf("u1", "a1", "c1", "u2"), labels(entries))
    }

    @Test
    fun cardsWithoutAnyTurn_renderAfterTheConversation() {
        val entries = AssistantConversationOrder.build(
            messages = listOf(msg(MessageRole.ASSISTANT, "greeting")),
            cards = listOf(card("c1"))
        )

        assertEquals(listOf("greeting", "c1"), labels(entries))
    }

    @Test
    fun entries_areStableAndUniquelyKeyed() {
        val messages = listOf(msg(MessageRole.USER, "u1"), msg(MessageRole.ASSISTANT, "a1"))
        val cards = listOf(card("c1"))

        val first = AssistantConversationOrder.build(messages, cards)
        val second = AssistantConversationOrder.build(messages, cards)

        assertEquals(first.map { it.key }, second.map { it.key })
        assertEquals(first.size, first.map { it.key }.toSet().size)
    }

    @Test
    fun currentTurnCard_isTheLatestTurnsCard() {
        val messages = listOf(
            msg(MessageRole.USER, "u1"),
            msg(MessageRole.ASSISTANT, "a1"),
            msg(MessageRole.USER, "u2"),
            msg(MessageRole.ASSISTANT, "a2")
        )
        val entries = AssistantConversationOrder.build(messages, listOf(card("c1"), card("c2")))

        assertEquals("c2", AssistantConversationOrder.currentTurnCard(entries)?.cardId)
    }

    @Test
    fun currentTurnCard_isNullWhenLatestTurnHasNoCard() {
        val messages = listOf(
            msg(MessageRole.USER, "u1"),
            msg(MessageRole.ASSISTANT, "a1"),
            msg(MessageRole.USER, "u2")
        )
        val entries = AssistantConversationOrder.build(messages, listOf(card("c1")))

        assertNull(AssistantConversationOrder.currentTurnCard(entries))
    }

    @Test
    fun currentTurnCard_isNullWhileConversationIsIdle() {
        val entries = AssistantConversationOrder.build(
            messages = listOf(msg(MessageRole.ASSISTANT, "greeting")),
            cards = emptyList()
        )

        assertNull(AssistantConversationOrder.currentTurnCard(entries))
    }
}
