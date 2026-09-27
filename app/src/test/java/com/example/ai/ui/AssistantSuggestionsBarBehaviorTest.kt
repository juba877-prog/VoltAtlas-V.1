package com.example.ai.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.example.ai.context.EngineeringContext
import com.example.ai.service.MessageRole
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SUGGESTIONS
 * 7.  Suggestions render outside the conversation list.
 * 8.  Suggestions never create conversation messages.
 * 9.  The maximum visible suggestion count is enforced.
 * 10. Suggestions stay compact (bounded height, >= 48dp touch targets).
 * 11. Updating suggestions does not alter conversation history.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class AssistantSuggestionsBarBehaviorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun openAssistant(): EngineeringAssistantViewModel {
        val viewModel = EngineeringAssistantViewModel()
        viewModel.open(
            AssistantEntryPoint.TOP_BAR_ICON,
            EngineeringContext(projectId = "P-SUG", projectName = "Suggestion Project")
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                EngineeringAssistantSheet(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        return viewModel
    }

    private fun collectTestTags(node: SemanticsNode, acc: MutableList<String> = mutableListOf()): List<String> {
        node.config.getOrNull(SemanticsProperties.TestTag)?.let { acc.add(it) }
        node.children.forEach { collectTestTags(it, acc) }
        return acc
    }

    @Test
    fun suggestionsRender_outsideTheConversationList() {
        openAssistant()

        composeTestRule.onNodeWithTag("assistant_suggestions_bar").assertExists()
        composeTestRule.onNodeWithTag("assistant_conversation_list").assertExists()

        val conversationTags = collectTestTags(
            composeTestRule.onNodeWithTag("assistant_conversation_list").fetchSemanticsNode()
        )
        assertFalse(
            "SuggestionsBar must not be a descendant of the conversation list",
            conversationTags.any { it == "assistant_suggestions_bar" || it == "assistant_suggestion_chip" }
        )
    }

    @Test
    fun suggestions_neverBecomeConversationMessages() {
        val viewModel = openAssistant()
        val assistantMessagesBefore = viewModel.uiState.value.messages.count { it.role == MessageRole.ASSISTANT }

        composeTestRule.onAllNodesWithTag("assistant_suggestion_chip").assertCountEquals(AssistantSuggestions.MAX_VISIBLE)

        viewModel.sendMessage("Analyze the feeder")
        composeTestRule.waitForIdle()

        val state = viewModel.uiState.value
        assertEquals(
            "Suggestions must not append assistant messages",
            assistantMessagesBefore,
            state.messages.count { it.role == MessageRole.ASSISTANT }
        )
        assertTrue("Suggestion labels must never appear as messages", state.messages.none { it.text == "Size cable" })
    }

    @Test
    fun maximumVisibleSuggestionCount_isEnforced() {
        openAssistant()

        val chips = composeTestRule.onAllNodesWithTag("assistant_suggestion_chip").fetchSemanticsNodes().size
        assertTrue("Visible suggestions must never exceed 4 (was $chips)", chips <= AssistantSuggestions.MAX_VISIBLE)
        assertTrue("Idle assistant must offer suggestions", chips > 0)
    }

    @Test
    fun suggestionsStayCompact_withAccessibleTouchTargets() {
        openAssistant()

        val density = composeTestRule.density.density
        val barHeightPx = composeTestRule.onNodeWithTag("assistant_suggestions_bar")
            .fetchSemanticsNode().size.height
        assertTrue("SuggestionsBar height ${barHeightPx}px exceeds the compact 72dp budget", barHeightPx <= 72f * density)

        val chip = composeTestRule.onAllNodesWithTag("assistant_suggestion_chip")[0]
        val chipBounds = chip.fetchSemanticsNode().touchBoundsInRoot
        assertTrue("Suggestion chip touch height must be >= 48dp", chipBounds.height >= 48f * density)
    }

    @Test
    fun updatingSuggestions_doesNotAlterConversationHistory() {
        val viewModel = openAssistant()
        val historyBefore = viewModel.uiState.value.messages
        val cardsBefore = viewModel.uiState.value.typedCards

        viewModel.sendMessage("New request")
        composeTestRule.waitForIdle()

        val after = viewModel.uiState.value
        assertEquals(historyBefore.size + 1, after.messages.size)
        assertEquals("Prior conversation history must be untouched", historyBefore, after.messages.take(historyBefore.size))
        assertEquals(
            "Prior result cards must be untouched",
            cardsBefore,
            after.typedCards.take(cardsBefore.size)
        )
    }
}
