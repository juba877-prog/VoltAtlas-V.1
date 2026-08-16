package com.example.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.sld.SldValidationIssue
import com.example.sld.SldValidationStatus
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.example.ui.screens.DashboardScreen

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun testDashboard_PassState_360dp() {
        val summary = ProjectValidationSummary(
            overallStatus = "100% COMPLIANT / PASS",
            totalFailures = 0,
            totalReviewsRequired = 0,
            totalWarnings = 0,
            openIssues = emptyList()
        )
        val state = UiState.Success(
            circuits = emptyList(),
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
            pfCorrectionResults = emptyList(),
            validationSummary = summary
        )

        composeTestRule.setContent {
            com.example.ui.theme.VoltAtlasTheme {
                DashboardScreen(state = state, onNavigate = {})
            }
        }
        
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/Dashboard_Pass_360dp.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun testDashboard_ReviewState_360dp() {
        val issues = listOf(
            SldValidationIssue(
                issueCode = "ATS_CONNECTION",
                affectedNodeId = "ats_1",
                componentName = "ATS Connection",
                failedCondition = "Emergency output board not connected to ATS",
                severity = SldValidationStatus.DATA_REQUIRED
            ),
            SldValidationIssue(
                issueCode = "TX_LOAD",
                affectedNodeId = "tx_1",
                componentName = "Transformer Loading",
                failedCondition = "T-1 is approaching recommended loading limit",
                severity = SldValidationStatus.WARNING
            )
        )
        val summary = ProjectValidationSummary(
            overallStatus = "REVIEW REQUIRED",
            totalFailures = 0,
            totalReviewsRequired = 1,
            totalWarnings = 1,
            openIssues = issues
        )
        val state = UiState.Success(
            circuits = emptyList(),
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
            pfCorrectionResults = emptyList(),
            validationSummary = summary
        )

        composeTestRule.setContent {
            com.example.ui.theme.VoltAtlasTheme {
                DashboardScreen(state = state, onNavigate = {})
            }
        }
        
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/Dashboard_Review_360dp.png")
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-xhdpi")
    fun testDashboard_PassState_320dp() {
        val summary = ProjectValidationSummary(
            overallStatus = "100% COMPLIANT / PASS",
            totalFailures = 0,
            totalReviewsRequired = 0,
            totalWarnings = 0,
            openIssues = emptyList()
        )
        val state = UiState.Success(
            circuits = emptyList(),
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
            pfCorrectionResults = emptyList(),
            validationSummary = summary
        )
        composeTestRule.setContent {
            com.example.ui.theme.VoltAtlasTheme {
                DashboardScreen(state = state, onNavigate = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/Dashboard_Pass_320dp.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun testDashboard_PassState_411dp() {
        val summary = ProjectValidationSummary(
            overallStatus = "100% COMPLIANT / PASS",
            totalFailures = 0,
            totalReviewsRequired = 0,
            totalWarnings = 0,
            openIssues = emptyList()
        )
        val state = UiState.Success(
            circuits = emptyList(),
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
            pfCorrectionResults = emptyList(),
            validationSummary = summary
        )
        composeTestRule.setContent {
            com.example.ui.theme.VoltAtlasTheme {
                DashboardScreen(state = state, onNavigate = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/Dashboard_Pass_411dp.png")
    }
}
