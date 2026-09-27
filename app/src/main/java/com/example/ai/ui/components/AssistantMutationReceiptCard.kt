package com.example.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ai.ui.ProposalFieldChangeUiModel
import com.example.ui.theme.EngineeringStatusPalette

/**
 * MD3 Mutation Receipt Card displaying post-commit durable truth.
 */
@Composable
fun AssistantMutationReceiptCard(
    receiptId: String,
    revisionBefore: Long,
    revisionAfter: Long,
    appliedChanges: List<ProposalFieldChangeUiModel>,
    revalidationStatus: String,
    isPartialOrFailed: Boolean = false,
    isAlreadyApplied: Boolean = false,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mutation_receipt")
            .testTag("assistant_mutation_receipt_card_$receiptId"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isAlreadyApplied -> "Already Applied"
                        isPartialOrFailed -> "Change Applied (Revalidation Warning)"
                        else -> "Change Applied"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                val badgeText = when {
                    isAlreadyApplied -> "ALREADY APPLIED"
                    isPartialOrFailed -> "APPLIED (REVAL PARTIAL)"
                    else -> "CHANGE APPLIED"
                }
                val badgeColor = when {
                    isAlreadyApplied -> MaterialTheme.colorScheme.secondaryContainer
                    isPartialOrFailed -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
                val textColor = when {
                    isAlreadyApplied -> MaterialTheme.colorScheme.onSecondaryContainer
                    isPartialOrFailed -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .semantics { contentDescription = "Receipt Status: $badgeText" }
                        .testTag("receipt_status_badge")
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                }
            }

            // State N: Already applied / Idempotent
            if (isAlreadyApplied) {
                Text(
                    text = "Project is already in the requested state. No changes were applied. Revision remains Rev $revisionBefore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Project Revision: Rev $revisionBefore → Rev $revisionAfter",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )

                // State M notice: never say mutation failed
                if (isPartialOrFailed) {
                    Text(
                        text = "Change committed to project (Rev $revisionBefore → Rev $revisionAfter). Revalidation flagged warnings: $revalidationStatus",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.testTag("revalidation_warning_notice")
                    )
                }
            }

            if (appliedChanges.isNotEmpty()) {
                Text(
                    text = "Committed Changes:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    appliedChanges.forEach { change ->
                        ProposalFieldChangeRow(change = change)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Revalidation: $revalidationStatus",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isPartialOrFailed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("revalidation_status")
                )
                Text(
                    text = "Receipt: $receiptId",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .testTag("action_dismiss_receipt")
            ) {
                Text("Done")
            }
        }
    }
}
