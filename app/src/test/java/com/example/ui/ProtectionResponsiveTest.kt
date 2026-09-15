package com.example.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.engine.ShortCircuitResult
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 3C — ProtectionScreen responsive coverage (structure/semantics, no
 * screenshot goldens).
 *
 * Renders ShortCircuitCard under the mandatory combined stress (narrow
 * 320dp window + enlarged font scale + long engineering content):
 *
 *  1. Margin value column: with widthIn(min = 68dp) the right-aligned
 *     percentage (e.g. "125.0%") may GROW beyond the old fixed 68dp cap at
 *     fontScale 1.5 instead of clipping its leading digits (a clipped
 *     right-aligned value would read as an ambiguous different number).
 *     At fontScale 1.0 the column stays at the 68dp alignment width.
 *  2. MetricItemTile 3-up row (Isc / Iw / Device Icu): the kA unit is
 *     measured first and keeps a measurable width; the value ellipsizes
 *     instead of starving the unit.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi")
class ProtectionResponsiveTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val result = ShortCircuitResult(
        circuitId = 7,
        sourceName = "Transformer",
        cableMm2 = "240 mm²",
        iscKA = 100.0,
        iwKA = 300.0,
        deviceKA = 225.0,
        deviceCheck = "PASS",
        cableCheck = "PASS",
        marginPct = 125.0,
        status = "PASS",
        note = "Short Circuit Withhold margin verified against device rating"
    )

    private fun renderCard(fontScale: Float) {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale)
            ) {
                VoltAtlasTheme(darkTheme = false) {
                    ShortCircuitCard(
                        res = result,
                        circuitName = "Protection Coordination Feeder",
                        isDark = false
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun narrowWidth_fsc1_5_marginValueGrowsBeyondOldFixedCap_unitsRemainVisible() {
        renderCard(fontScale = 1.5f)

        val oldCapPx = with(composeTestRule.density) { 68.dp.toPx() }
        val marginNode = composeTestRule.onNodeWithText("125.0%")
            .fetchSemanticsNode()
        val width = marginNode.boundsInRoot.width
        assertTrue(
            "margin value must be allowed to grow beyond the old 68dp fixed width (width=$width, cap=$oldCapPx)",
            width >= oldCapPx
        )
        composeTestRule.onNodeWithText("Margin").assertExists()

        // MetricItemTile 3-up row: every kA unit keeps measurable width.
        val unitNodes = composeTestRule.onAllNodesWithText("kA")
            .fetchSemanticsNodes()
        assertTrue("expected >= 3 kA units, found ${unitNodes.size}", unitNodes.size >= 3)
        unitNodes.forEach { node ->
            assertTrue("kA unit must keep measurable width", node.boundsInRoot.width > 0f)
        }
    }

    @Test
    fun narrowWidth_fsc1_0_marginColumnStaysAtAlignmentWidth() {
        renderCard(fontScale = 1.0f)

        val alignmentPx = with(composeTestRule.density) { 68.dp.toPx() }
        val marginNode = composeTestRule.onNodeWithText("125.0%")
            .fetchSemanticsNode()
        // At fontScale 1.0 the column stays at the established 68dp width
        // (rendering unchanged versus the previous fixed-width layout).
        assertTrue(
            "margin column must stay ~68dp at fsc 1.0 (width=${marginNode.boundsInRoot.width})",
            marginNode.boundsInRoot.width <= alignmentPx + 2f
        )
        composeTestRule.onNodeWithText("Margin").assertExists()
    }
}