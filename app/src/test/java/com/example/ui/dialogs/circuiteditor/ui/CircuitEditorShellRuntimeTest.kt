package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runtime specification tests for CircuitEditorShell and Navigation.
 *
 * Evidence Label: NEW_EDITOR_RUNTIME_OBSERVED
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class CircuitEditorShellRuntimeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun test01_identityRendersAsInitialStep() {
        val holder = CircuitEditorTestStateHolder()
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("circuit_editor_step_identity").assertIsDisplayed()
        composeTestRule.onNodeWithTag("circuit_editor_step_load").assertDoesNotExist()
    }

    @Test
    fun test02_closeInvokesOnDismissExactlyOnce() {
        val holder = CircuitEditorTestStateHolder()
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        assertEquals(0, holder.dismissCount)
        composeTestRule.onNodeWithTag("circuit_editor_close_btn").performClick()
        assertEquals(1, holder.dismissCount)
    }

    @Test
    fun test03_systemBackOnIdentityDismisses() {
        val holder = CircuitEditorTestStateHolder()
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        assertEquals(0, holder.dismissCount)
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        assertEquals(1, holder.dismissCount)
    }

    @Test
    fun test04_bottomBackBehaviorAfterNavigatingToLoad() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_step_load").assertIsDisplayed()
        val backBtn = composeTestRule.onNodeWithTag("circuit_editor_back_btn")
        backBtn.assertIsEnabled()
        backBtn.performClick()

        assertEquals(CircuitWorkflowStep.IDENTITY, holder.currentStep)
        composeTestRule.onNodeWithTag("circuit_editor_step_identity").assertIsDisplayed()
    }

    @Test
    fun test05_systemBackFromLoadReturnsToIdentity() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        assertEquals(0, holder.dismissCount)
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals(0, holder.dismissCount)
        assertEquals(CircuitWorkflowStep.IDENTITY, holder.currentStep)
        composeTestRule.onNodeWithTag("circuit_editor_step_identity").assertIsDisplayed()
    }

    @Test
    fun test06_noSaveActionExists() {
        val holder = CircuitEditorTestStateHolder()
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("save_circuit_btn").assertDoesNotExist()
        composeTestRule.onNodeWithText("Save").assertDoesNotExist()
        composeTestRule.onNodeWithText("Save Circuit").assertDoesNotExist()
    }

    @Test
    fun test07_stickyActionAreaRemainsVisible() {
        val holder = CircuitEditorTestStateHolder()
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_next_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("circuit_editor_back_btn").assertIsDisplayed()
        // Back is disabled on initial Identity step
        composeTestRule.onNodeWithTag("circuit_editor_back_btn").assertIsNotEnabled()
    }

    @Test
    fun test08_progressDerivesSixVersusSevenSteps() {
        val loadHolder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(panelType = "LOAD")
        )
        assertEquals(6, loadHolder.applicableSteps.size)

        val panelHolder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(panelType = "DB")
        )
        assertEquals(7, panelHolder.applicableSteps.size)
    }

    @Test
    fun test09_progressExposesCurrentStepSemantics() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(panelType = "LOAD")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNode(hasStateDescription("Step 1 of 6")).assertIsDisplayed()
    }

    @Test
    fun test10_forwardStepTapIsBlockedWhenUnvisited() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.IDENTITY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Tap on step 2 node "2" should be ignored because unvisited
        composeTestRule.onNodeWithText("2").performClick()
        assertEquals(CircuitWorkflowStep.IDENTITY, holder.currentStep)
    }

    @Test
    fun test11_backwardVisitedStepNavigationWorks() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Tap on visited step 1 node "1"
        composeTestRule.onNodeWithText("1").performClick()
        assertEquals(CircuitWorkflowStep.IDENTITY, holder.currentStep)
        composeTestRule.onNodeWithTag("circuit_editor_step_identity").assertIsDisplayed()
    }
}
