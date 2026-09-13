package com.example.ui.dialogs.circuiteditor.ui.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.components.VoltAtlasDropdown
import com.example.ui.components.VoltAtlasTextField
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft

/**
 * Step 6: Panel Header Equipment Data configuration content.
 *
 * Applicable only for distribution boards and switchboards (when panelType != "LOAD" && panelType.isNotBlank()).
 * Matches the legacy CircuitEditDialog specification (lines 899-1018, 1120-1128).
 *
 * Responsive layout ensures single-column stacking at compact phone widths (320dp)
 * and large font scales (1.5x) to eliminate clipping or label/input truncation.
 */
@Composable
fun PanelHeaderStepContent(
    draft: CircuitEditorDraft,
    onDraftChange: (CircuitEditorDraft) -> Unit,
    scRatingText: String? = "Unavailable (Calculated after save)",
    modifier: Modifier = Modifier
) {
    val incomingTypeOptions = listOf("", "MCCB", "ACB", "LBS", "Switch-Disconnector", "Fuse-Switch")
    val ipRatingOptions = listOf("", "IP20", "IP41", "IP54", "IP55", "IP65")
    val cableEntryOptions = listOf("", "Top", "Bottom", "Top/Bottom")

    val displayScRating = if (scRatingText.isNullOrBlank()) "Unavailable (Calculated after save)" else scRatingText

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("circuit_editor_step_panel_header"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Busbar Rating (A) & Short Circuit (kA) [Calculated]
        ResponsiveFieldPair(
            firstField = {
                VoltAtlasTextField(
                    value = draft.busbarRatingA,
                    onValueChange = { onDraftChange(draft.copy(busbarRatingA = it)) },
                    label = "Busbar Rating (A)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("input_edit_dialog_busbar_rating")
                )
            },
            secondField = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("display_edit_dialog_sc_rating")
                        .semantics {
                            contentDescription = "Calculated Short-Circuit Rating: $displayScRating"
                        },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Calculated Short-Circuit Rating",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = displayScRating,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        )

        // 2. Main Incoming Type & Incoming Count
        ResponsiveFieldPair(
            firstField = {
                VoltAtlasDropdown(
                    value = draft.mainIncomingType,
                    options = incomingTypeOptions,
                    onSelect = { onDraftChange(draft.copy(mainIncomingType = it)) },
                    label = "Main Incoming Type",
                    modifier = Modifier.testTag("input_edit_dialog_main_incoming_type")
                )
            },
            secondField = {
                VoltAtlasTextField(
                    value = draft.mainIncomingCount,
                    onValueChange = { onDraftChange(draft.copy(mainIncomingCount = it)) },
                    label = "Incoming Count",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("input_edit_dialog_main_incoming_count")
                )
            }
        )

        // 3. IP Rating & Cable Entry
        ResponsiveFieldPair(
            firstField = {
                VoltAtlasDropdown(
                    value = draft.ipRating,
                    options = ipRatingOptions,
                    onSelect = { onDraftChange(draft.copy(ipRating = it)) },
                    label = "IP Rating",
                    modifier = Modifier.testTag("input_edit_dialog_ip_rating")
                )
            },
            secondField = {
                VoltAtlasDropdown(
                    value = draft.cableEntry,
                    options = cableEntryOptions,
                    onSelect = { onDraftChange(draft.copy(cableEntry = it)) },
                    label = "Cable Entry",
                    modifier = Modifier.testTag("input_edit_dialog_cable_entry")
                )
            }
        )

        // 4. Location & Number of Ways
        ResponsiveFieldPair(
            firstField = {
                VoltAtlasTextField(
                    value = draft.panelLocation,
                    onValueChange = { onDraftChange(draft.copy(panelLocation = it)) },
                    label = "Location",
                    modifier = Modifier.testTag("input_edit_dialog_location")
                )
            },
            secondField = {
                VoltAtlasTextField(
                    value = draft.numberOfWays,
                    onValueChange = { onDraftChange(draft.copy(numberOfWays = it)) },
                    label = "Number of Ways",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("input_edit_dialog_number_of_ways")
                )
            }
        )

        // 5. Feeder / Supply Cable Description
        VoltAtlasTextField(
            value = draft.feederDescription,
            onValueChange = { onDraftChange(draft.copy(feederDescription = it)) },
            label = "Feeder / Supply Cable Description",
            modifier = Modifier.testTag("input_edit_dialog_feeder_description")
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
