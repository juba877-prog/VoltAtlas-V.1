package com.example.ui.dialogs.circuiteditor.ui.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.components.VoltAtlasDropdown
import com.example.ui.components.VoltAtlasTextField
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft

/**
 * Step 5: Installation Conditions configuration content.
 *
 * Implements environmental and physical installation inputs matching the legacy
 * CircuitEditDialog specification (lines 773-778, 811-897, 1051-1056, 1093-1101).
 *
 * Responsive layout ensures single-column stacking at compact phone widths (320dp)
 * and large font scales (1.5x) to eliminate clipping or label/input truncation.
 */
@Composable
fun InstallationStepContent(
    draft: CircuitEditorDraft,
    onDraftChange: (CircuitEditorDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    val installationOptions = listOf("Laid in Ducts", "Laid Direct in Ground", "Laid in Free Air")
    val formationOptions = listOf("Flat", "Trefoil")
    val spacingOptions = listOf("Touching", "0.15m", "0.30m")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("circuit_editor_step_installation"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Installation Method
        VoltAtlasDropdown(
            value = draft.installation,
            options = installationOptions,
            onSelect = { onDraftChange(draft.copy(installation = it)) },
            label = "Installation Method",
            modifier = Modifier.testTag("input_installation_method")
        )

        // 2. Air Temp & Ground Temp
        ResponsiveFieldPair(
            firstField = {
                VoltAtlasTextField(
                    value = draft.airTempC,
                    onValueChange = { onDraftChange(draft.copy(airTempC = it)) },
                    label = "Air Temp",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        Text(
                            text = "°C",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    modifier = Modifier.testTag("input_air_temp")
                )
            },
            secondField = {
                VoltAtlasTextField(
                    value = draft.gndTempC,
                    onValueChange = { onDraftChange(draft.copy(gndTempC = it)) },
                    label = "Gnd Temp",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        Text(
                            text = "°C",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    modifier = Modifier.testTag("input_gnd_temp")
                )
            }
        )

        // 3. Burial Depth & Soil Resistivity
        ResponsiveFieldPair(
            firstField = {
                VoltAtlasTextField(
                    value = draft.burialDepth_m,
                    onValueChange = { onDraftChange(draft.copy(burialDepth_m = it)) },
                    label = "Burial Depth",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        Text(
                            text = "m",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    modifier = Modifier.testTag("input_burial_depth")
                )
            },
            secondField = {
                VoltAtlasTextField(
                    value = draft.soilRes,
                    onValueChange = { onDraftChange(draft.copy(soilRes = it)) },
                    label = "Soil Res",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        Text(
                            text = "Ωm",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    modifier = Modifier.testTag("input_soil_res")
                )
            }
        )

        // 4. Grouping (No. of Circuits)
        VoltAtlasTextField(
            value = draft.noCircuits,
            onValueChange = { onDraftChange(draft.copy(noCircuits = it)) },
            label = "Grouping (No. of Circuits)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.testTag("input_no_circuits")
        )

        // 5. Formation & Spacing
        ResponsiveFieldPair(
            firstField = {
                VoltAtlasDropdown(
                    value = draft.formation,
                    options = formationOptions,
                    onSelect = { onDraftChange(draft.copy(formation = it)) },
                    label = "Formation",
                    modifier = Modifier.testTag("input_formation")
                )
            },
            secondField = {
                VoltAtlasDropdown(
                    value = draft.spacing,
                    options = spacingOptions,
                    onSelect = { onDraftChange(draft.copy(spacing = it)) },
                    label = "Spacing",
                    modifier = Modifier.testTag("input_spacing")
                )
            }
        )
    }
}

/**
 * Adaptive container that renders two input fields side-by-side on wide displays,
 * but cleanly stacks them vertically on compact widths (320dp) or large font scale (1.5x).
 */
@Composable
private fun ResponsiveFieldPair(
    firstField: @Composable () -> Unit,
    secondField: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val minFieldWidth = 150.dp * maxOf(1f, fontScale)
        val requiredRowWidth = (minFieldWidth * 2) + 8.dp
        val isSingleColumn = maxWidth < requiredRowWidth
        if (isSingleColumn) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                firstField()
                secondField()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) { firstField() }
                Box(modifier = Modifier.weight(1f)) { secondField() }
            }
        }
    }
}
