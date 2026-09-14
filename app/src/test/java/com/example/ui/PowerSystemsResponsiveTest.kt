package com.example.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import com.example.engine.GeneratorEngine
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 3B — PowerSystemsScreen responsive coverage (structure/semantics,
 * no screenshot goldens).
 *
 * GeneratorSettingsPanel renders its hero KPI grid through the file-local
 * CompactKpiTile (value + unit). Under the mandatory combined stress
 * (narrow 320dp window + fontScale 1.5 + long engineering value) the tile's
 * unit must remain visible — it is measured first by the width-allocation
 * fix — and the value must remain reachable as semantics.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi")
class PowerSystemsResponsiveTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderGeneratorPanel(fontScale: Float) {
        val generator = GeneratorEntity(
            generatorId = "GEN-01",
            generatorName = "Emergency Generator"
        )
        val circuit = CircuitEntity(
            circuitName = "Panel Distribution Board",
            loadKVA = 2400.0,
            assignedGeneratorId = "GEN-01",
            fedFrom = "Transformer"
        )
        val result = GeneratorEngine.calculate(generator, listOf(circuit), totalConnectedLoad = 2400.0)

        val state = UiState.Success(
            circuits = listOf(circuit),
            transformers = emptyList(),
            sizingResults = emptyMap(),
            transformerResults = emptyMap(),
            boq = emptyList(),
            drums = emptyList(),
            procurement = emptyList(),
            panels = emptyList(),
            generatorResults = mapOf("GEN-01" to result),
            generators = listOf(generator),
            pfCorrectionResults = emptyList()
        )

        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale)
            ) {
                VoltAtlasTheme(darkTheme = false) {
                    GeneratorSettingsPanel(
                        state = state,
                        singleSettings = generator,
                        onSave = {},
                        onAddGenerator = {},
                        onDeleteGenerator = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun narrowWidth_fsc1_5_kpiTileUnitsRemainVisible() {
        renderGeneratorPanel(fontScale = 1.5f)

        // The essential-load engineering value is still in the semantics
        // (it legitimately appears in the KPI tile and connected-load chips).
        assertTrue(
            composeTestRule.onAllNodesWithText("2400.00")
                .fetchSemanticsNodes().isNotEmpty()
        )

        // Exactly the tile unit texts match "kVA" (chips/labels carry extra
        // text) — at least two tiles are visible and every unit keeps a
        // non-zero measured width (never starved/clipped by the value).
        val unitNodes = composeTestRule.onAllNodesWithText("kVA")
            .fetchSemanticsNodes()
        assertTrue("expected >= 2 visible kVA units, found ${unitNodes.size}", unitNodes.size >= 2)
        unitNodes.forEach { node ->
            val width = node.boundsInRoot.width
            assertTrue("kVA unit must keep measurable width, got $width", width > 0f)
        }
    }

    @Test
    fun narrowWidth_fsc1_0_kpiTilesRenderNormally() {
        renderGeneratorPanel(fontScale = 1.0f)

        assertTrue(
            composeTestRule.onAllNodesWithText("2400.00")
                .fetchSemanticsNodes().isNotEmpty()
        )
        val unitNodes = composeTestRule.onAllNodesWithText("kVA")
            .fetchSemanticsNodes()
        assertTrue(unitNodes.size >= 2)
        unitNodes.forEach { node ->
            assertTrue(node.boundsInRoot.width > 0f)
        }
    }
}