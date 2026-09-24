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
import com.example.ai.ui.ProposalReviewActionType
import com.example.ai.ui.ProposalReviewActionUiModel
import com.example.ai.ui.ProposalReviewUiModel

/**
 * Dedicated MD3 Proposal Review Card for Phase 7F-4.
 *
 * Invariants:
 * - Visually distinct with "PROPOSED CHANGE · Not Applied" badge.
 * - Shows exact Before/After diffs for all proposed fields.
 * - Stale proposals display "STALE PROPOSAL" and disable approval continuation.
 * - All action callbacks are non-executing in Phase 7F-4.
 */
@Composable
fun AssistantProposalReviewCard(
    model: ProposalReviewUiModel,
    onActionClick: (ProposalReviewActionUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("proposal_card")
            .testTag("assistant_proposal_review_card_${model.proposalId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (model.isStale) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Section 1: Header Row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Proposal Review",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${model.targetEntityType.uppercase()} · ${model.targetEntityLabel}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Not Applied Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .semantics { contentDescription = "Proposal state: Not Applied" }
                            .testTag("not_applied_badge")
                    ) {
                        Text(
                            text = "NOT APPLIED",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    // Stale vs Current Badge
                    val statusColor = if (model.isStale) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                    val statusText = if (model.isStale) "STALE" else "CURRENT"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .semantics { contentDescription = "Proposal validity: $statusText" }
                            .testTag("proposal_validity_badge")
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (model.isStale) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // --- Section 2: Change Summary ---
            Text(
                text = "${model.operation}: ${model.fieldChanges.size} field change(s) proposed",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )

            // --- Section 3: Before / After Changes ---
            Text(
                text = "Proposed Modifications:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                model.fieldChanges.forEach { change ->
                    ProposalFieldChangeRow(change = change)
                }
            }

            // --- Section 4: Engineering Evidence ---
            model.governingCriterion?.let { gov ->
                Text(
                    text = "Governing Check: $gov",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = model.evidenceSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // --- Section 5: Impact ---
            if (model.affectedDomains.isNotEmpty()) {
                Text(
                    text = "Affected Domains: ${model.affectedDomains.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- Section 6: Validity & Revisions ---
            Text(
                text = "Base Revision: Rev ${model.baseProjectRevision} (Live: Rev ${model.currentLiveRevision})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (model.isStale) {
                Text(
                    text = "⚠️ Proposal is STALE due to project revision advance. Sizing must be recalculated before approval.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error
                )
            }

            // --- Section 7: Actions Row ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cancel Button
                OutlinedButton(
                    onClick = {
                        onActionClick(
                            ProposalReviewActionUiModel(
                                actionType = ProposalReviewActionType.CANCEL_PROPOSAL_REVIEW,
                                label = "Cancel"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("action_cancel_proposal")
                ) {
                    Text("Cancel")
                }

                // Edit Button
                FilledTonalButton(
                    onClick = {
                        onActionClick(
                            ProposalReviewActionUiModel(
                                actionType = ProposalReviewActionType.EDIT_PROPOSAL,
                                label = "Edit"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("action_edit_proposal")
                ) {
                    Text("Edit")
                }

                // Continue to Approval Button (Non-executable in 7F-4)
                Button(
                    onClick = {
                        onActionClick(
                            ProposalReviewActionUiModel(
                                actionType = ProposalReviewActionType.CONTINUE_TO_APPROVAL_NON_EXECUTABLE,
                                label = "Review Approval Step",
                                isPrimary = true,
                                isEnabled = !model.isStale
                            )
                        )
                    },
                    enabled = !model.isStale,
                    modifier = Modifier.weight(1.5f).testTag("action_continue_approval")
                ) {
                    Text("Review Approval Step")
                }
            }
        }
    }
}
