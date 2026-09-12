package com.example.ui.dialogs.circuiteditor.ui.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CircuitEntity
import com.example.ui.components.ThemeBorderSubtle
import com.example.ui.components.ThemePrimary
import com.example.ui.components.ThemeSurface
import com.example.ui.components.ThemeSurfaceHigh
import com.example.ui.components.ThemeTextPrimary
import com.example.ui.components.ThemeTextSecondary
import com.example.ui.components.VoltAtlasDataRow
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

                FilledTonalButton(
                    onClick = onAddTransformer,
                    modifier = Modifier
                        .testTag("add_transformer_btn")
                        .sizeIn(minHeight = 48.dp)
                        .padding(top = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ThemeSurfaceHigh,
                        contentColor = ThemePrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "➕ Add TX",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }

        // Computed Hierarchy Information Card (Read-Only)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("computed_hierarchy_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Derived Network Hierarchy",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                HierarchyField(
                    label = "Supply Source",
                    value = draft.fedFrom
                )
                HierarchyField(
                    label = "Hierarchy Level",
                    value = "${resolution.calculatedLevel} (Root = 0)"
                )
                HierarchyField(
                    label = "Source Type",
                    value = resolution.calculatedSourceType
                )
                HierarchyField(
                    label = "Resolved Transformer",
                    value = resolution.finalTxId.ifBlank { "None" }
                )
            }
        }
    }
}

/**
 * Responsive stacked label-value presentation for engineering hierarchy data.
 * Guarantees zero truncation at large accessibility font scales (1.3x - 1.5x)
 * and restrained emphasis using MD3 bodyLarge rather than display tokens.
 */
@Composable
private fun HierarchyField(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
