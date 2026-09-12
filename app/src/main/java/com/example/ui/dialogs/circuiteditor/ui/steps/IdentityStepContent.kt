package com.example.ui.dialogs.circuiteditor.ui.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CircuitEntity
import com.example.ui.components.VoltAtlasDropdown
import com.example.ui.components.VoltAtlasTextField
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolution
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot

/**
 * Step 1: Identity & Source configuration content.
 */
@Composable
fun IdentityStepContent(
    draft: CircuitEditorDraft,
    onDraftChange: (CircuitEditorDraft) -> Unit,
    existingCircuits: List<CircuitEntity>,
    editingCircuitId: Int,
    availableTransformers: List<TransformerSnapshot>,
    resolution: CircuitSourceResolution,
    identityError: String?,
    onAddTransformer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelTypeOptions = remember {
        listOf("LOAD", "MDB", "SMDB", "DB", "MCC", "PANEL")
    }

    val fedFromOptions = remember(existingCircuits, editingCircuitId) {
        val validBoards = existingCircuits.filter { candidate ->
            candidate.id != editingCircuitId &&
                candidate.circuitName.isNotBlank() &&
                candidate.panelType != null &&
                candidate.panelType != "LOAD"
        }.map { it.circuitName }
        listOf("Transformer") + validBoards
    }

    val transformerOptions = remember(availableTransformers) {
        if (availableTransformers.isEmpty()) listOf("None") else availableTransformers.map { it.transformerId }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("circuit_editor_step_identity"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Circuit / Panel Name
        VoltAtlasTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) },
            label = "Circuit / Panel Name",
            errorText = identityError,
            modifier = Modifier.testTag("input_circuit_name")
        )

        // Board / Load Classification
        VoltAtlasDropdown(
            value = draft.panelType,
            options = panelTypeOptions,
            onSelect = { onDraftChange(draft.copy(panelType = it)) },
            label = "Board Classification",
            modifier = Modifier.testTag("input_panel_type")
        )

        // Fed From (Supply Source)
        VoltAtlasDropdown(
            value = draft.fedFrom,
            options = fedFromOptions,
            onSelect = { onDraftChange(draft.copy(fedFrom = it)) },
            label = "Fed From",
            modifier = Modifier.testTag("input_fed_from")
        )

        // Transformer Selection (shown only when Fed From == "Transformer")
        if (draft.fedFrom == "Transformer") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    val currentTxValue = if (draft.transformerId.isNotBlank()) {
                        draft.transformerId
                    } else {
                        availableTransformers.firstOrNull()?.transformerId ?: "None"
                    }
                    VoltAtlasDropdown(
                        value = currentTxValue,
                        options = transformerOptions,
                        onSelect = { onDraftChange(draft.copy(transformerId = it)) },
                        label = "Transformer",
                        modifier = Modifier.testTag("input_transformer_id")
                    )
                }

                FilledTonalIconButton(
                    onClick = onAddTransformer,
                    modifier = Modifier
                        .testTag("add_transformer_btn")
                        .size(48.dp)
                        .semantics {
                            contentDescription = "Add Transformer"
                        },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Computed Hierarchy Information Card (Compact MD3 Tonal Card)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("computed_hierarchy_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Derived Hierarchy",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("derived_hierarchy_title")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hierarchy_field_supply_source"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Supply Source",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = draft.fedFrom,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("supply_source_value")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hierarchy_field_hierarchy_level"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hierarchy Level",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${resolution.calculatedLevel} (Root = 0)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("hierarchy_level_value")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hierarchy_field_source_type"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Source Type",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = resolution.calculatedSourceType,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("source_type_value")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hierarchy_field_resolved_transformer"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Resolved Transformer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = resolution.finalTxId.ifBlank { "None" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("resolved_transformer_value")
                    )
                }
            }
        }
    }
}
