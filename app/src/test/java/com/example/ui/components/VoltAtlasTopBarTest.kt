package com.example.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Component contract tests for VoltAtlasTopBar under compose-component-design.
 *
 * Verifies:
 * 1. Structured overload renders title, subtitle, back navigation, and actions.
 * 2. Slot overload renders custom composable title and custom navigation slot.
 * 3. Root modifier is caller-placeable and applied to the root layout.
 * 4. Back button click invokes the supplied onBack callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class VoltAtlasTopBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun structuredOverload_rendersTitleSubtitleAndBackAction() {
        var backClicked = false
        composeTestRule.setContent {
            VoltAtlasTheme {
                VoltAtlasTopBar(
                    title = "Voltage Drop Calculator",
                    subtitle = "IEC 60364-5-52 Standard",
                    onBack = { backClicked = true },
                    backButtonTestTag = "custom_back_btn",
                    actions = {
                        Text(text = "Action", modifier = Modifier.testTag("action_text"))
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Voltage Drop Calculator").assertIsDisplayed()
        composeTestRule.onNodeWithText("IEC 60364-5-52 Standard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("action_text").assertIsDisplayed()

        val backNode = composeTestRule.onNodeWithTag("custom_back_btn")
        backNode.assertExists()
        backNode.assertHasClickAction()
        backNode.performClick()
        assertTrue("onBack must be invoked upon clicking back button", backClicked)
    }

    @Test
    fun slotOverload_rendersCustomTitleContentAndNavigationSlot() {
        composeTestRule.setContent {
            VoltAtlasTheme {
                VoltAtlasTopBar(
                    modifier = Modifier.testTag("root_top_bar"),
                    titleContent = {
                        Text(text = "Custom Slot Title", modifier = Modifier.testTag("slot_title"))
                    },
                    navigationIcon = {
                        Text(text = "Nav Slot", modifier = Modifier.testTag("nav_slot"))
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("root_top_bar").assertExists()
        composeTestRule.onNodeWithTag("slot_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_slot").assertIsDisplayed()
    }
}
