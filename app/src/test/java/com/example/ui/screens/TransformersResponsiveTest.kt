package com.example.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.TransformerEngine
import com.example.ui.EngineeringViewModel
import com.example.ui.UiState
import com.example.ui.theme.VoltAtlasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 4A — TransformersScreen responsive coverage (structure/semantics,
 * no screenshot goldens).
 *
 * Renders the production TransformersScreen with real calculation results from
 * TransformerEngine under narrow (320dp) and medium (700dp) widths, font scales
 * 1.0 and 1.5, and long engineering values.
 *
 * Verifies that:
 * 1. CompactKpiTile unit texts ("kVA", "kW") keep measurable non-zero width
 *    under narrow width + fontScale 1.5 (never pushed off or starved by long values).
 * 2. The Auto-recommend capacity switch remains reachable and displayed when scrolled to.
 * 3. Transformer selector chips and action controls remain accessible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi")
class TransformersNarrowWidthTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: EngineeringViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<Application>()
        viewModel = EngineeringViewModel(context)
        viewModel.preferences.setHasCompletedFirstLaunch(true)
    }

    @After
    fun tearDown() {
        AppDatabase.useTestExecutors = false
        Dispatchers.resetMain()
    }

    @Test
    fun narrowWidth_fsc1_5_kpiTileUnitsRemainVisible() {
        val tx = TransformerEntity(
            transformerId = "TX-01",
            transformerName = "Primary Substation Transformer",
            kVA = 2500.0,
            voltage = 400,
            percentZ = 6.0,
            xr = 12.0,
            status = "Active",
            sizeMode = "Auto",
            txType = "Dry Type (Cast Resin)",
            pf = 0.85
        )
        val circuit = CircuitEntity(
            id = 1,
            projectId = "TEST_PROJ",
            circuitName = "Main Distribution Board Feeder",
            loadKVA = 2000.0,
            transformerId = "TX-01"
        )
        val result = TransformerEngine.calculate(tx, listOf(circuit))

        val state = UiState.Success(
            circuits = listOf(circuit),
            transformers = listOf(tx),
            sizingResults = emptyMap(),
            transformerResults = mapOf("TX-01" to result),
            boq = emptyList(),
            drums = emptyList(),
            procurement = emptyList(),
            panels = emptyList(),
            pfCorrectionResults = emptyList()
        )

        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = 1.5f)
            ) {
                VoltAtlasTheme(darkTheme = false) {
                    TransformersScreen(state = state, viewModel = viewModel)
                }
            }
        }
        composeTestRule.waitForIdle()

        // Verify active transformer selector chip is displayed
        composeTestRule.onNodeWithTag("tx_tab_TX-01").assertIsDisplayed()

        // Verify CompactKpiTile units ("kVA") remain visible and measurable
        val kvaNodes = composeTestRule.onAllNodesWithText("kVA").fetchSemanticsNodes()
        assertTrue("Expected at least one kVA unit node", kvaNodes.isNotEmpty())
        kvaNodes.forEach { node ->
            assertTrue(
                "kVA unit node must keep measurable width, got ${node.boundsInRoot.width}px",
                node.boundsInRoot.width > 0f
            )
        }

        // Verify "Auto-recommend capacity" switch is reachable in the vertical scroll container
        composeTestRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("tx_mode_switch"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tx_mode_switch").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w700dp-h800dp-xhdpi")
class TransformersMediumWidthTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: EngineeringViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<Application>()
        viewModel = EngineeringViewModel(context)
        viewModel.preferences.setHasCompletedFirstLaunch(true)
    }

    @After
    fun tearDown() {
        AppDatabase.useTestExecutors = false
        Dispatchers.resetMain()
    }

    @Test
    fun mediumWidth_fsc1_0_allSectionsRenderCleanly() {
        val tx = TransformerEntity(
            transformerId = "TX-01",
            transformerName = "Primary Substation Transformer",
            kVA = 2000.0,
            voltage = 400,
            percentZ = 6.0,
            xr = 10.0,
            status = "Active",
            sizeMode = "Auto",
            txType = "Dry Type (Cast Resin)",
            pf = 0.85
        )
        val circuit = CircuitEntity(
            id = 1,
            projectId = "TEST_PROJ",
            circuitName = "Main Distribution Board Feeder",
            loadKVA = 1600.0,
            transformerId = "TX-01"
        )
        val result = TransformerEngine.calculate(tx, listOf(circuit))

        val state = UiState.Success(
            circuits = listOf(circuit),
            transformers = listOf(tx),
            sizingResults = emptyMap(),
            transformerResults = mapOf("TX-01" to result),
            boq = emptyList(),
            drums = emptyList(),
            procurement = emptyList(),
            panels = emptyList(),
            pfCorrectionResults = emptyList()
        )

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                TransformersScreen(state = state, viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Electrical Ratings").assertIsDisplayed()
        composeTestRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("tx_mode_switch"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tx_mode_switch").assertIsDisplayed()
    }
}
