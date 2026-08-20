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
import com.example.ai.ui.ProposalReviewUiModel

/**
 * Step 2 Trusted Confirmation Surface for Phase 7F-5.
 *
 * Invariants:
 * - Visually explicit warning: "WILL MODIFY PROJECT".
 * - "Approve & Apply" button is the sole trusted confirmation action.
 * - Button is disabled during submission; double-clicks reuse the same in-flight action ID.
 */
@Composable
fun AssistantApprovalConfirmationCard(
    reviewModel: ProposalReviewUiModel,
    isSubmitting: Boolean,
    onConfirmApprove: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("assistant_approval_confirmation_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confirm Project Mutation",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .semantics { contentDescription = "Action notice: Will modify project" }
                        .testTag("will_modify_project_badge")
                ) {
                    Text(
                        text = "WILL MODIFY PROJECT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Text(
                text = "You are approving ${reviewModel.fieldChanges.size} change(s) for ${reviewModel.targetEntityType} ${reviewModel.targetEntityLabel}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "• Live Project Revision: Rev ${reviewModel.currentLiveRevision}\n" +
                        "• Database state will be updated atomically with a durable receipt.\n" +
                        "• Downstream voltage drop and protection checks will automatically revalidate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Actions Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f).testTag("action_cancel_approval_confirmation")
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = onConfirmApprove,
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1.5f).testTag("action_approve_and_apply")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Approve & Apply")
                }
            }
        }
    }
}
