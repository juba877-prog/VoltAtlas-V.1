package com.example.ui.dialogs.circuiteditor.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.ui.components.VoltAtlasDropdown
import com.example.ui.dialogs.CircuitEditDialog
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Diagnostic isolation matrix to distinguish among potential root causes of the
 * Robolectric AppNotIdleException encountered during legacy CircuitEditDialog composition.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class CircuitEditDialogDiagnosticIsolationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Case 1: Minimal Material 3 AlertDialog with static Text only
    @Test
    fun case1_alertDialog_staticTextOnly() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = { Button(onClick = {}) { Text("OK") } },
                    text = { Text("Static Text Only") }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // Case 2: Minimal AlertDialog containing one standard text field
    @Test
    fun case2_alertDialog_standardTextField() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                var text by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = { Button(onClick = {}) { Text("OK") } },
                    text = {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Label") }
                        )
                    }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // Case 3: Minimal AlertDialog containing one VoltAtlasDropdown
    @Test
    fun case3_alertDialog_oneVoltAtlasDropdown() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                var selected by remember { mutableStateOf("Option 1") }
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = { Button(onClick = {}) { Text("OK") } },
                    text = {
                        VoltAtlasDropdown(
                            value = selected,
                            options = listOf("Option 1", "Option 2"),
                            onSelect = { selected = it },
                            label = "Dropdown"
                        )
                    }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // Case 4: Minimal non-dialog Surface containing one VoltAtlasDropdown
    @Test
    fun case4_nonDialogSurface_oneVoltAtlasDropdown() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface {
                    var selected by remember { mutableStateOf("Option 1") }
                    VoltAtlasDropdown(
                        value = selected,
                        options = listOf("Option 1", "Option 2"),
                        onSelect = { selected = it },
                        label = "Dropdown"
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    // Case 5: Minimal AlertDialog with scroll container but without imePadding
    @Test
    fun case5_alertDialog_scrollContainer_withoutImePadding() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                var selected by remember { mutableStateOf("Option 1") }
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = { Button(onClick = {}) { Text("OK") } },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            VoltAtlasDropdown(
                                value = selected,
                                options = listOf("Option 1", "Option 2"),
                                onSelect = { selected = it },
                                label = "Dropdown"
                            )
                        }
                    }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // Case 6: Minimal AlertDialog with imePadding but without dropdowns
    @Test
    fun case6_alertDialog_imePadding_withoutDropdowns() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                var text by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = { Button(onClick = {}) { Text("OK") } },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState())
                                .imePadding()
                        ) {
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                label = { Text("Label") }
                            )
                        }
                    }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // Case 7: Real legacy CircuitEditDialog with standard test clock control
    @Test
    fun case7_realCircuitEditDialog_standardClock() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                CircuitEditDialog(
                    circuit = null,
                    existingCircuits = emptyList(),
                    onDismiss = {},
                    onSave = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // Case 8: Minimal always-visible legacy content subset
    @Test
    fun case8_minimalAlwaysVisibleLegacyContent_withoutAccordions() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                var name by remember { mutableStateOf("") }
                var panelType by remember { mutableStateOf("LOAD") }
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = { Button(onClick = {}) { Text("Save") } },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState())
                                .imePadding()
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Name") }
                            )
                            VoltAtlasDropdown(
                                value = panelType,
                                options = listOf("LOAD", "DB", "MDB"),
                                onSelect = { panelType = it },
                                label = "Board Type"
                            )
                        }
                    }
                )
            }
        }
        composeTestRule.waitForIdle()
    }
}
