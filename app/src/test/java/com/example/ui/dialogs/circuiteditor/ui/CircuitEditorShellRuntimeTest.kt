package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import androidx.compose.ui.unit.dp
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
    fun test07_stepOneOmitsBottomBackButtonWhilePreservingNext() {
        val holder = CircuitEditorTestStateHolder()
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_next_btn").assertIsDisplayed()
        // DEFECT C2-V08 / Section 5 #9: Back button is omitted on Step 1, not rendered as large disabled button
        composeTestRule.onNodeWithTag("circuit_editor_back_btn").assertDoesNotExist()
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

        // DEFECT C2-V05 / Section 5 #8: Step X of Y: Title semantics
        composeTestRule.onNode(hasStateDescription("Step 1 of 6: Identity & Source")).assertIsDisplayed()
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

    @Test
    fun test12_activeProgressPillDisplaysPositionAndHeaderDisplaysTitle() {
        val holder = CircuitEditorTestStateHolder()
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Active progress item exposes numbered pill and semantics on compact width
        composeTestRule.onNodeWithTag("active_progress_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("circuit_editor_step_header").assertIsDisplayed()
        composeTestRule.onNodeWithText("1. Identity & Source").assertIsDisplayed()
    }

    @Test
    fun test12b_progressSizingTokensSeparation() {
        // Contract requires separate documentation and verification of progress sizing tokens
        assertEquals(24.dp, CircuitEditorProgressTokens.VisibleCircleDiameter)
        assertEquals(48.dp, CircuitEditorProgressTokens.ActivePillWidth)
        assertEquals(28.dp, CircuitEditorProgressTokens.ActivePillHeight)
        assertEquals(48.dp, CircuitEditorProgressTokens.MinimumInteractiveSize)
        assertEquals(48.dp, CircuitEditorProgressTokens.AllocatedItemWidth)
        assertEquals(12.dp, CircuitEditorProgressTokens.ConnectorWidth)
        assertEquals(4.dp, CircuitEditorProgressTokens.InterItemSpacing)
        assertEquals(16.dp, CircuitEditorProgressTokens.RailDefaultHorizontalPadding)

        val holder = CircuitEditorTestStateHolder()
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Active node interactive bounds >= 48dp
        val activeBounds = composeTestRule.onNodeWithTag("progress_step_node_1").getBoundsInRoot()
        assertTrue("Active node touch target width >= 48dp", (activeBounds.right - activeBounds.left) >= 48.dp)
        assertTrue("Active node touch target height >= 48dp", (activeBounds.bottom - activeBounds.top) >= 48.dp)

        // Active visible pill is 48dp x 28dp
        val pillBounds = composeTestRule.onNodeWithTag("active_progress_pill").getBoundsInRoot()
        assertEquals(48.dp, pillBounds.right - pillBounds.left)
        assertEquals(28.dp, pillBounds.bottom - pillBounds.top)
    }

    @Test
    @Config(fontScale = 1.3f)
    fun test13_case1_sevenStepProgressAt360dpFont1_3AllNodesInsideViewport() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        val progressBounds = composeTestRule.onNodeWithTag("circuit_editor_progress").getBoundsInRoot()
        for (i in 1..7) {
            val nodeBounds = composeTestRule.onNodeWithTag("progress_step_node_$i").getBoundsInRoot()
            assertTrue(
                "Step $i left (${nodeBounds.left}) must be >= progress left (${progressBounds.left})",
                nodeBounds.left >= progressBounds.left
            )
            assertTrue(
                "Step $i right (${nodeBounds.right}) must be <= progress right (${progressBounds.right})",
                nodeBounds.right <= progressBounds.right
            )
        }
        composeTestRule.onNodeWithTag("active_progress_pill").assertIsDisplayed()
    }

    @Test
    @Config(fontScale = 1.5f)
    fun test14_case2_sevenStepProgressAt360dpFont1_5NoPartiallyClippedNode() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        val progressBounds = composeTestRule.onNodeWithTag("circuit_editor_progress").getBoundsInRoot()
        for (i in 1..7) {
            val nodeBounds = composeTestRule.onNodeWithTag("progress_step_node_$i").getBoundsInRoot()
            assertTrue(
                "Step $i left (${nodeBounds.left}) must be >= progress left (${progressBounds.left})",
                nodeBounds.left >= progressBounds.left
            )
            assertTrue(
                "Step $i right (${nodeBounds.right}) must be <= progress right (${progressBounds.right})",
                nodeBounds.right <= progressBounds.right
            )
        }
        composeTestRule.onNodeWithTag("active_progress_pill").assertIsDisplayed()
    }

    @Test
    fun test15_case3_sevenStepProgressAt360dpFont1_0LayoutMode() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        val progressBounds = composeTestRule.onNodeWithTag("circuit_editor_progress").getBoundsInRoot()
        for (i in 1..7) {
            val nodeBounds = composeTestRule.onNodeWithTag("progress_step_node_$i").getBoundsInRoot()
            assertTrue(
                "Step $i left (${nodeBounds.left}) must be >= progress left (${progressBounds.left})",
                nodeBounds.left >= progressBounds.left
            )
            assertTrue(
                "Step $i right (${nodeBounds.right}) must be <= progress right (${progressBounds.right})",
                nodeBounds.right <= progressBounds.right
            )
        }
        composeTestRule.onNodeWithTag("active_progress_pill").assertIsDisplayed()
    }

    @Test
    fun test16_case4_sixStepProgressAt360dpFont1_0CompactNumberedNodesFit() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "L-01", panelType = "LOAD"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("progress_mode_COMPACT_ACTIVE_PILL").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_progress_pill").assertIsDisplayed()

        val progressBounds = composeTestRule.onNodeWithTag("circuit_editor_progress").getBoundsInRoot()
        for (i in 1..6) {
            val nodeBounds = composeTestRule.onNodeWithTag("progress_step_node_$i").getBoundsInRoot()
            assertTrue(
                "Step $i left (${nodeBounds.left}) must be >= progress left (${progressBounds.left})",
                nodeBounds.left >= progressBounds.left
            )
            assertTrue(
                "Step $i right (${nodeBounds.right}) must be <= progress right (${progressBounds.right})",
                nodeBounds.right <= progressBounds.right
            )
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-xhdpi")
    fun test17a_case5_progressAt320dpFont1_0SelectsScrollableRail() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("progress_mode_HORIZONTAL_SCROLL_RAIL").assertIsDisplayed()
        val activeBounds = composeTestRule.onNodeWithTag("active_progress_pill").getBoundsInRoot()
        val progressBounds = composeTestRule.onNodeWithTag("circuit_editor_progress").getBoundsInRoot()
        assertTrue(activeBounds.left >= progressBounds.left)
        assertTrue(activeBounds.right <= progressBounds.right)
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test17b_case5_progressAt320dpFont1_3SelectsScrollableRail() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("progress_mode_HORIZONTAL_SCROLL_RAIL").assertIsDisplayed()
        val activeBounds = composeTestRule.onNodeWithTag("active_progress_pill").getBoundsInRoot()
        val progressBounds = composeTestRule.onNodeWithTag("circuit_editor_progress").getBoundsInRoot()
        assertTrue(activeBounds.left >= progressBounds.left)
        assertTrue(activeBounds.right <= progressBounds.right)
    }

    @Test
    @Config(qualifiers = "w412dp-h892dp-xhdpi", fontScale = 1.3f)
    fun test18_case6_progressAt412dpFont1_3SelectsCompactPillOnCompactWidth() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("progress_mode_COMPACT_ACTIVE_PILL").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_progress_pill").assertIsDisplayed()
    }

    @Test
    fun test19_case7_everyProgressItemRetains48dpInteractiveTarget() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        for (i in 1..7) {
            val bounds = composeTestRule.onNodeWithTag("progress_step_node_$i").getBoundsInRoot()
            val width = bounds.right - bounds.left
            val height = bounds.bottom - bounds.top
            assertTrue("Step $i width must be >= 48dp, was $width", width >= 48.dp)
            assertTrue("Step $i height must be >= 48dp, was $height", height >= 48.dp)
        }
    }

    @Test
    fun test20_case8_noDuplicateProgressTagsOrSemantics() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_progress").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_progress_pill").assertIsDisplayed()
        for (i in 1..7) {
            composeTestRule.onNodeWithTag("progress_step_node_$i").assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test21a_activeStep1FullyVisibleAt320dp() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.waitForIdle()
        val node1 = composeTestRule.onNodeWithTag("progress_step_node_1")
        node1.assertIsDisplayed()
        val node1Bounds = node1.getBoundsInRoot()
        assertTrue("Step 1 left (${node1Bounds.left}) must be >= 0dp", node1Bounds.left >= 0.dp)
        assertTrue("Step 1 right (${node1Bounds.right}) must be <= 320dp", node1Bounds.right <= 320.dp)

        val center1 = (node1Bounds.left + node1Bounds.right) / 2
        val delta1 = kotlin.math.abs((center1 - 160.dp).value)
        assertTrue("Step 1 center ($center1) must be within 2dp tolerance of 160dp, delta was $delta1", delta1 <= 2f)
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test21b_activeMiddleStepFullyVisibleAt320dp() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.FEEDER, // Step 4 of 7
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.waitForIdle()
        val node4 = composeTestRule.onNodeWithTag("progress_step_node_4")
        node4.assertIsDisplayed()
        val node4Bounds = node4.getBoundsInRoot()
        assertTrue("Step 4 left (${node4Bounds.left}) must be >= 0dp", node4Bounds.left >= 0.dp)
        assertTrue("Step 4 right (${node4Bounds.right}) must be <= 320dp", node4Bounds.right <= 320.dp)

        val center4 = (node4Bounds.left + node4Bounds.right) / 2
        val delta4 = kotlin.math.abs((center4 - 160.dp).value)
        assertTrue("Step 4 center ($center4) must be within 2dp tolerance of 160dp, delta was $delta4", delta4 <= 2f)
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test21c_activeStep7FullyVisibleAt320dpAfterNavigation() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.REVIEW, // Step 7 of 7
            initialVisited = CircuitWorkflowStep.values().toSet()
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.waitForIdle()
        val node7 = composeTestRule.onNodeWithTag("progress_step_node_7")
        node7.assertIsDisplayed()
        val node7Bounds = node7.getBoundsInRoot()
        assertTrue("Step 7 left (${node7Bounds.left}) must be >= 0dp", node7Bounds.left >= 0.dp)
        assertTrue("Step 7 right (${node7Bounds.right}) must be <= 320dp inside the viewport", node7Bounds.right <= 320.dp)

        val center7 = (node7Bounds.left + node7Bounds.right) / 2
        val delta7 = kotlin.math.abs((center7 - 160.dp).value)
        assertTrue("Step 7 center ($center7) must be within 2dp tolerance of 160dp, delta was $delta7", delta7 <= 2f)
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test21d_activeStep7FullyVisibleAt320dpInRtlLayout() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = CircuitWorkflowStep.values().toSet()
        )
        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
            ) {
                CircuitEditorTestHost(stateHolder = holder)
            }
        }

        composeTestRule.waitForIdle()
        val node7 = composeTestRule.onNodeWithTag("progress_step_node_7")
        node7.assertIsDisplayed()
        val node7Bounds = node7.getBoundsInRoot()
        assertTrue("Step 7 in RTL left (${node7Bounds.left}) must be >= 0dp", node7Bounds.left >= 0.dp)
        assertTrue("Step 7 in RTL right (${node7Bounds.right}) must be <= 320dp inside the viewport", node7Bounds.right <= 320.dp)

        val center7 = (node7Bounds.left + node7Bounds.right) / 2
        val delta7 = kotlin.math.abs((center7 - 160.dp).value)
        assertTrue("Step 7 in RTL center ($center7) must be within 2dp tolerance of 160dp, delta was $delta7", delta7 <= 2f)
    }

    @Test
    fun test22_failFirst_noDuplicateVisibleActiveStepTitleInCompactMode() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // The progress rail itself must NOT contain a duplicate visible title block
        composeTestRule.onNodeWithTag("progress_duplicate_title_bar").assertDoesNotExist()
        // There must be exactly one content heading for the active step
        composeTestRule.onNodeWithTag("circuit_editor_step_header").assertIsDisplayed()
    }

    @Test
    fun test23_failFirst_progressNodeSemanticStateAndDisabledForwardSemantics() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Active node 1 has Current state
        composeTestRule.onNodeWithTag("progress_step_node_1")
            .assert(hasStateDescription("Current"))

        // Unvisited node 2 has Upcoming state and is disabled in accessibility semantics
        composeTestRule.onNodeWithTag("progress_step_node_2")
            .assert(hasStateDescription("Upcoming"))
            .assertIsNotEnabled()
    }

    @Test
    fun test24_failFirst_activePillIsStadiumPillShapeWiderThanTall() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        val pillBounds = composeTestRule.onNodeWithTag("active_progress_pill").getBoundsInRoot()
        val pillWidth = pillBounds.right - pillBounds.left
        val pillHeight = pillBounds.bottom - pillBounds.top

        // Contract requires active node to use a stadium/pill visual shape distinct from inactive circles
        // Visible pill width must be 48dp and height 28dp (width > height + 8dp)
        assertTrue(
            "Active pill must be wider than tall (pill shape): width $pillWidth vs height $pillHeight",
            pillWidth >= pillHeight + 12.dp
        )
        assertTrue("Active pill width ($pillWidth) must be 48dp", kotlin.math.abs((pillWidth - 48.dp).value) <= 2f)
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test25_failFirst_autoCenteringExactToleranceAt320dp() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.FEEDER, // Step 4 of 7
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }
        composeTestRule.waitForIdle()

        val node4 = composeTestRule.onNodeWithTag("progress_step_node_4")
        node4.assertIsDisplayed()
        val bounds4 = node4.getBoundsInRoot()
        val center4 = (bounds4.left + bounds4.right) / 2
        val viewportCenter = 160.dp
        val delta4 = kotlin.math.abs((center4 - viewportCenter).value)

        assertTrue(
            "Step 4 center ($center4) must be within 2dp tolerance of viewport center ($viewportCenter), delta was $delta4",
            delta4 <= 2f
        )
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test26_failFirst_autoCenteringInRtlAt320dp() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.FEEDER,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER
            )
        )
        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
            ) {
                CircuitEditorTestHost(stateHolder = holder)
            }
        }
        composeTestRule.waitForIdle()

        val node4 = composeTestRule.onNodeWithTag("progress_step_node_4")
        node4.assertIsDisplayed()
        val bounds4 = node4.getBoundsInRoot()
        val center4 = (bounds4.left + bounds4.right) / 2
        val viewportCenter = 160.dp
        val delta4 = kotlin.math.abs((center4 - viewportCenter).value)

        assertTrue(
            "Step 4 in RTL center ($center4) must be within 2dp tolerance of viewport center ($viewportCenter), delta was $delta4",
            delta4 <= 2f
        )
    }

    @Test
    fun test27_failFirst_actionBarEqualWidthButtonsOnStep2() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        val backBounds = composeTestRule.onNodeWithTag("circuit_editor_back_btn").getBoundsInRoot()
        val nextBounds = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()

        val backWidth = backBounds.right - backBounds.left
        val nextWidth = nextBounds.right - nextBounds.left

        assertTrue(
            "Back and Next must share equal available width: back=$backWidth, next=$nextWidth",
            kotlin.math.abs((backWidth - nextWidth).value) <= 4f
        )
        assertTrue("Back must have minimum 48dp height", (backBounds.bottom - backBounds.top) >= 48.dp)
        assertTrue("Next must have minimum 48dp height", (nextBounds.bottom - nextBounds.top) >= 48.dp)
    }
}
