package com.example.ai.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.example.ai.context.EngineeringContext
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A11y Step 7: composer touch-bound and semantics regression coverage for the
 * real EngineeringAssistantSheet. The send IconButton is visually 44dp but its
 * Material3 minimum interactive touch target must still be >= 48dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class AssistantComposerA11yTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sendButton_has48dpTouchTarget_semanticsAndInputFieldPresent() {
        val viewModel = EngineeringAssistantViewModel()
        viewModel.open(
            AssistantEntryPoint.TOP_BAR_ICON,
            EngineeringContext(projectId = "P-1", projectName = "A11y Project")
        )
        viewModel.setInputText("Please check the cable size")

        composeTestRule.setContent {
            VoltAtlasTheme {
                EngineeringAssistantSheet(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // The real send IconButton: CD "Send message" must exist.
        composeTestRule.onNodeWithContentDescription("Send message").assertExists()
        composeTestRule.onNodeWithTag("assistant_send_button").assertIsEnabled()

        // Minimum 48dp touch target (Material3 IconButton minimum interactive size).
        val bounds = composeTestRule.onNodeWithTag("assistant_send_button")
            .fetchSemanticsNode().touchBoundsInRoot
        assertTrue("send button touch width ${bounds.width}px is below 48dp", bounds.width >= 48f)
        assertTrue("send button touch height ${bounds.height}px is below 48dp", bounds.height >= 48f)

        // Input field must be present.
        composeTestRule.onNodeWithTag("assistant_input_field").assertExists()
    }
}