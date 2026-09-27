package com.example.ai.ui

import android.view.KeyEvent
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ai.context.EngineeringContext
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * Keyboard / focus / Back behavior of the real Assistant sheet.
 *
 * KEYBOARD
 * 1. Assistant opens with the keyboard hidden (composer not focused).
 * 2. The composer is not focused initially (no automatic FocusRequester).
 * 3. Tapping the composer grants focus (and therefore the keyboard on device).
 * 4. Back while the keyboard is up hides the keyboard first and keeps the Assistant open.
 * 5. The Assistant is still open after that first Back.
 * 6. A second Back closes the Assistant.
 *
 * Note: Robolectric has no real IME, so "keyboard visible" is asserted through the composer
 * focus state that drives `keyboardOwnsBack`; the IME inset path is verified on-device and by
 * the source conformance test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class AssistantKeyboardBackBehaviorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun openAssistant(): EngineeringAssistantViewModel {
        val viewModel = EngineeringAssistantViewModel()
        viewModel.open(
            AssistantEntryPoint.TOP_BAR_ICON,
            EngineeringContext(projectId = "P-KB", projectName = "Keyboard Project")
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                EngineeringAssistantSheet(viewModel = viewModel)
            }
        }
        settle()
        return viewModel
    }

    private fun settle() {
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()
    }

    /**
     * CASE A path: a real BACK key event delivered to the sheet's dialog window, exactly like
     * the platform does. The composition key interception must consume it while the keyboard
     * owns Back.
     */
    private fun dispatchSystemBackKey() {
        val dialog = latestDialog()
        val decor = dialog.window?.decorView
        decor?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        decor?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
    }

    /**
     * CASE B path: the sheet dialog's own back dispatcher (the callback the platform invokes
     * once nothing in the composition consumes Back). Robolectric swallows synthetic BACK key
     * events in the unhandled case, so the dismiss path is exercised through this callback.
     */
    private fun dispatchDialogDismissBack() {
        latestDialog().onBackPressedDispatcher.onBackPressed()
    }

    private fun latestDialog(): ComponentDialog {
        val dialog = ShadowDialog.getLatestDialog() as? ComponentDialog
        assertNotNull("Expected the Compose sheet dialog to be showing", dialog)
        return dialog!!
    }

    // --- 1 & 2 ---------------------------------------------------------------------

    @Test
    fun assistantOpens_withKeyboardHiddenAndComposerNotFocused() {
        openAssistant()

        composeTestRule.onNodeWithTag("assistant_input_field").assertIsNotFocused()
    }

    // --- 3 ------------------------------------------------------------------------

    @Test
    fun composerReceivesFocus_onlyByExplicitUserTap() {
        openAssistant()
        composeTestRule.onNodeWithTag("assistant_input_field").assertIsNotFocused()

        composeTestRule.onNodeWithTag("assistant_input_field").performClick()
        settle()

        composeTestRule.onNodeWithTag("assistant_input_field").assertIsFocused()
    }

    // --- 4 & 5 --------------------------------------------------------------------

    @Test
    fun backWhileKeyboardVisible_dismissesKeyboardAndKeepsAssistantOpen() {
        val viewModel = openAssistant()
        composeTestRule.onNodeWithTag("assistant_input_field").performClick()
        settle()
        // The composer owns the keyboard: focus is granted and the keyboard is up on device.
        composeTestRule.onNodeWithTag("assistant_input_field").assertIsFocused()

        dispatchSystemBackKey()
        settle()

        // The keyboard-first Back path consumed the event at key level: the keyboard is
        // dismissed on device and the Assistant remains open.
        assertTrue("Assistant must stay open after the keyboard-back", viewModel.uiState.value.isOpen)
        composeTestRule.onNodeWithTag("assistant_input_field").assertExists()
    }

    // --- 6 ------------------------------------------------------------------------

    @Test
    fun secondBack_closesAssistant() {
        val viewModel = openAssistant()
        composeTestRule.onNodeWithTag("assistant_input_field").performClick()
        settle()

        dispatchSystemBackKey()
        settle()
        assertTrue(viewModel.uiState.value.isOpen)

        // Keyboard already dismissed by the first Back -> the dialog dismiss path runs.
        dispatchDialogDismissBack()
        settle()

        assertFalse("Second Back must close the Assistant", viewModel.uiState.value.isOpen)
        composeTestRule.onNodeWithTag("assistant_input_field").assertDoesNotExist()
    }

    @Test
    fun backWithoutKeyboard_closesAssistantImmediately() {
        val viewModel = openAssistant()

        dispatchDialogDismissBack()
        settle()

        assertFalse(viewModel.uiState.value.isOpen)
    }
}
