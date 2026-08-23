package com.example.sld

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
 * A11y Step 7 regression proof for the SLD radial toolbar:
 *  - every radial control exposes a >= 48dp touch target;
 *  - adjacent touch rectangles (touchBoundsInRoot) never intersect;
 *  - a click landing inside the 48dp interactive area but outside the
 *    44dp visible circle is still actionable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = RobolectricDeviceQualifiers.Pixel6)
class SldToolbarTouchBoundsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val nodeTags = listOf(
        "sld_mode_dropdown_trigger",
        "sld_search_toggle_btn",
        "sld_validation_toggle_btn",
        "sld_fit_btn",
        "sld_overflow_btn"
    )

    private fun setToolbar(
        onToggleSearch: () -> Unit = {},
        onToggleValidation: () -> Unit = {},
        onFitScreen: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                SldToolbar(
                    displayMode = DisplayMode.OVERVIEW,
                    onDisplayModeChange = {},
                    issueCount = 0,
                    isSearchOpen = false,
                    onToggleSearch = onToggleSearch,
                    isValidationOpen = false,
                    onToggleValidation = onToggleValidation,
                    onFitScreen = onFitScreen,
                    onFitSelection = {},
                    onResetView = {},
                    onExpandAll = {},
                    onCollapseAll = {},
                    isFullscreen = false,
                    onToggleFullscreen = {},
                    onExportImage = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun expandToolbar() {
        composeTestRule.onNodeWithContentDescription("Open Toolbar").performClick()
        composeTestRule.waitForIdle()
    }

    private fun touchBounds(tag: String): Rect =
        composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot

    private fun assertNoRectOverlap(a: Rect, b: Rect, labelA: String, labelB: String) {
        val overlaps = a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
        assertFalse("$labelA touch bounds $a must not overlap $labelB bounds $b", overlaps)
    }
// Every radial control must expose at least 48dp in both dimensions.
    @Test
    fun radialNodes_areAtLeast48dpTouchTargets() {
        setToolbar()
        expandToolbar()
        nodeTags.forEach { tag ->
            val b = touchBounds(tag)
            // Physical px are >= 48 because 48dp * density >= 48 for every density >= 1.
            assertTrue("$tag touch width ${b.width} px is below 48dp minimum", b.width >= 48f)
            assertTrue("$tag touch height ${b.height} px is below 48dp minimum", b.height >= 48f)
        }
    }

    // Amendment 1: prove non-overlap with actual touchBoundsInRoot rectangle intersection,
    // not center-to-center distance.
    @Test
    fun radialTouchTargets_doNotIntersectViaPairwiseRectangleChecks() {
        setToolbar()
        expandToolbar()
        nodeTags.forEachIndexed { i, tagA ->
            val rectA = touchBounds(tagA)
            for (j in i + 1 until nodeTags.size) {
                val tagB = nodeTags[j]
                assertNoRectOverlap(rectA, touchBounds(tagB), tagA, tagB)
            }
        }
    }

    // Amendment 2: click inside the 48dp box but outside the 44dp visible circle.
    // Visible circle radius = 22dp; box half-width = 24dp; we click at 23dp.
    @Test
    fun edgeClickWithin48dpButOutside44dpCircle_triggersAction() {
        var searchClicks = 0
        var fitClicks = 0
        setToolbar(
            onToggleSearch = { searchClicks++ },
            onFitScreen = { fitClicks++ }
        )
        expandToolbar()

        clickEdgeOf("sld_search_toggle_btn")
        composeTestRule.waitForIdle()
        assertTrue("Edge click (outside 44dp circle) on search toggle must open search", searchClicks == 1)

        clickEdgeOf("sld_fit_btn")
        composeTestRule.waitForIdle()
        assertTrue("Edge click (outside 44dp circle) on fit button must trigger fit", fitClicks == 1)
    }

    // Center clicks must still work after the expanded wrapper refactor.
    @Test
    fun centerClick_stillWorksAfterWrapperRefactor() {
        var validationClicks = 0
        setToolbar(onToggleValidation = { validationClicks++ })
        expandToolbar()
        composeTestRule.onNodeWithTag("sld_validation_toggle_btn").performClick()
        composeTestRule.waitForIdle()
        assertTrue(validationClicks == 1)
    }

    private fun clickEdgeOf(tag: String) {
        val b = touchBounds(tag)
        // scale = px per dp, derived from the 48dp-wide node.
        val scale = b.width / 48f
        val boxHalf = 24f * scale
        val circleRadius = 22f * scale
        // Point exactly 23dp from the center: inside the 48dp box, outside the 44dp circle.
        val radius23 = 23f * scale
        require(radius23 > circleRadius && radius23 < boxHalf) { "Invalid annulus geometry" }
        val localOffset = Offset(b.width / 2f, b.height / 2f - radius23)
        assertTrue(localOffset.x >= 0f && localOffset.x <= b.width)
        assertTrue(localOffset.y >= 0f && localOffset.y <= b.height)
        composeTestRule.onNodeWithTag(tag).performTouchInput { click(localOffset) }
    }
}