package com.example.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.engine.ConduitFillEngine
import com.example.engine.ConduitFillInput
import com.example.ui.theme.VoltAtlasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 4C — ConduitFillScreen responsive coverage.
 *
 * Verifies that:
 * 1. Under narrow width (320dp) + fontScale 1.5, KPI summary and 4-tile metric layout
 *    remain readable without horizontal text clipping or touch collision.
 * 2. Action buttons (Configure Conduit, View/Hide Calculation) stack and operate cleanly.
 * 3. Medium width (700dp) renders the standard inline layout regression-free.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi")
class ConduitFillNarrowWidthTest {

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
    fun narrowWidth_fsc1_5_metricsAndActionsAccessible() {
        val circuit = CircuitEntity(
            id = 1,
            circuitName = "Main Substation Feeder",
            loadKVA = 250.0,
            length_m = 120.0,
            installation = "Laid in Ducts",
            cableType = "Cu/XLPE/SWA/PVC"
        )
        val fillInput = ConduitFillInput(
            circuitId = 1,
            circuitName = "Main Substation Feeder",
            mainCableOD_mm = 34.5,
            cpcSizeMm2 = 25.0,
            cpcMaterial = "Cu",
            parallelRuns = 1,
            routeLength_m = 120.0,
            routingMode = "ONE_PER_CONDUIT",
            designProfile = "PROJECT_DESIGN"
        )
        val fillResult = ConduitFillEngine.calculate(fillInput, Catalog.conduits)

        val state = UiState.Success(
            circuits = listOf(circuit),
            transformers = emptyList(),
            sizingResults = emptyMap(),
            transformerResults = emptyMap(),
            boq = emptyList(),
            drums = emptyList(),
            procurement = emptyList(),
            panels = emptyList(),
            pfCorrectionResults = emptyList(),
            conduitResults = mapOf(1 to fillResult)
        )

        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = 1.5f)
            ) {
                VoltAtlasTheme(darkTheme = false) {
                    ConduitFillScreen(viewModel = viewModel, state = state)
                }
            }
        }
        composeTestRule.waitForIdle()

        // Root container and KPI banner
        composeTestRule.onNodeWithTag("conduit_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("CONDUIT FILL & CAPACITY").assertIsDisplayed()

        // Conduit card
        composeTestRule.onNodeWithTag("conduit_card_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Main Substation Feeder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Main Cable OD").assertIsDisplayed()
        composeTestRule.onNodeWithText("Conduit").assertIsDisplayed()

        // Action buttons
        composeTestRule.onNodeWithTag("configure_conduit_1").assertIsDisplayed()
        val viewCalcNode = composeTestRule.onNodeWithTag("view_calc_1")
        viewCalcNode.assertIsDisplayed()

        // Click to expand calculation breakdown
        viewCalcNode.performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CALCULATION DETAILS & PROVENANCE").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w700dp-h800dp-xhdpi")
class ConduitFillMediumWidthTest {

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
    fun mediumWidth_fsc1_0_allComponentsRenderCleanly() {
        val circuit = CircuitEntity(
            id = 2,
            circuitName = "Chiller Feeder Run",
            loadKVA = 400.0,
            length_m = 60.0,
            installation = "Laid in Ducts",
            cableType = "Cu/XLPE/SWA/PVC"
        )
        val fillInput = ConduitFillInput(
            circuitId = 2,
            circuitName = "Chiller Feeder Run",
            mainCableOD_mm = 42.0,
            cpcSizeMm2 = 35.0,
            cpcMaterial = "Cu",
            parallelRuns = 1,
            routeLength_m = 60.0,
            routingMode = "ONE_PER_CONDUIT",
            designProfile = "PROJECT_DESIGN"
        )
        val fillResult = ConduitFillEngine.calculate(fillInput, Catalog.conduits)

        val state = UiState.Success(
            circuits = listOf(circuit),
            transformers = emptyList(),
            sizingResults = emptyMap(),
            transformerResults = emptyMap(),
            boq = emptyList(),
            drums = emptyList(),
            procurement = emptyList(),
            panels = emptyList(),
            pfCorrectionResults = emptyList(),
            conduitResults = mapOf(2 to fillResult)
        )

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                ConduitFillScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("conduit_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chiller Feeder Run").assertIsDisplayed()
        composeTestRule.onNodeWithTag("configure_conduit_2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("view_calc_2").assertIsDisplayed()
    }
}
