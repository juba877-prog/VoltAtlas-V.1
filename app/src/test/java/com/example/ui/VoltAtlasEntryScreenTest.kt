package com.example.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * VoltAtlas entry / mode-selection screen verification (§28).
 *
 * Covers: exactly two destinations + their navigation callbacks, no duplicated Project Picker
 * or Quick Engineering content, click semantics and >= 48dp touch targets, branding,
 * security note, and the static-grid + animated-electrical background layers.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h844dp-xhdpi")
class VoltAtlasEntryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var projectsClicks = 0
    private var quickClicks = 0

    private fun setEntryContent() {
        projectsClicks = 0
        quickClicks = 0
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                VoltAtlasEntryScreen(
                    onOpenProjects = { projectsClicks++ },
                    onOpenQuickEngineering = { quickClicks++ },
                    onOpenSettings = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // --- §28 Navigation -------------------------------------------------------------

    @Test
    fun entryScreen_rendersExactlyTheTwoModeDestinations() {
        setEntryContent()

        composeTestRule.onNodeWithTag("entry_projects_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("entry_quick_card").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("entry_projects_card").assertCountEquals(1)
        composeTestRule.onAllNodesWithTag("entry_quick_card").assertCountEquals(1)

        // No Project Picker content is duplicated onto this surface.
        composeTestRule.onNodeWithText("+ New Project").assertDoesNotExist()
        composeTestRule.onNodeWithText("Import Project").assertDoesNotExist()
        composeTestRule.onNodeWithText("Recent Projects", substring = true).assertDoesNotExist()
    }

    @Test
    fun projectsCard_navigatesToProjectPicker() {
        setEntryContent()

        composeTestRule.onNodeWithTag("entry_projects_card").performClick()

        assertEquals(1, projectsClicks)
        assertEquals(0, quickClicks)
    }

    @Test
    fun quickEngineeringCard_navigatesToQuickEngineering() {
        setEntryContent()

        composeTestRule.onNodeWithTag("entry_quick_card").performClick()

        assertEquals(1, quickClicks)
        assertEquals(0, projectsClicks)
    }

    // --- §28 Accessibility ----------------------------------------------------------

    @Test
    fun cardsExposeClickSemantics_andLargeTouchTargets() {
        setEntryContent()

        composeTestRule.onNodeWithText("Projects").assertHasClickAction()
        composeTestRule.onNodeWithText("Quick Engineering").assertHasClickAction()

        val density = composeTestRule.density.density
        listOf("entry_projects_card", "entry_quick_card").forEach { tag ->
            val bounds = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
            assertTrue(
                "$tag touch height is below 48dp",
                bounds.height >= 48f * density
            )
            assertTrue(
                "$tag touch width is below 48dp",
                bounds.width >= 48f * density
            )
        }
    }

    @Test
    fun branding_subtitle_settingsAndSecurityNote_render() {
        setEntryContent()

        composeTestRule.onNodeWithTag("entry_logo").assertIsDisplayed()
        composeTestRule.onNodeWithText("VoltAtlas").assertIsDisplayed()
        composeTestRule.onNodeWithText("Electrical Engineering Platform").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("entry_security_note").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("All calculations are performed locally and securely")
            .assertIsDisplayed()
    }

    // --- §28 Background layers -------------------------------------------------------

    @Test
    fun backgroundLayers_staticGridAndElectricalLayerAreBothMounted() {
        setEntryContent()

        // Both layers are plain Compose Canvases: the static engineering grid and the animated
        // electrical layer (traces + energy pulses + node wake-up). Neither is an AndroidView,
        // so the background can never intercept touches meant for the cards.
        composeTestRule.onNodeWithTag("entry_static_grid").assertExists()
        composeTestRule.onNodeWithTag("entry_circuit_animation").assertExists()
    }

    // --- Cards are not color-only -----------------------------------------------------

    @Test
    fun cardsCarryDescriptiveText_beyondColorAlone() {
        setEntryContent()

        composeTestRule
            .onNodeWithText("Create, open & manage engineering projects")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Run standalone calculations without creating a project")
            .assertIsDisplayed()
    }
}

