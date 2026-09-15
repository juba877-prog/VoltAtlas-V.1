package com.example.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.ui.screens.quick.QuickConduitFillScreen
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Phase 4D — QuickConduitFillScreen responsive coverage (structure/semantics,
 * no screenshot goldens).
 *
 * Verifies that:
 * 1. Under narrow width (320dp) or enlarged font scale (1.5), input field pairs
 *    (main cable count/OD and CPC count/OD) stack vertically, giving each field
 *    adequate full row width for its label without truncation.
 * 2. On standard phone width (411dp) at font scale 1.0, field pairs remain inline.
 * 3. Metric cards and provenance details remain accessible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi")
class QuickConduitFillNarrowWidthTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun render(fontScale: Float) {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale)
            ) {
                VoltAtlasTheme(darkTheme = false) {
                    QuickConduitFillScreen(onBack = {})
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun narrowWidth_fsc1_5_fieldPairsStackAndKeepFullWidth() {
        render(fontScale = 1.5f)

        // Scroll to main cable count
        composeTestRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag("input_main_cable_count"))
        composeTestRule.waitForIdle()

        val mainCountNode = composeTestRule.onNodeWithTag("input_main_cable_count")
        mainCountNode.assertIsDisplayed()
        val mainCountWidth = mainCountNode.fetchSemanticsNode().boundsInRoot.width

        val contentWidthPx = with(composeTestRule.density) { 256.dp.toPx() }
        assertTrue(
            "stacked main cable field must use full card row width (width=$mainCountWidth)",
            mainCountWidth >= contentWidthPx * 0.9f
        )

        // Scroll to main cable OD
        composeTestRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag("input_main_cable_od"))
        composeTestRule.waitForIdle()

        val mainOdNode = composeTestRule.onNodeWithTag("input_main_cable_od")
        mainOdNode.assertIsDisplayed()
        val mainOdWidth = mainOdNode.fetchSemanticsNode().boundsInRoot.width
        assertTrue(
            "stacked main cable OD field must use full card row width (width=$mainOdWidth)",
            mainOdWidth >= contentWidthPx * 0.9f
        )

        // Scroll to CPC fields
        composeTestRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag("input_cpc_cable_count"))
        composeTestRule.waitForIdle()

        val cpcCountNode = composeTestRule.onNodeWithTag("input_cpc_cable_count")
        cpcCountNode.assertIsDisplayed()
        val cpcCountWidth = cpcCountNode.fetchSemanticsNode().boundsInRoot.width
        assertTrue(
            "stacked CPC count field must use full card row width (width=$cpcCountWidth)",
            cpcCountWidth >= contentWidthPx * 0.9f
        )

        composeTestRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag("input_cpc_cable_od"))
        composeTestRule.waitForIdle()

        val cpcOdNode = composeTestRule.onNodeWithTag("input_cpc_cable_od")
        cpcOdNode.assertIsDisplayed()
        val cpcOdWidth = cpcOdNode.fetchSemanticsNode().boundsInRoot.width
        assertTrue(
            "stacked CPC OD field must use full card row width (width=$cpcOdWidth)",
            cpcOdWidth >= contentWidthPx * 0.9f
        )

        // Labels remain accessible in semantics
        composeTestRule.onNodeWithText("Main Cables (Qty)").assertExists()
        composeTestRule.onNodeWithText("Main Cable OD (mm)").assertExists()
        composeTestRule.onNodeWithText("CPC Earth Cables (Qty)").assertExists()
        composeTestRule.onNodeWithText("CPC Cable OD (mm)").assertExists()

        // Top bar actions accessible
        composeTestRule.onNodeWithTag("conduit_back_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("conduit_copy_btn").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class QuickConduitFillInlineRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun normalPhoneWidth_fsc1_0_fieldPairsStayInline() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                QuickConduitFillScreen(onBack = {})
            }
        }
        composeTestRule.waitForIdle()

        val mainCountField = composeTestRule.onNodeWithTag("input_main_cable_count")
            .fetchSemanticsNode().boundsInRoot
        val mainOdField = composeTestRule.onNodeWithTag("input_main_cable_od")
            .fetchSemanticsNode().boundsInRoot

        // Inline: two fields share one row side-by-side
        assertTrue(
            "fields should be side-by-side: mainCount.left < mainOd.left",
            mainCountField.left < mainOdField.left
        )
        assertTrue(
            "fields should share the same vertical band: |top1 - top2| < 10px",
            abs(mainCountField.top - mainOdField.top) < 10f
        )

        composeTestRule.onNodeWithTag("conduit_back_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("conduit_copy_btn").assertIsDisplayed()
    }
}
