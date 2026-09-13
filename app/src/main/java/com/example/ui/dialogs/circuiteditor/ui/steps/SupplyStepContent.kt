package com.example.ui.dialogs.circuiteditor.ui.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.VoltAtlasDropdown
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolution
import com.example.ui.dialogs.circuiteditor.model.AtsSnapshot
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.GeneratorSnapshot
import com.example.ui.dialogs.circuiteditor.model.UpsSnapshot

/**
 * Stage C3 Supply & Backup step content.
 *
 * Implements an adaptive MD3 single-choice presentation for four supply categories:
 * NORMAL, EMERGENCY, UPS, EMERGENCY + UPS.
 *
 * Conditional backup equipment selection:
 * - NORMAL: no backup equipment selectors
 * - EMERGENCY: ATS and Generator selectors
 * - UPS: UPS selector
 * - EMERGENCY + UPS: ATS, Generator, and UPS selectors
 *
 * Inherited source hierarchy card displays read-only resolution context.
 */
@Composable
fun SupplyStepContent(
    draft: CircuitEditorDraft,
    onDraftChange: (CircuitEditorDraft) -> Unit,
    availableGenerators: List<GeneratorSnapshot> = emptyList(),
    availableAts: List<AtsSnapshot> = emptyList(),
    availableUps: List<UpsSnapshot> = emptyList(),
    sourceResolution: CircuitSourceResolution? = null,
    modifier: Modifier = Modifier
) {
    val categories = listOf("NORMAL", "EMERGENCY", "UPS", "EMERGENCY + UPS")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("circuit_editor_step_supply"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Supply Category Header & 2x2 Adaptive Layout
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Supply Category",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_supply_category"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: NORMAL & EMERGENCY
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryChoiceButton(
                        category = categories[0],
                        isSelected = draft.supplyCategory == categories[0],
                        onSelect = { onDraftChange(draft.copy(supplyCategory = categories[0])) },
                        modifier = Modifier.weight(1f)
                    )
                    CategoryChoiceButton(
                        category = categories[1],
                        isSelected = draft.supplyCategory == categories[1],
                        onSelect = { onDraftChange(draft.copy(supplyCategory = categories[1])) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: UPS & EMERGENCY + UPS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryChoiceButton(
                        category = categories[2],
                        isSelected = draft.supplyCategory == categories[2],
                        onSelect = { onDraftChange(draft.copy(supplyCategory = categories[2])) },
                        modifier = Modifier.weight(1f)
                    )
                    CategoryChoiceButton(
                        category = categories[3],
                        isSelected = draft.supplyCategory == categories[3],
                        onSelect = { onDraftChange(draft.copy(supplyCategory = categories[3])) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Conditional Backup Selectors
        if (draft.supplyCategory.contains("EMERGENCY")) {
            val atsOptions = remember(availableAts) {
                listOf("None") + availableAts.map { it.atsId }
            }
            val generatorOptions = remember(availableGenerators) {
                listOf("None") + availableGenerators.map { it.generatorId }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Emergency Power Source",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                VoltAtlasDropdown(
                    value = draft.assignedAtsId,
                    options = atsOptions,
                    onSelect = { onDraftChange(draft.copy(assignedAtsId = it)) },
                    label = "Assigned ATS Source",
                    modifier = Modifier.testTag("input_assigned_ats")
                )
                if (availableAts.isEmpty()) {
                    Text(
                        text = "No ATS configured in this project",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                VoltAtlasDropdown(
                    value = draft.assignedGeneratorId,
                    options = generatorOptions,
                    onSelect = { onDraftChange(draft.copy(assignedGeneratorId = it)) },
                    label = "Assigned Generator Source",
                    modifier = Modifier.testTag("input_assigned_generator")
                )
                if (availableGenerators.isEmpty()) {
                    Text(
                        text = "No generator configured in this project",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (draft.supplyCategory.contains("UPS")) {
            val upsOptions = remember(availableUps) {
                listOf("None") + availableUps.map { it.upsId }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "UPS Power Source",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                VoltAtlasDropdown(
                    value = draft.assignedUpsId,
                    options = upsOptions,
                    onSelect = { onDraftChange(draft.copy(assignedUpsId = it)) },
                    label = "Assigned UPS Source",
                    modifier = Modifier.testTag("input_assigned_ups")
                )
                if (availableUps.isEmpty()) {
                    Text(
                        text = "No UPS configured in this project",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Inherited Source Card
        if (sourceResolution != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inherited_source_card"),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Inherited Source Hierarchy",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Level:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${sourceResolution.calculatedLevel} (Root = 0)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Supply Source:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = draft.fedFrom,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Source Type:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = sourceResolution.calculatedSourceType,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Resolved Transformer:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = sourceResolution.finalTxId.ifEmpty { "None" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChoiceButton(
    category: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = isSelected,
                onClick = onSelect,
                role = Role.RadioButton
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 4.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = category,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
