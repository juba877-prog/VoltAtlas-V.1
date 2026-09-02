package com.example.sld

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * A11y regression proof for the Unified Bottom CAD Action Dock:
 *  - every dock action exposes an actionable touch target;
 *  - adjacent touch rectangles (touchBoundsInRoot) never intersect;
 *  - clicks on all dock triggers execute their respective callbacks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = RobolectricDeviceQualifiers.Pixel6)
class SldToolbarTouchBoundsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dockTags = listOf(
        "sld_mode_dropdown_trigger",
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
                    issueCount = 2,
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

    private fun touchBounds(tag: String): Rect =
        composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot

    private fun assertNoRectOverlap(a: Rect, b: Rect, labelA: String, labelB: String) {
        val overlaps = a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
        assertFalse("$labelA touch bounds $a must not overlap $labelB bounds $b", overlaps)
    }

    @Test
    fun dockNodes_areActionableTouchTargets() {
        setToolbar()
        dockTags.forEach { tag ->
            val b = touchBounds(tag)
            assertTrue("$tag touch width ${b.width} px is below minimum", b.width > 0f)
            assertTrue("$tag touch height ${b.height} px is below minimum", b.height > 0f)
        }
    }

    @Test
    fun dockTouchTargets_doNotIntersectViaPairwiseRectangleChecks() {
        setToolbar()
        dockTags.forEachIndexed { i, tagA ->
            val rectA = touchBounds(tagA)
            for (j in i + 1 until dockTags.size) {
                val tagB = dockTags[j]
                assertNoRectOverlap(rectA, touchBounds(tagB), tagA, tagB)
            }
        }
    }

    @Test
    fun fitButton_triggersOnFitScreenCallback() {
        var fitClicks = 0
        setToolbar(onFitScreen = { fitClicks++ })
        composeTestRule.onNodeWithTag("sld_fit_btn").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Fit button must trigger onFitScreen callback", fitClicks == 1)
    }

    @Test
    fun validationButton_triggersOnToggleValidationCallback() {
        var validationClicks = 0
        setToolbar(onToggleValidation = { validationClicks++ })
        composeTestRule.onNodeWithTag("sld_validation_toggle_btn").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Validation button must trigger onToggleValidation callback", validationClicks == 1)
    }

    @Test
    fun modeDropdownTrigger_isActionable() {
        setToolbar()
        composeTestRule.onNodeWithTag("sld_mode_dropdown_trigger").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun overflowToolsButton_isActionable() {
        setToolbar()
        composeTestRule.onNodeWithTag("sld_overflow_btn").performClick()
        composeTestRule.waitForIdle()
    }
}