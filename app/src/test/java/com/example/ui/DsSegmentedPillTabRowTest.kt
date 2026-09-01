package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.CircuitEntity
import com.example.ui.designsystem.components.DsPillTabItem
import com.example.ui.designsystem.components.DsSegmentedPillTabRow
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DsSegmentedPillTabRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun testDsSegmentedPillTabRow_renamedFixture_generalizability() {
        var selected = 0
        val customTabs = listOf(
            DsPillTabItem("Substation Alpha", Icons.Filled.Bolt, "tab_custom_1"),
            DsPillTabItem("Busway Feeder 2", Icons.Filled.Tune, "tab_custom_2"),
            DsPillTabItem("Emergency Core 3", Icons.Filled.Home, "tab_custom_3")
        )

        composeTestRule.setContent {
            VoltAtlasTheme {
                DsSegmentedPillTabRow(
                    tabs = customTabs,
                    selectedTabIndex = selected,
                    onTabSelected = { selected = it }
                )
            }
        }

        // Verify arbitrary names render without hardcoded constraints
        composeTestRule.onNodeWithText("Substation Alpha").assertExists()
        composeTestRule.onNodeWithText("Busway Feeder 2").assertExists()
        composeTestRule.onNodeWithText("Emergency Core 3").assertExists()

        // Verify selection
        composeTestRule.onNodeWithTag("tab_custom_2").performClick()
        assertEquals(1, selected)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun testProtectionScreen_topBar_allTabsExist() {
        val state = UiState.Success(
            circuits = listOf(CircuitEntity(id = 1, circuitName = "DP-G3")),
            transformers = emptyList(),
            sizingResults = emptyMap(),
            transformerResults = emptyMap(),
            boq = emptyList(),
            drums = emptyList(),
            procurement = emptyList(),
            panels = emptyList(),
            generatorResult = null,
            atsResult = null,
            upsResult = null,
            pfCorrectionResults = emptyList()
        )

        composeTestRule.setContent {
            VoltAtlasTheme {
                ProtectionScreen(state = state)
            }
        }

        // Assert all 5 short labels exist
        composeTestRule.onNodeWithTag("tab_protection").assertExists()
        composeTestRule.onNodeWithTag("tab_sc").assertExists()
        composeTestRule.onNodeWithTag("tab_coord").assertExists()
        composeTestRule.onNodeWithTag("tab_efl").assertExists()
        composeTestRule.onNodeWithTag("tab_earthing").assertExists()

        composeTestRule.onNodeWithText("Protection").assertExists()
        composeTestRule.onNodeWithText("SC").assertExists()
        composeTestRule.onNodeWithText("Coord").assertExists()
        composeTestRule.onNodeWithText("EFL").assertExists()
        composeTestRule.onNodeWithText("Earthing").assertExists()
    }
}
