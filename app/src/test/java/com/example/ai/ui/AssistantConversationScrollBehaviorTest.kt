package com.example.ai.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.example.ai.context.EngineeringContext
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCROLL
 * 12. At the latest position a new response auto-scrolls.
 * 13. A user who scrolled upward keeps their reading position.
 * 14. The new-message indicator appears in that case.
 * 15. Clicking the indicator smoothly returns to the latest content.
 * 16. There is exactly ONE authoritative conversation list / scroll state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w400dp-h800dp-xhdpi")
class AssistantConversationScrollBehaviorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun openAssistant(): EngineeringAssistantViewModel {
        val viewModel = EngineeringAssistantViewModel()
        viewModel.open(
            AssistantEntryPoint.TOP_BAR_ICON,
            EngineeringContext(projectId = "P-SCROLL", projectName = "Scroll Project")
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                EngineeringAssistantSheet(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        return viewModel
    }

    /** Builds a long conversation, letting every turn settle (one frame per turn). */
    private fun fillConversation(viewModel: EngineeringAssistantViewModel, turns: Int) {
        repeat(turns) { index ->
            viewModel.sendMessage("history message $index")
            composeTestRule.waitForIdle()
        }
    }

    private fun settleScroll() {
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.waitForIdle()
    }

    // --- 12 -----------------------------------------------------------------------

    @Test
    fun atLatest_newResponseAutoScrollsToNewestContent() {
        val viewModel = openAssistant()
        fillConversation(viewModel, turns = 12)

        composeTestRule.onNodeWithText("history message 11").assertIsDisplayed()
        composeTestRule.onNodeWithText("history message 0").assertDoesNotExist()

        viewModel.sendMessage("newest arrival")
        composeTestRule.waitForIdle()
        settleScroll()

        composeTestRule.onNodeWithText("newest arrival").assertIsDisplayed()
    }

    // --- 13 & 14 ------------------------------------------------------------------

    @Test
    fun scrolledUp_newResponseDoesNotStealThePosition_andIndicatorAppears() {
        val viewModel = openAssistant()
        fillConversation(viewModel, turns = 12)

        composeTestRule.onNodeWithTag("assistant_conversation_list").performScrollToIndex(0)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("history message 0").assertIsDisplayed()

        viewModel.sendMessage("must not steal the position")
        composeTestRule.waitForIdle()
        settleScroll()

        composeTestRule.onNodeWithText("history message 0").assertIsDisplayed()
        composeTestRule.onNodeWithText("must not steal the position").assertDoesNotExist()
        composeTestRule.onNodeWithTag("assistant_new_message_indicator").assertExists()
    }

    // --- 15 -----------------------------------------------------------------------

    @Test
    fun clickingIndicator_returnsToTheNewestContentAndHidesItself() {
        val viewModel = openAssistant()
        fillConversation(viewModel, turns = 12)

        composeTestRule.onNodeWithTag("assistant_conversation_list").performScrollToIndex(0)
        composeTestRule.waitForIdle()
        viewModel.sendMessage("indicator target")
        composeTestRule.waitForIdle()
        settleScroll()
        composeTestRule.onNodeWithTag("assistant_new_message_indicator").assertExists()

        composeTestRule.onNodeWithTag("assistant_new_message_indicator").performClick()
        composeTestRule.waitForIdle()
        settleScroll()

        composeTestRule.onNodeWithText("indicator target").assertIsDisplayed()
        composeTestRule.onNodeWithTag("assistant_new_message_indicator").assertDoesNotExist()
    }

    @Test
    fun noIndicator_whileUserIsAlreadyAtTheLatestContent() {
        val viewModel = openAssistant()
        fillConversation(viewModel, turns = 6)

        composeTestRule.onNodeWithTag("assistant_new_message_indicator").assertDoesNotExist()

        viewModel.sendMessage("stays at bottom")
        composeTestRule.waitForIdle()
        settleScroll()

        composeTestRule.onNodeWithTag("assistant_new_message_indicator").assertDoesNotExist()
    }

    // --- 16 -----------------------------------------------------------------------

    @Test
    fun exactlyOneAuthoritativeConversationListExists() {
        val viewModel = openAssistant()
        fillConversation(viewModel, turns = 4)

        composeTestRule.onAllNodesWithTag("assistant_conversation_list").assertCountEquals(1)
    }
}
