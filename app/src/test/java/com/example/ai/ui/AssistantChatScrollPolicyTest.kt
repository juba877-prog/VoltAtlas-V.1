package com.example.ai.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scroll-policy tests for the Assistant conversation list.
 *
 * The policy is deterministic and driven only by the conversation content signature and the
 * user's position, so it can be verified without a device.
 */
class AssistantChatScrollPolicyTest {

    // --- isNearLatest -------------------------------------------------------------

    @Test
    fun emptyOrNotYetLaidOutList_countsAsNearLatest() {
        assertTrue(AssistantChatScrollPolicy.isNearLatest(lastVisibleIndex = -1, totalItems = 0))
    }

    @Test
    fun bottomOfList_isNearLatest() {
        assertTrue(AssistantChatScrollPolicy.isNearLatest(lastVisibleIndex = 9, totalItems = 10))
    }

    @Test
    fun withinSlackWindow_stillCountsAsNearLatest() {
        // total = 10 -> last index 9; slack 2 -> indices 7,8,9 all count as latest.
        assertTrue(AssistantChatScrollPolicy.isNearLatest(lastVisibleIndex = 7, totalItems = 10))
    }

    @Test
    fun beyondSlackWindow_isNotNearLatest() {
        assertFalse(AssistantChatScrollPolicy.isNearLatest(lastVisibleIndex = 6, totalItems = 10))
    }

    @Test
    fun scrolledWellUp_isNotNearLatest() {
        assertFalse(AssistantChatScrollPolicy.isNearLatest(lastVisibleIndex = 5, totalItems = 60))
    }

    @Test
    fun slackCoversTheOneFrameLagOfTheLayoutSnapshot() {
        // A newly appended item updates totalItems one frame before the visible window, so a
        // user who was at the bottom must still be judged "near latest".
        assertTrue(AssistantChatScrollPolicy.isNearLatest(lastVisibleIndex = 49, totalItems = 51))
    }

    // --- shouldAutoScroll ---------------------------------------------------------

    @Test
    fun autoScroll_onlyWhenNearLatestAndContentChanged() {
        assertTrue(AssistantChatScrollPolicy.shouldAutoScroll(isNearLatest = true, contentChanged = true))
    }

    @Test
    fun noAutoScroll_whenReadingOlderContent() {
        assertFalse(AssistantChatScrollPolicy.shouldAutoScroll(isNearLatest = false, contentChanged = true))
    }

    @Test
    fun noAutoScroll_whenOnlyRecompositionHappened() {
        // Suggestion updates, card re-renders and unrelated UI state do NOT change the content
        // signature, so they can never move the user's reading position.
        assertFalse(AssistantChatScrollPolicy.shouldAutoScroll(isNearLatest = true, contentChanged = false))
    }

    // --- shouldShowNewMessageIndicator --------------------------------------------

    @Test
    fun indicator_showsOnlyForNewContentWhileScrolledUp() {
        assertTrue(
            AssistantChatScrollPolicy.shouldShowNewMessageIndicator(
                conversationSignature = "5:card:c5",
                seenConversationSignature = "4:card:c4",
                isNearLatest = false
            )
        )
    }

    @Test
    fun indicator_hiddenWhileUserIsAtTheLatest() {
        assertFalse(
            AssistantChatScrollPolicy.shouldShowNewMessageIndicator(
                conversationSignature = "5:card:c5",
                seenConversationSignature = "4:card:c4",
                isNearLatest = true
            )
        )
    }

    @Test
    fun indicator_hiddenWhenNoNewContentArrived() {
        assertFalse(
            AssistantChatScrollPolicy.shouldShowNewMessageIndicator(
                conversationSignature = "5:card:c5",
                seenConversationSignature = "5:card:c5",
                isNearLatest = false
            )
        )
    }
}
