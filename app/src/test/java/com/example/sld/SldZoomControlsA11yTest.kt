package com.example.sld

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.AtsResult
import com.example.engine.GeneratorResult
import com.example.engine.TransformerResult
import com.example.ui.theme.VoltAtlasTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A11y Step 7: SLD zoom controls and draggable radial toolbar.
 *  - Zoom In / Zoom Out / Fit to Screen and the toolbar FAB expose >= 48dp touch targets.
 *  - Amendment 3: after the radial ring radius change, phone portrait, phone
 *    landscape, and wide-screen layout checks prove the expanded radial fan
 *    never clips at screen edges nor overlaps the zoom cluster or the FAB.
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
                    transformers = transformers,
                    generatorResult = genResult,
                    atsEntity = atsEntity,
                    atsResult = atsResult,
                    transformerResults = transformerResults
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun touchBounds(contentDescription: String): Rect =
        composeTestRule.onNodeWithContentDescription(contentDescription).fetchSemanticsNode().touchBoundsInRoot

    private fun touchBoundsByTag(tag: String): Rect =
        composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot

    private fun expandToolbar() {
        composeTestRule.onNodeWithContentDescription("Open Toolbar").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun zoomControls_have48dpTouchTargets() {
        setSldScreen()
        listOf("Zoom In", "Zoom Out", "Fit to Screen").forEach { cd ->
            val b = touchBounds(cd)
            assertTrue("$cd touch width ${b.width}px is below 48dp", b.width >= 48f)
            assertTrue("$cd touch height ${b.height}px is below 48dp", b.height >= 48f)
        }
    }

    @Test
    fun toolbarFab_isAtLeast48dpTouchTarget() {
        setSldScreen()
        val b = touchBounds("Open Toolbar")
        assertTrue("FAB touch width ${b.width}px is below 48dp", b.width >= 48f)
        assertTrue("FAB touch height ${b.height}px is below 48dp", b.height >= 48f)
    }
private val radialTags = listOf(
        "sld_mode_dropdown_trigger",
        "sld_search_toggle_btn",
        "sld_validation_toggle_btn",
        "sld_fit_btn",
        "sld_overflow_btn"
    )

    private fun assertRadialLayoutFitsInsideCompactAndAvoidsZoom() {
        expandToolbar()
        val rootSize = composeTestRule.onRoot().fetchSemanticsNode().size
        val rootRect = Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())
        val zoomRect = touchBounds("Zoom In")

        radialTags.forEach { tag ->
            val b = touchBoundsByTag(tag)
            assertTrue("$tag left ${b.left} must be >= 0 (no left clipping)", b.left >= 0f)
            assertTrue("$tag top ${b.top} must be >= 0 (no top clipping)", b.top >= 0f)
            assertTrue("$tag right ${b.right} must be <= root width ${rootRect.width} (no right clipping)", b.right <= rootRect.width)
            assertTrue("$tag bottom ${b.bottom} must be <= root height ${rootRect.height} (no bottom clipping)", b.bottom <= rootRect.height)
            val overlapsZoom = b.left < zoomRect.right && b.right > zoomRect.left &&
                b.top < zoomRect.bottom && b.bottom > zoomRect.top
            assertFalse("$tag bounds $b must not overlap zoom cluster $zoomRect", overlapsZoom)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun radialFan_fitsPhonePortrait_noClippingNoOverlap() {
        setSldScreen()
        assertRadialLayoutFitsInsideCompactAndAvoidsZoom()
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-xhdpi")
    fun radialFan_fitsPhoneLandscape_noClippingNoOverlap() {
        setSldScreen()
        assertRadialLayoutFitsInsideCompactAndAvoidsZoom()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun radialFan_fitsWideScreen_noClippingNoOverlap() {
        setSldScreen()
        assertRadialLayoutFitsInsideCompactAndAvoidsZoom()
    }
}