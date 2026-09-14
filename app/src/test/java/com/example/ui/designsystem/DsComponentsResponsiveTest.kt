package com.example.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.DsListRowItem
import com.example.ui.designsystem.components.DsMetricTile
import com.example.ui.designsystem.components.DsSummaryStatCard
import com.example.ui.designsystem.theme.DsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 2 shared-component responsive coverage (structure/semantics, no
 * screenshot goldens).
 *
 * Verifies the shared DS primitives under the mandatory combined stress:
 * constrained width + enlarged font scale + long engineering content:
 *
 *  - DsMetricTile: a long value can never push the engineering unit out of
 *    the tile; short value+unit still share one bottom-aligned line.
 *  - DsSummaryStatCard: long value/caption remain reachable, no hard clip.
 *  - DsListRowItem: long title + long trailing value keep the trailing
 *    chevron reachable; trailing truncates gracefully.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class DsComponentsResponsiveTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun centerOf(node: SemanticsNode): Pair<Float, Float> {
        val b = node.boundsInRoot
        return (b.left + b.right) / 2f to (b.top + b.bottom) / 2f
    }

    @Test
    fun metricTile_longValue_keepsUnitVisible_narrowWidth_fsc1_5() {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = 1.5f)
            ) {
                DsTheme(darkTheme = false) {
                    Box(Modifier.width(150.dp)) {
                        DsMetricTile(
                            label = "Maximum Demand Current",
                            value = "125000.00",
                            unit = "kA"
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val tileRight = with(composeTestRule.density) { 150.dp.toPx() } + 1f
        val unitBounds = composeTestRule.onNodeWithText("kA").fetchSemanticsNode().boundsInRoot
        assertTrue("unit must remain visible (width ${unitBounds.width})", unitBounds.width > 0f)
        assertTrue(
            "unit must stay inside the 150dp tile (right=${unitBounds.right})",
            unitBounds.right <= tileRight
        )
        composeTestRule.onNodeWithText("125000.00").assertExists()
        composeTestRule.onNodeWithText("Maximum Demand Current").assertExists()
    }

    @Test
    fun metricTile_shortValueAndUnit_shareOneBottomAlignedLine_fsc1_0() {
        composeTestRule.setContent {
            DsTheme(darkTheme = false) {
                Box(Modifier.width(220.dp)) {
                    DsMetricTile(label = "Short Circuit", value = "12.4", unit = "kA")
                }
            }
        }
        composeTestRule.waitForIdle()

        val valueNode = composeTestRule.onNodeWithText("12.4").fetchSemanticsNode()
        val unitNode = composeTestRule.onNodeWithText("kA").fetchSemanticsNode()
        // Same line: unit sits to the right of the value; the Row is
        // bottom-aligned, so BOTTOM edges coincide (centers differ because
        // value is titleMedium and unit is bodySmall).
        assertTrue(unitNode.boundsInRoot.left > valueNode.boundsInRoot.left)
        assertEquals(valueNode.boundsInRoot.bottom, unitNode.boundsInRoot.bottom, 2f)
    }

    @Test
    fun summaryStatCard_longContent_narrowWidth_fsc1_5_reachable() {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = 1.5f)
            ) {
                DsTheme(darkTheme = false) {
                    Box(Modifier.width(140.dp)) {
                        DsSummaryStatCard(
                            icon = Icons.Filled.CheckCircle,
                            value = "125000.00",
                            caption = "Cable ampacity across all configured sub-feeders",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val tileRight = with(composeTestRule.density) { 140.dp.toPx() } + 1f
        val valueBounds = composeTestRule.onNodeWithText("125000.00")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "value must stay inside the 140dp tile (right=${valueBounds.right})",
            valueBounds.right <= tileRight
        )
        composeTestRule.onNodeWithText("Cable ampacity across all configured sub-feeders")
            .assertExists()
    }

    @Test
    fun listRowItem_longTitleLongTrailing_narrowWidth_fsc1_3_chevronVisible() {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = 1.3f)
            ) {
                DsTheme(darkTheme = false) {
                    Box(Modifier.width(230.dp)) {
                        DsListRowItem(
                            title = "Transformer Secondary Full Load Current",
                            subtitle = "1500 kVA · 11/0.4 kV",
                            trailingValue = "125000.00 A",
                            icon = Icons.Filled.Home
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val tileRight = with(composeTestRule.density) { 230.dp.toPx() } + 1f
        val trailingBounds = composeTestRule.onNodeWithText("125000.00 A")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "trailing value must stay inside the 230dp row (right=${trailingBounds.right})",
            trailingBounds.right <= tileRight
        )
        composeTestRule.onNodeWithText("Transformer Secondary Full Load Current").assertExists()
        // The trailing affordance must remain reachable.
        composeTestRule.onNodeWithContentDescription("Open").assertExists()
    }
}
