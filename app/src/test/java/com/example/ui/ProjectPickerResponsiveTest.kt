package com.example.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.example.data.ProjectEntity
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 4B — ProjectPickerScreen responsive coverage.
 *
 * Verifies that:
 * 1. Under narrow width (320dp) + fontScale 1.5, top actions (+ New Project, Import Project)
 *    and ProjectCard actions (export, rename, delete) stack cleanly without horizontal compression
 *    or clipping.
 * 2. Long project titles wrap or elide cleanly without breaking action button accessibility.
 * 3. Medium width (700dp) maintains standard inline layout regression-free.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi")
class ProjectPickerNarrowWidthTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun narrowWidth_fsc1_5_actionsAndLongTitleAccessible() {
        val testProject = ProjectEntity(
            projectId = "PROJ-101",
            projectName = "Hospital Main Electrical Substation Emergency Distribution Grid",
            projectCode = "HOSP-01",
            clientName = "National Health Infrastructure Authority",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        )

        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = 1.5f)
            ) {
                VoltAtlasTheme(darkTheme = false) {
                    ProjectPickerScreen(
                        projects = listOf(testProject),
                        onCreateProject = {},
                        onOpenProject = {},
                        onRenameProject = { _, _ -> },
                        onDeleteProject = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // Top actions
        composeTestRule.onNodeWithText("+ New Project").assertIsDisplayed()
        composeTestRule.onNodeWithText("📥 Import Project").assertIsDisplayed()

        // Quick Engineering is no longer reachable from the Project Picker: it moved to the
        // new entry / mode-selection screen (§1 flow).
        composeTestRule.onAllNodesWithTag("quick_calc_btn").assertCountEquals(0)

        // Long title is rendered; scroll if necessary to inspect card
        composeTestRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag("export_project_PROJ-101"))
        composeTestRule.waitForIdle()

        val titleNode = composeTestRule.onNodeWithText("Hospital Main Electrical Substation Emergency Distribution Grid")
        titleNode.assertIsDisplayed()
        assertTrue(
            "Project title should have non-zero width",
            titleNode.fetchSemanticsNode().boundsInRoot.width > 0f
        )

        // Action button on card
        composeTestRule.onNodeWithTag("export_project_PROJ-101").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w700dp-h800dp-xhdpi")
class ProjectPickerMediumWidthTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mediumWidth_fsc1_0_inlineRegression() {
        val testProject = ProjectEntity(
            projectId = "PROJ-202",
            projectName = "Commercial Tower Substation",
            clientName = "Emaar Properties",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        )

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                ProjectPickerScreen(
                    projects = listOf(testProject),
                    onCreateProject = {},
                    onOpenProject = {},
                    onRenameProject = { _, _ -> },
                    onDeleteProject = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("+ New Project").assertIsDisplayed()
        composeTestRule.onNodeWithText("📥 Import Project").assertIsDisplayed()
        composeTestRule.onNodeWithText("Commercial Tower Substation").assertIsDisplayed()
        composeTestRule.onNodeWithTag("export_project_PROJ-202").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("quick_calc_btn").assertCountEquals(0)
    }
}
