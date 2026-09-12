package com.example.ui.dialogs.circuiteditor.ui.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LoadCategories
import com.example.ui.components.ThemeBorderSubtle
import com.example.ui.components.ThemePrimary
import com.example.ui.theme.ThemeStatusWarning
import com.example.ui.components.ThemeSurface
import com.example.ui.components.ThemeTextPrimary
import com.example.ui.components.ThemeTextSecondary
import com.example.ui.components.ThemeTextTertiary
import com.example.ui.components.VoltAtlasDropdown
import com.example.ui.components.VoltAtlasTextField
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft

/**
 * Step 2: Load Specifications configuration content.
 */
@Composable
fun LoadStepContent(
    draft: CircuitEditorDraft,
    onDraftChange: (CircuitEditorDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryOptions = remember {
        listOf("Unspecified (None)") + LoadCategories.OPTIONS.map { it.label }
    }
    val currentCategoryLabel = remember(draft.loadCategory) {
        LoadCategories.findByCode(draft.loadCategory)?.label ?: "Unspecified (None)"
    }

    val loadNatureOptions = remember { listOf("Static", "Motor") }
    val startingMethodOptions = remember { listOf("DOL", "Star-Delta", "Soft-Starter", "VFD") }
    val phaseOptions = remember { listOf("3P+N", "1P+N", "3P") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("circuit_editor_step_load"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Load Category (Classification & Automatic Suggestion)
        VoltAtlasDropdown(
            value = currentCategoryLabel,
            options = categoryOptions,
            onSelect = { selectedLabel ->
                if (selectedLabel.startsWith("Unspecified")) {
                    onDraftChange(draft.copy(loadCategory = null))
                } else {
                    val opt = LoadCategories.OPTIONS.firstOrNull { it.label == selectedLabel }
                    if (opt != null) {
                        onDraftChange(
                            draft.copy(
                                loadCategory = opt.code,
                                pf = opt.defaultPf.toString(),
                                demandFactor = opt.defaultDf.toString()
                            )
                        )
                    }
                }
            },
            label = "Load Category",
            modifier = Modifier.testTag("input_load_category")
        )

        // Motor Warning Card (visible when loadCategory == "MTR")
        if (draft.loadCategory == "MTR") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("motor_warning_card"),
                colors = CardDefaults.cardColors(
                    containerColor = ThemeStatusWarning.copy(alpha = 0.08f)
                ),
                border = BorderStroke(1.dp, ThemeStatusWarning.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = ThemeStatusWarning,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Note: Motor category affects load reporting only. Manually verify breaker curve (C/D) and starting-current cable sizing per IEC 60947-4-1 — this is not yet automated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ThemeStatusWarning
                    )
                }
            }
        }

        // Load Nature
        VoltAtlasDropdown(
            value = draft.loadNature,
            options = loadNatureOptions,
            onSelect = { onDraftChange(draft.copy(loadNature = it)) },
            label = "Load Nature",
            modifier = Modifier.testTag("input_load_nature")
        )

        // Motor-specific inputs (visible only when loadNature == "Motor")
        if (draft.loadNature == "Motor") {
            ResponsiveFieldPair(
                firstField = {
                    VoltAtlasDropdown(
                        value = draft.startingMethod,
                        options = startingMethodOptions,
                        onSelect = { onDraftChange(draft.copy(startingMethod = it)) },
                        label = "Starting Method",
                        modifier = Modifier.testTag("input_starting_method")
                    )
                },
                secondField = {
                    VoltAtlasTextField(
                        value = draft.startingCurrentMultiplier,
                        onValueChange = { onDraftChange(draft.copy(startingCurrentMultiplier = it)) },
                        label = "Start Multiplier",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.testTag("input_starting_current_multiplier")
                    )
                }
            )
        }

        // Electrical Load Inputs: Load (kVA) & Power Factor
        ResponsiveFieldPair(
            firstField = {
                VoltAtlasTextField(
                    value = draft.loadKVA,
                    onValueChange = { onDraftChange(draft.copy(loadKVA = it)) },
                    label = "Load",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        Text(
                            text = "kVA",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThemeTextTertiary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    modifier = Modifier.testTag("input_load_kva")
                )
            },
            secondField = {
                VoltAtlasTextField(
                    value = draft.pf,
                    onValueChange = { onDraftChange(draft.copy(pf = it)) },
                    label = "Power Factor",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("input_pf")
                )
            }
        )

        // Electrical Load Inputs: Demand Factor & Safety Factor
        ResponsiveFieldPair(
            firstField = {
                VoltAtlasTextField(
                    value = draft.demandFactor,
                    onValueChange = { onDraftChange(draft.copy(demandFactor = it)) },
                    label = "Demand Factor",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("input_demand_factor")
                )
            },
            secondField = {
                VoltAtlasTextField(
                    value = draft.safetyFactor,
                    onValueChange = { onDraftChange(draft.copy(safetyFactor = it)) },
                    label = "Safety Factor",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("input_safety_factor")
                )
            }
        )

        // Voltage & Phase Configuration
        ResponsiveFieldPair(
            firstField = {
                VoltAtlasTextField(
                    value = draft.voltageV,
                    onValueChange = { onDraftChange(draft.copy(voltageV = it)) },
                    label = "Voltage",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        Text(
                            text = "V",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThemeTextTertiary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    modifier = Modifier.testTag("input_voltage")
                )
            },
            secondField = {
                VoltAtlasDropdown(
                    value = draft.phases,
                    options = phaseOptions,
                    onSelect = { onDraftChange(draft.copy(phases = it)) },
                    label = "Phases",
                    modifier = Modifier.testTag("input_phases")
                )
            }
        )

        // Harmonic / Non-Linear Load Toggle
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_harmonic_load"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Non-linear / Harmonic Load",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = ThemeTextPrimary
                    )
                    Text(
                        text = "Marks circuit for neutral sizing and harmonic derating considerations",
                        style = MaterialTheme.typography.bodySmall,
                        color = ThemeTextSecondary,
                        modifier = Modifier.testTag("harmonic_supporting_text")
                    )
                }
                Switch(
                    checked = draft.isHarmonic,
                    onCheckedChange = { onDraftChange(draft.copy(isHarmonic = it)) },
                    modifier = Modifier.testTag("checkbox_is_harmonic"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

/**
 * Adaptive layout for paired fields.
 * Stacks vertically when available measured width cannot satisfy the minimum readable width
 * for both fields (scaled by font scale), ensuring labels and inputs remain fully visible and readable.
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
