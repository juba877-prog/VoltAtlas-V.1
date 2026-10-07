package com.example.sld

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.data.AppDatabase
import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.AtsResult
import com.example.engine.GeneratorResult
import com.example.engine.TransformerResult
import com.example.ui.theme.VoltAtlasTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A11y regression proof for the Zero-Overlap Modern CAD Dock & Top-Right Radar Minimap:
 *  - Unified bottom CAD dock controls fit inside compact phone portrait, phone landscape, and wide screens.
 *  - Top bar and bottom dock controls never clip at screen edges nor overlap one another.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = RobolectricDeviceQualifiers.Pixel6)
class SldZoomControlsA11yTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val transformers = listOf(
        TransformerEntity(projectId = "proj_1", transformerId = "TX-01", kVA = 1000.0, voltage = 400)
    )
    private val transformerResults = mapOf(
        "TX-01" to TransformerResult(
            connectedLoad = 800.0,
            circuitsAssigned = 5,
            realPower = 800.0,
            demandLoad = 600.0,
            diversifiedLoad = 600.0,
            futureLoad = 0.0,
            designLoad = 600.0,
            requiredKVA = 600.0,
            recommendedSize = 1000.0,
            selectedSize = 1000.0,
            loadingPct = 60.0,
            flc = 1443.37,
            zt = 5.0,
            rt = 0.0,
            xt = 0.0,
            isc3Phase = 28.87,
            isc1Phase = 0.0,
            status = "PASS",
            z0 = 0.0,
            vectorGroup = "Dyn11"
        )
    )
    private val atsEntity = AtsEntity(projectId = "proj_1", systemVoltage = 400)
    private val atsResult = AtsResult(finalRating = 1600.0, governingCurrent = 1443.0, status = "PASS")
    private val genResult = GeneratorResult(finalSize = 1000.0, loadingPct = 50.0, status = "PASS")
    private val circuits = listOf(
        CircuitEntity(id = 1, projectId = "proj_1", circuitName = "MDB-01", fedFrom = "Transformer", atsIncluded = true, generatorIncluded = true),
        CircuitEntity(id = 2, projectId = "proj_1", circuitName = "SMDB-01", fedFrom = "MDB-01")
    )

    private fun setSldScreen() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                com.example.ui.SldScreen(
                    circuits = circuits,
                    // P1-11 (E-09): align with the production caller contract — MainLayout
                    // always passes an AppState-backed activeProjectId; the screen no longer
                    // guesses from the first entity row when it is blank.
                    activeProjectId = "proj_1",
                    transformers = transformers,
                    generators = listOf(com.example.data.GeneratorEntity(projectId = "proj_1", generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = genResult.finalSize)),
                    generatorResults = mapOf("GEN-01" to genResult),
                    atsList = listOf(atsEntity),
                    atsResults = mapOf(atsEntity.atsId to atsResult),
                    transformerResults = transformerResults
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @After
    fun tearDown() {
        // KT-12 EXPERIMENT (documented in docs/audit/KT-12_GATE_ACCEPTANCE.md): SldScreen captures
        // AppDatabase.getDatabase(context) inside remember { } (SldScreen.kt:96). Whether the reset
        // belongs in @After (close+null, invalidating a still-composed screen) or @Before (null only,
        // before any screen is built) is decided by measurement, not intuition — see that file.
        AppDatabase.resetInstanceForTesting()
    }

    private fun touchBoundsByTag(tag: String): Rect =
        composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot

    private val dockTags = listOf(
        "sld_mode_dropdown_trigger",
        "sld_validation_toggle_btn",
        "sld_fit_btn",
        "sld_overflow_btn"
    )

    private fun assertCadDockFitsInsideAndNoClipping() {
        val rootSize = composeTestRule.onRoot().fetchSemanticsNode().size
        val rootRect = Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())

        dockTags.forEach { tag ->
            val b = touchBoundsByTag(tag)
            assertTrue("$tag left ${b.left} must be >= 0 (no left clipping)", b.left >= 0f)
            assertTrue("$tag top ${b.top} must be >= 0 (no top clipping)", b.top >= 0f)
            assertTrue("$tag right ${b.right} must be <= root width ${rootRect.width} (no right clipping)", b.right <= rootRect.width)
            assertTrue("$tag bottom ${b.bottom} must be <= root height ${rootRect.height} (no bottom clipping)", b.bottom <= rootRect.height)
        }
    }

    @Test
    fun dockControls_areActionableAndExposeTouchBounds() {
        setSldScreen()
        dockTags.forEach { tag ->
            val b = touchBoundsByTag(tag)
            assertTrue("$tag touch width ${b.width}px is zero", b.width > 0f)
            assertTrue("$tag touch height ${b.height}px is zero", b.height > 0f)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun cadDock_fitsPhonePortrait_noClipping() {
        setSldScreen()
        assertCadDockFitsInsideAndNoClipping()
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-xhdpi")
    fun cadDock_fitsPhoneLandscape_noClipping() {
        setSldScreen()
        assertCadDockFitsInsideAndNoClipping()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun cadDock_fitsWideScreen_noClipping() {
        setSldScreen()
        assertCadDockFitsInsideAndNoClipping()
    }
}
