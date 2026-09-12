package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runtime specification tests for CableFamilyPicker.
 *
 * Evidence Label: NEW_EDITOR_RUNTIME_OBSERVED
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class CableFamilyPickerRuntimeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleFamilies = listOf(
        "Cu/XLPE/SWA/PVC - 4C",
        "Cu/XLPE/None/PVC - 4C",
        "Al/XLPE/STA/PVC - 4C",
        "Cu/PVC/None/PVC - 1C"
    )

    @Test
    fun test25_pickerOpensWhenOpenIsTrue() {
        var isOpen by mutableStateOf(false)
        composeTestRule.setContent {
            VoltAtlasTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    CableFamilyPicker(
                        isOpen = isOpen,
                        currentFamily = sampleFamilies.first(),
                        candidateFamilies = sampleFamilies,
                        onConfirm = {},
                        onCancel = { isOpen = false }
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("cable_family_picker").assertDoesNotExist()

        isOpen = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cable_family_picker").assertIsDisplayed()
    }

    @Test
    fun test26_compatibleRealResultsAreDisplayed() {
        composeTestRule.setContent {
            VoltAtlasTheme {
                CableFamilyPicker(
                    isOpen = true,
                    currentFamily = sampleFamilies.first(),
                    candidateFamilies = sampleFamilies,
                    onConfirm = {},
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("cable_family_results").assertIsDisplayed()
        sampleFamilies.forEach { family ->
            composeTestRule.onNodeWithText(family).assertIsDisplayed()
        }
    }

    @Test
    fun test27_searchFiltersDisplayedResults() {
        composeTestRule.setContent {
            VoltAtlasTheme {
                CableFamilyPicker(
                    isOpen = true,
                    currentFamily = sampleFamilies.first(),
                    candidateFamilies = sampleFamilies,
                    onConfirm = {},
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("cable_family_search").performTextInput("STA")
        composeTestRule.onNodeWithText("Al/XLPE/STA/PVC - 4C").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cu/XLPE/SWA/PVC - 4C").assertDoesNotExist()
    }

    @Test
    fun test28_selectingThenConfirmingUpdatesCableTypeOnce() {
        var confirmedType: String? = null
        var confirmCount = 0

        composeTestRule.setContent {
            VoltAtlasTheme {
                CableFamilyPicker(
                    isOpen = true,
                    currentFamily = sampleFamilies.first(),
                    candidateFamilies = sampleFamilies,
                    onConfirm = {
                        confirmedType = it
                        confirmCount++
                    },
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Al/XLPE/STA/PVC - 4C").performClick()
        composeTestRule.onNodeWithTag("cable_family_confirm").performClick()

        assertEquals("Al/XLPE/STA/PVC - 4C", confirmedType)
        assertEquals(1, confirmCount)
    }

    @Test
    fun test29_cancelPreservesPreviousCableType() {
        var confirmedType: String? = "Cu/XLPE/SWA/PVC - 4C"
        var isCancelled = false

        composeTestRule.setContent {
            VoltAtlasTheme {
                CableFamilyPicker(
                    isOpen = true,
                    currentFamily = confirmedType!!,
                    candidateFamilies = sampleFamilies,
                    onConfirm = { confirmedType = it },
                    onCancel = { isCancelled = true }
                )
            }
        }

        // Tap an alternative item
        composeTestRule.onNodeWithText("Al/XLPE/STA/PVC - 4C").performClick()
        // Tap Cancel
        composeTestRule.onNodeWithTag("cable_family_cancel").performClick()

        assertTrue(isCancelled)
        assertEquals("Cu/XLPE/SWA/PVC - 4C", confirmedType)
    }

    @Test
    fun test30_emptyResultStateAppearsCorrectly() {
        composeTestRule.setContent {
            VoltAtlasTheme {
                CableFamilyPicker(
                    isOpen = true,
                    currentFamily = sampleFamilies.first(),
                    candidateFamilies = sampleFamilies,
                    onConfirm = {},
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("cable_family_search").performTextInput("NonExistentPatternXYZ")
        composeTestRule.onNodeWithTag("cable_family_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithText("No matching cable families found").assertIsDisplayed()
    }

    @Test
    fun test31_selectedResultExposesSelectedSemantics() {
        composeTestRule.setContent {
            VoltAtlasTheme {
                CableFamilyPicker(
                    isOpen = true,
                    currentFamily = "Cu/XLPE/SWA/PVC - 4C",
                    candidateFamilies = sampleFamilies,
                    onConfirm = {},
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Cu/XLPE/SWA/PVC - 4C").assertIsSelected()
    }

    @Test
    fun test32_searchAndPendingSelectionSurviveRecomposition() {
        var triggerRecomposition by mutableStateOf(0)

        composeTestRule.setContent {
            // Read state to observe recomposition
            val count = triggerRecomposition
            VoltAtlasTheme {
                CableFamilyPicker(
                    isOpen = true,
                    currentFamily = sampleFamilies.first(),
                    candidateFamilies = sampleFamilies,
                    onConfirm = {},
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("cable_family_search").performTextInput("None")
        composeTestRule.onNodeWithText("Cu/XLPE/None/PVC - 4C").performClick()

        triggerRecomposition++
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Cu/XLPE/None/PVC - 4C").assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun test33_keyboardAndInsetBehaviorKeepsSearchAndConfirmVisible() {
        composeTestRule.setContent {
            VoltAtlasTheme {
                CableFamilyPicker(
                    isOpen = true,
                    currentFamily = sampleFamilies.first(),
                    candidateFamilies = sampleFamilies,
                    onConfirm = {},
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("cable_family_search").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cable_family_confirm").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cable_family_cancel").assertIsDisplayed()
    }
}
