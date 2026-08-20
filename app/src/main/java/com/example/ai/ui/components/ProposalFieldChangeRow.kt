package com.example.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.ui.ProposalChangeActionUi
import com.example.ai.ui.ProposalFieldChangeUiModel

/**
 * MD3 Before/After row displaying a single proposed field modification.
 */
@Composable
fun ProposalFieldChangeRow(
    change: ProposalFieldChangeUiModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(10.dp)
            .testTag("proposal_field_change_${change.fieldKey}"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = change.label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            val actionBadgeColor = if (change.action == ProposalChangeActionUi.CLEAR_TO_AUTOMATIC) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
            val actionBadgeText = if (change.action == ProposalChangeActionUi.CLEAR_TO_AUTOMATIC) "CLEAR" else "SET"

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(actionBadgeColor)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = actionBadgeText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Before -> After display
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "${change.label}: current is ${change.currentValue?.formattedText ?: "None"}, proposed is ${change.proposedValue?.formattedText ?: "None"}"
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Current Value
            Text(
                text = change.currentValue?.formattedText ?: "Not Set",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Changes to",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(16.dp)
            )

            // Proposed Value
            Text(
                text = change.proposedValue?.formattedText ?: "None",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
