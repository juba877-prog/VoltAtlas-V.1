package com.example.ui.dialogs.circuiteditor.ui.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.ThemeBorderSubtle
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolution
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflow
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep

/**
 * Step 7 (or 6 for LOAD circuits): Review & Save step content.
 *
 * Displays a structured, read-only summary of all configured circuit properties
 * grouped by workflow section. Each section features a clear "Edit" trigger to navigate
 * directly to the respective configuration step.
 *
 * Implements final save action and displays immediate validation feedback in
 * [review_validation_error] if any save constraint is violated.
 */
@Composable
fun ReviewStepContent(
    draft: CircuitEditorDraft,
    resolution: CircuitSourceResolution,
    onEditSection: (CircuitWorkflowStep) -> Unit,
    onSaveClick: () -> Unit = {},
    saveError: String? = null,
    scRatingText: String = "N/A (Calculated after save)",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("circuit_editor_step_review"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Identity & Source
        val resolvedSourceDisplay = when {
            draft.fedFrom == "Transformer" -> resolution.finalTxId.ifBlank { "None" }
            resolution.parentBoardObj != null -> resolution.parentBoardObj.circuitName
            else -> draft.fedFrom
        }
        ReviewSectionCard(
            title = "1. Identity & Source",
            sectionTag = "review_identity_section",
            editTag = "review_edit_identity",
            onEdit = { onEditSection(CircuitWorkflowStep.IDENTITY) }
        ) { isStacked ->
            ReviewField(label = "Circuit / Panel Name", value = draft.name.ifBlank { "(Not set)" }, isStacked = isStacked)
            ReviewField(label = "Panel Type", value = draft.panelType, isStacked = isStacked)
            ReviewField(label = "Fed From", value = draft.fedFrom, isStacked = isStacked)
            ReviewField(label = "Resolved Source", value = resolvedSourceDisplay, isStacked = isStacked)
        }

        // Section 2: Load Specifications
        val motorDetail = if (draft.loadNature == "Motor") " (${draft.startingMethod})" else ""
        ReviewSectionCard(
            title = "2. Load Specifications",
            sectionTag = "review_load_section",
            editTag = "review_edit_load",
            onEdit = { onEditSection(CircuitWorkflowStep.LOAD) }
        ) { isStacked ->
            ReviewField(label = "Load", value = "${draft.loadKVA} kVA", isStacked = isStacked)
            ReviewField(label = "Power Factor", value = draft.pf, isStacked = isStacked)
            ReviewField(label = "Demand Factor", value = draft.demandFactor, isStacked = isStacked)
            ReviewField(label = "Voltage & Phases", value = "${draft.voltageV} V • ${draft.phases}", isStacked = isStacked)
            ReviewField(label = "Load Nature", value = "${draft.loadNature}$motorDetail", isStacked = isStacked)
            if (draft.loadCategory != null) {
                ReviewField(label = "Load Category", value = draft.loadCategory, isStacked = isStacked)
            }
            ReviewField(label = "Harmonics", value = if (draft.isHarmonic) "Harmonic Load" else "Standard", isStacked = isStacked)
        }

        // Section 3: Supply & Backup
        ReviewSectionCard(
            title = "3. Supply & Backup",
            sectionTag = "review_supply_section",
            editTag = "review_edit_supply",
            onEdit = { onEditSection(CircuitWorkflowStep.SUPPLY) }
        ) { isStacked ->
            ReviewField(label = "Supply Category", value = draft.supplyCategory, isStacked = isStacked)
            if (draft.supplyCategory == "EMERGENCY" || draft.supplyCategory == "EMERGENCY + UPS") {
                ReviewField(label = "Assigned ATS", value = draft.assignedAtsId, isStacked = isStacked)
                ReviewField(label = "Assigned Generator", value = draft.assignedGeneratorId, isStacked = isStacked)
            }
            if (draft.supplyCategory == "UPS" || draft.supplyCategory == "EMERGENCY + UPS") {
                ReviewField(label = "Assigned UPS", value = draft.assignedUpsId, isStacked = isStacked)
            }
        }

        // Section 4: Feeder Selection
        ReviewSectionCard(
            title = "4. Feeder Selection",
            sectionTag = "review_feeder_section",
            editTag = "review_edit_feeder",
            onEdit = { onEditSection(CircuitWorkflowStep.FEEDER) }
        ) { isStacked ->
            ReviewField(label = "Feeder Type", value = draft.feederType, isStacked = isStacked)
            if (draft.feederType == "Cable") {
                ReviewField(label = "Manufacturer", value = draft.manufacturer, isStacked = isStacked)
                ReviewField(label = "Cable Family", value = draft.cableType.ifBlank { "(Default Cable)" }, isStacked = isStacked)
            } else {
                ReviewField(label = "Manufacturer", value = draft.buswayMfg, isStacked = isStacked)
                ReviewField(label = "Busway Model", value = draft.buswayModel, isStacked = isStacked)
            }
            ReviewField(label = "Parallel Runs", value = draft.parallelRuns, isStacked = isStacked)
            ReviewField(label = "Length", value = "${draft.length_m} m", isStacked = isStacked)
            ReviewField(label = "Max Voltage Drop", value = "${draft.maxVDpct}%", isStacked = isStacked)
        }

        // Section 5: Installation Conditions
        ReviewSectionCard(
            title = "5. Installation Conditions",
            sectionTag = "review_installation_section",
            editTag = "review_edit_installation",
            onEdit = { onEditSection(CircuitWorkflowStep.INSTALLATION) }
        ) { isStacked ->
            ReviewField(label = "Installation Method", value = draft.installation, isStacked = isStacked)
            ReviewField(label = "Air Temperature", value = "${draft.airTempC} °C", isStacked = isStacked)
            ReviewField(label = "Ground Temperature", value = "${draft.gndTempC} °C", isStacked = isStacked)
            ReviewField(label = "Burial Depth", value = "${draft.burialDepth_m} m", isStacked = isStacked)
            ReviewField(label = "Soil Resistivity", value = "${draft.soilRes} Ωm", isStacked = isStacked)
            ReviewField(label = "Grouping", value = "${draft.noCircuits} Circuits", isStacked = isStacked)
            ReviewField(label = "Formation", value = draft.formation, isStacked = isStacked)
            ReviewField(label = "Spacing", value = draft.spacing, isStacked = isStacked)
        }

        // Section 6: Panel Header Equipment (conditional: only if panelType != "LOAD" && panelType.isNotBlank())
        if (CircuitWorkflow.isPanelHeaderApplicable(draft.panelType)) {
            ReviewSectionCard(
                title = "6. Panel Header Equipment",
                sectionTag = "review_panel_header_section",
                editTag = "review_edit_panel_header",
                onEdit = { onEditSection(CircuitWorkflowStep.PANEL_HEADER) }
            ) { isStacked ->
                ReviewField(
                    label = "Busbar Rating",
                    value = if (draft.busbarRatingA.isNotBlank()) "${draft.busbarRatingA} A" else "Not specified",
                    isStacked = isStacked
                )
                ReviewField(label = "Calculated Short Circuit", value = scRatingText, isStacked = isStacked)
                if (draft.mainIncomingType.isNotBlank()) {
                    ReviewField(label = "Main Incoming Type", value = draft.mainIncomingType, isStacked = isStacked)
                }
                if (draft.mainIncomingCount.isNotBlank()) {
                    ReviewField(label = "Incoming Count", value = draft.mainIncomingCount, isStacked = isStacked)
                }
                if (draft.ipRating.isNotBlank()) {
                    ReviewField(label = "IP Rating", value = draft.ipRating, isStacked = isStacked)
                }
                if (draft.cableEntry.isNotBlank()) {
                    ReviewField(label = "Cable Entry", value = draft.cableEntry, isStacked = isStacked)
                }
                if (draft.panelLocation.isNotBlank()) {
                    ReviewField(label = "Location", value = draft.panelLocation, isStacked = isStacked)
                }
                if (draft.numberOfWays.isNotBlank()) {
                    ReviewField(label = "Number of Ways", value = draft.numberOfWays, isStacked = isStacked)
                }
                if (draft.feederDescription.isNotBlank()) {
                    ReviewField(label = "Feeder Description", value = draft.feederDescription, isStacked = isStacked)
                }
            }
        }
    }
}

@Composable
private fun ReviewSectionCard(
    title: String,
    sectionTag: String,
    editTag: String,
    onEdit: () -> Unit,
    content: @Composable (isStacked: Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(sectionTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, ThemeBorderSubtle),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val isCompactOrLargeFont = maxWidth < 340.dp || fontScale > 1.2f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${sectionTag}_header"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .testTag("${sectionTag}_title")
                            .weight(1f, fill = false)
                            .padding(end = 8.dp)
                    )

                    if (isCompactOrLargeFont) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .testTag(editTag)
                                .size(48.dp)
                                .semantics {
                                    contentDescription = "Edit $title"
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        TextButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .testTag(editTag)
                                .heightIn(min = 48.dp)
                                .semantics {
                                    contentDescription = "Edit $title"
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Edit",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val isStacked = maxWidth < 340.dp || fontScale > 1.2f
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    content(isStacked)
                }
            }
        }
    }
}

@Composable
private fun ReviewField(
    label: String,
    value: String,
    isStacked: Boolean,
    modifier: Modifier = Modifier
) {
    if (isStacked) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}
