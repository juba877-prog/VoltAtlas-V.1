package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LoadTypeBreakdown
import com.example.data.LoadTypeBreakdownRow
import com.example.engine.PanelScheduleRecursiveResult
import com.example.engine.PanelScheduleRow
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 3A — PanelScheduleScreen responsive coverage (structure/semantics,
 * no screenshot goldens).
 *
 *  1. The load-mismatch sync button carries a dynamic kVA target; at narrow
 *     width + fontScale 1.5 it must WRAP (grow vertically), never clip the
 *     engineering value out of view.
 *  2. The Load Type Breakdown is a genuine 6-column engineering table: below
 *     its content-derived minimum (~370dp) it scrolls horizontally (column
 *     association preserved); at medium width no scroll container appears.
 *  3. Schedule row cards keep the value-first priority: load + edit affordance
 *     remain present beside a long description.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class PanelScheduleResponsiveTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun fsc(scale: Float, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = scale)
            ) {
                VoltAtlasTheme(darkTheme = false) { content() }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun narrowWidth_fsc1_5_mismatchSyncButtonWraps_insteadOfClipping() {
        fsc(1.5f) {
            Box(Modifier.width(320.dp)) {
                LoadMismatchConflictCard(
                    scheduleResult = PanelScheduleRecursiveResult(
                        panelName = "MDB-Main",
                        panelCircuit = null,
                        aggregatedChildrenLoadKVA = 12500.0,
                        panelOwnLoadKVA = 12000.0,
                        hasLoadMismatch = true
                    ),
                    onSyncCircuitLoad = {}
                )
            }
        }

        // The full engineering value stays part of the semantics.
        val syncText = composeTestRule.onNodeWithText("Update Load to 12500.0 kVA")
        syncText.assertExists()
        // Wrapped, not clipped: at 320dp + fsc 1.5 the single line would be
        // wider than the card, so the text node must be at least two lines
        // tall (~2 x 13sp x 1.5). A clipped single line stays ~1 line tall.
        val height = syncText.fetchSemanticsNode().boundsInRoot.height
        val minTwoLines = with(composeTestRule.density) { 20.sp.toPx() * 2 }
        assertTrue("sync label must wrap (height=$height)", height >= minTwoLines)
    }

    @Test
    fun narrowWidth_loadTypeBreakdownTableScrollsHorizontally() {
        fsc(1.0f) {
            Box(Modifier.width(320.dp)) {
                LoadTypeBreakdownCard(
                    breakdown = LoadTypeBreakdown(
                        rows = listOf(
                            LoadTypeBreakdownRow(
                                categoryCode = "LIGHTING",
                                categoryLabel = "Lighting & Power Circuits",
                                circuitCount = 12,
                                connectedKva = 1250.0,
                                demandKva = 1000.0,
                                averagePf = 0.85,
                                effectiveDemandFactor = 0.80
                            )
                        ),
                        totalConnectedKva = 12500.0,
                        totalDemandKva = 10000.0,
                        overallDemandFactor = 0.8
                    )
                )
            }
        }

        // Genuine table -> exactly one horizontal scroll viewport appears.
        assertEquals(1, composeTestRule.onAllNodes(hasScrollAction())
            .fetchSemanticsNodes().size)
        // The last column remains reachable through the scroll container.
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Demand kVA"))
        composeTestRule.onNodeWithText("Demand kVA").assertExists()
    }

    @Test
    fun scheduleRowCard_longDescription_keepsLoadValuePresent() {
        fsc(1.0f) {
            Box(Modifier.width(320.dp)) {
                PanelScheduleRowCard(
                    row = PanelScheduleRow(
                        itemNo = 3,
                        description = "Transformer Secondary Full Load Current",
                        circuitId = 42,
                        loadKVA = 12.5,
                        phases = "3P+N",
                        assignedPhase = "L1"
                    ),
                    onSubPanelClick = {},
                    onEditClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Transformer Secondary Full Load Current").assertExists()
        composeTestRule.onNodeWithText("12.5 kVA").assertExists()
        composeTestRule.onNodeWithText("3").assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w700dp-h800dp-xhdpi")
class PanelScheduleTableMediumTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mediumWidth_loadTypeBreakdownTableDoesNotScroll() {
        composeTestRule.setContent {
            // No width box: the card fills the 700dp window.
            VoltAtlasTheme(darkTheme = false) {
                LoadTypeBreakdownCard(
                    breakdown = LoadTypeBreakdown(
                        rows = listOf(
                            LoadTypeBreakdownRow(
                                categoryCode = "LIGHTING",
                                categoryLabel = "Lighting & Power Circuits",
                                circuitCount = 12,
                                connectedKva = 1250.0,
                                demandKva = 1000.0,
                                averagePf = 0.85,
                                effectiveDemandFactor = 0.80
                            )
                        ),
                        totalConnectedKva = 12500.0,
                        totalDemandKva = 10000.0,
                        overallDemandFactor = 0.8
                    )
                )
            }
        }
        composeTestRule.waitForIdle()

        // At medium width the weighted table fits: no scroll container,
        // all six columns directly visible.
        assertEquals(0, composeTestRule.onAllNodes(hasScrollAction())
            .fetchSemanticsNodes().size)
        composeTestRule.onNodeWithText("Demand kVA").assertExists()
        composeTestRule.onNodeWithText("Lighting & Power Circuits").assertExists()
    }
}
