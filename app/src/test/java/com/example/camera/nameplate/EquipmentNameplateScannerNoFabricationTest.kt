package com.example.camera.nameplate

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Nameplate scanner must never present an invented reading as a scanned one.
 *
 * The scanner used to open with a hardcoded sample (37.0 kW / 400 V / 68.5 A / 0.86 / 92.4)
 * whenever no nameplate had been read, and both "Apply" buttons substituted those same
 * invented numbers for any field the engineer left blank. Those values reached the
 * motor-starter and cable-sizing calculations.
 *
 * This locks the corrected contract:
 *  - with no scan result the fields are EMPTY (no sample reading is shown),
 *  - applying from an empty sheet does NOT invoke the downstream callbacks at all,
 *  - a real scan result still populates the fields.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h844dp-xhdpi")
class EquipmentNameplateScannerNoFabricationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun fieldText(tag: String): String {
        val node = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
        val editable = node.config[SemanticsProperties.EditableText]
        return editable?.text ?: ""
    }

    private fun setSheet(
        scan: NameplateScanResult? = null,
        onMotor: (Double, Double, Double, Double) -> Unit = { _, _, _, _ -> },
        onCable: (Double, Double, Double) -> Unit = { _, _, _ -> }
    ) {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                EquipmentNameplateScannerContent(
                    scanResult = scan,
                    onApplyToMotorStarter = onMotor,
                    onApplyToCableSizing = onCable,
                    onClose = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun noScanResult_allNameplateFieldsStartEmpty_notASampleReading() {
        setSheet(scan = null)

        assertEquals("power field must not show a fabricated value", "", fieldText("scanner_power_input"))
        assertEquals("voltage field must not show a fabricated value", "", fieldText("scanner_voltage_input"))
        assertEquals("current field must not show a fabricated value", "", fieldText("scanner_current_input"))
        assertEquals("pf field must not show a fabricated value", "", fieldText("scanner_pf_input"))
        assertEquals("efficiency field must not show a fabricated value", "", fieldText("scanner_eff_input"))
    }

    @Test
    fun noScanResult_applyToMotorStarter_doesNotInvokeCallbackWithInventedValues() {
        var motorCalls = 0
        setSheet(scan = null, onMotor = { _, _, _, _ -> motorCalls++ })

        composeTestRule.onNodeWithTag("apply_to_motor_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "Apply must not reach the motor starter with invented nameplate values",
            0,
            motorCalls
        )
    }

    @Test
    fun noScanResult_applyToCableSizing_doesNotInvokeCallbackWithInventedValues() {
        var cableCalls = 0
        setSheet(scan = null, onCable = { _, _, _ -> cableCalls++ })

        composeTestRule.onNodeWithTag("apply_to_cable_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "Apply must not reach cable sizing with invented nameplate values",
            0,
            cableCalls
        )
    }

    @Test
    fun scannedResult_stillPopulatesTheFields() {
        setSheet(
            scan = NameplateScanResult(
                equipmentType = NameplateEquipmentType.MOTOR,
                powerKw = 22.0,
                voltageV = 400.0,
                currentAmps = 38.5,
                powerFactor = 0.88,
                efficiencyPct = 94.0
            )
        )

        assertEquals("22.0", fieldText("scanner_power_input"))
        assertEquals("400", fieldText("scanner_voltage_input"))
        assertEquals("38.5", fieldText("scanner_current_input"))
        assertEquals("0.88", fieldText("scanner_pf_input"))
        assertEquals("94.0", fieldText("scanner_eff_input"))
    }
}