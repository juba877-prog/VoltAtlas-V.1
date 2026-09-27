package com.example.ai.ui

/**
 * Scroll policy for the Assistant conversation list.
 *
 * Single authoritative policy used by the sheet — there is exactly one [androidx.compose.foundation.lazy.LazyListState]
 * for the conversation and every scroll decision goes through these functions.
 *
 * POLICY
 * - `isNearLatest == true` (the user is reading the latest content) AND new content arrives
 *   -> auto-scroll smoothly to the newest item.
 * - `isNearLatest == false` (the user intentionally scrolled up) AND new content arrives
 *   -> preserve the reading position and surface the "New message" indicator.
 *
 * The list never scrolls because of recomposition, suggestion updates, or unrelated UI
 * state changes: callers key their scroll effects on the conversation content signature only.
 */
object AssistantChatScrollPolicy {

    /**
     * How many items above the end still count as "reading the latest".
     *
     * Reason (not an arbitrary pixel value): [androidx.compose.foundation.lazy.LazyListLayoutInfo]
     * reflects the previous frame when a new item is inserted, so a one-item tolerance is
     * required to avoid a spurious "not near latest" verdict in the frame the content
     * arrives. A second item covers back-to-back insertions (message + typing indicator)
     * within the same frame.
     */
    const val NEAR_LATEST_SLACK_ITEMS = 2

    /**
     * True when the user is positioned at (or immediately above) the newest content.
     *
     * @param lastVisibleIndex index of the bottom-most visible item, or -1 before first layout.
     * @param totalItems total item count of the conversation list.
     */
    fun isNearLatest(lastVisibleIndex: Int, totalItems: Int): Boolean {
        if (totalItems <= 0) return true
        if (lastVisibleIndex < 0) return false
        return lastVisibleIndex >= totalItems - 1 - NEAR_LATEST_SLACK_ITEMS
    }

    /**
     * Auto-scroll is allowed only when the user already is at the latest content and the
     * actual conversation content signature changed. Recomposition, suggestion changes, and
     * unrelated state never change the signature, so they never scroll.
     */
    fun shouldAutoScroll(isNearLatest: Boolean, contentChanged: Boolean): Boolean =
        isNearLatest && contentChanged

    /**
     * The floating "New message" control is shown only while genuinely new conversation
     * content exists AND the user has moved away from the latest position.
     */
    fun shouldShowNewMessageIndicator(
        conversationSignature: String,
        seenConversationSignature: String,
        isNearLatest: Boolean
    ): Boolean = conversationSignature != seenConversationSignature && !isNearLatest
}
