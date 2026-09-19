package com.example.ui.screens.quick

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.ui.theme.VoltAtlasTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Roborazzi screenshot regression suite for Quick Motor Starter & Overload screen.
 * Covers:
 * 1. Default Direct-On-Line (DOL 6.5x) configuration in portrait dark mode.
 * 2. Star-Delta (Y-Δ 2.2x) starter configuration with 3-contactor breakdown (KM1/KM2/KM3).
 * 3. Variable Frequency Drive (VFD 1.15x) starter configuration.
 * 4. Landscape responsiveness.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickMotorStarterScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val outputDir = File("build/outputs/roborazzi").apply { mkdirs() }

    @Test
    @Config(qualifiers = "w380dp-h850dp-xhdpi")
    fun testMotorStarter_DOL_portrait_dark() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                QuickMotorStarterScreen(onBack = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "MotorStarter_DOL_Portrait_Dark.png").path)
    }

    @Test
    @Config(qualifiers = "w380dp-h850dp-xhdpi")
    fun testMotorStarter_StarDelta_portrait_dark() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                QuickMotorStarterScreen(onBack = {})
            }
        }
        composeTestRule.waitForIdle()

        // Select Star-Delta starting method
        composeTestRule.onNodeWithText("Y-Δ (2.2×)").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().captureRoboImage(File(outputDir, "MotorStarter_StarDelta_Portrait_Dark.png").path)
    }

    @Test
    @Config(qualifiers = "w380dp-h850dp-xhdpi")
    fun testMotorStarter_VFD_portrait_dark() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                QuickMotorStarterScreen(onBack = {})
            }
        }
        composeTestRule.waitForIdle()

        // Select VFD starting method
        composeTestRule.onNodeWithText("VFD (1.15×)").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().captureRoboImage(File(outputDir, "MotorStarter_VFD_Portrait_Dark.png").path)
    }

    @Test
    @Config(qualifiers = "w820dp-h400dp-xhdpi")
    fun testMotorStarter_DOL_landscape_dark() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                QuickMotorStarterScreen(onBack = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "MotorStarter_DOL_Landscape_Dark.png").path)
    }

    @Test
    @Config(qualifiers = "w380dp-h850dp-xhdpi")
    fun testMotorStarter_scanNameplateButton_opensScannerSheet() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                QuickMotorStarterScreen(onBack = {})
            }
        }
        composeTestRule.waitForIdle()

        // Verify Scan button is displayed and click it
        composeTestRule.onNodeWithTag("motor_starter_scan_camera_btn").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Verify that the Nameplate Scanner Sheet is displayed
        composeTestRule.onNodeWithTag("nameplate_scanner_sheet").assertIsDisplayed()
    }
}
